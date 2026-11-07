package com.chagok.chagok.sdk.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.chagok.chagok.model.Capability
import com.chagok.chagok.model.TurnSignal
import com.chagok.chagok.sdk.vehicle.VehicleGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * F-01 실제 Pleos 차량 연결.
 *
 * | 앱 값        | 출처                                                      |
 * |--------------|-----------------------------------------------------------|
 * | turnSignal   | Vehicle SDK TurnSignal (CAR_LIGHTS)                       |
 * | wiperOn      | Vehicle SDK Wiper, 앞유리 영역 (CAR_WINDOWS)              |
 * | parkingBrake | Vehicle SDK Gear가 P인지 (주차 브레이크 API가 SDK에 없음) |
 * | speedKmh     | CarSpeedSource (안드로이드 CarPropertyManager)            |
 *
 * Gear API는 SDK에서 @hide 표시라 바뀔 수 있다. 막히면 parkingBrake만 다른 출처로 바꾸면 된다.
 * SDK 콜백은 어느 스레드에서 올지 모르므로 값은 StateFlow에만 넣는다 (스레드 안전).
 */
@SuppressLint("MissingPermission")   // 권한은 매니페스트에 선언. 속도 권한은 S-02에서 요청
class PleosVehicleGateway(
    context: Context,
    private val speedSource: CarSpeedSource = CarSpeedSource(context),
    private val capabilityTimeoutMs: Long = 3_000,
) : VehicleGateway {

    private val vehicle = Vehicle(context.applicationContext)
    private var initialized = false
    private var subscribed = false
    private var wiperArea: WindowArea? = null

    private val _connected = MutableStateFlow(false)
    private val _parked = MutableStateFlow(true)            // 기어 값이 오기 전엔 주차로 본다 (안전한 쪽)
    private val _signal = MutableStateFlow(TurnSignal.OFF)
    private val _wiper = MutableStateFlow(false)

    override val connected: StateFlow<Boolean> = _connected.asStateFlow()
    override val speedKmh: Flow<Float> = speedSource.speedKmh
    override val parkingBrake: Flow<Boolean> = _parked
    override val turnSignal: Flow<TurnSignal> = _signal
    override val wiperOn: Flow<Boolean> = _wiper

    // ---- 리스너 ----
    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            _signal.value = when (state) {
                TurnSignalValue.LEFT -> TurnSignal.LEFT
                TurnSignalValue.RIGHT -> TurnSignal.RIGHT
                TurnSignalValue.OFF, null -> TurnSignal.OFF
            }
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "turn signal failed", e) }
    }

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) {
            _wiper.value = state == WiperState.ON
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "wiper failed", e) }
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            if (gear == null || gear == VehicleGear.GEAR_UNKNOWN) return
            _parked.value = gear == VehicleGear.GEAR_PARK
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "gear failed", e) }
    }

    // ---- 연결 ----

    /**
     * 차량 서비스 연결 → 지원 여부 조회 → 구독 시작.
     * 방향지시등·와이퍼·속도가 모두 안 되면 연결 실패로 본다 (S-01 재시도 대상).
     * navi는 여기서 알 수 없으므로 false로 두고 AppState가 내비 연결 결과로 채운다.
     */
    override suspend fun connect(): Result<Capability> = runCatching {
        if (!initialized) {
            vehicle.initialize()
            initialized = true
        }

        val blink = withTimeoutOrNull(capabilityTimeoutMs) { checkTurnSignal() } ?: false
        wiperArea = withTimeoutOrNull(capabilityTimeoutMs) { findWiperArea() }
        val speed = speedSource.start()

        if (!blink && wiperArea == null && !speed) {
            error("차량 서비스에 연결하지 못했어요 (지원 신호 없음)")
        }

        subscribe(blink)
        _connected.value = true
        Capability(speed = speed, blink = blink, wiper = wiperArea != null, navi = false)
    }.onFailure { Log.w(TAG, "connect failed", it) }

    private suspend fun checkTurnSignal(): Boolean = suspendCancellableCoroutine { cont ->
        vehicle.getTurnSignal().checkTurnSignalStateCapabilities(
            onComplete = { config: CapabilityConfig -> if (cont.isActive) cont.resume(config.canRead()) },
            onFailed = { e: Exception ->
                Log.w(TAG, "turn signal capability failed", e)
                if (cont.isActive) cont.resume(false)
            },
        )
    }

    /** 와이퍼를 읽을 수 있는 창 영역. 앞유리(FRONT)가 있으면 그걸, 없으면 첫 번째 */
    private suspend fun findWiperArea(): WindowArea? = suspendCancellableCoroutine { cont ->
        vehicle.getWiper().checkWiperStateCapabilities(
            onComplete = { config: CapabilityConfig, areas: Map<WindowArea, List<WiperState>> ->
                val area = if (!config.canRead()) null
                else areas.keys.firstOrNull { it.toString().contains("FRONT", ignoreCase = true) }
                    ?: areas.keys.firstOrNull()
                if (cont.isActive) cont.resume(area)
            },
            onFailed = { e: Exception ->
                Log.w(TAG, "wiper capability failed", e)
                if (cont.isActive) cont.resume(null)
            },
        )
    }

    private fun subscribe(blink: Boolean) {
        if (subscribed) return
        if (blink) runSafely("register turn signal") { vehicle.getTurnSignal().registerTurnSignalState(turnListener) }
        wiperArea?.let { area -> runSafely("register wiper") { vehicle.getWiper().registerWiperState(area, wiperListener) } }
        runSafely("register gear") { vehicle.getGear().registerGearSelection(gearListener) }
        subscribed = true
    }

    /** 구독 해제 + SDK 해제 (Vehicle.release()) */
    override fun release() {
        if (subscribed) {
            runSafely("unregister turn signal") { vehicle.getTurnSignal().unregisterTurnSignalState(turnListener) }
            wiperArea?.let { area -> runSafely("unregister wiper") { vehicle.getWiper().unregisterWiperState(area, wiperListener) } }
            runSafely("unregister gear") { vehicle.getGear().unregisterGearSelection(gearListener) }
            subscribed = false
        }
        speedSource.stop()
        if (initialized) {
            runSafely("release") { vehicle.release() }
            initialized = false
        }
        _connected.value = false
    }

    private inline fun runSafely(what: String, block: () -> Unit) {
        try { block() } catch (e: Exception) { Log.w(TAG, "$what failed", e) }
    }

    private companion object { const val TAG = "PleosVehicleGateway" }
}

private fun CapabilityConfig.canRead(): Boolean =
    accessType == AccessType.READ || accessType == AccessType.READ_WRITE

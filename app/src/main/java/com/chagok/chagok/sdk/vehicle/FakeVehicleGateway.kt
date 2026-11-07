package com.chagok.chagok.sdk.vehicle

import com.chagok.chagok.model.Capability
import com.chagok.chagok.model.TurnSignal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/**
 * 에뮬레이터 없이 개발하기 위한 가짜. set~ 함수로 값을 직접 쏜다.
 *
 * 속도는 실제 SDK(연속 속성)처럼 연결된 동안 speedPeriodMs마다 계속 보낸다.
 * 그래야 속도 미수신 감시(SignalWatchdog)가 정상으로 본다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeVehicleGateway(
    private val capability: Capability = Capability(speed = true, blink = true, wiper = true, navi = true),
    private val speedPeriodMs: Long = 500,
) : VehicleGateway {
    private val _connected = MutableStateFlow(false)
    private val _speed = MutableStateFlow(0f)
    private val _speedFeed = MutableStateFlow(true)   // false면 속도 값이 끊긴 상황
    private val _brake = MutableStateFlow(true)      // 처음엔 주차 상태
    private val _signal = MutableStateFlow(TurnSignal.OFF)
    private val _wiper = MutableStateFlow(false)

    /** connect() 결과를 바꿔 실패를 흉내 낸다 (S-01 재시도 확인용) */
    var failConnect: Boolean = false

    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    override val speedKmh: Flow<Float> =
        combine(_connected, _speedFeed, _speed) { c, feed, v -> Triple(c, feed, v) }
            .flatMapLatest { (c, feed, v) ->
                if (!c || !feed) emptyFlow()
                else flow { while (true) { emit(v); delay(speedPeriodMs) } }
            }

    override val parkingBrake: Flow<Boolean> = _brake
    override val turnSignal: Flow<TurnSignal> = _signal
    override val wiperOn: Flow<Boolean> = _wiper

    override suspend fun connect(): Result<Capability> {
        if (failConnect) return Result.failure(IllegalStateException("fake connect failed"))
        _connected.value = true
        return Result.success(capability)
    }

    override fun release() { _connected.value = false }

    // ---- 디버그용 입력 ----
    fun setSpeed(v: Float) { _speed.value = v }
    fun setParkingBrake(on: Boolean) { _brake.value = on }
    fun setTurnSignal(s: TurnSignal) { _signal.value = s }
    fun setWiper(on: Boolean) { _wiper.value = on }

    /** 속도 값만 끊기게 한다 (연결은 유지) */
    fun setSpeedFeed(on: Boolean) { _speedFeed.value = on }

    /** SDK 연결이 끊긴 상황 */
    fun simulateDisconnect() { _connected.value = false }
}

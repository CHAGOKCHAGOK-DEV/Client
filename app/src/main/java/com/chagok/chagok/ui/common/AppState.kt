package com.chagok.chagok.ui.common

import com.chagok.chagok.data.repository.MissionResultStore
import com.chagok.chagok.domain.driving.DriveStateDetector
import com.chagok.chagok.domain.driving.TurnSignalTracker
import com.chagok.chagok.domain.mission.MissionEngine
import com.chagok.chagok.model.*
import com.chagok.chagok.permission.PermissionChecker
import com.chagok.chagok.sdk.navigation.NaviGateway
import com.chagok.chagok.sdk.speech.SpeechPort
import com.chagok.chagok.sdk.vehicle.VehicleGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/** 차량 신호를 한 덩어리로 보여 주는 값 (화면 4-1의 vehicle) */
data class VehicleSnapshot(
    val speedKmh: Float = 0f,
    val parkingBrake: Boolean = true,
    val turnSignal: TurnSignal = TurnSignal.OFF,
    val wiperOn: Boolean = false,
    val sdkConnected: Boolean = false,
)

/**
 * 앱 전역 상태 모음. UI는 여기 있는 StateFlow만 구독한다.
 * scope는 한 스레드(Main)에서 돌아야 한다.
 */
class AppState(
    scope: CoroutineScope,
    private val vehicle: VehicleGateway,
    navi: NaviGateway,
    speech: SpeechPort,
    store: MissionResultStore,
    val permissionChecker: PermissionChecker,
    private val nowMs: () -> Long,
    autoStart: Boolean = false,                 // 🟡 열린 이슈 #4. 임시로 꺼 둠
) {
    // ---- 주행 상태 (F-02) ----
    private val detector = DriveStateDetector(scope, vehicle.speedKmh, vehicle.parkingBrake)
    val driveState: StateFlow<DriveState> = detector.state

    // ---- 차량 신호 (F-03, F-05) ----
    private val signalTracker = TurnSignalTracker(scope, vehicle.turnSignal, nowMs)

    val vehicleSnapshot: StateFlow<VehicleSnapshot> = combine(
        vehicle.speedKmh, vehicle.parkingBrake, vehicle.turnSignal, vehicle.wiperOn, vehicle.connected,
    ) { speed, brake, signal, wiper, connected ->
        VehicleSnapshot(speed, brake, signal, wiper, connected)
    }.stateIn(scope, SharingStarted.Eagerly, VehicleSnapshot())

    // ---- 내비 (F-07) ----
    val naviInfo: StateFlow<NaviInfo> =
        navi.naviInfo.stateIn(scope, SharingStarted.Eagerly, NaviInfo())

    // ---- 권한, capability ----
    val permission: StateFlow<PermissionState> = permissionChecker.state

    private val _capability = MutableStateFlow(Capability())
    val capability: StateFlow<Capability> = _capability.asStateFlow()

    // ---- 출발 버튼(ready)과 부기장 모드(mode) ----
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val autoStartFlag = MutableStateFlow(autoStart)

    /** 부기장 모드: 출발 버튼을 눌렀거나 자동시작이 켜져 있고, 주행 중일 때 ON (S-06 e 상태 판단용) */
    val mode: StateFlow<Boolean> = combine(_ready, autoStartFlag) { r, a -> r || a }
        .stateIn(scope, SharingStarted.Eagerly, false)

    // ---- 미션 (F-09) ----
    private val engine = MissionEngine(
        scope = scope,
        driveState = driveState,
        blinkEvents = signalTracker.events,
        wiperOn = vehicle.wiperOn,
        naviInfo = navi.naviInfo,
        speech = speech,
        store = store,
        nowMs = nowMs,
        enabled = { mode.value && driveState.value == DriveState.DRIVING },
    )
    val missionState: StateFlow<MissionState?> = engine.state
    val missionCurrent: StateFlow<MissionCode?> = engine.current

    // ---- 동작 ----

    /** S-01: 차량 연결. 성공하면 capability 저장 */
    suspend fun connectVehicle(): Result<Capability> =
        vehicle.connect().onSuccess { _capability.value = it }

    /** S-05 [부기장 출발!] 토글. 연결이 끊기면 켤 수 없다 */
    fun toggleReady() {
        if (!_ready.value && !vehicleSnapshot.value.sdkConnected) return
        _ready.value = !_ready.value
    }

    fun setAutoStart(on: Boolean) { autoStartFlag.value = on }

    /** 주행 종료(주차 브레이크 ON) 시 출발 상태 초기화 */
    fun resetReady() { _ready.value = false }

    fun release() = vehicle.release()
}
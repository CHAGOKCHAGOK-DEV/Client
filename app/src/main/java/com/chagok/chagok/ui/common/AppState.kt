package com.chagok.chagok.ui.common

import com.chagok.chagok.data.repository.InMemoryTripStore
import com.chagok.chagok.data.repository.MissionResultStore
import com.chagok.chagok.data.repository.TripStore
import com.chagok.chagok.domain.driving.DriveStateDetector
import com.chagok.chagok.domain.driving.TurnSignalTracker
import com.chagok.chagok.domain.driving.VehicleHealth
import com.chagok.chagok.domain.driving.vehicleHealthFlow
import com.chagok.chagok.domain.mission.MissionEngine
import com.chagok.chagok.domain.mission.MissionTiming
import com.chagok.chagok.domain.trip.TripTracker
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
 *
 * 다른 담당자가 채울 연결 지점:
 * - setNaviSpeaking(): 오디오 포커스(내비 안내 재생 중)
 */
class AppState(
    scope: CoroutineScope,
    private val vehicle: VehicleGateway,
    private val navi: NaviGateway,
    private val speech: SpeechPort,
    store: MissionResultStore,
    val permissionChecker: PermissionChecker,
    private val nowMs: () -> Long,
    autoStart: Boolean = false,                 // 🟡 열린 이슈 #4. 임시로 꺼 둠
    val tripStore: TripStore = InMemoryTripStore(),
    speedTimeoutMs: Long = 5_000,               // 데이터 미수신 판단 5초 (초기값)
    longStopMs: Long = 5 * 60_000L,             // 🟡 열린 이슈 #3
) {
    val missionStore: MissionResultStore = store

    // 속도는 여러 곳(판정, 스냅샷, 감시)에서 쓰므로 한 번만 구독해서 나눠 쓴다
    private val speed: SharedFlow<Float> =
        vehicle.speedKmh.shareIn(scope, SharingStarted.Eagerly, replay = 1)

    // ---- 주행 상태 (F-02) ----
    private val detector = DriveStateDetector(scope, speed, vehicle.parkingBrake)
    val driveState: StateFlow<DriveState> = detector.state

    /** F-15-1 주행 중 UI 제한 (C-02). 정차가 확정되면 풀린다 */
    val uiLocked: StateFlow<Boolean> = driveState.map { it == DriveState.DRIVING }
        .stateIn(scope, SharingStarted.Eagerly, false)

    // ---- 차량 신호 (F-03, F-05) ----
    private val signalTracker = TurnSignalTracker(scope, vehicle.turnSignal, nowMs)

    val vehicleSnapshot: StateFlow<VehicleSnapshot> = combine(
        speed.onStart { emit(0f) }, vehicle.parkingBrake, vehicle.turnSignal, vehicle.wiperOn, vehicle.connected,
    ) { speed, brake, signal, wiper, connected ->
        VehicleSnapshot(speed, brake, signal, wiper, connected)
    }.stateIn(scope, SharingStarted.Eagerly, VehicleSnapshot())

    /**
     * 연결·속도 수신 상태. OK가 아니면 미션 전체 중지.
     * 화면 이동은 없다: S-05는 상태 칩·출발 버튼, S-06은 g "차량 신호 확인 중"으로만 알리고 돌아오면 자동 재개.
     */
    val vehicleHealth: StateFlow<VehicleHealth> =
        vehicleHealthFlow(vehicle.connected, speed, speedTimeoutMs)
            .stateIn(scope, SharingStarted.Eagerly, VehicleHealth.DISCONNECTED)

    // ---- 내비 (F-07) ----
    val naviInfo: StateFlow<NaviInfo> =
        navi.naviInfo.stateIn(scope, SharingStarted.Eagerly, NaviInfo())

    private val naviSpeaking = MutableStateFlow(false)

    // ---- 권한, capability ----
    val permission: StateFlow<PermissionState> = permissionChecker.state

    private val _capability = MutableStateFlow(Capability())
    val capability: StateFlow<Capability> = _capability.asStateFlow()

    // ---- 출발 버튼(ready)과 부기장 모드(mode) ----
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val autoStartFlag = MutableStateFlow(autoStart)

    /** 부기장 모드: 출발 버튼을 눌렀거나 자동시작이 켜져 있으면 ON (S-06 e 상태 판단용) */
    val mode: StateFlow<Boolean> = combine(_ready, autoStartFlag) { r, a -> r || a }
        .stateIn(scope, SharingStarted.Eagerly, false)

    // ---- 미션 (F-09) ----
    // trip보다 먼저 만들어야 trip 종료 콜백에서 engine.reset()을 부를 수 있다
    private lateinit var trips: TripTracker

    private val engine = MissionEngine(
        scope = scope,
        driveState = driveState,
        blinkEvents = signalTracker.events,
        wiperOn = vehicle.wiperOn,
        naviInfo = navi.naviInfo,
        speech = speech,
        store = missionStore,
        nowMs = nowMs,
        intervalMs = { MissionTiming.INTERVAL_MS },
        isNaviSpeaking = { naviSpeaking.value },
        enabled = {
            trips.current.value != null &&
                driveState.value == DriveState.DRIVING &&
                vehicleHealth.value == VehicleHealth.OK
        },
        isSupported = { code -> _capability.value.supports(code) },
        tripId = { trips.current.value?.id },
    )
    val missionState: StateFlow<MissionState?> = engine.state
    val missionCurrent: StateFlow<MissionCode?> = engine.current
    val missionJudged: StateFlow<JudgedMission?> = engine.judged

    // ---- 주행 기록 ----
    init {
        trips = TripTracker(
            scope = scope,
            driveState = driveState,
            mode = mode,
            store = tripStore,
            nowMs = nowMs,
            longStopMs = longStopMs,
            onEnded = {
                engine.reset()
                _ready.value = false        // 주행이 끝나면 출발 버튼은 다시 꺼진 상태로
            },
        )
        // F-15-3 데이터 미수신(연결 끊김·속도 미수신): 대기 중인 미션을 버린다
        vehicleHealth.filter { it != VehicleHealth.OK }.onEach { engine.reset() }.launchIn(scope)
    }

    /** 진행 중인 주행 (없으면 null) */
    val currentTrip: StateFlow<Trip?> get() = trips.current

    /** 가장 최근에 끝난 주행 (S-07 "주행 종료" 판단용) */
    val lastEndedTrip: StateFlow<Trip?> get() = trips.lastEnded

    // ---- 동작 ----

    /**
     * S-01: 차량 연결. 성공하면 capability 저장.
     * 내비는 따로 연결하고, 실패해도 차량 연결은 성공으로 본다 (NEXT_TURN만 꺼짐).
     */
    suspend fun connectVehicle(): Result<Capability> =
        vehicle.connect()
            .map { it.copy(navi = navi.connect()) }
            .onSuccess { _capability.value = it }

    /** 다시 연결: 구독을 풀고 다시 연결해 capability를 새로 받는다 (S-02에서 권한 허용 뒤) */
    suspend fun reconnectVehicle(): Result<Capability> {
        vehicle.release()
        return connectVehicle()
    }

    /** S-05 [부기장 출발!] 토글. 연결이 끊기면 켤 수 없다 */
    fun toggleReady() {
        if (!_ready.value && vehicleHealth.value == VehicleHealth.DISCONNECTED) return
        _ready.value = !_ready.value
    }

    fun setAutoStart(on: Boolean) { autoStartFlag.value = on }

    /** 오디오 포커스 담당이 호출: 내비 음성 안내 재생 중이면 true */
    fun setNaviSpeaking(on: Boolean) { naviSpeaking.value = on }

    /**
     * S-06 [정차 완료]: 주행을 USER_STOP으로 끝낸다 → S-07.
     * 버튼은 정차 중에만 보이지만, 주행 판정 중이면 여기서도 한 번 더 막는다.
     */
    fun finishTrip() {
        if (driveState.value == DriveState.DRIVING) return
        trips.endNow(TripEndReason.USER_STOP)
    }

    /** 주행 종료 시 출발 상태 초기화 (trip 종료 시 자동으로도 불림) */
    fun resetReady() { _ready.value = false }

    /** F-11 TTS. S-04 첫 저장 인사 등 */
    suspend fun speak(text: String) = speech.speak(text)

    /** 앱 종료: 진행 중인 주행을 APP_EXIT로 끝내고 SDK 해제 */
    fun release() {
        trips.endNow(TripEndReason.APP_EXIT)
        navi.release()
        vehicle.release()
        speech.release()
    }
}

/** 이 차에서 해당 미션이 되는가 (S-05 미션 가능 목록, 엔진 후보 차단) */
fun Capability.supports(code: MissionCode): Boolean = when (code) {
    MissionCode.BLINK -> blink
    MissionCode.WIPER -> wiper
    MissionCode.NEXT_TURN -> navi
    MissionCode.NEAR_DEST -> navi
    // 🟡 엔진 미구현(이슈 7·8). 붙이기 전까지는 목록에서 "이 차는 아직 안 돼요"
    else -> false
}

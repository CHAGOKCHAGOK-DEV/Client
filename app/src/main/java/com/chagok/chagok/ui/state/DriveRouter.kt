package com.chagok.chagok.ui.state

import com.chagok.chagok.model.DriveState
import com.chagok.chagok.model.Trip
import com.chagok.chagok.ui.common.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/** 온보딩 이후 화면 자동 전환 결과 */
data class DriveRoute(
    val screen: Screen,                      // MAIN / DRIVING / SUMMARY
    val endedTrip: Trip? = null,             // SUMMARY일 때 요약할 주행
)

/**
 * 온보딩(S-01~S-04)이 끝난 뒤 S-05 / S-06 / S-07 사이 자동 전환 (2. 상태 규칙).
 *
 * - 주행 중이면 S-06 (모드가 꺼져 있어도 S-06의 e 상태)
 * - 주행이 끝났고 아직 [메인으로]를 안 눌렀으면 S-07 "오늘도 수고했어요"
 *   주행 종료 = [정차 완료] / 주차 브레이크 / 정차 5분 (TripTracker)
 * - 주행(trip) 중 정차는 S-06 그대로 (f "잠깐 멈췄어요"). 신호 대기마다 화면이 바뀌지 않는다
 * - 그 밖에는 S-05
 *
 * 연결 끊김·속도 미수신은 화면을 옮기지 않는다 (S-05 상태 칩, S-06 g 상태가 알린다).
 *
 * lockOverlay: S-02·S-03·S-04에 있을 때 주행 판정이 나면 덮는 C-02 잠금
 */
class DriveRouter(
    scope: CoroutineScope,
    private val app: AppState,
) {
    private val acknowledgedTripId = MutableStateFlow<String?>(null)

    val lockOverlay: StateFlow<Boolean> = app.uiLocked

    val route: StateFlow<DriveRoute> = combine(
        app.driveState, app.currentTrip, app.lastEndedTrip, acknowledgedTripId,
    ) { drive, current, ended, acked ->
        when {
            drive == DriveState.DRIVING -> DriveRoute(Screen.DRIVING)
            ended != null && ended.id != acked -> DriveRoute(Screen.SUMMARY, endedTrip = ended)
            current != null -> DriveRoute(Screen.DRIVING)          // 주행 중 정차 (f)
            else -> DriveRoute(Screen.MAIN)
        }
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, DriveRoute(Screen.MAIN))

    /** S-07 [메인으로] */
    fun backToMain() { acknowledgedTripId.value = app.lastEndedTrip.value?.id }
}

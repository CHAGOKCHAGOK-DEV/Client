package com.chagok.chagok.ui.state

import com.chagok.chagok.domain.driving.LimitedReason
import com.chagok.chagok.model.DriveState
import com.chagok.chagok.model.Trip
import com.chagok.chagok.ui.common.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/** 온보딩 이후 화면 자동 전환 결과 */
data class DriveRoute(
    val screen: Screen,                      // MAIN / DRIVING / SUMMARY / LIMITED
    val endedTrip: Trip? = null,             // SUMMARY일 때: null이면 "잠깐 쉬어가요", 있으면 "오늘도 수고했어요"
    val limitedReason: LimitedReason? = null,
)

/**
 * 온보딩(S-01~S-04)이 끝난 뒤 S-05 / S-06 / S-07 / S-08 사이 자동 전환 (2. 상태 규칙).
 *
 * - 제한 사유가 있으면 S-08
 * - 주행 중이면 S-06 (모드가 꺼져 있어도 S-06의 e 상태)
 * - 주행이 끝났고 아직 [메인으로]를 안 눌렀으면 S-07 "오늘도 수고했어요"
 * - 주행 중 정차가 pauseSummaryDelayMs 이어지면 S-07 "잠깐 쉬어가요" (그전까지는 S-06 유지)
 *   🟡 열린 이슈 #2: 신호 대기마다 화면이 바뀌지 않게 지연. 설계서 권고 10초
 * - [계속 가기]를 누르면 다음 출발 때까지 S-06에 머문다
 * - 그 밖에는 S-05
 *
 * lockOverlay: S-02·S-03·S-04에 있을 때 주행 판정이 나면 덮는 C-02 잠금
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveRouter(
    scope: CoroutineScope,
    private val app: AppState,
    private val pauseSummaryDelayMs: Long = 10_000,
) {
    private val acknowledgedTripId = MutableStateFlow<String?>(null)
    private val pauseDismissed = MutableStateFlow(false)

    val lockOverlay: StateFlow<Boolean> = app.uiLocked

    val route: StateFlow<DriveRoute> = combine(
        combine(app.driveState, app.currentTrip, app.lastEndedTrip) { d, cur, ended -> Triple(d, cur, ended) },
        app.limitedReason, acknowledgedTripId, pauseDismissed,
    ) { (drive, current, ended), limited, acked, dismissed ->
        Input(drive, current, ended, limited, acked, dismissed)
    }
        .transformLatest { i ->
            when {
                i.limited != null -> emit(DriveRoute(Screen.LIMITED, limitedReason = i.limited))
                i.drive == DriveState.DRIVING -> emit(DriveRoute(Screen.DRIVING))
                i.ended != null && i.ended.id != i.acked -> emit(DriveRoute(Screen.SUMMARY, endedTrip = i.ended))
                i.drive == DriveState.STOPPED && i.current != null -> {
                    emit(DriveRoute(Screen.DRIVING))
                    if (!i.dismissed) {
                        delay(pauseSummaryDelayMs)
                        emit(DriveRoute(Screen.SUMMARY))
                    }
                }
                else -> emit(DriveRoute(Screen.MAIN))
            }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, DriveRoute(Screen.MAIN))

    init {
        // 다시 출발하면 [계속 가기] 표시를 지운다 (다음 정차 때 다시 요약)
        scope.launchIn(app.driveState.filter { it == DriveState.DRIVING }.onEach { pauseDismissed.value = false })
    }

    /** S-07 [계속 가기] (잠깐 정차일 때) */
    fun continueDriving() { pauseDismissed.value = true }

    /** S-07 [메인으로] (주행 종료일 때) */
    fun backToMain() { acknowledgedTripId.value = app.lastEndedTrip.value?.id }

    private data class Input(
        val drive: DriveState, val current: Trip?, val ended: Trip?,
        val limited: LimitedReason?, val acked: String?, val dismissed: Boolean,
    )
}

private fun CoroutineScope.launchIn(flow: Flow<*>) = flow.launchIn(this)

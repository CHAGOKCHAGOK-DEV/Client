package com.chagok.chagok.ui.state

import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.model.MissionOutcome
import com.chagok.chagok.model.MissionResult
import com.chagok.chagok.model.Trip
import com.chagok.chagok.model.TripEndReason
import com.chagok.chagok.ui.common.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/** S-07 결과 행 칩: ✓ "성공" / ○ "다음에!" (오답·무응답 건너뜀). "틀렸어요"는 쓰지 않는다 */
enum class SummaryRowResult { SUCCESS, NEXT_TIME }

data class SummaryRow(val code: MissionCode, val result: SummaryRowResult)

/** S07_a~e */
enum class SummaryKind {
    NORMAL,        // a 기본 (e 6개 이상도 같은 문구)
    ALL_CORRECT,   // b 전부 성공
    NONE_CORRECT,  // c 하나도 못 맞힘
    EMPTY,         // d 미션 0개
}

/** S-07이 그릴 값 */
data class SummaryUiState(
    val kind: SummaryKind,
    val bubble: String,           // 왼쪽 말풍선
    val summaryLine: String,      // 제목 아래 한 줄
    val rows: List<SummaryRow>,   // 최근 MAX_LIST개, 오래된 것이 위
    val footer: String,
    val idleNotice: Boolean,      // f 정차 5분 자동 종료면 제목 위 안내 칩
    val offlineNotice: Boolean,   // g 인터넷 연결이 없으면 목록 아래 안내 칩
    val locked: Boolean,          // h 요약 중 다시 출발 → C-02 잠금
)

/**
 * S-07 주행 종료 요약 (피그마 S07 a~h).
 * - 이번 주행(tripId) 결과만. 오답도 완료로 세고, 무응답 건너뜀은 "다음에!"로 보여 준다
 * - 묻지도 못하고 버린 후보(EXPIRED), 묻거나 듣는 중에 주행이 끝난 미션(interrupted)은 넣지 않는다
 * - [메인으로]: 첫 탭만 받는다. 출발 버튼 끄고(ready=false) S-05
 *
 * - g: 이번 주행 기록을 아직 서버에 못 보냈으면 안내 칩. 경고 아님, [메인으로]는 그대로
 *   인터넷이 없거나, 보내 봤는데 실패해서 기기에 남아 있으면(pendingTrips) 보인다. 보내지면 사라진다
 */
class SummaryScreen(
    scope: CoroutineScope,
    private val app: AppState,
    private val router: DriveRouter,
    online: Flow<Boolean> = flowOf(true),
    pendingTrips: Flow<Set<String>> = flowOf(emptySet()),
) {
    val state: StateFlow<SummaryUiState> = combine(
        app.lastEndedTrip, app.missionStore.results, app.uiLocked, online, pendingTrips,
    ) { trip, results, locked, isOnline, pending ->
        summarize(trip, results).copy(
            locked = locked,
            offlineNotice = trip != null && (!isOnline || trip.id in pending),
        )
    }.stateIn(scope, SharingStarted.Eagerly, summarize(app.lastEndedTrip.value, emptyList()))

    private var leaving = false

    /** [메인으로] (연속 탭은 첫 탭만) */
    fun onMain() {
        if (leaving || app.uiLocked.value) return
        leaving = true
        app.resetReady()
        router.backToMain()
    }

    companion object {
        const val MAX_LIST = 5

        fun summarize(trip: Trip?, all: List<MissionResult>): SummaryUiState {
            val shown = all
                .filter { trip != null && it.tripId == trip.id && !it.interrupted && it.outcome != MissionOutcome.EXPIRED }
                .sortedBy { it.triggeredAtMs }
            val total = shown.size
            val success = shown.count { it.succeeded }

            val kind = when {
                total == 0 -> SummaryKind.EMPTY
                success == total -> SummaryKind.ALL_CORRECT
                success == 0 -> SummaryKind.NONE_CORRECT
                else -> SummaryKind.NORMAL
            }
            val (bubble, line) = when (kind) {
                SummaryKind.EMPTY -> ScreenTexts.SUMMARY_BUBBLE_EMPTY to ScreenTexts.SUMMARY_LINE_EMPTY
                SummaryKind.ALL_CORRECT -> ScreenTexts.SUMMARY_BUBBLE_ALL to "이번 주행 미션 ${total}개 모두 성공!"
                SummaryKind.NONE_CORRECT -> ScreenTexts.SUMMARY_BUBBLE_NONE to "이번 주행에서 미션 ${total}개를 만났어요"
                SummaryKind.NORMAL -> ScreenTexts.SUMMARY_BUBBLE to "이번 주행 미션 ${total}개 중 ${success}개 성공"
            }
            return SummaryUiState(
                kind = kind,
                bubble = bubble,
                summaryLine = line,
                rows = shown.takeLast(MAX_LIST).map {
                    SummaryRow(it.code, if (it.succeeded) SummaryRowResult.SUCCESS else SummaryRowResult.NEXT_TIME)
                },
                footer = if (total > MAX_LIST) ScreenTexts.SUMMARY_FOOTER_RECENT else ScreenTexts.SUMMARY_FOOTER,
                idleNotice = trip?.endReason == TripEndReason.IDLE_TIMEOUT,
                offlineNotice = false,
                locked = false,
            )
        }
    }
}

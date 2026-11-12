package com.chagok.chagok.ui.state

import com.chagok.chagok.data.repository.OnboardingRepository
import com.chagok.chagok.domain.driving.VehicleHealth
import com.chagok.chagok.model.DriveState
import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.model.MissionState
import com.chagok.chagok.ui.common.AppState
import com.chagok.chagok.ui.common.supports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import com.chagok.chagok.model.AgeGroup

/** S05-01 상태 칩 */
data class StatusChipUi(val text: String, val isError: Boolean)

/** S05-08 미션 가능 목록 한 줄 */
data class MissionAvailabilityUi(val code: MissionCode, val title: String, val available: Boolean)

/** S-05가 그릴 값 */
data class MainUiState(
    val statusChip: StatusChipUi,
    val profileChip: String?,              // S05-02 "은우 부기장 · 7~8세" (프로필 없으면 null)
    val mascotMessage: String,             // S05-03
    val todayDone: Int,                    // S05-04 "오늘의 미션 5개 중 2개 완료"
    val todayGoal: Int,
    val missions: List<MissionAvailabilityUi>,
    val startOn: Boolean,                  // S05-07 [부기장 출발!] 토글 상태
    val startEnabled: Boolean,             // 연결 끊김이면 비활성
) {
    /** 진행 링 퍼센트 (목표 초과 시 100) */
    val todayPercent: Int get() = if (todayGoal <= 0) 0 else minOf(100, todayDone * 100 / todayGoal)
}

/** 미션 이름 (S-05 목록, S-07 요약에서 같이 쓰면 된다) */
fun MissionCode.title(): String = when (this) {
    MissionCode.BLINK -> "깜빡이 맞히기"
    MissionCode.WIPER -> "와이퍼"
    MissionCode.NEXT_TURN -> "다음 방향"
    MissionCode.NEAR_DEST -> "곧 도착"
}

/**
 * S-05 메인 (F-01, F-02).
 * 오늘 진행 = 오늘 날짜(zone 기준)에 시작된 미션 중 DONE(정답·오답 모두) 수.
 * 건너뜀(SKIPPED)·만료(EXPIRED)는 아이가 실제로 한 게 아니라서 세지 않는다.
 */
class MainScreen(
    private val scope: CoroutineScope,
    private val app: AppState,
    onboarding: OnboardingRepository,
    private val speak: suspend (String) -> Unit,
    private val nowMs: () -> Long,
    private val zone: ZoneId = ZoneId.of("Asia/Seoul"),
    private val todayGoal: Int = 5,                      // 🟡 열린 이슈 #7
) {
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val todayDone: Flow<Int> = app.missionStore.results.map { list ->
        val today = Instant.ofEpochMilli(nowMs()).atZone(zone).toLocalDate()
        list.count {
            it.state == MissionState.DONE &&
                Instant.ofEpochMilli(it.triggeredAtMs).atZone(zone).toLocalDate() == today
        }
    }

    private val chip: Flow<StatusChipUi> = combine(app.vehicleHealth, app.driveState) { h, d ->
        when {
            h == VehicleHealth.DISCONNECTED -> StatusChipUi(ScreenTexts.CHIP_DISCONNECTED, isError = true)
            d == DriveState.PARKED -> StatusChipUi(ScreenTexts.CHIP_PARKED, isError = false)
            else -> StatusChipUi(ScreenTexts.CHIP_STOPPED, isError = false)
        }
    }

    private val mascot: Flow<String> = combine(app.ready, app.lastEndedTrip) { ready, ended ->
        val today = Instant.ofEpochMilli(nowMs()).atZone(zone).toLocalDate()
        val endedToday = ended?.endedAtMs?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true
        when {
            ready -> ScreenTexts.MAIN_READY
            endedToday -> ScreenTexts.MAIN_DONE_TODAY
            else -> ScreenTexts.MAIN_DEFAULT
        }
    }

    val state: StateFlow<MainUiState> = combine(
        combine(chip, onboarding.profile, mascot) { c, p, m -> Triple(c, p, m) },
        todayDone,
        app.capability,
        app.ready,
        app.vehicleHealth,
    ) { (c, p, m), done, cap, ready, health ->
        MainUiState(
            statusChip = c,
            profileChip = p?.let { "${it.nickname} 부기장 · ${AgeGroup.fromCode(it.ageGroup)?.label ?: it.ageGroup}" },
            mascotMessage = m,
            todayDone = done,
            todayGoal = todayGoal,
            missions = MissionCode.entries.map { MissionAvailabilityUi(it, it.title(), cap.supports(it)) },
            startOn = ready,
            startEnabled = health != VehicleHealth.DISCONNECTED,
        )
    }.stateIn(
        scope, SharingStarted.Eagerly,
        MainUiState(StatusChipUi(ScreenTexts.CHIP_PARKED, false), null, ScreenTexts.MAIN_DEFAULT, 0, todayGoal, emptyList(), false, false),
    )

    init {
        // S-05 "SDK 연결 끊김 → 제한 모드 안내 → S-08", 지원 미션 0개도 S-08
        scope.launch {
            app.limitedReason.filterNotNull().collect { _events.emit(UiEvent.Navigate(Screen.LIMITED)) }
        }
    }

    /** [부기장 출발!] 토글. 켜지면 준비 완료 음성 1회 */
    fun onStartToggle() {
        if (!state.value.startEnabled && !state.value.startOn) return
        val wasOn = app.ready.value
        app.toggleReady()
        if (!wasOn && app.ready.value) scope.launch { speak(ScreenTexts.MAIN_READY_TTS) }
    }

    /** 프로필 칩 / 설정 탭 → S-04 수정 모드 */
    fun onProfileClick() { _events.tryEmit(UiEvent.Navigate(Screen.PROFILE)) }

    /** 배지 탭 → 2차 안내 */
    fun onBadgeTab() { _events.tryEmit(UiEvent.Toast(ScreenTexts.BADGE_TOAST)) }
}

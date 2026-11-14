package com.chagok.chagok.ui.state

import com.chagok.chagok.data.repository.OnboardingRepository
import com.chagok.chagok.domain.driving.VehicleHealth
import com.chagok.chagok.domain.mission.MissionDefinitions
import com.chagok.chagok.model.AgeGroup
import com.chagok.chagok.model.DriveState
import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.model.MissionResult
import com.chagok.chagok.model.MissionState
import com.chagok.chagok.ui.common.AppState
import com.chagok.chagok.ui.common.supports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** S05-01 상태 칩 */
data class StatusChipUi(val text: String, val isError: Boolean)

/** 미션 행 상태 (피그마 S05_미션행의 상태 4가지) */
enum class MissionRowStatus {
    AVAILABLE,     // 사용 가능
    DONE_TODAY,    // 오늘 완료
    ON_DRIVE,      // 출발하면 시작해요 (주행 중 조건이 맞아야 시작하는 미션. 아직 해당 미션 없음)
    UNSUPPORTED,   // 이 차는 아직 안 돼요
}

/** S05-08 미션 목록 한 줄 */
data class MissionAvailabilityUi(val code: MissionCode, val title: String, val status: MissionRowStatus) {
    /** 이 차에서 되는 미션인가 */
    val available: Boolean get() = status != MissionRowStatus.UNSUPPORTED
}

/** S-05가 그릴 값 */
data class MainUiState(
    val statusChip: StatusChipUi,
    val profileChip: String?,              // S05-02 "은우 부기장 · 7~8세" (프로필 없으면 null)
    val profileInitial: String?,           // 프로필 칩 동그라미 안 첫 글자 "은"
    val mascotMessage: String,             // S05-03
    val todayDone: Int,                    // S05-04 "오늘의 미션 5개 중 2개 완료"
    val todayGoal: Int,
    val missions: List<MissionAvailabilityUi>,
    val startOn: Boolean,                  // S05-07 [부기장 출발!] 토글 상태
    val startEnabled: Boolean,             // 연결 끊김이면 비활성
) {
    /** 진행 바 퍼센트 (목표 초과 시 100) */
    val todayPercent: Int get() = if (todayGoal <= 0) 0 else minOf(100, todayDone * 100 / todayGoal)
}

/** 미션 이름 (S-05 목록, S-06 이름 칩, S-07 요약에서 같이 쓴다) */
fun MissionCode.title(): String = MissionDefinitions.of(this).name

/**
 * S-05 메인 (F-01, F-02).
 * 오늘 진행 = 오늘 날짜(zone 기준)에 시작된 미션 중 DONE(정답·오답 모두) 수.
 * 건너뜀(SKIPPED)·만료(EXPIRED)는 아이가 실제로 한 게 아니라서 세지 않는다.
 *
 * @param homeMissions 목록에 보여 줄 미션과 순서 (피그마 S05_1: 깜빡이 소리 · 다음 회전 · 비 오는 날)
 */
class MainScreen(
    private val scope: CoroutineScope,
    private val app: AppState,
    onboarding: OnboardingRepository,
    private val speak: suspend (String) -> Unit,
    private val nowMs: () -> Long,
    private val zone: ZoneId = ZoneId.of("Asia/Seoul"),
    private val todayGoal: Int = 5,                      // 열린 이슈 #7: 상수
    private val homeMissions: List<MissionCode> = listOf(MissionCode.BLINK, MissionCode.NEXT_TURN, MissionCode.WIPER),
) {
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    /** 오늘 완료(DONE)한 미션 결과 */
    private val todayDone: Flow<List<MissionResult>> = app.missionStore.results.map { list ->
        val today = Instant.ofEpochMilli(nowMs()).atZone(zone).toLocalDate()
        list.filter {
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
        val doneCodes = done.map { it.code }.toSet()
        MainUiState(
            statusChip = c,
            profileChip = p?.let { "${it.nickname} 부기장 · ${ageLabel(it.ageGroup)}" },
            profileInitial = p?.nickname?.take(1),
            mascotMessage = m,
            todayDone = done.size,
            todayGoal = todayGoal,
            missions = homeMissions.map { code ->
                val status = when {
                    !cap.supports(code) -> MissionRowStatus.UNSUPPORTED
                    code in doneCodes -> MissionRowStatus.DONE_TODAY
                    else -> MissionRowStatus.AVAILABLE
                }
                MissionAvailabilityUi(code, code.title(), status)
            },
            startOn = ready,
            startEnabled = health != VehicleHealth.DISCONNECTED,
        )
    }.stateIn(
        scope, SharingStarted.Eagerly,
        MainUiState(
            StatusChipUi(ScreenTexts.CHIP_PARKED, false), null, null, ScreenTexts.MAIN_DEFAULT,
            0, todayGoal, emptyList(), startOn = false, startEnabled = false,
        ),
    )

    /** [부기장 출발!] 토글. 켜지면 준비 완료 음성 1회 */
    fun onStartToggle() {
        if (!state.value.startEnabled && !state.value.startOn) return
        val wasOn = app.ready.value
        app.toggleReady()
        if (!wasOn && app.ready.value) scope.launch { speak(ScreenTexts.MAIN_READY_TTS) }
    }

    /** 프로필 칩 / 설정 탭 → S-04 수정 모드 */
    fun onProfileClick() { _events.tryEmit(UiEvent.Navigate(Screen.PROFILE)) }

    /** 칩 표기는 "7~8세" (S-04 버튼의 "7 ~ 8세"에서 띄어쓰기를 뺀다) */
    private fun ageLabel(code: String): String =
        (AgeGroup.fromCode(code)?.label ?: code).replace(" ~ ", "~")
}

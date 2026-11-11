package com.chagok.chagok.ui.state

import com.chagok.chagok.data.repository.OnboardingRepository
import com.chagok.chagok.ui.common.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** S-01 단계 (진행 바 3칸) */
enum class StartupStep(val index: Int) { CONNECT(1), PERMISSION(2), SETTINGS(3) }

/** S-01 오류 종류 (화면설계서 S-01 실패 종류별 문구) */
enum class StartupError {
    CAR_CONN,         // 1/3 연결 실패·10초 초과 → [다시 시도] [닫기]
    CAR_UNSUPPORTED,  // 필수 신호(속도) 미지원 → [닫기]만
    PERM_READ,        // 2/3 권한 상태 읽기 실패 → [다시 시도] [닫기]
}

/** S-01이 그릴 값 */
data class StartupUiState(
    val step: StartupStep = StartupStep.CONNECT,
    val message: String = ScreenTexts.STARTUP_CONNECT,   // S01-03 상태 문구
    val error: Boolean = false,
    val errorKind: StartupError? = null,
    val attempts: Int = 0,                               // 실패한 연결 시도 수
    val showRetry: Boolean = false,                      // [다시 시도]
    val showLimitedMode: Boolean = false,                // [제한 모드로 보기] (3회 실패부터)
    val destination: Screen? = null,                     // 정해지면 화면이 이동
) {
    val progress: String get() = "${step.index} / 3"
}

/**
 * S-01 시작·초기화 (F-01, F-01-1).
 * 1/3 차량 연결 → 2/3 권한 상태 읽기 → 3/3 미션팩 확인(실패 무시) + 동의·프로필 확인 → 다음 화면.
 * 최소 표시 시간 없이 단계가 끝나는 대로 진행한다.
 *
 * @param missionPackSync 서버 미션팩 최신 버전 확인. 실패해도 진행 (앱 내장팩 사용)
 */
class StartupFlow(
    private val scope: CoroutineScope,
    private val app: AppState,
    private val onboarding: OnboardingRepository,
    private val missionPackSync: suspend () -> Unit = {},
    private val maxAttemptsBeforeLimited: Int = 3,
    private val stepTimeoutMs: Long = 10_000,             // 🟡 한 단계 제한 시간
) {
    private val _state = MutableStateFlow(StartupUiState())
    val state: StateFlow<StartupUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** 앱 실행 직후 한 번 */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { run() }
    }

    /** [다시 시도]: 1/3부터 다시 */
    fun retry() {
        if (job?.isActive == true) return
        _state.value = _state.value.copy(
            step = StartupStep.CONNECT, message = ScreenTexts.STARTUP_CONNECT,
            error = false, errorKind = null, showRetry = false,
        )
        start()
    }

    /** [제한 모드로 보기] */
    fun enterLimitedMode() {
        app.enterLimitedMode()
        _state.value = _state.value.copy(destination = Screen.LIMITED)
    }

    private suspend fun run() {
        // 1/3 연결 (10초 넘으면 실패)
        _state.value = _state.value.copy(step = StartupStep.CONNECT, message = ScreenTexts.STARTUP_CONNECT)
        val connected = withTimeoutOrNull(stepTimeoutMs) { app.connectVehicle() }
            ?: Result.failure(IllegalStateException("차량 연결 시간 초과"))
        val capability = connected.getOrElse {
            val attempts = _state.value.attempts + 1
            _state.value = _state.value.copy(
                message = ScreenTexts.STARTUP_ERROR, error = true, errorKind = StartupError.CAR_CONN,
                attempts = attempts, showRetry = true,
                showLimitedMode = attempts >= maxAttemptsBeforeLimited,
            )
            return
        }
        // 필수 capability(속도)가 없으면 이 차에서는 쓸 수 없다. 다시 시도해도 같으므로 [닫기]만
        if (!capability.speed) {
            _state.value = _state.value.copy(
                message = ScreenTexts.STARTUP_ERROR, error = true, errorKind = StartupError.CAR_UNSUPPORTED,
                showRetry = false, showLimitedMode = false,
            )
            return
        }

        // 2/3 권한
        _state.value = _state.value.copy(step = StartupStep.PERMISSION, message = ScreenTexts.STARTUP_PERMISSION)
        val permRead = withTimeoutOrNull(stepTimeoutMs) { runCatching { app.permissionChecker.refresh() } }
        if (permRead == null || permRead.isFailure) {
            _state.value = _state.value.copy(
                message = ScreenTexts.STARTUP_ERROR, error = true, errorKind = StartupError.PERM_READ,
                showRetry = true, showLimitedMode = false,
            )
            return
        }

        // 3/3 설정. 서버는 실패해도 조용히 넘어간다 (오프라인 동작)
        _state.value = _state.value.copy(step = StartupStep.SETTINGS, message = ScreenTexts.STARTUP_SETTINGS)
        withTimeoutOrNull(stepTimeoutMs) { runCatching { missionPackSync() } }

        _state.value = _state.value.copy(
            destination = ScreenRouter.afterOnboarding(
                permission = app.permission.value,
                consented = onboarding.consented.value,
                hasProfile = onboarding.profile.value != null,
                limited = app.limitedReason.value,
                drive = app.driveState.value,
            ),
        )
    }
}

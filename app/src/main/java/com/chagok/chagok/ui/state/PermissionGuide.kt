package com.chagok.chagok.ui.state

import com.chagok.chagok.data.repository.OnboardingRepository
import com.chagok.chagok.model.PermissionKind
import com.chagok.chagok.model.PermissionStatus
import com.chagok.chagok.ui.common.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** S02-02 권한 행 하나 */
data class PermissionRowUi(
    val kind: PermissionKind,
    val title: String,
    val status: PermissionStatus,      // GRANTED 완료 / NEEDED 허용 필요 / DENIED 거부됨 / CHECKING 확인 중
)

/** S-02가 그릴 값 */
data class PermissionUiState(
    val rows: List<PermissionRowUi>,
    val showDeniedBanner: Boolean,     // S02-03
    val showLater: Boolean,            // S02-05 [나중에]: 거부가 있을 때만
    val recheckEnabled: Boolean,       // S02-04 [다시 확인]: 확인 중엔 비활성
    val allDone: Boolean,              // "모두 확인됐어요!" 표시
    val locked: Boolean,               // C-02 주행 잠금 오버레이
)

/**
 * S-02 권한 안내 (F-01-1).
 * - 행 탭 → 시스템 권한 창(C-01). AndroidPermissionChecker.requester가 화면에 연결돼 있어야 뜬다.
 * - 3종 모두 완료 → "모두 확인됐어요!" 1초 후 다음 화면
 * - [다시 확인] → 행을 0.7초 "확인 중…"으로 보여 준 뒤 다시 읽기
 * - [나중에] → 토스트 후 앱 종료
 * - 주행 판정이 나면 잠금(C-02), 정차하면 자동 해제
 */
class PermissionGuide(
    private val scope: CoroutineScope,
    private val app: AppState,
    private val onboarding: OnboardingRepository,
    private val recheckMs: Long = 700,
    private val doneDelayMs: Long = 1_000,
) {
    private val checking = MutableStateFlow(false)
    private var navigating = false

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val state: StateFlow<PermissionUiState> =
        combine(app.permission, checking, app.uiLocked) { p, chk, locked ->
            fun row(kind: PermissionKind, title: String, s: PermissionStatus) =
                PermissionRowUi(kind, title, if (chk) PermissionStatus.CHECKING else s)
            PermissionUiState(
                rows = listOf(
                    row(PermissionKind.VEHICLE, ScreenTexts.PERM_VEHICLE, p.vehicle),
                    row(PermissionKind.MIC, ScreenTexts.PERM_MIC, p.mic),
                    row(PermissionKind.VOICE, ScreenTexts.PERM_VOICE, p.voice),
                ),
                showDeniedBanner = p.anyDenied,
                showLater = p.anyDenied,
                recheckEnabled = !chk,
                allDone = p.allGranted,
                locked = locked,
            )
        }.stateIn(scope, SharingStarted.Eagerly, PermissionUiState(emptyList(), false, false, true, false, false))

    init {
        // 모두 완료되면 1초 뒤 다음 화면 (그사이 다시 거부되면 취소)
        scope.launch {
            app.permission.map { it.allGranted }.distinctUntilChanged().collectLatest { ok ->
                if (!ok) return@collectLatest
                delay(doneDelayMs)
                goNext()
            }
        }
    }

    /** 행 탭: 완료된 행은 무시 */
    fun onRowClick(kind: PermissionKind) {
        if (state.value.locked) return
        val row = state.value.rows.firstOrNull { it.kind == kind } ?: return
        if (row.status == PermissionStatus.GRANTED || row.status == PermissionStatus.CHECKING) return
        scope.launch { app.permissionChecker.request(kind) }
    }

    /** [다시 확인] */
    fun recheck() {
        if (checking.value || state.value.locked) return
        scope.launch {
            checking.value = true
            delay(recheckMs)
            app.permissionChecker.refresh()
            checking.value = false
        }
    }

    /** [다음]: 3종 모두 완료면 바로 다음 화면. 아니면 false (화면이 경고 토스트를 띄움) */
    fun next(): Boolean {
        val s = state.value
        if (s.locked) return true          // 주행 중엔 아무것도 안 함
        if (!s.allDone) return false
        scope.launch { goNext() }
        return true
    }

    /** [나중에] */
    fun later() {
        scope.launch {
            _events.emit(UiEvent.Toast(ScreenTexts.PERM_LATER_TOAST))
            _events.emit(UiEvent.ExitApp)
        }
    }

    /** 다음 화면으로. 권한이 없어 S-01에서 속도 구독을 못 했으면, 허용된 지금 다시 연결한다 */
    private suspend fun goNext() {
        if (navigating) return       // [다음] 탭과 1초 자동 이동이 겹쳐도 한 번만
        navigating = true
        if (!app.capability.value.speed) app.reconnectVehicle()
        _events.emit(UiEvent.Navigate(nextScreen()))
    }

    private fun nextScreen(): Screen = ScreenRouter.afterOnboarding(
        permission = app.permission.value,
        consented = onboarding.consented.value,
        hasProfile = onboarding.profile.value != null,
        limited = app.limitedReason.value,
        drive = app.driveState.value,
    )
}

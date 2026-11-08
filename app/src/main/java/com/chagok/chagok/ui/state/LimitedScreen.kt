package com.chagok.chagok.ui.state

import com.chagok.chagok.domain.driving.LimitedReason
import com.chagok.chagok.ui.common.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class LimitedUiState(
    val reason: LimitedReason?,
    val connecting: Boolean,           // "연결하고 있어요…"
    val failedAttempts: Int,           // 실패한 [다시 연결] 횟수
    val retryEnabled: Boolean,
    val locked: Boolean,               // C-02
)

/**
 * S-08 안전 제한 모드 (F-15-2·F-15-3).
 * 들어오는 조건은 AppState.limitedReason, 화면 전환은 DriveRouter가 한다.
 */
class LimitedScreen(
    private val scope: CoroutineScope,
    private val app: AppState,
    private val settleMs: Long = 1_000,
) {
    private val connecting = MutableStateFlow(false)
    private val failedAttempts = MutableStateFlow(0)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val state: StateFlow<LimitedUiState> =
        combine(app.limitedReason, connecting, failedAttempts, app.uiLocked) { reason, c, n, locked ->
            LimitedUiState(reason, c, n, retryEnabled = !c && !locked, locked = locked)
        }.stateIn(
            scope, SharingStarted.Eagerly,
            LimitedUiState(app.limitedReason.value, false, 0, retryEnabled = true, locked = app.uiLocked.value),
        )

    init {
        // 저절로 복구되면 토스트 후 S-05
        scope.launch {
            app.limitedReason.drop(1).filter { it == null }.collect {
                if (connecting.value) return@collect     // [다시 연결] 결과는 retry()가 처리
                _events.emit(UiEvent.Toast(ScreenTexts.LIMITED_RECOVERED))
                _events.emit(UiEvent.Navigate(Screen.MAIN))
            }
        }
    }

    /** [다시 연결]: 성공하면 S-05, 실패하면 시도 횟수만 올린다 */
    fun retry() {
        if (!state.value.retryEnabled) return
        connecting.value = true
        scope.launch {
            // 제한 사유는 한 박자 늦게 풀리므로 잠깐 기다려 본다
            val ok = app.reconnectVehicle().isSuccess &&
                withTimeoutOrNull(settleMs) { app.limitedReason.first { it == null }; true } == true
            connecting.value = false
            if (ok) _events.emit(UiEvent.Navigate(Screen.MAIN))
            else failedAttempts.value += 1
        }
    }

    /** [프로필 보기] → S-04 수정 모드 */
    fun openProfile() {
        if (state.value.locked) return
        scope.launch { _events.emit(UiEvent.Navigate(Screen.PROFILE)) }
    }
}

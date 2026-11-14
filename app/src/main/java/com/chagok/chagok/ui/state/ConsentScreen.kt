package com.chagok.chagok.ui.state

import com.chagok.chagok.data.repository.ConsentItem
import com.chagok.chagok.data.repository.ConsentRecord
import com.chagok.chagok.data.repository.OnboardingRepository
import com.chagok.chagok.ui.common.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConsentRowUi(
    val item: ConsentItem,
    val text: String,
    val checked: Boolean,
)

data class ConsentUiState(
    val rows: List<ConsentRowUi>,
    val allChecked: Boolean,
    val agreeEnabled: Boolean,
    val laterEnabled: Boolean,         // S03-08 [나중에]: 모두 체크면 비활성
    val locked: Boolean,               // C-02
)

/**
 * S-03 부모 동의. 주행 중엔 입력 무시 (체크는 유지, C-02).
 * ✕ / [나중에]는 화면이 나가기 모달(C-04)을 띄운다. 모두 체크면 [나중에] 비활성.
 */
class ConsentScreen(
    private val scope: CoroutineScope,
    private val app: AppState,
    private val onboarding: OnboardingRepository,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val onConsentSaved: (ConsentRecord) -> Unit = {},   // 서버 기록 (화면이 넘어가도 끝까지)
) {
    private val checked = MutableStateFlow<Set<ConsentItem>>(emptySet())
    private var submitted = false

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val state: StateFlow<ConsentUiState> =
        combine(checked, app.uiLocked) { c, locked -> build(c, locked) }
            .stateIn(scope, SharingStarted.Eagerly, build(emptySet(), app.uiLocked.value))

    private val locked: Boolean get() = app.uiLocked.value

    fun toggle(item: ConsentItem) {
        if (locked) return
        checked.value = if (item in checked.value) checked.value - item else checked.value + item
    }

    fun toggleAll() {
        if (locked) return
        checked.value = if (allChecked(checked.value)) emptySet() else ConsentItem.entries.toSet()
    }

    /** 저장 후 프로필 없으면 S-04, 있으면 S-05 */
    fun agree() {
        if (locked || submitted) return
        if (!allChecked(checked.value)) return
        submitted = true
        val record = ConsentRecord(
            version = ConsentRecord.CURRENT_VERSION,
            items = checked.value,
            agreedAtMs = nowMs(),
        )
        onboarding.saveConsent(record)
        onConsentSaved(record)
        scope.launch { _events.emit(UiEvent.Navigate(nextScreen())) }
    }

    private fun nextScreen(): Screen = ScreenRouter.afterOnboarding(
        permission = app.permission.value,
        consented = true,
        hasProfile = onboarding.profile.value != null,
        drive = app.driveState.value,
    )

    private fun build(c: Set<ConsentItem>, locked: Boolean) = ConsentUiState(
        rows = ConsentItem.entries.map { ConsentRowUi(it, it.text(), it in c) },
        allChecked = allChecked(c),
        agreeEnabled = allChecked(c),
        laterEnabled = !allChecked(c),
        locked = locked,
    )

    private fun allChecked(c: Set<ConsentItem>) = c.containsAll(ConsentItem.entries)
}

fun ConsentItem.text(): String = when (this) {
    ConsentItem.NICKNAME -> ScreenTexts.CONSENT_ITEM_NICKNAME
    ConsentItem.VOICE_DISCARD -> ScreenTexts.CONSENT_ITEM_VOICE
    ConsentItem.FAMILY_ONLY -> ScreenTexts.CONSENT_ITEM_FAMILY
}

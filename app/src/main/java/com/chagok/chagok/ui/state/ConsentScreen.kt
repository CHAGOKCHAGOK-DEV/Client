package com.chagok.chagok.ui.state

import com.chagok.chagok.data.repository.ConsentItem
import com.chagok.chagok.data.repository.ConsentRecord
import com.chagok.chagok.data.repository.OnboardingRepository
import com.chagok.chagok.model.DriveState
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
    val showRefusalDialog: Boolean,    // C-04
    val locked: Boolean,               // C-02
)

/** S-03 부모 동의 + C-04 동의 거부 창. 주행 중엔 입력 무시 (체크는 유지) */
class ConsentScreen(
    private val scope: CoroutineScope,
    private val app: AppState,
    private val onboarding: OnboardingRepository,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val checked = MutableStateFlow<Set<ConsentItem>>(emptySet())
    private val refusalDialog = MutableStateFlow(false)
    private var submitted = false

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val state: StateFlow<ConsentUiState> =
        combine(checked, refusalDialog, app.driveState) { c, dialog, drive -> build(c, dialog, drive) }
            .stateIn(scope, SharingStarted.Eagerly, build(emptySet(), false, app.driveState.value))

    private val locked: Boolean get() = app.driveState.value == DriveState.DRIVING

    fun toggle(item: ConsentItem) {
        if (locked || refusalDialog.value) return
        checked.value = if (item in checked.value) checked.value - item else checked.value + item
    }

    fun toggleAll() {
        if (locked || refusalDialog.value) return
        checked.value = if (allChecked(checked.value)) emptySet() else ConsentItem.entries.toSet()
    }

    /** 저장 후 프로필 없으면 S-04, 있으면 S-05 */
    fun agree() {
        if (locked || refusalDialog.value || submitted) return
        if (!allChecked(checked.value)) return
        submitted = true
        onboarding.saveConsent(
            ConsentRecord(
                version = ConsentRecord.CURRENT_VERSION,
                items = checked.value,
                agreedAtMs = nowMs(),
            )
        )
        scope.launch { _events.emit(UiEvent.Navigate(nextScreen())) }
    }

    /** 저장하지 않고 C-04만 띄운다 */
    fun refuse() {
        if (locked) return
        refusalDialog.value = true
    }

    fun dismissRefusal() {
        if (locked) return
        refusalDialog.value = false
    }

    private fun nextScreen(): Screen = ScreenRouter.afterOnboarding(
        permission = app.permission.value,
        consented = true,
        hasProfile = onboarding.profile.value != null,
        limited = app.limitedReason.value,
        drive = app.driveState.value,
    )

    private fun build(c: Set<ConsentItem>, dialog: Boolean, drive: DriveState) = ConsentUiState(
        rows = ConsentItem.entries.map { ConsentRowUi(it, it.text(), it in c) },
        allChecked = allChecked(c),
        agreeEnabled = allChecked(c),
        showRefusalDialog = dialog,
        locked = drive == DriveState.DRIVING,
    )

    private fun allChecked(c: Set<ConsentItem>) = c.containsAll(ConsentItem.entries)
}

fun ConsentItem.text(): String = when (this) {
    ConsentItem.NICKNAME -> ScreenTexts.CONSENT_ITEM_NICKNAME
    ConsentItem.VOICE_DISCARD -> ScreenTexts.CONSENT_ITEM_VOICE
    ConsentItem.FAMILY_ONLY -> ScreenTexts.CONSENT_ITEM_FAMILY
}

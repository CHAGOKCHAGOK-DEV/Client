package com.chagok.chagok.ui.consent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chagok.chagok.ChagokApp
import com.chagok.chagok.data.repository.ConsentItem
import com.chagok.chagok.ui.state.ConsentScreen
import com.chagok.chagok.ui.state.ConsentUiState
import com.chagok.chagok.ui.state.UiEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** S-03 화면용. 실제 동작은 ConsentScreen */
class ConsentViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ChagokApp

    private val screen = ConsentScreen(
        scope = viewModelScope,
        app = app.appState,
        onboarding = app.onboarding,
        onConsentSaved = { record -> app.appScope.launch { app.agreements.syncConsent(record) } },
    )

    val state: StateFlow<ConsentUiState> = screen.state
    val events: SharedFlow<UiEvent> = screen.events

    fun toggle(item: ConsentItem) = screen.toggle(item)
    fun toggleAll() = screen.toggleAll()

    /** [다음]: 전체 동의 전이면 무시 */
    fun agree() = screen.agree()
}
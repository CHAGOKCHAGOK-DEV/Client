package com.chagok.chagok.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chagok.chagok.ChagokApp
import com.chagok.chagok.ui.state.MainScreen
import com.chagok.chagok.ui.state.MainUiState
import com.chagok.chagok.ui.state.UiEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** S-05 화면용. 실제 동작은 MainScreen */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ChagokApp

    private val screen = MainScreen(
        scope = viewModelScope,
        app = app.appState,
        onboarding = app.onboarding,
        speak = { text -> app.appState.speak(text) },
        nowMs = { System.currentTimeMillis() },
    )

    val state: StateFlow<MainUiState> = screen.state
    val events: SharedFlow<UiEvent> = screen.events

    /** S05-07 [부기장 출발!] 토글 */
    fun onStartToggle() = screen.onStartToggle()

    /** S05-02 프로필 칩 / 설정 탭 → S-04 수정 */
    fun onProfileClick() = screen.onProfileClick()
}

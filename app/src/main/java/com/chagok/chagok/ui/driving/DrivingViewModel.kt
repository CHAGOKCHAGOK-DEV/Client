package com.chagok.chagok.ui.driving

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chagok.chagok.ChagokApp
import com.chagok.chagok.ui.state.DrivingScreen
import com.chagok.chagok.ui.state.DrivingUiState
import kotlinx.coroutines.flow.StateFlow

/** S-06 화면용. 실제 동작은 DrivingScreen */
class DrivingViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ChagokApp

    private val screen = DrivingScreen(
        scope = viewModelScope,
        app = app.appState,
        onboarding = app.onboarding,
        speak = { text -> app.appState.speak(text) },
    )

    val state: StateFlow<DrivingUiState> = screen.state

    /** S06-04 [정차 완료] */
    fun onStopDone() = screen.onStopDone()

    /** 화면 터치: "정차 후에 눌러요" 음성 (주행당 1회) */
    fun onTouch() = screen.onTouch()
}

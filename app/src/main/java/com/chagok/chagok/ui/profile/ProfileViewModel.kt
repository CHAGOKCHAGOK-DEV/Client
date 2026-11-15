package com.chagok.chagok.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chagok.chagok.ChagokApp
import com.chagok.chagok.model.AgeGroup
import com.chagok.chagok.ui.state.ProfileScreen
import com.chagok.chagok.ui.state.ProfileUiState
import com.chagok.chagok.ui.state.UiEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** S-04 화면용. 실제 동작은 ProfileScreen이 한다 */
class ProfileViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as ChagokApp

    private val screen = ProfileScreen(
        scope = viewModelScope,
        app = app.appState,
        onboarding = app.onboarding,
        saveProfile = { app.onboarding.saveProfile(it) },
        // 화면이 바로 S-05로 넘어가도 인사가 끊기지 않게 앱 범위에서 말한다
        say = { text -> app.appScope.launch { app.appState.speak(text) } },
        editMode = savedState.get<Boolean>(ProfileActivity.EXTRA_EDIT) ?: false,
        onProfileSaved = { app.appScope.launch { app.copilots.sync() } },
    )

    val state: StateFlow<ProfileUiState> = screen.state
    val events: SharedFlow<UiEvent> = screen.events

    fun onNicknameChanged(text: String) = screen.onNicknameChanged(text)
    fun selectAge(age: AgeGroup) = screen.selectAge(age)
    fun save() = screen.save()
    fun cancel() = screen.cancel()
}
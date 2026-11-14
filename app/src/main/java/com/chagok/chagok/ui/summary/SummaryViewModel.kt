package com.chagok.chagok.ui.summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chagok.chagok.ChagokApp
import com.chagok.chagok.ui.state.SummaryScreen
import com.chagok.chagok.ui.state.SummaryUiState
import kotlinx.coroutines.flow.StateFlow

/** S-07 화면용. 실제 동작은 SummaryScreen */
class SummaryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ChagokApp

    private val screen = SummaryScreen(viewModelScope, app.appState, app.driveRouter, app.network.online)

    val state: StateFlow<SummaryUiState> = screen.state

    /** [메인으로] */
    fun onMain() = screen.onMain()
}

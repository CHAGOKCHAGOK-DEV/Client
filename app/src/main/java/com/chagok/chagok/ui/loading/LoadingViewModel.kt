package com.chagok.chagok.ui.loading

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chagok.chagok.ChagokApp
import com.chagok.chagok.ui.state.StartupFlow
import com.chagok.chagok.ui.state.StartupUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * S-01 로딩 화면용. 실제 동작은 StartupFlow(F-01, F-01-1)가 한다.
 * 1/3 차 연결 → 2/3 권한 읽기 → 3/3 저장값·미션팩 → 분기(S-02/03/04/05/06)
 */
class LoadingViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ChagokApp

    private val flow = StartupFlow(
        scope = viewModelScope,
        app = app.appState,
        onboarding = app.onboarding,
        // 3/3: 설치 등록·미션팩·재전송. 실패해도 진행 (오프라인 동작)
        missionPackSync = { app.serverSync.onStartup() },
    )

    val state: StateFlow<StartupUiState> = flow.state

    init { flow.start() }

    /** [다시 시도]: 1/3부터. 진행 중 연타는 무시 */
    fun retry() = flow.retry()
}

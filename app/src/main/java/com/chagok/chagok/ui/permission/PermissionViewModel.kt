package com.chagok.chagok.ui.permission

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chagok.chagok.ChagokApp
import com.chagok.chagok.model.PermissionKind
import com.chagok.chagok.ui.state.PermissionGuide
import com.chagok.chagok.ui.state.PermissionUiState
import com.chagok.chagok.ui.state.UiEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** S-02 화면용. 실제 동작은 PermissionGuide */
class PermissionViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ChagokApp

    private val guide = PermissionGuide(viewModelScope, app.appState, app.onboarding)

    val state: StateFlow<PermissionUiState> = guide.state
    val events: SharedFlow<UiEvent> = guide.events

    /** S02-02 행 탭 → 시스템 권한 창(C-01) */
    fun onRowClick(kind: PermissionKind) = guide.onRowClick(kind)

    /** [다음]: false면 아직 완료 전 → 경고 토스트 */
    fun next(): Boolean = guide.next()

    /** S02-05 [나중에] → 토스트 후 앱 종료 */
    fun later() = guide.later()

    /** 화면 복귀 시 권한 다시 읽기 (설정 앱에서 바꾸고 돌아온 경우) */
    fun refresh() = guide.refresh()
}
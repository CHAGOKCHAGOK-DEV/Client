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
import com.chagok.chagok.data.remote.AgreementDocument
import kotlinx.coroutines.flow.MutableStateFlow

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

    private val terms = MutableStateFlow<List<AgreementDocument>>(emptyList())

    private companion object {
        /** 카드 → 약관 제목에 들어 있는 단어 */
        val TERM_KEYWORD = mapOf(
            ConsentItem.NICKNAME to "이용약관",        // [필수] 서비스 이용약관
            ConsentItem.VOICE_DISCARD to "처리방침",   // [필수] 개인정보 처리방침
            ConsentItem.FAMILY_ONLY to "아동",         // [필수] 아동 개인정보 수집·이용
        )
    }

    init { loadTerms() }

    /** 서버 약관 불러오기. 실패하면 [보기]를 누를 때 다시 시도 */
    fun loadTerms() {
        viewModelScope.launch {
            app.agreements.fetchCurrent().onSuccess { terms.value = it }
        }
    }

    /** 카드에 해당하는 약관. 서버 순서와 상관없이 제목 단어로 찾는다 */
    fun termFor(item: ConsentItem): AgreementDocument? =
        terms.value.firstOrNull { it.title?.contains(TERM_KEYWORD.getValue(item)) == true }

    fun toggle(item: ConsentItem) = screen.toggle(item)
    fun toggleAll() = screen.toggleAll()

    /** [다음]: 전체 동의 전이면 무시 */
    fun agree() = screen.agree()
}
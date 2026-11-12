package com.chagok.chagok.ui.consent

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chagok.chagok.ChagokApp
import com.chagok.chagok.R
import com.chagok.chagok.data.repository.ConsentItem
import com.chagok.chagok.ui.common.intentFor
import com.chagok.chagok.ui.component.ExitDialog
import com.chagok.chagok.ui.component.bindCheckCard
import com.chagok.chagok.ui.component.highlight
import com.chagok.chagok.ui.state.ConsentUiState
import com.chagok.chagok.ui.state.Screen
import com.chagok.chagok.ui.state.UiEvent
import kotlinx.coroutines.launch
import com.chagok.chagok.ui.state.ScreenTexts

/**
 * S-03 부모 동의 (S03_1, S03_2).
 * - 항목 카드 탭 → 체크 토글, [전체 동의] → 전체 토글
 * - 모두 체크 → [다음] 온, [다음] 탭 → 동의 저장 후 S-04(프로필 없음) / S-05
 * - [나중에] / X / 뒤로가기 → 나가기 모달
 */
class ConsentActivity : AppCompatActivity() {

    private val viewModel: ConsentViewModel by viewModels()

    private lateinit var cards: Map<ConsentItem, View>
    private lateinit var cardAll: View
    private lateinit var btnNext: View

    private var exitDialog: ExitDialog? = null
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consent)

        cards = mapOf(
            ConsentItem.NICKNAME to findViewById(R.id.card_nickname),
            ConsentItem.VOICE_DISCARD to findViewById(R.id.card_voice),
            ConsentItem.FAMILY_ONLY to findViewById(R.id.card_family),
        )
        cardAll = findViewById(R.id.card_all)
        btnNext = findViewById(R.id.btn_next)

        findViewById<TextView>(R.id.tv_bubble).text = ScreenTexts.CONSENT_TITLE
        findViewById<TextView>(R.id.tv_title).text = highlight(ScreenTexts.CONSENT_SUB, TITLE_KEY)

        cards.forEach { (item, card) -> card.setOnClickListener { viewModel.toggle(item) } }
        cardAll.setOnClickListener { viewModel.toggleAll() }
        btnNext.setOnClickListener { viewModel.agree() }
        findViewById<View>(R.id.btn_later).setOnClickListener { showExitModal() }
        findViewById<View>(R.id.btn_close).setOnClickListener { showExitModal() }
        onBackPressedDispatcher.addCallback(this) { showExitModal() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch { viewModel.events.collect { handle(it) } }
            }
        }
    }

    override fun onDestroy() {
        exitDialog?.dismiss()
        super.onDestroy()
    }

    private fun render(state: ConsentUiState) {
        state.rows.forEach { row -> cards[row.item]?.bindCheckCard(row.text, row.checked) }
        cardAll.bindCheckCard(ScreenTexts.CONSENT_ALL, state.allChecked)
        btnNext.isActivated = state.agreeEnabled
        // TODO: state.locked → C-02 주행 잠금 오버레이
    }

    private fun handle(event: UiEvent) {
        when (event) {
            is UiEvent.Toast -> Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()   // TODO: C-03
            is UiEvent.Navigate -> navigate(event.to)
            UiEvent.ExitApp -> exitApp()
        }
    }

    private fun showExitModal() {
        if (exitDialog?.isShowing == true) return
        exitDialog = ExitDialog(this) { exitApp() }.also { it.show() }
    }

    private fun exitApp() {
        (application as ChagokApp).appState.release()
        finishAffinity()
    }

    private fun navigate(next: Screen) {
        if (navigated) return
        navigated = true
        startActivity(intentFor(next))
        overridePendingTransition(0, 0)
        finish()
    }

    companion object {
        private const val TITLE_KEY = "정보 이용"
    }
}
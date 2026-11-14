package com.chagok.chagok.ui.permission

import android.content.Intent
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chagok.chagok.ChagokApp
import com.chagok.chagok.MainActivity
import com.chagok.chagok.R
import com.chagok.chagok.model.PermissionKind
import com.chagok.chagok.permission.AndroidPermissionChecker
import com.chagok.chagok.ui.component.bindPermissionCard
import com.chagok.chagok.ui.state.PermissionUiState
import com.chagok.chagok.ui.state.Screen
import com.chagok.chagok.ui.state.UiEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.addCallback
import com.chagok.chagok.ui.component.ExitDialog
import com.chagok.chagok.ui.common.intentFor
import android.view.MotionEvent
import com.chagok.chagok.ui.component.DriveLockGuard
import android.widget.ImageView

/**
 * S-02 권한 안내 (F-01-1).
 * - 행 탭 → 시스템 권한 창(C-01). 허용 → 완료(테두리), 거부 → 거부됨
 * - [다음] 오프(3종 완료 전) 탭 → 하단 경고 토스트 2초 (S02_2)
 * - 3종 모두 완료 → [다음] 온 + "모두 확인됐어요!", [다음] 탭 또는 1초 후 자동 이동
 * - [나중에] (거부가 있을 때만) → 토스트 후 앱 종료
 */
class PermissionActivity : AppCompatActivity() {

    private val viewModel: PermissionViewModel by viewModels()

    private lateinit var cards: Map<PermissionKind, View>
    private lateinit var tvBubble: TextView
    private lateinit var btnNext: View
    private lateinit var btnLater: View
    private lateinit var toastWarning: View

    private var toastJob: Job? = null
    private var navigated = false
    private var exitDialog: ExitDialog? = null
    private lateinit var driveLock: DriveLockGuard

    /** 주행 중엔 화면 터치 차단 */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean =
        driveLock.interceptTouch(ev) || super.dispatchTouchEvent(ev)

    // 시스템 권한 창 결과를 AndroidPermissionChecker.request()로 돌려준다
    private var pendingResult: CompletableDeferred<Map<String, Boolean>>? = null
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            pendingResult?.complete(result)
            pendingResult = null
        }

    private val checker: AndroidPermissionChecker?
        get() = (application as ChagokApp).appState.permissionChecker as? AndroidPermissionChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)
        driveLock = DriveLockGuard(this) { showExitModal() }

        cards = mapOf(
            PermissionKind.VEHICLE to findViewById(R.id.card_vehicle),
            PermissionKind.MIC to findViewById(R.id.card_mic),
            PermissionKind.VOICE to findViewById(R.id.card_voice),
        )
        // 권한 카드 아이콘
        val icons = mapOf(
            PermissionKind.VEHICLE to R.drawable.ic_perm_vehicle,
            PermissionKind.MIC to R.drawable.ic_perm_mic,
            PermissionKind.VOICE to R.drawable.ic_perm_voice,
        )
        cards.forEach { (kind, card) ->
            card.findViewById<ImageView>(R.id.iv_permission_icon).setImageResource(icons.getValue(kind))
        }
        tvBubble = findViewById(R.id.tv_bubble)
        btnNext = findViewById(R.id.btn_next)
        btnLater = findViewById(R.id.btn_later)
        toastWarning = findViewById(R.id.toast_warning)

        findViewById<TextView>(R.id.tv_title).text = highlight(R.string.perm_title, R.string.perm_title_key)

        cards.forEach { (kind, card) -> card.setOnClickListener { viewModel.onRowClick(kind) } }
        btnNext.setOnClickListener { if (!viewModel.next()) showWarning() }
        btnLater.setOnClickListener { showExitModal() }
        findViewById<View>(R.id.btn_close).setOnClickListener { showExitModal() }
        onBackPressedDispatcher.addCallback(this) { showExitModal() }

        checker?.requester = { perms ->
            CompletableDeferred<Map<String, Boolean>>().also {
                pendingResult = it
                permissionLauncher.launch(perms)
            }.await()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch { viewModel.events.collect { handle(it) } }
            }
        }
    }

    override fun onDestroy() {
        checker?.requester = null
        pendingResult?.cancel()
        exitDialog?.dismiss()
        driveLock.release()
        super.onDestroy()
    }

    private fun render(state: PermissionUiState) {
        state.rows.forEach { row -> cards[row.kind]?.bindPermissionCard(row.title, row.status) }

        tvBubble.setText(if (state.allDone) R.string.perm_bubble_done else R.string.perm_bubble)
        btnNext.isActivated = state.allDone
        if (state.allDone) hideWarning()
    }

    private fun handle(event: UiEvent) {
        when (event) {
            // TODO: C-03 토스트 컴포넌트가 생기면 교체
            is UiEvent.Toast -> Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()
            is UiEvent.Navigate -> navigate(event.to)
            UiEvent.ExitApp -> exitApp()
        }
    }

    // ---------- 나가기 모달 (S02_3) ----------

    private fun showExitModal() {
        if (exitDialog?.isShowing == true) return
        exitDialog = ExitDialog(this) { exitApp() }.also { it.show() }
    }

    /** 앱 종료 전에 차량 SDK 구독·연결을 해제한다 */
    private fun exitApp() {
        (application as ChagokApp).appState.release()
        finishAffinity()
    }

    // ---------- 경고 토스트 (S02_2) ----------

    private fun showWarning() {
        toastJob?.cancel()
        toastWarning.visibility = View.VISIBLE
        toastJob = lifecycleScope.launch {
            delay(WARNING_MS)
            toastWarning.visibility = View.INVISIBLE
        }
    }

    private fun hideWarning() {
        toastJob?.cancel()
        toastWarning.visibility = View.INVISIBLE
    }

    // ---------- 공통 ----------

    private fun highlight(fullRes: Int, keyRes: Int): CharSequence {
        val full = getString(fullRes)
        val key = getString(keyRes)
        val s = SpannableString(full)
        val i = full.indexOf(key)
        if (i >= 0) {
            s.setSpan(
                ForegroundColorSpan(getColor(R.color.brand)),
                i, i + key.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return s
    }

    private fun navigate(next: Screen) {
        if (navigated) return   // [다음] 탭과 1초 자동 이동이 겹쳐도 한 번만
        navigated = true
        // TODO: S-03 / S-04 / S-05 Activity가 생기면 각각 연결
        startActivity(intentFor(next))
        overridePendingTransition(0, 0)
        finish()
    }

    companion object {
        private const val WARNING_MS = 2_000L
    }
}
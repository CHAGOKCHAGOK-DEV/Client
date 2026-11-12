package com.chagok.chagok.ui.loading

import android.animation.ObjectAnimator
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chagok.chagok.ChagokApp
import com.chagok.chagok.R
import com.chagok.chagok.ui.state.Screen
import com.chagok.chagok.ui.state.StartupError
import com.chagok.chagok.ui.state.StartupStep
import com.chagok.chagok.ui.state.StartupUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.chagok.chagok.ui.common.intentFor

/**
 * S01_2 로딩중 / 로딩실패 (S-01, F-01, F-01-1).
 * 최소 표시 시간 없이 단계가 끝나는 대로 진행하고, 3/3이 끝나면 분기 화면으로 바로 이동한다.
 */
class LoadingActivity : AppCompatActivity() {

    private val viewModel: LoadingViewModel by viewModels()

    private lateinit var groupLoading: View
    private lateinit var groupFail: View
    private lateinit var tvBubble: TextView
    private lateinit var tvStatus: TextView
    private lateinit var ivSpinner: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var tvFailTitle: TextView
    private lateinit var tvFailSub: TextView
    private lateinit var btnRetry: TextView
    private lateinit var btnLimited: TextView

    private lateinit var spin: Animation
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        groupLoading = findViewById(R.id.group_loading)
        groupFail = findViewById(R.id.group_fail)
        tvBubble = findViewById(R.id.tv_bubble)
        tvStatus = findViewById(R.id.tv_status)
        ivSpinner = findViewById(R.id.iv_spinner)
        progress = findViewById(R.id.progress)
        tvFailTitle = findViewById(R.id.tv_fail_title)
        tvFailSub = findViewById(R.id.tv_fail_sub)
        btnRetry = findViewById(R.id.btn_retry)
        btnLimited = findViewById(R.id.btn_limited)

        spin = AnimationUtils.loadAnimation(this, R.anim.rotate_cw_infinite)

        btnRetry.setOnClickListener { viewModel.retry() }
        btnLimited.setOnClickListener { viewModel.enterLimitedMode() }
        // 피그마: 모달 노출 없이 close. 앱 종료 전에 차량 SDK 구독·연결을 해제한다 (4-4 생명주기)
        findViewById<View>(R.id.btn_close).setOnClickListener {
            (application as ChagokApp).appState.release()
            finishAffinity()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private suspend fun render(state: StartupUiState) {
        val next = state.destination
        when {
            next != null -> {
                if (next != Screen.LIMITED) {
                    showLoading(StartupStep.SETTINGS)
                    animateProgress(PROGRESS_MAX)
                    delay(BAR_ANIM_MS) // 바가 다 찬 걸 보여 주고 바로 이동
                }
                navigate(next)
            }
            state.error -> showFail(state)
            else -> {
                showLoading(state.step)
                animateProgress(state.step.index * PROGRESS_MAX / 3)
            }
        }
    }

    // ---------- 로딩중 ----------

    private fun showLoading(step: StartupStep) {
        groupFail.visibility = View.GONE
        groupLoading.visibility = View.VISIBLE
        if (ivSpinner.animation == null) ivSpinner.startAnimation(spin)

        // 피그마: 1·2단계 "부기장 출근 중!", 3단계 "거의 다 왔어요!"
        tvBubble.setText(
            if (step == StartupStep.SETTINGS) R.string.loading_bubble_almost else R.string.loading_bubble
        )
        tvStatus.text = when (step) {
            StartupStep.CONNECT -> highlight(R.string.loading_step1, R.string.loading_step1_key)
            StartupStep.PERMISSION -> highlight(R.string.loading_step2, R.string.loading_step2_key)
            StartupStep.SETTINGS -> highlight(R.string.loading_step3, R.string.loading_step3_key)
        }
    }

    private fun animateProgress(target: Int) {
        if (progress.progress == target) return
        ObjectAnimator.ofInt(progress, "progress", progress.progress, target)
            .setDuration(BAR_ANIM_MS)
            .start()
    }

    private fun highlight(fullRes: Int, keyRes: Int): CharSequence {
        val full = getString(fullRes)
        val key = getString(keyRes)
        val s = SpannableString(full)
        val i = full.indexOf(key)
        if (i >= 0) {
            s.setSpan(
                ForegroundColorSpan(getColor(R.color.correct)),
                i, i + key.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return s
    }

    // ---------- 로딩실패 ----------

    private fun showFail(state: StartupUiState) {
        ivSpinner.clearAnimation()
        groupLoading.visibility = View.GONE
        groupFail.visibility = View.VISIBLE
        progress.progress = 0

        val (title, sub) = when (state.errorKind) {
            StartupError.PERM_READ -> R.string.fail_perm_read_title to R.string.fail_perm_read_sub
            StartupError.CAR_UNSUPPORTED -> R.string.fail_unsupported_title to R.string.fail_unsupported_sub
            StartupError.CAR_CONN, null -> R.string.fail_car_conn_title to R.string.fail_car_conn_sub
        }
        tvFailTitle.setText(title)
        tvFailSub.setText(sub)
        btnRetry.visibility = if (state.showRetry) View.VISIBLE else View.GONE
        // 3회째 연결 실패부터
        btnLimited.visibility = if (state.showLimitedMode) View.VISIBLE else View.GONE
    }

    // ---------- 이동 ----------

    private fun navigate(next: Screen) {
        if (navigated) return
        navigated = true
        // TODO: S-02·S-03·S-04·S-05·S-06·S-08 화면이 생기면 각 Activity로 연결.
        //       지금은 MainActivity로 가고, 정해진 화면 이름을 함께 넘긴다.
        val intent = intentFor(next)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    companion object {
        private const val PROGRESS_MAX = 1000
        private const val BAR_ANIM_MS = 300L
    }
}

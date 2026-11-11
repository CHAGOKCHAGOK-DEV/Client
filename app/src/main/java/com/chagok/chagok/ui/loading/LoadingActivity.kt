package com.chagok.chagok.ui.loading

import android.animation.ObjectAnimator
import android.content.Intent
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
import com.chagok.chagok.MainActivity
import com.chagok.chagok.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** S01_2 로딩(3단계) + 로딩 실패 */
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

        spin = AnimationUtils.loadAnimation(this, R.anim.rotate_cw_infinite)

        btnRetry.setOnClickListener { viewModel.retry() }
        // 피그마: 모달 노출 없이 close
        findViewById<View>(R.id.btn_close).setOnClickListener { finishAffinity() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private suspend fun render(state: LoadingUiState) {
        when (state) {
            is LoadingUiState.Loading -> {
                showLoading()
                tvBubble.setText(
                    if (state.step == 3) R.string.loading_bubble_almost else R.string.loading_bubble
                )
                tvStatus.text = when (state.step) {
                    1 -> highlight(getString(R.string.loading_step1), getString(R.string.loading_step1_key))
                    2 -> highlight(getString(R.string.loading_step2), getString(R.string.loading_step2_key))
                    else -> highlight(getString(R.string.loading_step3), getString(R.string.loading_step3_key))
                }
                animateProgress(state.completed * PROGRESS_MAX / 3)
            }

            is LoadingUiState.Done -> {
                animateProgress(PROGRESS_MAX)
                delay(BAR_ANIM_MS) // 바가 100% 찬 걸 보여주고 바로 이동
                navigate(state.next)
            }

            is LoadingUiState.Failed -> showFail(state.reason)
        }
    }

    private fun showLoading() {
        groupFail.visibility = View.GONE
        groupLoading.visibility = View.VISIBLE
        if (ivSpinner.animation == null) ivSpinner.startAnimation(spin)
    }

    private fun showFail(reason: FailReason) {
        ivSpinner.clearAnimation()
        groupLoading.visibility = View.GONE
        groupFail.visibility = View.VISIBLE
        progress.progress = 0

        val (title, sub) = when (reason) {
            FailReason.CAR_CONN -> R.string.fail_car_conn_title to R.string.fail_car_conn_sub
            FailReason.PERM_READ -> R.string.fail_perm_read_title to R.string.fail_perm_read_sub
            FailReason.STORE_READ -> R.string.fail_store_read_title to R.string.fail_store_read_sub
            FailReason.CAR_UNSUPPORTED -> R.string.fail_unsupported_title to R.string.fail_unsupported_sub
        }
        tvFailTitle.setText(title)
        tvFailSub.setText(sub)
        // 미지원 차량은 다시 시도해도 소용없으므로 [닫기]만
        btnRetry.visibility = if (reason == FailReason.CAR_UNSUPPORTED) View.GONE else View.VISIBLE
    }

    private fun navigate(next: Destination) {
        if (navigated) return
        navigated = true
        // TODO: S-02 권한 / S-03 동의 / S-04 설정 화면이 생기면 각각 연결
        val target = when (next) {
            Destination.PERMISSION -> MainActivity::class.java
            Destination.CONSENT -> MainActivity::class.java
            Destination.PROFILE -> MainActivity::class.java
            Destination.MAIN -> MainActivity::class.java
        }
        startActivity(Intent(this, target))
        overridePendingTransition(0, 0)
        finish()
    }

    private fun animateProgress(target: Int) {
        if (progress.progress == target) return
        ObjectAnimator.ofInt(progress, "progress", progress.progress, target)
            .setDuration(BAR_ANIM_MS)
            .start()
    }

    /** 문장에서 핵심 단어만 민트로 */
    private fun highlight(full: String, key: String): CharSequence {
        val s = SpannableString(full)
        val i = full.indexOf(key)
        if (i >= 0) {
            s.setSpan(
                ForegroundColorSpan(getColor(R.color.brand_strong)),
                i, i + key.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return s
    }

    companion object {
        private const val PROGRESS_MAX = 1000
        private const val BAR_ANIM_MS = 300L
    }
}
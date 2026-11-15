package com.chagok.chagok.ui.summary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chagok.chagok.ChagokApp
import com.chagok.chagok.R
import com.chagok.chagok.ui.common.intentFor
import com.chagok.chagok.ui.component.DriveLockGuard
import com.chagok.chagok.ui.component.iconRes
import com.chagok.chagok.ui.state.Screen
import com.chagok.chagok.ui.state.ScreenTexts
import com.chagok.chagok.ui.state.SummaryKind
import com.chagok.chagok.ui.state.SummaryRow
import com.chagok.chagok.ui.state.SummaryRowResult
import com.chagok.chagok.ui.state.SummaryUiState
import com.chagok.chagok.ui.state.title
import kotlinx.coroutines.launch

/**
 * S-07 주행 종료 요약 (피그마 S07 a~h).
 * - 왼쪽 말풍선 + 일러스트, 오른쪽 제목 · 요약 한 줄 · 결과 목록(최대 5) · [메인으로]
 * - 미션 0개면 목록 대신 빈 상태 카드, 정차 5분 종료면 제목 위 안내 칩, 인터넷이 없으면 목록 아래 안내 칩
 * - 요약 중 다시 달리면 C-02 잠금. 정차가 확정되면 저절로 닫힌다
 * - 정차해 있어도 자동으로 메인으로 넘기지 않는다. [메인으로]를 기다린다
 */
class SummaryActivity : AppCompatActivity() {

    private val viewModel: SummaryViewModel by viewModels()

    private lateinit var tvBubble: TextView
    private lateinit var chipIdle: View
    private lateinit var tvLine: TextView
    private lateinit var listRows: LinearLayout
    private lateinit var cardEmpty: View
    private lateinit var chipOffline: View
    private lateinit var tvFooter: TextView
    private lateinit var btnMain: TextView

    private lateinit var driveLock: DriveLockGuard
    private var renderedRows: List<SummaryRow>? = null
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary)
        driveLock = DriveLockGuard(this)   // S07_h: 요약 중 다시 달리면 C-02

        tvBubble = findViewById(R.id.tv_bubble)
        chipIdle = findViewById(R.id.chip_idle)
        tvLine = findViewById(R.id.tv_summary_line)
        listRows = findViewById(R.id.list_rows)
        cardEmpty = findViewById(R.id.card_empty)
        chipOffline = findViewById(R.id.chip_offline)
        tvFooter = findViewById(R.id.tv_footer)
        btnMain = findViewById(R.id.btn_main)

        btnMain.isActivated = true
        btnMain.setOnClickListener { viewModel.onMain() }
        onBackPressedDispatcher.addCallback(this) { /* 요약은 [메인으로]로만 나간다 */ }

        val router = (application as ChagokApp).driveRouter
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch {
                    router.route.collect { route ->
                        if (route.screen != Screen.SUMMARY) navigate(route.screen)
                    }
                }
            }
        }
    }

    /** 주행 중엔 화면 터치 차단 */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean =
        driveLock.interceptTouch(ev) || super.dispatchTouchEvent(ev)

    override fun onDestroy() {
        driveLock.release()
        super.onDestroy()
    }

    private fun render(state: SummaryUiState) {
        tvBubble.text = state.bubble
        chipIdle.visibility = if (state.idleNotice) View.VISIBLE else View.GONE
        tvLine.text = state.summaryLine

        val empty = state.kind == SummaryKind.EMPTY
        cardEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        listRows.visibility = if (empty) View.GONE else View.VISIBLE
        if (renderedRows != state.rows) {
            renderedRows = state.rows
            renderRows(state.rows)
        }
        chipOffline.visibility = if (state.offlineNotice) View.VISIBLE else View.GONE
        tvFooter.text = state.footer
    }

    private fun renderRows(rows: List<SummaryRow>) {
        listRows.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val gap = (ROW_GAP_DP * resources.displayMetrics.density).toInt()
        rows.forEachIndexed { i, row ->
            val v = inflater.inflate(R.layout.item_summary_row, listRows, false)
            v.findViewById<ImageView>(R.id.iv_row_icon).setImageResource(row.code.iconRes())

            val success = row.result == SummaryRowResult.SUCCESS
            v.findViewById<TextView>(R.id.tv_row_title).apply {
                text = row.code.title()
                setTextColor(getColor(if (success) R.color.gray_600 else R.color.gray_500))
            }
            v.findViewById<TextView>(R.id.tv_row_chip).apply {
                text = if (success) ScreenTexts.SUMMARY_SUCCESS else ScreenTexts.SUMMARY_NEXT_TIME
                backgroundTintList = getColorStateList(if (success) R.color.mint_100 else R.color.gray_100)
                setTextColor(getColor(if (success) R.color.brand_pressed else R.color.gray_500))
            }
            if (i > 0) (v.layoutParams as ViewGroup.MarginLayoutParams).topMargin = gap
            listRows.addView(v)
        }
    }

    private fun navigate(next: Screen) {
        if (navigated) return
        navigated = true
        driveLock.release()
        startActivity(intentFor(next))
        overridePendingTransition(0, 0)
        finish()
    }

    private companion object {
        const val ROW_GAP_DP = 28
    }
}

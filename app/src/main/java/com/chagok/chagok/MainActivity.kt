package com.chagok.chagok

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chagok.chagok.ui.common.intentFor
import com.chagok.chagok.ui.component.ChagokDialog
import com.chagok.chagok.ui.component.bindMissionRow
import com.chagok.chagok.ui.component.bindSideTab
import com.chagok.chagok.ui.component.highlight
import com.chagok.chagok.ui.main.MainViewModel
import com.chagok.chagok.ui.profile.ProfileActivity
import com.chagok.chagok.ui.state.MainUiState
import com.chagok.chagok.ui.state.MissionAvailabilityUi
import com.chagok.chagok.ui.state.Screen
import com.chagok.chagok.ui.state.UiEvent
import kotlinx.coroutines.launch

/**
 * S-05 메인 (S05_1, S05_2). 정차·주차 중 기본 화면.
 * - [부기장 출발!]: 준비 상태 토글. 연결이 끊기면 오프(회색)
 * - 프로필 칩 / 설정 탭 → S-04 수정 모드
 * - 기록·배지 탭 → "지금은 지원하지 않는 기능이에요" 모달 (S05_2)
 * - X → 앱 종료 (🟡 설계서에 동작 없음)
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var tvBubble: TextView
    private lateinit var dotStatus: View
    private lateinit var tvStatus: TextView
    private lateinit var chipProfile: View
    private lateinit var tvProfileInitial: TextView
    private lateinit var tvProfile: TextView
    private lateinit var listMissions: LinearLayout
    private lateinit var tvProgress: TextView
    private lateinit var progressToday: ProgressBar
    private lateinit var btnStart: TextView

    private var infoDialog: ChagokDialog? = null
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvBubble = findViewById(R.id.tv_bubble)
        dotStatus = findViewById(R.id.dot_status)
        tvStatus = findViewById(R.id.tv_status)
        chipProfile = findViewById(R.id.chip_profile)
        tvProfileInitial = findViewById(R.id.tv_profile_initial)
        tvProfile = findViewById(R.id.tv_profile)
        listMissions = findViewById(R.id.list_missions)
        tvProgress = findViewById(R.id.tv_progress)
        progressToday = findViewById(R.id.progress_today)
        btnStart = findViewById(R.id.btn_start)

        findViewById<TextView>(R.id.tv_title).text = highlight(R.string.main_title, R.string.main_title_key)

        // S05-05 사이드 탭: 미션(현재) / 기록·배지(2차) / 설정(S-04)
        findViewById<View>(R.id.tab_mission)
            .bindSideTab(R.drawable.ic_tab_mission, R.string.main_tab_mission, selected = true)
        findViewById<View>(R.id.tab_record).apply {
            bindSideTab(R.drawable.ic_tab_record, R.string.main_tab_record, selected = false)
            setOnClickListener { showUnsupportedModal() }
        }
        findViewById<View>(R.id.tab_settings).apply {
            bindSideTab(R.drawable.ic_tab_settings, R.string.main_tab_settings, selected = false)
            setOnClickListener { viewModel.onProfileClick() }
        }

        chipProfile.setOnClickListener { viewModel.onProfileClick() }
        btnStart.setOnClickListener { viewModel.onStartToggle() }
        findViewById<View>(R.id.btn_close).setOnClickListener { exitApp() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch { viewModel.events.collect { handle(it) } }
            }
        }

        // 주행 판정이 나면 S-06으로 (모드가 꺼져 있어도 e 상태로 간다)
        val router = (application as ChagokApp).driveRouter
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                router.route.collect { if (it.screen == Screen.DRIVING) navigate(Screen.DRIVING) }
            }
        }
        // TODO(S-07): 주행이 끝나면(route = SUMMARY) S-07로
    }

    override fun onDestroy() {
        infoDialog?.dismiss()
        super.onDestroy()
    }

    private fun render(state: MainUiState) {
        tvBubble.text = state.mascotMessage

        // S05-01 상태 칩: 끊김이면 빨간 점
        tvStatus.text = state.statusChip.text
        dotStatus.backgroundTintList = ColorStateList.valueOf(
            getColor(if (state.statusChip.isError) R.color.pink else R.color.status_dot_ok)
        )

        // S05-02 프로필 칩
        chipProfile.visibility = if (state.profileChip != null) View.VISIBLE else View.GONE
        tvProfile.text = state.profileChip
        tvProfileInitial.text = state.profileInitial

        renderMissions(state.missions)

        // S05-04 오늘 진행
        tvProgress.text = getString(R.string.main_progress, state.todayGoal, state.todayDone)
        progressToday.progress = state.todayPercent

        // S05-07 [부기장 출발!]: 켜면 "출발 준비 완료!" (🟡 켜진 모양은 시안 없음: 연한 민트 + 테두리)
        //  연결 끊김이면 회색 오프. 이미 켜 둔 건 끌 수 있게 그대로 둔다
        btnStart.isActivated = state.startEnabled || state.startOn
        btnStart.setText(if (state.startOn) R.string.main_start_ready else R.string.main_start)
        btnStart.setBackgroundResource(if (state.startOn) R.drawable.bg_cta_stroke else R.drawable.bg_cta_primary)
        btnStart.setTextColor(getColor(if (state.startOn) R.color.brand_pressed else R.color.gray_00))
    }

    /** S05-08 미션 목록. 줄 수가 바뀔 때만 새로 붙이고, 평소엔 내용만 갱신 */
    private fun renderMissions(missions: List<MissionAvailabilityUi>) {
        if (listMissions.childCount != missions.size) {
            listMissions.removeAllViews()
            val gap = (MISSION_ROW_GAP_DP * resources.displayMetrics.density).toInt()
            missions.forEachIndexed { i, _ ->
                val row = LayoutInflater.from(this).inflate(R.layout.item_mission_row, listMissions, false)
                if (i > 0) (row.layoutParams as ViewGroup.MarginLayoutParams).topMargin = gap
                listMissions.addView(row)
            }
        }
        missions.forEachIndexed { i, mission -> listMissions.getChildAt(i).bindMissionRow(mission) }
    }

    private fun handle(event: UiEvent) {
        when (event) {
            is UiEvent.Toast -> Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()   // TODO: C-03
            is UiEvent.Navigate -> navigate(event.to)
            UiEvent.ExitApp -> exitApp()
        }
    }

    // ---------- 미지원 기능 안내 (S05_2) ----------

    private fun showUnsupportedModal() {
        if (infoDialog?.isShowing == true) return
        infoDialog = ChagokDialog(
            context = this,
            title = getString(R.string.main_unsupported_title),
            message = getString(R.string.main_unsupported_message),
            leftText = null,                               // [확인] 하나만
            rightText = getString(R.string.common_ok),
        ).also { it.show() }
    }

    // ---------- 공통 ----------

    /** 앱 종료 전에 차량 SDK 구독·연결을 해제한다 */
    private fun exitApp() {
        (application as ChagokApp).appState.release()
        finishAffinity()
    }

    private fun navigate(next: Screen) {
        if (navigated) return
        navigated = true
        val intent = intentFor(next)
        // S-05에서 가는 S-04는 항상 수정 모드 ([저장]·[취소] 후 S-05로 돌아온다)
        if (next == Screen.PROFILE) intent.putExtra(ProfileActivity.EXTRA_EDIT, true)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    companion object {
        /** S-01 로딩이 정한 다음 화면 (ui.state.Screen 이름). S-06·S-07 Activity가 생기기 전까지 임시로 여기로 온다 */
        const val EXTRA_SCREEN = "screen"

        /** 미션 행 사이 간격 16 - 행 아래 그림자 4 */
        private const val MISSION_ROW_GAP_DP = 12
    }
}

package com.chagok.chagok.ui.driving

import android.app.Activity
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.chagok.chagok.R
import com.chagok.chagok.ui.component.backgroundRes
import com.chagok.chagok.ui.component.driveTileRes
import com.chagok.chagok.ui.component.stateTileRes
import com.chagok.chagok.ui.state.DrivingDisplay
import com.chagok.chagok.ui.state.DrivingUiState
import com.chagok.chagok.ui.state.title

/** activity_driving 한 장을 DrivingUiState대로 그린다 (주행 화면, 디버그 미리보기에서 같이 쓴다) */
class DrivingView(private val activity: Activity) {

    val root: View = activity.findViewById(R.id.drive_root)
    val btnStopDone: View = activity.findViewById(R.id.btn_stop_done)
    private val tvMissionName: TextView = activity.findViewById(R.id.tv_mission_name)
    private val ivTile: ImageView = activity.findViewById(R.id.iv_tile)
    private val tvHeadline: TextView = activity.findViewById(R.id.tv_headline)
    private val tvSub: TextView = activity.findViewById(R.id.tv_sub)
    private val chipMode: View = activity.findViewById(R.id.chip_mode)
    private val dotMode: View = activity.findViewById(R.id.dot_mode)
    private val tvMode: TextView = activity.findViewById(R.id.tv_mode)
    private val ivParticles: View = activity.findViewById(R.id.iv_particles)

    init {
        btnStopDone.isActivated = true
    }

    fun render(state: DrivingUiState) {
        root.setBackgroundColor(activity.getColor(state.display.backgroundRes()))

        // 미션 이름 칩 + 미션 타일 (b·c·d) / 상태 타일 (a·e·f·g)
        val mission = state.mission
        tvMissionName.visibility = if (mission != null) View.VISIBLE else View.GONE
        tvMissionName.text = mission?.title()
        ivTile.setImageResource(mission?.driveTileRes() ?: state.display.stateTileRes())
        setTopMargin(ivTile, if (mission != null) TILE_TOP_WITH_CHIP_DP else TILE_TOP_DP)

        tvHeadline.text = state.headline
        tvSub.text = state.sub

        // [정차 완료]가 있으면 모드 칩이 그 아래로
        btnStopDone.visibility = if (state.stopDoneVisible) View.VISIBLE else View.GONE
        setTopMargin(chipMode, if (state.stopDoneVisible) CHIP_TOP_UNDER_BUTTON_DP else CHIP_TOP_DP)

        // 🟡 피그마의 "· 눌러서 끄기/켜기"는 주행 중 조작이라 팀 결정 전까지 빼 둔다
        tvMode.text = state.modeChip
        dotMode.backgroundTintList = ColorStateList.valueOf(
            activity.getColor(if (state.modeOn) R.color.status_dot_ok else R.color.gray_400)
        )

        ivParticles.visibility = if (state.display == DrivingDisplay.CORRECT) View.VISIBLE else View.GONE
    }

    private fun setTopMargin(view: View, dp: Int) {
        val lp = view.layoutParams as ViewGroup.MarginLayoutParams
        val px = (dp * activity.resources.displayMetrics.density).toInt()
        if (lp.topMargin != px) {
            lp.topMargin = px
            view.layoutParams = lp
        }
    }

    private companion object {
        const val TILE_TOP_DP = 170              // 미션 칩 없을 때 타일 위 (피그마 a·e·f·g)
        const val TILE_TOP_WITH_CHIP_DP = 45     // 미션 칩(110 + 높이) 아래 → 타일 위 230
        const val CHIP_TOP_DP = 98
        const val CHIP_TOP_UNDER_BUTTON_DP = 52
    }
}

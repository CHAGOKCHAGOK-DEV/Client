package com.chagok.chagok.ui.component

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.chagok.chagok.R
import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.ui.state.MissionAvailabilityUi
import com.chagok.chagok.ui.state.MissionRowStatus

/** 미션 아이콘 (S-05 미션 행, S-06 미션 화면에서 같이 쓴다) */
@DrawableRes
fun MissionCode.iconRes(): Int = when (this) {
    MissionCode.BLINK -> R.drawable.ic_mission_blinker
    MissionCode.WIPER -> R.drawable.ic_mission_windshield
    MissionCode.NEXT_TURN -> R.drawable.ic_mission_turn
    MissionCode.NEAR_DEST -> R.drawable.ic_mission_dest
}

/** 아이콘 타일 배경색 (아이콘 색에서 뽑은 연한 색) */
@ColorRes
fun MissionCode.tileColorRes(): Int = when (this) {
    MissionCode.BLINK -> R.color.mission_tile_blinker
    MissionCode.WIPER -> R.color.mission_tile_windshield
    MissionCode.NEXT_TURN -> R.color.mission_tile_turn
    MissionCode.NEAR_DEST -> R.color.mission_tile_dest
}

/**
 * item_mission_row 한 줄을 그린다 (피그마 S05_미션행: 미션 × 상태 4가지).
 * 꺼진 상태(출발하면 시작해요 / 이 차는 아직 안 돼요)는 타일·글자·칩이 회색.
 */
fun View.bindMissionRow(ui: MissionAvailabilityUi) {
    val off = ui.status == MissionRowStatus.ON_DRIVE || ui.status == MissionRowStatus.UNSUPPORTED

    findViewById<View>(R.id.tile_mission_icon).backgroundTintList =
        color(if (off) R.color.mission_tile_off else ui.code.tileColorRes())
    findViewById<ImageView>(R.id.iv_mission_icon).setImageResource(ui.code.iconRes())

    findViewById<TextView>(R.id.tv_mission_title).apply {
        text = ui.title
        setTextColor(ContextCompat.getColor(context, if (off) R.color.gray_500 else R.color.gray_600))
    }

    val (label, bg, fg) = when (ui.status) {
        MissionRowStatus.AVAILABLE ->
            Triple(R.string.mission_status_available, R.color.mission_chip_available, R.color.brand_pressed)
        MissionRowStatus.DONE_TODAY ->
            Triple(R.string.mission_status_done, R.color.mint_200, R.color.brand_pressed)
        MissionRowStatus.ON_DRIVE ->
            Triple(R.string.mission_status_on_drive, R.color.mission_tile_off, R.color.gray_500)
        MissionRowStatus.UNSUPPORTED ->
            Triple(R.string.mission_status_unsupported, R.color.mission_tile_off, R.color.gray_500)
    }
    findViewById<TextView>(R.id.tv_mission_status).apply {
        setText(label)
        setTextColor(ContextCompat.getColor(context, fg))
        backgroundTintList = color(bg)
    }
}

private fun View.color(@ColorRes res: Int): ColorStateList =
    ColorStateList.valueOf(ContextCompat.getColor(context, res))

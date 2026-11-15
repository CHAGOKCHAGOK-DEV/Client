package com.chagok.chagok.ui.component

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.chagok.chagok.R
import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.ui.state.DrivingDisplay

/**
 * S-06 미션 타일 (피그마 S06_프론트미션타일: 연한 타일 + 미션 아이콘 + 스피커 배지).
 * 이미지는 334x334 캔버스 왼쪽 위에 320 타일. 그림만 바꾸려면 같은 이름 파일 교체.
 */
@DrawableRes
fun MissionCode.driveTileRes(): Int = when (this) {
    MissionCode.BLINK -> R.drawable.img_mission_tile_blinker
    MissionCode.WIPER -> R.drawable.img_mission_tile_windshield
    MissionCode.NEXT_TURN -> R.drawable.img_mission_tile_turn
    MissionCode.NEAR_DEST -> R.drawable.img_mission_tile_dest
    MissionCode.BLINK_LEARN -> R.drawable.img_mission_tile_sign
    MissionCode.RAIN_STOP -> R.drawable.img_mission_tile_sunwiper
    MissionCode.SPEED_GUESS -> R.drawable.img_mission_tile_gauge
    MissionCode.SIGNAL_WAIT -> R.drawable.img_mission_tile_trafficlight
    MissionCode.DEPART -> R.drawable.img_mission_tile_car
    MissionCode.BLINK_MEMORY -> R.drawable.img_mission_tile_star
    MissionCode.WAIT_BET -> R.drawable.img_mission_tile_clock
    MissionCode.SPEED_CHANGE -> R.drawable.img_mission_tile_flame
    MissionCode.TODAY_REPORT -> R.drawable.img_mission_tile_trophy
}

/** 미션이 없는 상태(a·e·f·g)의 타일 */
@DrawableRes
fun DrivingDisplay.stateTileRes(): Int = when (this) {
    DrivingDisplay.MODE_OFF -> R.drawable.img_drive_tile_mode_off
    DrivingDisplay.STOPPED -> R.drawable.img_drive_tile_stopped
    DrivingDisplay.SIGNAL_LOST -> R.drawable.img_drive_tile_signal
    else -> R.drawable.img_drive_tile_waiting
}

/** 상태별 배경 */
@ColorRes
fun DrivingDisplay.backgroundRes(): Int = when (this) {
    DrivingDisplay.WAITING -> R.color.drive_bg_waiting
    DrivingDisplay.SPEAKING -> R.color.drive_bg_speaking
    DrivingDisplay.LISTENING -> R.color.drive_bg_listening
    DrivingDisplay.CORRECT -> R.color.drive_bg_correct
    DrivingDisplay.WRONG -> R.color.drive_bg_wrong
    DrivingDisplay.MODE_OFF -> R.color.drive_bg_mode_off
    DrivingDisplay.STOPPED -> R.color.drive_bg_stopped
    DrivingDisplay.SIGNAL_LOST -> R.color.drive_bg_signal
}

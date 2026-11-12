package com.chagok.chagok.ui.component

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.chagok.chagok.R

enum class PermissionStatus(
    @StringRes val label: Int,
    @ColorRes val bgColor: Int,
    @ColorRes val textColor: Int,
) {
    NEED(R.string.permission_status_need, R.color.sun_soft, R.color.sun_text),
    GRANTED(R.string.permission_status_granted, R.color.mint_100, R.color.brand_pressed),
    DENIED(R.string.permission_status_denied, R.color.pink_soft, R.color.pink),
}

/** 권한 상태 칩의 글자·색을 한 번에 바꿈 */
fun TextView.setPermissionStatus(status: PermissionStatus) {
    setText(status.label)
    setTextColor(ContextCompat.getColor(context, status.textColor))
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, status.bgColor))
}

/** 권한 카드 전체(칩 + 완료 테두리)를 상태에 맞게 바꿈 */
fun bindPermissionCard(card: android.view.View, chip: TextView, status: PermissionStatus) {
    chip.setPermissionStatus(status)
    card.isSelected = status == PermissionStatus.GRANTED
}
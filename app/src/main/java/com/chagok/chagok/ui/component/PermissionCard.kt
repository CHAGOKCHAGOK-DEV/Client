package com.chagok.chagok.ui.component

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.chagok.chagok.R
import com.chagok.chagok.model.PermissionStatus

/** 권한 상태 칩의 글자·색 */
fun TextView.setPermissionStatus(status: PermissionStatus) {
    val (label, bg, fg) = when (status) {
        PermissionStatus.NEEDED -> Triple(R.string.permission_status_need, R.color.sun_soft, R.color.sun_text)
        PermissionStatus.GRANTED -> Triple(R.string.permission_status_granted, R.color.mint_100, R.color.brand_pressed)
        PermissionStatus.DENIED -> Triple(R.string.permission_status_denied, R.color.pink_soft, R.color.pink)
        PermissionStatus.CHECKING -> Triple(R.string.permission_status_checking, R.color.gray_100, R.color.gray_500) // 🟡 피그마에 없음
    }
    setText(label)
    setTextColor(ContextCompat.getColor(context, fg))
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bg))
}

/** item_permission 카드 하나를 그림. 완료면 brand 2dp 테두리 */
fun View.bindPermissionCard(title: CharSequence, status: PermissionStatus) {
    findViewById<TextView>(R.id.tv_permission_title).text = title
    findViewById<TextView>(R.id.tv_permission_status).setPermissionStatus(status)
    isSelected = status == PermissionStatus.GRANTED
}
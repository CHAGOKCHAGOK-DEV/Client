package com.chagok.chagok.ui.component

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.chagok.chagok.R

/** item_side_tab 하나를 그린다. 현재 탭(selected)은 초록 배경 + 흰 굵은 글자 */
fun View.bindSideTab(@DrawableRes icon: Int, @StringRes label: Int, selected: Boolean) {
    isSelected = selected
    findViewById<ImageView>(R.id.iv_tab_icon).setImageResource(icon)
    findViewById<TextView>(R.id.tv_tab_label).apply {
        setText(label)
        isSelected = selected
        typeface = context.astaSans(if (selected) 800 else 600)
    }
}

package com.chagok.chagok.ui.component

import android.view.View
import android.widget.TextView
import com.chagok.chagok.R

fun View.bindCheckCard(text: CharSequence, checked: Boolean) {
    findViewById<TextView>(R.id.tv_check_title).text = text
    isSelected = checked
}
package com.chagok.chagok.ui.component

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.chagok.chagok.R

fun View.bindCheckCard(text: CharSequence, checked: Boolean, onView: (() -> Unit)? = null) {
    findViewById<TextView>(R.id.tv_check_title).text = text
    isSelected = checked
    findViewById<TextView>(R.id.tv_check_view).apply {
        isVisible = onView != null
        setOnClickListener { onView?.invoke() }
    }
}
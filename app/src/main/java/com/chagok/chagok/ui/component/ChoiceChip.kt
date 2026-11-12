package com.chagok.chagok.ui.component

import android.graphics.Typeface
import android.widget.TextView

fun TextView.setChoiceSelected(selected: Boolean) {
    isSelected = selected
    typeface = if (selected) {
        Typeface.create("sans-serif", Typeface.BOLD)
    } else {
        Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
}
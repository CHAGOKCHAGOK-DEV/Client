package com.chagok.chagok.ui.component

import android.widget.TextView

fun TextView.setChoiceSelected(selected: Boolean) {
    isSelected = selected
    typeface = context.astaSans(if (selected) 700 else 600)
}
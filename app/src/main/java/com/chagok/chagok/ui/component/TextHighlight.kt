package com.chagok.chagok.ui.component

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.annotation.StringRes
import com.chagok.chagok.R

fun Context.highlight(@StringRes fullRes: Int, @StringRes keyRes: Int): CharSequence =
    highlight(getString(fullRes), getString(keyRes))

fun Context.highlight(full: String, key: String): CharSequence {
    val s = SpannableString(full)
    val i = full.indexOf(key)
    if (i >= 0) {
        s.setSpan(
            ForegroundColorSpan(getColor(R.color.brand)),
            i, i + key.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return s
}
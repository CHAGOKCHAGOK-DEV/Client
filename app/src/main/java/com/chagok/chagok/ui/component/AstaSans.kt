package com.chagok.chagok.ui.component

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.chagok.chagok.R

/** Asta Sans를 원하는 굵기로 (600, 700 등) */
fun Context.astaSans(weight: Int): Typeface =
    Typeface.create(ResourcesCompat.getFont(this, R.font.asta_sans), weight, false)
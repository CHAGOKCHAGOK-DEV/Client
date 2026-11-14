package com.chagok.chagok.ui.component

import android.app.Activity
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.chagok.chagok.R

/**
 * C-03 토스트: 화면 아래 가운데 알약 모양 한 줄, 2.2초 뒤 사라짐. 조작 없음.
 * 같은 화면에서 다시 부르면 앞의 것을 바로 바꾼다.
 *
 *   ChagokToast.show(this, "연결이 돌아왔어요")
 */
object ChagokToast {

    const val DURATION_MS = 2_200L
    private const val FADE_MS = 200L
    private const val TAG = "chagok_toast"

    fun show(activity: Activity, text: CharSequence) {
        val root = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        root.findViewWithTag<View>(TAG)?.let { old ->
            old.animate().cancel()
            root.removeView(old)
        }

        val view = LayoutInflater.from(activity).inflate(R.layout.view_toast, root, false) as TextView
        view.tag = TAG
        view.text = text
        view.alpha = 0f
        root.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply { bottomMargin = activity.resources.getDimensionPixelSize(R.dimen.toast_bottom) },
        )
        view.animate().alpha(1f).setDuration(FADE_MS).withEndAction {
            view.animate().alpha(0f).setStartDelay(DURATION_MS - FADE_MS * 2).setDuration(FADE_MS)
                .withEndAction { root.removeView(view) }
                .start()
        }.start()
    }
}

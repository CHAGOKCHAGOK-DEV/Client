package com.chagok.chagok.ui.component

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import com.chagok.chagok.R

/**
 * C-02 주행 잠금 오버레이 (피그마 S07_h 카드).
 * 앱 창 전체를 어둡게 덮고 가운데 카드 "주행 중이에요 / 차를 멈추면 계속할 수 있어요".
 * 버튼 없음. 바깥 탭·뒤로가기로 안 닫힌다. 정차가 확정되면 화면이 dismiss()로 닫는다.
 * 보통은 직접 쓰지 말고 DriveLockGuard를 쓴다.
 */
class DriveLockOverlay(context: Context) : AppCompatDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_chagok)

        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)   // 딤은 레이아웃이 직접
        }
        setCancelable(false)

        findViewById<TextView>(R.id.tv_dialog_title)?.setText(R.string.lock_title)
        findViewById<TextView>(R.id.tv_dialog_message)?.setText(R.string.lock_sub)
        // 버튼 줄은 쓰지 않는다
        (findViewById<View>(R.id.btn_dialog_right)?.parent as? View)?.visibility = View.GONE
    }
}

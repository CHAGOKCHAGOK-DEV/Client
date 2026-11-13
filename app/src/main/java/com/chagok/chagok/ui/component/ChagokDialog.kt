package com.chagok.chagok.ui.component

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import com.chagok.chagok.R

/**
 * 공통 모달
 * - 왼쪽(회색) / 오른쪽(초록) 버튼: 누르면 닫고 콜백
 * - 딤(모달 바깥) 탭 / 뒤로가기 → 닫기만
 */
open class ChagokDialog(
    context: Context,
    private val title: CharSequence,
    private val message: CharSequence,
    private val leftText: CharSequence,
    private val rightText: CharSequence,
    private val onLeft: () -> Unit = {},
    private val onRight: () -> Unit = {},
) : AppCompatDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_chagok)

        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)   // 딤은 레이아웃이 직접
        }
        setCancelable(true)

        findViewById<TextView>(R.id.tv_dialog_title)?.text = title
        findViewById<TextView>(R.id.tv_dialog_message)?.text = message
        findViewById<android.view.View>(R.id.dim)?.setOnClickListener { dismiss() }

        findViewById<TextView>(R.id.btn_dialog_left)?.apply {
            text = leftText
            setOnClickListener { dismiss(); onLeft() }
        }
        findViewById<TextView>(R.id.btn_dialog_right)?.apply {
            text = rightText
            isActivated = true   // CTA 온 상태 (bg_cta_primary는 activated일 때 초록)
            setOnClickListener { dismiss(); onRight() }
        }
    }
}
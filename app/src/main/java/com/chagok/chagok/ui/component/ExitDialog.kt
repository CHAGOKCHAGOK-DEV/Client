package com.chagok.chagok.ui.component

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialog
import com.chagok.chagok.R

/**
 * 나가기 모달 (S02_3).
 * 시스템 딤이 Pleos에서 안 먹어서, 화면 전체에 딤 레이어를 직접 깐다.
 * - [나가기] → onLeave
 * - [계속하기] / 딤(모달 바깥) 탭 / 뒤로가기 → 닫기만
 */
class ExitDialog(
    context: Context,
    private val onLeave: () -> Unit,
) : AppCompatDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_exit)

        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)   // 딤은 레이아웃이 직접
        }
        setCancelable(true)

        findViewById<View>(R.id.dim)?.setOnClickListener { dismiss() }
        findViewById<View>(R.id.btn_exit_leave)?.setOnClickListener {
            dismiss()
            onLeave()
        }
        findViewById<View>(R.id.btn_exit_continue)?.apply {
            isActivated = true   // CTA 온 상태 (bg_cta_primary는 activated일 때 초록)
            setOnClickListener { dismiss() }
        }
    }
}
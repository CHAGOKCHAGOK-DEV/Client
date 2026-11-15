package com.chagok.chagok.ui.component

import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chagok.chagok.ChagokApp
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * C-02 주행 잠금 (S-02·S-03·S-04·S-07 공통).
 * - 주행 판정 → 잠금 오버레이(DriveLockOverlay) 자동 표시, 키보드 닫기
 * - 주행 중 터치 차단. 오버레이는 바깥 탭·뒤로가기로 안 닫힘
 * - 정차 확정 → 자동 해제. 화면 입력값은 건드리지 않음
 *
 * 쓰는 법 (Activity):
 *   driveLock = DriveLockGuard(this)                           // onCreate에서
 *   override fun dispatchTouchEvent(ev: MotionEvent) =
 *       driveLock.interceptTouch(ev) || super.dispatchTouchEvent(ev)
 *   driveLock.release()                                        // onDestroy에서
 *
 * @param locked 잠금 여부. 기본은 주행 판정(AppState.uiLocked)
 */
class DriveLockGuard(
    private val activity: AppCompatActivity,
    private val locked: StateFlow<Boolean> = (activity.application as ChagokApp).appState.uiLocked,
) {
    private var overlay: DriveLockOverlay? = null

    init {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                locked.collect { driving -> if (driving) show() else hide() }
            }
        }
    }

    /** 주행 중이면 화면 터치를 막는다. true = 터치 먹음 */
    fun interceptTouch(ev: MotionEvent): Boolean {
        if (!locked.value) return false
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) show()
        return true
    }

    /** onDestroy에서 */
    fun release() = hide()

    private fun show() {
        if (overlay?.isShowing == true || activity.isFinishing) return
        hideKeyboard()
        overlay = DriveLockOverlay(activity).also { it.show() }
    }

    private fun hide() {
        overlay?.dismiss()
        overlay = null
    }

    /** 입력 중이던 글자는 그대로 두고 키보드만 닫는다 */
    private fun hideKeyboard() {
        val focused = activity.currentFocus ?: return
        activity.getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }
}

package com.chagok.chagok.ui.component

import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chagok.chagok.ChagokApp
import com.chagok.chagok.R
import kotlinx.coroutines.launch

/**
 * 주행 정지 모달 (S-02·S-03·S-04 공통).
 * - 주행 판정 → 모달 자동 표시, 키보드 닫기
 * - 주행 중 터치 차단: 바깥 탭·뒤로가기로 안 닫힘. [확인]으로 닫아도 화면을 만지면 다시 뜸
 * - 정차 확정 → 자동 해제. 화면 입력값은 건드리지 않음
 *
 * 쓰는 법 (Activity):
 *   driveLock = DriveLockGuard(this) { showExitModal() }      // onCreate에서
 *   override fun dispatchTouchEvent(ev: MotionEvent) =
 *       driveLock.interceptTouch(ev) || super.dispatchTouchEvent(ev)
 */
class DriveLockGuard(
    private val activity: AppCompatActivity,
    private val onLater: () -> Unit,
) {
    private val locked = (activity.application as ChagokApp).appState.uiLocked
    private var dialog: ChagokDialog? = null

    init {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                locked.collect { driving -> if (driving) show() else hide() }
            }
        }
    }

    /** 주행 중이면 화면 터치를 막고 모달을 다시 띄운다. true = 터치 먹음 */
    fun interceptTouch(ev: MotionEvent): Boolean {
        if (!locked.value) return false
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) show()
        return true
    }

    /** onDestroy에서 */
    fun release() = hide()

    private fun show() {
        if (dialog?.isShowing == true || activity.isFinishing) return
        hideKeyboard()
        dialog = ChagokDialog(
            context = activity,
            title = activity.getString(R.string.drive_warning_title),
            message = activity.getString(R.string.drive_warning_message),
            leftText = activity.getString(R.string.drive_warning_later),
            rightText = activity.getString(R.string.common_ok),
            onLeft = onLater,
            cancelable = false,
        ).also { it.show() }
    }

    private fun hide() {
        dialog?.dismiss()
        dialog = null
    }

    /** 입력 중이던 글자는 그대로 두고 키보드만 닫는다 */
    private fun hideKeyboard() {
        val focused = activity.currentFocus ?: return
        activity.getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }
}
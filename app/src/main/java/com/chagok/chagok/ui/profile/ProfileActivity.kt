package com.chagok.chagok.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chagok.chagok.ChagokApp
import com.chagok.chagok.R
import com.chagok.chagok.model.AgeGroup
import com.chagok.chagok.ui.common.intentFor
import com.chagok.chagok.ui.component.ExitDialog
import com.chagok.chagok.ui.component.NicknameInputView
import com.chagok.chagok.ui.component.highlight
import com.chagok.chagok.ui.component.setChoiceSelected
import com.chagok.chagok.ui.state.ProfileUiState
import com.chagok.chagok.ui.state.Screen
import com.chagok.chagok.ui.state.UiEvent
import kotlinx.coroutines.launch
import android.view.MotionEvent
import com.chagok.chagok.ui.component.DriveLockGuard

/**
 * S-04 부기장 설정 (S04_1, S04_2).
 * 첫 실행: [시작] / [나중에]·X·뒤로가기 → 나가기 모달
 * 수정 모드(EXTRA_EDIT): [저장] / [취소]·X·뒤로가기 → 입력 버리고 S-05
 */
class ProfileActivity : AppCompatActivity() {

    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var tvBubble: TextView
    private lateinit var input: NicknameInputView
    private lateinit var tvError: TextView
    private lateinit var chips: Map<AgeGroup, TextView>
    private lateinit var btnSave: View

    private var editMode = false
    private var exitDialog: ExitDialog? = null
    private lateinit var driveLock: DriveLockGuard
    private var navigated = false

    /** 주행 중엔 화면 터치 차단 */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean =
        driveLock.interceptTouch(ev) || super.dispatchTouchEvent(ev)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        driveLock = DriveLockGuard(this) { onBackOrCancel() }
        editMode = intent.getBooleanExtra(EXTRA_EDIT, false)

        tvBubble = findViewById(R.id.tv_bubble)
        input = findViewById(R.id.input_nickname)
        tvError = findViewById(R.id.tv_nickname_error)
        btnSave = findViewById(R.id.btn_save)
        chips = mapOf(
            AgeGroup.AGE_5_6 to findViewById(R.id.chip_age_5_6),
            AgeGroup.AGE_7_8 to findViewById(R.id.chip_age_7_8),
            AgeGroup.AGE_9P to findViewById(R.id.chip_age_9p),
        )

        findViewById<TextView>(R.id.tv_title).text =
            highlight(R.string.profile_title, R.string.profile_title_key)
        findViewById<TextView>(R.id.btn_save)
            .setText(if (editMode) R.string.profile_save else R.string.profile_start)
        findViewById<TextView>(R.id.btn_secondary)
            .setText(if (editMode) R.string.profile_cancel else R.string.perm_later)

        // 수정 모드: 저장된 별명 채우기 (화면 회전 등으로 다시 만들어질 땐 EditText가 알아서 복원)
        if (editMode && savedInstanceState == null) input.setText(viewModel.state.value.nickname)

        input.onTextChanged = { viewModel.onNicknameChanged(it) }
        chips.forEach { (age, chip) ->
            chip.text = age.label
            chip.setOnClickListener { viewModel.selectAge(age) }
        }
        btnSave.setOnClickListener { viewModel.save() }
        findViewById<View>(R.id.btn_secondary).setOnClickListener { onBackOrCancel() }
        findViewById<View>(R.id.btn_close).setOnClickListener { onBackOrCancel() }
        onBackPressedDispatcher.addCallback(this) { onBackOrCancel() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch { viewModel.events.collect { handle(it) } }
            }
        }
    }

    override fun onDestroy() {
        exitDialog?.dismiss()
        super.onDestroy()
    }

    private fun render(state: ProfileUiState) {
        tvBubble.text = state.bubble
        tvError.text = state.nicknameError
        tvError.visibility = if (state.nicknameError != null) View.VISIBLE else View.INVISIBLE
        chips.forEach { (age, chip) -> chip.setChoiceSelected(age == state.age) }
        btnSave.isActivated = state.saveEnabled
    }

    private fun handle(event: UiEvent) {
        when (event) {
            is UiEvent.Toast -> Toast.makeText(this, event.text, Toast.LENGTH_SHORT).show()   // TODO: C-03
            is UiEvent.Navigate -> navigate(event.to)
            UiEvent.ExitApp -> exitApp()
        }
    }

    /** 첫 실행은 나가기 모달, 수정 모드는 입력 버리고 S-05 */
    private fun onBackOrCancel() {
        if (editMode) {
            viewModel.cancel()
            return
        }
        if (exitDialog?.isShowing == true) return
        exitDialog = ExitDialog(this) { exitApp() }.also { it.show() }
    }

    private fun exitApp() {
        (application as ChagokApp).appState.release()
        finishAffinity()
    }

    private fun navigate(next: Screen) {
        if (navigated) return
        navigated = true
        input.finishEditing()   // 키보드 닫기
        startActivity(intentFor(next))
        overridePendingTransition(0, 0)
        finish()
    }

    companion object {
        /** S-05에서 수정으로 들어올 때 true */
        const val EXTRA_EDIT = "edit"
    }
}
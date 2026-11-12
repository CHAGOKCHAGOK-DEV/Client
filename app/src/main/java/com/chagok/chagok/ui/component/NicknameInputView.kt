package com.chagok.chagok.ui.component

import android.content.Context
import android.text.InputFilter
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.chagok.chagok.R

/**
 * S-04 부기장 별명 입력칸. 상태 3개를 스스로 바꾼다.
 * - 미입력: 힌트 (gray-400, 600)
 * - 입력 중: 글자 (gray-600, 700) + "n/8"
 * - 입력 완료: 민트 배경 + 연필. 키보드 [완료]로 들어가고, 탭하면 다시 입력 중
 *
 * 레이아웃에서: <com.chagok.chagok.ui.component.NicknameInputView android:layout_width="500dp" ... />
 */
class NicknameInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val editText: EditText
    private val count: TextView
    private val editIcon: ImageView

    private var editing = false

    /** 글자가 바뀔 때마다 (앞뒤 공백 제거한 값) */
    var onTextChanged: ((String) -> Unit)? = null

    /** 지금 입력된 별명 (앞뒤 공백 제거) */
    val text: String get() = editText.text.toString().trim()

    /** 입력 완료 상태인가 (글자가 있고 입력 중이 아님) */
    val isComplete: Boolean get() = !editing && text.isNotEmpty()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_input)
        val d = resources.displayMetrics.density
        // padding 28/32, 아래는 그림자 4 포함 32
        setPadding((32 * d).toInt(), (28 * d).toInt(), (32 * d).toInt(), (32 * d).toInt())

        LayoutInflater.from(context).inflate(R.layout.view_nickname_input, this, true)
        editText = findViewById(R.id.et_nickname)
        count = findViewById(R.id.tv_nickname_count)
        editIcon = findViewById(R.id.iv_nickname_edit)

        editText.filters = arrayOf(InputFilter.LengthFilter(MAX_LENGTH))
        editText.doAfterTextChanged {
            render()
            onTextChanged?.invoke(text)
        }
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) startEditing() else finishEditing()
        }
        editText.setOnClickListener { startEditing() }
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                finishEditing(); true
            } else false
        }
        setOnClickListener { startEditing() }
        editIcon.setOnClickListener { startEditing() }

        render()
    }

    /** 저장된 별명을 채워 넣을 때 (입력 완료 상태로 보임) */
    fun setText(value: String) {
        editText.setText(value)
        editing = false
        render()
    }

    fun startEditing() {
        editing = true
        editText.isCursorVisible = true
        if (!editText.hasFocus()) editText.requestFocus()
        editText.setSelection(editText.length())
        imm().showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        render()
    }

    fun finishEditing() {
        editing = false
        editText.isCursorVisible = false
        imm().hideSoftInputFromWindow(editText.windowToken, 0)
        render()
    }

    private fun render() {
        val len = editText.length()
        val done = !editing && len > 0

        isSelected = done
        editText.typeface = context.astaSans(if (len > 0) 700 else 600)
        count.isVisible = editing
        count.text = context.getString(R.string.nickname_count, len, MAX_LENGTH)
        editIcon.isVisible = done
    }

    private fun imm() = context.getSystemService(InputMethodManager::class.java)

    companion object {
        const val MAX_LENGTH = 8
    }
}
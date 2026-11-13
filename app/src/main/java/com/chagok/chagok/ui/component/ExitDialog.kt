package com.chagok.chagok.ui.component

import android.content.Context
import com.chagok.chagok.R

/**
 * 나가기 모달 (S02_3).
 * - [나가기] → onLeave
 * - [계속하기] / 바깥 탭 / 뒤로가기 → 닫기만
 */
class ExitDialog(
    context: Context,
    onLeave: () -> Unit,
) : ChagokDialog(
    context = context,
    title = context.getString(R.string.exit_modal_title),
    message = context.getString(R.string.exit_modal_sub),
    leftText = context.getString(R.string.exit_modal_leave),
    rightText = context.getString(R.string.exit_modal_continue),
    onLeft = onLeave,
)
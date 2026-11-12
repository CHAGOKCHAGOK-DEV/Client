package com.chagok.chagok.data.remote

import android.content.Context

/**
 * 서버 전송 실패 표시 (재시도 큐). 다음 앱 시작(S-01 3/3) 때 다시 보낸다.
 */
class PendingSyncStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** S-03 동의를 아직 서버에 못 보냄 */
    var consent: Boolean
        get() = prefs.getBoolean(KEY_CONSENT, false)
        set(v) { prefs.edit().putBoolean(KEY_CONSENT, v).apply() }

    /** S-04 프로필을 아직 서버에 못 보냄 */
    var copilot: Boolean
        get() = prefs.getBoolean(KEY_COPILOT, false)
        set(v) { prefs.edit().putBoolean(KEY_COPILOT, v).apply() }

    /** 서버에 프로필이 만들어진 적 있음 (true면 수정은 PUT) */
    var copilotCreated: Boolean
        get() = prefs.getBoolean(KEY_COPILOT_CREATED, false)
        set(v) { prefs.edit().putBoolean(KEY_COPILOT_CREATED, v).apply() }

    private companion object {
        const val PREFS = "chagok_pending_sync"
        const val KEY_CONSENT = "consent"
        const val KEY_COPILOT = "copilot"
        const val KEY_COPILOT_CREATED = "copilot_created"
    }
}
package com.chagok.chagok.data.remote

import android.content.Context

/**
 * 설치 ID·토큰 저장. 앱 전용 저장소라 다른 앱은 읽을 수 없다.
 * 토큰은 등록 응답에서 한 번만 받으므로 지우면 다시 등록해야 한다 (서버 기록과 끊김).
 */
class InstallationStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val installationId: String? get() = prefs.getString(KEY_ID, null)
    val token: String? get() = prefs.getString(KEY_TOKEN, null)
    val isRegistered: Boolean get() = installationId != null && token != null

    fun save(installationId: String, token: String) {
        prefs.edit()
            .putString(KEY_ID, installationId)
            .putString(KEY_TOKEN, token)
            .commit()   // 토큰은 다시 못 받으니 바로 디스크에 쓴다
    }

    fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val PREFS = "chagok_installation"
        const val KEY_ID = "installation_id"
        const val KEY_TOKEN = "installation_token"
    }
}
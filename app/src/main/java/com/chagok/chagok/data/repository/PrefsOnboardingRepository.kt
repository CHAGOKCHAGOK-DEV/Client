package com.chagok.chagok.data.repository

import android.content.Context
import com.chagok.chagok.ui.common.MissionIntensity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * S-03 부모 동의 · S-04 부기장 설정 저장 (기기 안에만 저장).
 *
 * - 아이 정보는 닉네임·연령대만. 실명·사진·음성은 저장하지 않는다.
 * - 동의를 철회하거나 [기록 삭제]를 하면 프로필까지 모두 지운다.
 * - 값은 앱 시작 때 한 번 읽어 StateFlow로 들고 있고, 쓸 때 바로 반영한다 (화면 분기 S-01이 즉시 읽음).
 */
class PrefsOnboardingRepository(context: Context) : OnboardingRepository {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _consented = MutableStateFlow(prefs.getBoolean(KEY_CONSENTED, false))
    override val consented: StateFlow<Boolean> = _consented.asStateFlow()

    private val _profile = MutableStateFlow(readProfile())
    override val profile: StateFlow<CopilotProfile?> = _profile.asStateFlow()

    private val _intensity = MutableStateFlow(readIntensity())
    /** 미션 강도 (S-04). AppState.setIntensity()로 넘겨 준다 */
    val intensity: StateFlow<MissionIntensity> = _intensity.asStateFlow()

    /** 동의한 시각 (설정 화면 표시·서버 업로드용). 동의 안 했으면 null */
    val consentedAtMs: Long? get() = prefs.getLong(KEY_CONSENTED_AT, 0L).takeIf { it > 0 }

    /** S-03 [동의하고 시작] */
    fun agree(nowMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putBoolean(KEY_CONSENTED, true)
            .putLong(KEY_CONSENTED_AT, nowMs)
            .putInt(KEY_CONSENT_VERSION, CONSENT_VERSION)
            .apply()
        _consented.value = true
    }

    /** 동의 철회. 아이 정보가 남지 않도록 프로필도 지운다 */
    fun withdraw() {
        clearAll()
    }

    /** S-04 [저장]. 닉네임은 앞뒤 공백을 지워서 저장 */
    fun saveProfile(profile: CopilotProfile, intensity: MissionIntensity = _intensity.value) {
        val p = profile.copy(nickname = profile.nickname.trim())
        prefs.edit()
            .putString(KEY_NICKNAME, p.nickname)
            .putString(KEY_AGE_GROUP, p.ageGroup)
            .putString(KEY_INTENSITY, intensity.name)
            .apply()
        _profile.value = p
        _intensity.value = intensity
    }

    /** 기기에 저장된 동의·프로필을 모두 지운다 */
    fun clearAll() {
        prefs.edit().clear().apply()
        _consented.value = false
        _profile.value = null
        _intensity.value = MissionIntensity.NORMAL
    }

    private fun readProfile(): CopilotProfile? {
        val name = prefs.getString(KEY_NICKNAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val age = prefs.getString(KEY_AGE_GROUP, null) ?: return null
        return CopilotProfile(name, age)
    }

    private fun readIntensity(): MissionIntensity =
        prefs.getString(KEY_INTENSITY, null)
            ?.let { runCatching { MissionIntensity.valueOf(it) }.getOrNull() }
            ?: MissionIntensity.NORMAL

    private companion object {
        const val PREFS_NAME = "onboarding"
        const val KEY_CONSENTED = "consented"
        const val KEY_CONSENTED_AT = "consented_at"
        const val KEY_CONSENT_VERSION = "consent_version"
        const val KEY_NICKNAME = "nickname"
        const val KEY_AGE_GROUP = "age_group"
        const val KEY_INTENSITY = "intensity"

        /** 동의 문구가 바뀌면 올린다. 저장된 버전이 낮으면 다시 동의를 받는 데 쓴다 */
        const val CONSENT_VERSION = 1
    }
}

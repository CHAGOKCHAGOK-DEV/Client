package com.chagok.chagok.data.repository

import android.content.Context
import com.chagok.chagok.ui.common.MissionIntensity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 동의·프로필을 기기에만 저장 (서버 전송 없음) */
class PrefsOnboardingRepository(context: Context) : OnboardingRepository {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _consent = MutableStateFlow(readConsent())
    override val consent: StateFlow<ConsentRecord?> = _consent.asStateFlow()

    private val _consented = MutableStateFlow(ConsentRecord.isValid(_consent.value))
    override val consented: StateFlow<Boolean> = _consented.asStateFlow()

    private val _profile = MutableStateFlow(readProfile())
    override val profile: StateFlow<CopilotProfile?> = _profile.asStateFlow()

    private val _intensity = MutableStateFlow(readIntensity())
    val intensity: StateFlow<MissionIntensity> = _intensity.asStateFlow()

    override fun saveConsent(record: ConsentRecord) {
        prefs.edit()
            .putInt(KEY_CONSENT_VERSION, record.version)
            .putLong(KEY_CONSENT_AT, record.agreedAtMs)
            .putBoolean(KEY_ITEM_NICKNAME, ConsentItem.NICKNAME in record.items)
            .putBoolean(KEY_ITEM_VOICE, ConsentItem.VOICE_DISCARD in record.items)
            .putBoolean(KEY_ITEM_FAMILY, ConsentItem.FAMILY_ONLY in record.items)
            .apply()
        _consent.value = record
        _consented.value = ConsentRecord.isValid(record)
    }

    // TODO: S-04에서 명세대로 수정
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

    fun clearAll() {
        prefs.edit().clear().apply()
        _consent.value = null
        _consented.value = false
        _profile.value = null
        _intensity.value = MissionIntensity.NORMAL
    }

    private fun readConsent(): ConsentRecord? {
        if (!prefs.contains(KEY_CONSENT_VERSION)) return null
        val items = buildSet {
            if (prefs.getBoolean(KEY_ITEM_NICKNAME, false)) add(ConsentItem.NICKNAME)
            if (prefs.getBoolean(KEY_ITEM_VOICE, false)) add(ConsentItem.VOICE_DISCARD)
            if (prefs.getBoolean(KEY_ITEM_FAMILY, false)) add(ConsentItem.FAMILY_ONLY)
        }
        return ConsentRecord(
            version = prefs.getInt(KEY_CONSENT_VERSION, 0),
            items = items,
            agreedAtMs = prefs.getLong(KEY_CONSENT_AT, 0L),
        )
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
        const val KEY_CONSENT_VERSION = "consent_version"
        const val KEY_CONSENT_AT = "consent_accepted_at"
        const val KEY_ITEM_NICKNAME = "consent_nickname"
        const val KEY_ITEM_VOICE = "consent_voice_discard"
        const val KEY_ITEM_FAMILY = "consent_family_only"
        const val KEY_NICKNAME = "nickname"
        const val KEY_AGE_GROUP = "age_group"
        const val KEY_INTENSITY = "intensity"
    }
}

package com.chagok.chagok.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 부기장 프로필 중 A 화면(S-05 칩, S-06 칩)에 필요한 값 */
data class CopilotProfile(
    val nickname: String,
    val ageGroup: String,            // TODO: 명세에서 빠짐, S-04에서 제거
)

/** S-03 동의 항목 (화면 순서) */
enum class ConsentItem { NICKNAME, VOICE_DISCARD, FAMILY_ONLY }

data class ConsentRecord(
    val version: Int,
    val items: Set<ConsentItem>,
    val agreedAtMs: Long,
) {
    val allAgreed: Boolean get() = items.containsAll(ConsentItem.entries)

    companion object {
        /** 동의 문구가 바뀌면 올린다 (낮은 버전은 다시 동의) */
        const val CURRENT_VERSION = 1

        fun isValid(r: ConsentRecord?): Boolean =
            r != null && r.version >= CURRENT_VERSION && r.allAgreed
    }
}

/** 동의(S-03)·프로필(S-04) 저장소 */
interface OnboardingRepository {
    val consented: StateFlow<Boolean>
    val consent: StateFlow<ConsentRecord?>
    val profile: StateFlow<CopilotProfile?>
    fun saveConsent(record: ConsentRecord)
    /** 서버 약관이 바뀌어 다시 동의받아야 할 때 */
    fun clearConsent()
}

/** 개발·테스트용 */
class InMemoryOnboardingRepository(
    consented: Boolean = false,
    profile: CopilotProfile? = null,
) : OnboardingRepository {
    private val _consent = MutableStateFlow(if (consented) fullConsent() else null)
    private val _consented = MutableStateFlow(consented)
    private val _profile = MutableStateFlow(profile)
    override val consent: StateFlow<ConsentRecord?> = _consent.asStateFlow()
    override val consented: StateFlow<Boolean> = _consented.asStateFlow()
    override val profile: StateFlow<CopilotProfile?> = _profile.asStateFlow()

    override fun saveConsent(record: ConsentRecord) {
        _consent.value = record
        _consented.value = ConsentRecord.isValid(record)
    }

    override fun clearConsent() {
        _consent.value = null
        _consented.value = false
    }

    fun setConsented(v: Boolean) {
        _consent.value = if (v) fullConsent() else null
        _consented.value = v
    }

    fun setProfile(p: CopilotProfile?) { _profile.value = p }

    private fun fullConsent() = ConsentRecord(ConsentRecord.CURRENT_VERSION, ConsentItem.entries.toSet(), 0L)
}

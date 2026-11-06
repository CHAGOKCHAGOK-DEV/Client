package com.chagok.chagok.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 부기장 프로필 중 A 화면(S-05 칩, S-06 칩)에 필요한 값 */
data class CopilotProfile(
    val nickname: String,
    val ageGroup: String,            // "5~6세" / "7~8세" / "9~10세"
)

/**
 * 동의(S-03)·프로필(S-04) 읽기 창구. S-01 분기와 S-05/S-06 칩에서 쓴다.
 * 실제 구현(Room/DataStore)은 S-03·S-04 담당이 이 인터페이스로 만들어 주면 된다.
 */
interface OnboardingRepository {
    val consented: StateFlow<Boolean>
    val profile: StateFlow<CopilotProfile?>
}

/** 개발·테스트용 */
class InMemoryOnboardingRepository(
    consented: Boolean = false,
    profile: CopilotProfile? = null,
) : OnboardingRepository {
    private val _consented = MutableStateFlow(consented)
    private val _profile = MutableStateFlow(profile)
    override val consented: StateFlow<Boolean> = _consented.asStateFlow()
    override val profile: StateFlow<CopilotProfile?> = _profile.asStateFlow()

    fun setConsented(v: Boolean) { _consented.value = v }
    fun setProfile(p: CopilotProfile?) { _profile.value = p }
}

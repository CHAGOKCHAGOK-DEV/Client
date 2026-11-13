package com.chagok.chagok.data.remote

import com.chagok.chagok.data.repository.PrefsOnboardingRepository
import com.chagok.chagok.ui.common.MissionIntensity

/**
 * S-04 부기장 프로필 서버 저장. 로컬에 저장된 프로필을 서버에 반영한다.
 * - 서버에 만든 적 없으면 POST, 있으면 PUT
 * - POST가 서버 오류면(이미 있음 등) PUT으로 한 번 더
 * - 실패하면 표시를 남기고 다음 시작 때 다시 보낸다
 */
class CopilotRepository(
    private val api: ChagokApi,
    private val installations: InstallationRepository,
    private val pending: PendingSyncStore,
    private val onboarding: PrefsOnboardingRepository,
) {

    /** 프로필 저장 직후 */
    suspend fun sync(): Result<Unit> {
        pending.copilot = true
        val profile = onboarding.profile.value ?: run {
            pending.copilot = false
            return Result.success(Unit)
        }
        installations.ensureRegistered().onFailure { return Result.failure(it) }

        val body = CopilotRequest(
            nickname = profile.nickname,
            ageGroup = serverAgeGroup(profile.ageGroup),
            missionIntensity = serverIntensity(onboarding.intensity.value),
        )

        var result = if (pending.copilotCreated) {
            apiCall { api.updateCopilot(body) }
        } else {
            apiCall { api.createCopilot(body) }
        }
        // 서버에 이미 있는데 앱이 모르는 경우 (예: 저장 직후 응답만 못 받음) → 수정으로
        if (!pending.copilotCreated && result.exceptionOrNull() is ApiException) {
            result = apiCall { api.updateCopilot(body) }
        }

        return result
            .onSuccess {
                pending.copilotCreated = true
                pending.copilot = false
            }
            .map { }
    }

    /** S-01: 전에 실패한 게 있으면 다시 */
    suspend fun retryPending(): Result<Unit> =
        if (pending.copilot) sync() else Result.success(Unit)

    private companion object {
        /** 🟡 앱(설계서) AGE_9P ↔ 서버 AGE_9_PLUS. 코드 통일되면 삭제 */
        fun serverAgeGroup(code: String): String = when (code) {
            "AGE_9P" -> "AGE_9_PLUS"
            else -> code
        }

        /** 앱 RELAXED/NORMAL/ACTIVE ↔ 서버 LOW/NORMAL/HIGH */
        fun serverIntensity(i: MissionIntensity): String = when (i) {
            MissionIntensity.RELAXED -> "LOW"
            MissionIntensity.NORMAL -> "NORMAL"
            MissionIntensity.ACTIVE -> "HIGH"
        }
    }
}
package com.chagok.chagok.data.remote

import com.chagok.chagok.data.repository.CopilotProfile
import com.chagok.chagok.data.repository.OnboardingRepository
import com.chagok.chagok.model.AgeGroup

/** 부기장 프로필 API 3개만 (테스트에서 가짜로 바꾸기 쉽게) */
interface CopilotApi {
    suspend fun get(): ApiResponse<CopilotResponse>
    suspend fun create(body: CopilotRequest): ApiResponse<CopilotResponse>
    suspend fun update(body: CopilotRequest): ApiResponse<CopilotResponse>
}

fun ChagokApi.copilotApi(): CopilotApi = object : CopilotApi {
    override suspend fun get() = getCopilot()
    override suspend fun create(body: CopilotRequest) = createCopilot(body)
    override suspend fun update(body: CopilotRequest) = updateCopilot(body)
}

/** 프로필 전송 상태 (PendingSyncStore가 기기에 저장) */
interface CopilotSyncFlags {
    /** 아직 서버에 못 보낸 프로필 수정이 있음 */
    var copilot: Boolean
    /** 서버에 프로필이 있음 (true면 수정은 PUT) */
    var copilotCreated: Boolean
}

/**
 * S-04 부기장 프로필 ↔ 서버 (GET / POST / PUT /api/v1/copilot).
 * - restore(): S-01에서 기기에 프로필이 없으면 서버 것을 가져온다 (기기 저장값이 지워졌을 때 S-04를 다시 안 거치게)
 * - sync(): S-04 저장 직후. 서버에 없으면 POST, 있으면 PUT.
 *   서버와 앱이 알고 있는 게 다르면 한 번 바꿔서 다시 (POST가 409 → PUT, PUT이 404 → POST)
 * - 실패하면 표시를 남기고 다음 시작 때 다시 보낸다. 기기 값이 기준이다 (서버 값으로 덮지 않음)
 */
class CopilotRepository(
    private val api: CopilotApi,
    private val ensureRegistered: suspend () -> Result<Unit>,
    private val flags: CopilotSyncFlags,
    private val onboarding: OnboardingRepository,
    private val saveProfile: (CopilotProfile) -> Unit,
) {

    /**
     * S-01 3/3. 동의는 했는데 기기에 프로필이 없을 때만 서버에서 가져온다.
     * @return 가져와서 저장했으면 true
     */
    suspend fun restore(): Result<Boolean> {
        if (!onboarding.consented.value || onboarding.profile.value != null) return Result.success(false)
        ensureRegistered().onFailure { return Result.failure(it) }
        return apiCall { api.get() }
            .map { server ->
                val age = AgeGroup.fromCode(server.ageGroup)?.code ?: server.ageGroup
                saveProfile(CopilotProfile(nickname = server.nickname, ageGroup = age))
                flags.copilotCreated = true
                flags.copilot = false
                true
            }
            .recoverCatching { e ->
                if (e.isNotFound()) { flags.copilotCreated = false; false }   // 서버에도 없음 → S-04
                else throw e
            }
    }

    /** 프로필 저장 직후 */
    suspend fun sync(): Result<Unit> {
        flags.copilot = true
        val profile = onboarding.profile.value ?: run {
            flags.copilot = false
            return Result.success(Unit)
        }
        ensureRegistered().onFailure { return Result.failure(it) }

        val body = CopilotRequest(
            nickname = profile.nickname,
            ageGroup = AgeGroup.fromCode(profile.ageGroup)?.code ?: profile.ageGroup,
            missionIntensity = SERVER_INTENSITY,
        )

        val result = if (flags.copilotCreated) {
            apiCall { api.update(body) }.retryWhen({ it.isNotFound() }) { apiCall { api.create(body) } }
        } else {
            apiCall { api.create(body) }.retryWhen({ it.isConflict() }) { apiCall { api.update(body) } }
        }

        return result
            .onSuccess {
                flags.copilotCreated = true
                flags.copilot = false
            }
            .map { }
    }

    /** S-01: 전에 실패한 게 있으면 다시 */
    suspend fun retryPending(): Result<Unit> =
        if (flags.copilot) sync() else Result.success(Unit)

    private suspend fun <T> Result<T>.retryWhen(
        cond: (Throwable) -> Boolean,
        again: suspend () -> Result<T>,
    ): Result<T> {
        val e = exceptionOrNull() ?: return this
        return if (cond(e)) again() else this
    }

    private companion object {
        /** 미션 강도 선택은 없앴다 (간격 3분 고정). 서버 필드가 남아 있어 NORMAL로 보낸다 */
        const val SERVER_INTENSITY = "NORMAL"

        fun Throwable.isNotFound() = this is ApiException && (httpStatus == 404 || code == "COPILOT_404")
        fun Throwable.isConflict() = this is ApiException && (httpStatus == 409 || code == "COPILOT_409")
    }
}

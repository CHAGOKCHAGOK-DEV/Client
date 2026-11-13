package com.chagok.chagok.data.remote

import com.chagok.chagok.data.repository.ConsentRecord
import com.chagok.chagok.data.repository.OnboardingRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * S-03 동의 서버 기록.
 * 앱은 모든 항목에 동의해야 넘어가므로, 서버의 현재 필수 문서 전부에 ACCEPTED를 보낸다.
 * 실패하면 표시를 남기고 다음 시작 때 다시 보낸다.
 */
class AgreementRepository(
    private val api: ChagokApi,
    private val installations: InstallationRepository,
    private val pending: PendingSyncStore,
    private val onboarding: OnboardingRepository,
) {
    /** S-03 [보기]용: 현재 약관 (제목·내용 포함) */
    suspend fun fetchCurrent(): Result<List<AgreementDocument>> {
        installations.ensureRegistered().onFailure { return Result.failure(it) }
        return apiCall { api.currentAgreements() }.map { it.documents }
    }

    /** 동의 저장 직후 */
    suspend fun syncConsent(record: ConsentRecord): Result<Unit> {
        pending.consent = true
        return send(record).onSuccess { pending.consent = false }
    }

    /** S-01: 전에 실패한 게 있으면 다시 */
    suspend fun retryPending(): Result<Unit> {
        if (!pending.consent) return Result.success(Unit)
        val record = onboarding.consent.value ?: run {
            pending.consent = false
            return Result.success(Unit)
        }
        return syncConsent(record)
    }

    /**
     * S-01: 서버 기준으로 다시 동의가 필요한지 확인. 필요하면 앱 동의를 지운다 → S-03으로 감.
     * 앱에서 동의한 적 없으면 확인하지 않음 (어차피 S-03). 실패하면 앱 기록 그대로.
     */
    suspend fun verifyWithServer(): Result<Unit> {
        if (!onboarding.consented.value) return Result.success(Unit)

        // 못 보낸 동의가 있으면 먼저 보낸다 (안 그러면 방금 동의한 사람도 '동의 필요'로 나옴)
        if (pending.consent) retryPending().onFailure { return Result.failure(it) }

        installations.ensureRegistered().onFailure { return Result.failure(it) }
        return apiCall { api.agreementStatus() }.map { status ->
            if (status.consentRequired) onboarding.clearConsent()
        }
    }

    private suspend fun send(record: ConsentRecord): Result<Unit> {
        installations.ensureRegistered().onFailure { return Result.failure(it) }

        val docs = apiCall { api.currentAgreements() }
            .getOrElse { return Result.failure(it) }
            .documents
        if (docs.isEmpty()) return Result.success(Unit)

        val occurredAt = Instant.ofEpochMilli(record.agreedAtMs)
            .atZone(SEOUL)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val events = docs.map { doc ->
            ConsentEvent(
                // 같은 동의면 항상 같은 ID → 다시 보내도 서버에 중복 저장되지 않음
                eventId = UUID.nameUUIDFromBytes("${doc.code}:${doc.version}:${record.agreedAtMs}".toByteArray()).toString(),
                code = doc.code,
                version = doc.version,
                action = ACCEPTED,
                occurredAt = occurredAt,
            )
        }
        return apiCall { api.recordConsents(AgreementConsentRequest(events)) }.map { }
    }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        const val ACCEPTED = "ACCEPTED"
    }
}
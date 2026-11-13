package com.chagok.chagok.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---------- 공통 ----------

@Serializable
data class HealthResponse(val status: String, val serverTime: String? = null)

// ---------- S-01 설치 등록 ----------

@Serializable
data class InstallationRegistrationResponse(
    val installationId: String,
    val token: String,          // 이 응답에서만 내려온다. 잃어버리면 다시 받을 수 없음
)

// ---------- S-01 미션팩 ----------

@Serializable
data class MissionPackResponse(
    val code: String,
    val version: Int,
    val schemaVersion: Int,
    val publishedAt: String? = null,
    val missions: List<MissionDto> = emptyList(),
)

@Serializable
data class MissionDto(
    val code: String,
    val triggerType: String,
    val config: JsonElement? = null,   // 미션마다 형태가 달라서 JSON 그대로
)

// ---------- S-03 동의 ----------

@Serializable
data class AgreementDocument(
    val documentId: Long? = null,
    val code: String,
    val version: Int,
    val title: String? = null,          // current 응답에서만
    val content: String? = null,        // current 응답에서만. 마크다운
    val required: Boolean? = null,
    val effectiveAt: String? = null,
    val state: String? = null,          // status 응답에서만: ACCEPTED / OUTDATED / NOT_ACCEPTED
)

@Serializable
data class AgreementCurrentResponse(val documents: List<AgreementDocument> = emptyList())

@Serializable
data class AgreementStatusResponse(
    val consentRequired: Boolean,
    val documents: List<AgreementDocument> = emptyList(),
)

@Serializable
data class ConsentEvent(
    val eventId: String,        // UUID. 같은 eventId로 다시 보내면 처음 기록을 돌려준다 (재시도 안전)
    val code: String,
    val version: Int,
    val action: String,         // ACCEPTED / WITHDRAWN
    val occurredAt: String,     // ISO-8601 (예: 2026-11-12T22:58:00+09:00)
)

@Serializable
data class AgreementConsentRequest(val events: List<ConsentEvent>)

@Serializable
data class AgreementConsentResponse(val consents: List<JsonElement> = emptyList())

// ---------- S-04 부기장 프로필 ----------

@Serializable
data class CopilotRequest(
    val nickname: String,
    val ageGroup: String,           // AGE_5_6 / AGE_7_8 / AGE_9_PLUS
    val missionIntensity: String,   // LOW / NORMAL / HIGH
)

@Serializable
data class CopilotResponse(
    val nickname: String,
    val ageGroup: String,
    val missionIntensity: String,
)
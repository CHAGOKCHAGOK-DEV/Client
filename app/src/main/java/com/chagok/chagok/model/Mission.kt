package com.chagok.chagok.model

import com.chagok.chagok.sdk.speech.Answer

enum class MissionCode { BLINK, WIPER, NEXT_TURN }

enum class MissionState {
    CANDIDATE, HOLD,                 // 대기
    ASKING, LISTENING, JUDGING,      // 진행
    DONE, SKIPPED, EXPIRED           // 완료
}

/** 미션 후보. 중복 키 = code + triggeredAtMs */
data class MissionCandidate(
    val code: MissionCode,
    val triggeredAtMs: Long,
    val expectedAnswer: Answer,     // 생성 시점에 정해 둔 정답
    val expiresAtMs: Long?,         // 시간 기준 유효 (BLINK/WIPER). NEXT_TURN은 null
    val tripId: String? = null,     // 후보가 생긴 주행. 엔진이 offer 시점에 채운다
) {
    val key: String get() = "${code.name}@$triggeredAtMs"
}

/** 완료된 미션 기록. 음성·원문 텍스트는 저장하지 않는다. */
data class MissionResult(
    val id: String,
    val tripId: String?,            // 어느 주행에서 나온 결과인지 (S-07 요약, 업로드 묶음)
    val code: MissionCode,
    val expectedAnswer: Answer,
    val givenAnswer: Answer?,       // 건너뜀이면 null
    val state: MissionState,        // DONE / SKIPPED / EXPIRED
    val correct: Boolean,
    val retryCount: Int,
    val triggeredAtMs: Long,
    val askedAtMs: Long?,
    val answeredAtMs: Long?,
)
package com.chagok.chagok.model

import com.chagok.chagok.sdk.speech.Answer

enum class MissionCode { BLINK, WIPER, NEXT_TURN, NEAR_DEST }

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

/** 저장 데이터의 result 값 */
enum class MissionOutcome { CORRECT, WRONG, SKIPPED, EXPIRED }

/** 완료된 미션 기록. 음성·원문 텍스트는 저장하지 않는다. */
data class MissionResult(
    val id: String,
    val tripId: String?,            // 어느 주행에서 나온 결과인지 (S-07 요약, 업로드 묶음)
    val code: MissionCode,
    val expectedAnswer: Answer,
    val givenAnswer: Answer?,       // 건너뜀 NONE, 묻지 않고 폐기되면 null
    val state: MissionState,        // DONE / SKIPPED / EXPIRED
    val correct: Boolean,
    val retryCount: Int,
    val triggeredAtMs: Long,
    val askedAtMs: Long?,
    val answeredAtMs: Long?,
) {
    val outcome: MissionOutcome get() = when (state) {
        MissionState.SKIPPED -> MissionOutcome.SKIPPED
        MissionState.EXPIRED -> MissionOutcome.EXPIRED
        else -> if (correct) MissionOutcome.CORRECT else MissionOutcome.WRONG
    }

    /** S-07 "N개 중": 오답도 수행한 미션 */
    val attempted: Boolean get() = outcome == MissionOutcome.CORRECT || outcome == MissionOutcome.WRONG

    /** S-07 "M개 성공": 정답만 */
    val succeeded: Boolean get() = outcome == MissionOutcome.CORRECT
}
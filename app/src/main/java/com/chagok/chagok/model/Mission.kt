package com.chagok.chagok.model

import com.chagok.chagok.sdk.speech.Answer

enum class MissionCode {
    BLINK,          // F-01 깜빡이 소리
    WIPER,          // F-03 비 오는 날
    NEXT_TURN,      // F-05 다음 회전
    NEAR_DEST,      // F-12 곧 도착
    // ↓ 화면(S-06·S-05)만 준비됨. 트리거·판정은 이슈 7·8에서 엔진에 붙인다
    BLINK_LEARN,    // F-02 깜빡이 배우기
    RAIN_STOP,      // F-04 비가 그쳤어요
    SPEED_GUESS,    // F-06 빠를까 느릴까
    SIGNAL_WAIT,    // F-07 신호 대기
    DEPART,         // F-08 부기장 출동 (틀림 없음)
    BLINK_MEMORY,   // F-09 깜빡이 기억 게임
    WAIT_BET,       // F-10 기다리기 내기
    SPEED_CHANGE,   // F-11 빨라졌을까 느려졌을까
    TODAY_REPORT,   // F-13 오늘의 리포트 (듣는 중 없음)
}

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

/** 방금 판정한 미션 (S-06 결과 화면: 정답이면 d, 틀리거나 무응답이면 틀림 화면) */
data class JudgedMission(val code: MissionCode, val correct: Boolean)

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
    val interrupted: Boolean = false, // 묻거나 듣는 중에 주행이 끝나 SKIPPED 처리됨 (S-07 요약에서 뺀다)
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
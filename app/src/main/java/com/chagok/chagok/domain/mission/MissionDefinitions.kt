package com.chagok.chagok.domain.mission

import com.chagok.chagok.model.MissionCandidate
import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.model.TurnDir
import com.chagok.chagok.sdk.speech.Answer

/** 미션 한 종류의 대본과 판정 규칙. 질문은 화면에 띄우지 않고 소리로만 전한다. */
data class MissionDefinition(
    val code: MissionCode,
    val question: String,
    val correctMessage: String,
    val wrongMessage: String,
    val validMs: Long?,            // 시간 기준 유효 시간. NEXT_TURN은 null (거리로 폐기)
)

object MissionDefinitions {

    const val BLINK_LOOKBACK_MS = 30_000L   // 직전 30초 안에 깜빡이 기록이 있으면 정답 YES

    val BLINK = MissionDefinition(
        code = MissionCode.BLINK,
        question = "부기장님! 기장님이 깜빡이를 켰나요?",
        correctMessage = "정확해요! 기장님이 깜빡이를 켰어요.",
        wrongMessage = "앗, 기장님이 켰어요. 다음엔 더 잘 볼 수 있어요!",
        validMs = 30_000L,
    )

    val WIPER = MissionDefinition(
        code = MissionCode.WIPER,
        question = "비가 오나 봐요! 와이퍼가 켜졌나요?",
        correctMessage = "맞아요! 비 오는 날엔 천천히 가요.",
        wrongMessage = "맞아요! 비 오는 날엔 천천히 가요.",   // 문서: 오답도 같은 안내 멘트
        validMs = 30_000L,
    )

    val NEXT_TURN = MissionDefinition(
        code = MissionCode.NEXT_TURN,
        question = "다음엔 왼쪽일까요, 오른쪽일까요?",
        correctMessage = "맞혔어요! 곧 만나요.",
        wrongMessage = "곧 알 수 있어요, 같이 봐요!",
        validMs = null,
    )

    fun of(code: MissionCode): MissionDefinition = when (code) {
        MissionCode.BLINK -> BLINK
        MissionCode.WIPER -> WIPER
        MissionCode.NEXT_TURN -> NEXT_TURN
    }

    /** 방향지시등 이벤트 → 후보. 켜졌다 꺼진 직후 정답은 YES. */
    fun blinkCandidate(nowMs: Long) = MissionCandidate(
        code = MissionCode.BLINK,
        triggeredAtMs = nowMs,
        expectedAnswer = Answer.YES,
        expiresAtMs = nowMs + BLINK.validMs!!,
    )

    /** 와이퍼 켜짐 → 후보. 켜진 상태이므로 정답은 YES. */
    fun wiperCandidate(nowMs: Long) = MissionCandidate(
        code = MissionCode.WIPER,
        triggeredAtMs = nowMs,
        expectedAnswer = Answer.YES,
        expiresAtMs = nowMs + WIPER.validMs!!,
    )

    /** 내비 다음 회전이 좌/우일 때 후보. 정답은 내비 방향. */
    fun nextTurnCandidate(nowMs: Long, dir: TurnDir): MissionCandidate? {
        val expected = when (dir) {
            TurnDir.LEFT -> Answer.LEFT
            TurnDir.RIGHT -> Answer.RIGHT
            TurnDir.NONE -> return null
        }
        return MissionCandidate(
            code = MissionCode.NEXT_TURN,
            triggeredAtMs = nowMs,
            expectedAnswer = expected,
            expiresAtMs = null,
        )
    }

    /** 응답 정답 여부. 무응답(NONE)은 오답 취급이 아니라 호출 쪽에서 재질문 처리한다. */
    fun isCorrect(candidate: MissionCandidate, given: Answer): Boolean =
        given == candidate.expectedAnswer
}
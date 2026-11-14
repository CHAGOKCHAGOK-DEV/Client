package com.chagok.chagok.domain.mission

import com.chagok.chagok.model.MissionCandidate
import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.model.NaviInfo
import com.chagok.chagok.model.TurnDir
import com.chagok.chagok.sdk.speech.Answer
import com.chagok.chagok.sdk.speech.AnswerKind

/** 미션 한 종류의 대본과 판정 규칙. 질문은 화면에 띄우지 않고 소리로만 전한다. */
data class MissionDefinition(
    val code: MissionCode,
    val question: String,
    val correctMessage: String,
    val wrongMessage: String,
    val validMs: Long?,            // 시간 기준 유효 시간. NEXT_TURN·NEAR_DEST는 null (내비 상태로 폐기)
    val answerKind: AnswerKind = AnswerKind.YES_NO,
)

object MissionDefinitions {

    const val BLINK_LOOKBACK_MS = 30_000L   // 직전 30초 안에 깜빡이 기록이 있으면 정답 YES

    val BLINK = MissionDefinition(
        code = MissionCode.BLINK,
        question = "부기장님! 기장님이 깜빡이를 켰나요?",
        correctMessage = "정확해요! 기장님이 깜빡이를 켰어요.",
        wrongMessage = "앗, 기장님이 켰어요. 다음엔 더 잘 들어봐요!",
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
        answerKind = AnswerKind.LEFT_RIGHT,
    )

    const val NEAR_DEST_ETA_SEC = 300       // ETA 5분 이하면 시작
    const val NEAR_DEST_MIN_DIST_M = 1_000  // 남은 거리 1km 이하면 시작 안 함, 대기 중이면 폐기

    val NEAR_DEST = MissionDefinition(
        code = MissionCode.NEAR_DEST,
        question = "곧 도착이에요! 도착하면 기장님께 뭐라고 말할까요?",
        correctMessage = "멋진 말이에요! 기장님이 기뻐할 거예요.",
        wrongMessage = "괜찮아요, 도착하면 같이 말해 봐요!",   // 🟡 무응답 멘트는 설계서에 없어 임시 문구
        validMs = null,
        answerKind = AnswerKind.FREE,
    )

    fun of(code: MissionCode): MissionDefinition = when (code) {
        MissionCode.BLINK -> BLINK
        MissionCode.WIPER -> WIPER
        MissionCode.NEXT_TURN -> NEXT_TURN
        MissionCode.NEAR_DEST -> NEAR_DEST
        // 🟡 대본·판정은 이슈 7·8. 아직 엔진이 이 미션들을 만들지 않으므로 여기 오면 버그
        else -> error("미션 정의가 아직 없어요: $code")
    }

    /** NEAR_DEST 시작 조건. ETA 0은 경로 없음으로 본다 */
    fun isNearDestination(info: NaviInfo): Boolean {
        val eta = info.etaSec ?: return false
        return eta in 1..NEAR_DEST_ETA_SEC && !isTooCloseToDestination(info)
    }

    /** 남은 거리를 모르면 false (ETA만으로 판단) */
    fun isTooCloseToDestination(info: NaviInfo): Boolean =
        info.remainDistM?.let { it <= NEAR_DEST_MIN_DIST_M } ?: false

    /** 곧 도착 후보. 아무 말이나 하면 정답 */
    fun nearDestCandidate(nowMs: Long) = MissionCandidate(
        code = MissionCode.NEAR_DEST,
        triggeredAtMs = nowMs,
        expectedAnswer = Answer.ANY,
        expiresAtMs = null,
    )

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
    fun isCorrect(candidate: MissionCandidate, given: Answer): Boolean = when (candidate.expectedAnswer) {
        Answer.ANY -> given != Answer.NONE
        else -> given == candidate.expectedAnswer
    }
}
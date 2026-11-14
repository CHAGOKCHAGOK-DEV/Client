package com.chagok.chagok.ui.state

import com.chagok.chagok.model.MissionCode

/**
 * 결과 문구에 들어가는 숫자 (엔진이 미션을 판정할 때 채운다. 이슈 7·8).
 * 비어 있으면 숫자 없는 문구로 대신한다.
 */
data class MissionFacts(
    val speedKmh: Int? = null,        // F-06 지금 속도
    val speedDiffKmh: Int? = null,    // F-11 아까보다 + 빨라짐 / - 느려짐
    val rightBlinks: Int? = null,     // F-09 오른쪽 깜빡이 횟수
    val leftBlinks: Int? = null,      // F-09 왼쪽 깜빡이 횟수
    val waitedSec: Int? = null,       // F-10 기다린 시간
    val waitWasShort: Boolean? = null,// F-10 정답이 "금방"이었는지
)

/**
 * S-06 미션 화면 문구 (피그마 「s06 완료완료」 프론트 대본 13개).
 * 화면에는 질문·정답을 쓰지 않고 지금 상태와 결과 한 줄만 쓴다.
 * 🟡 연령대별로 대본이 갈리면(이슈 6) 듣는 중 부제도 연령대별로 바뀐다. 지금은 세 연령 대답을 함께 적는다.
 */
object DrivingTexts {

    /** b 말하는 중. 미션마다 다른 게 있으면 여기 (없으면 공통 문구) */
    fun speaking(code: MissionCode): Pair<String, String>? = when (code) {
        MissionCode.TODAY_REPORT -> "도착! 오늘의 리포트" to "오늘 한 미션을 알려줄게요"
        else -> null
    }

    /** c 듣는 중 부제: 어떻게 대답하면 되는지. F-13은 듣는 중이 없다 */
    fun listenHint(code: MissionCode): String = when (code) {
        MissionCode.BLINK -> "예 / 아니오 · 왼쪽 / 오른쪽으로 대답해 주세요"
        MissionCode.WIPER -> "예 / 아니오 · 와이퍼 / 라이트로 대답해요"
        MissionCode.NEXT_TURN -> "왼쪽 / 오른쪽으로 대답해 주세요"
        MissionCode.NEAR_DEST -> "아무 말이나 괜찮아요"
        MissionCode.BLINK_LEARN -> "아무 말이나 · 뒤차 / 하늘 · 돌기 전 / 다음"
        MissionCode.RAIN_STOP -> "예 · 아무 말 · 그쳤어 / 많이 와"
        MissionCode.SPEED_GUESS -> "빨리 / 천천히 · 빨라 / 느려로 대답해요"
        MissionCode.SIGNAL_WAIT -> "멈춰 / 가 · 초록 / 빨강으로 대답해요"
        MissionCode.DEPART -> "아무 말이나 \"네!\" 하면 돼요"
        MissionCode.BLINK_MEMORY -> "왼쪽 / 오른쪽으로 대답해 주세요"
        MissionCode.WAIT_BET -> "금방 / 오래 · 안에 / 오래로 대답해요"
        MissionCode.SPEED_CHANGE -> "빨라졌어 / 느려졌어로 대답해요"
        MissionCode.TODAY_REPORT -> ""
    }

    /** d 결과(정답) 제목·부제 */
    fun correct(code: MissionCode, facts: MissionFacts = MissionFacts()): Pair<String, String> = when (code) {
        MissionCode.BLINK -> "정확해요!" to "기장님이 깜빡이를 켰어요."
        MissionCode.WIPER -> "맞아요!" to "비 오는 날엔 천천히 가요."
        MissionCode.NEXT_TURN -> "맞혔어요!" to "곧 만나요."
        MissionCode.NEAR_DEST -> "멋진 말이에요!" to "기장님이 기뻐할 거예요."
        MissionCode.BLINK_LEARN -> "맞아요!" to BLINK_LEARN_SUB
        MissionCode.RAIN_STOP -> "맞아요!" to RAIN_STOP_SUB
        MissionCode.SPEED_GUESS -> "맞아요!" to speedSub(facts)
        MissionCode.SIGNAL_WAIT -> "맞아요!" to SIGNAL_SUB
        MissionCode.DEPART -> "부기장 출동!" to "좋아요, 출발!"
        MissionCode.BLINK_MEMORY -> blinkMemoryTitle(facts) to blinkMemorySub(facts)
        MissionCode.WAIT_BET -> "출발!" to waitSub(facts)
        MissionCode.SPEED_CHANGE -> speedChangeTitle(facts) to speedChangeSub(facts)
        MissionCode.TODAY_REPORT -> "수고했어요!" to "기장님, 부기장에게 고맙다고 말해 주세요!"
    }

    /**
     * 틀림(오답·무응답) 제목·부제. "틀렸어요"는 쓰지 않는다.
     * F-08은 아무 대답이나 정답이라 틀림 화면이 없어 결과 화면과 같게 둔다.
     */
    fun wrong(code: MissionCode, facts: MissionFacts = MissionFacts()): Pair<String, String> = when (code) {
        MissionCode.BLINK -> "앗, 기장님이 켰어요" to "다음엔 더 잘 들어봐요!"
        MissionCode.WIPER -> "괜찮아요!" to "비 오는 날엔 천천히 가요."
        MissionCode.NEXT_TURN -> "괜찮아요!" to "곧 알 수 있어요, 같이 봐요!"
        MissionCode.NEAR_DEST -> "괜찮아요!" to "도착하면 같이 말해 봐요!"
        MissionCode.BLINK_LEARN -> "괜찮아요!" to BLINK_LEARN_SUB
        MissionCode.RAIN_STOP -> "괜찮아요!" to RAIN_STOP_SUB
        MissionCode.SPEED_GUESS -> "괜찮아요!" to speedSub(facts)
        MissionCode.SIGNAL_WAIT -> "괜찮아요!" to SIGNAL_SUB
        MissionCode.DEPART -> correct(code, facts)
        MissionCode.BLINK_MEMORY -> "괜찮아요!" to blinkMemorySub(facts)
        MissionCode.WAIT_BET -> "괜찮아요!" to waitSub(facts)
        MissionCode.SPEED_CHANGE -> "괜찮아요!" to speedChangeSub(facts)
        MissionCode.TODAY_REPORT -> correct(code, facts)
    }

    private const val BLINK_LEARN_SUB = "깜빡이는 돌기 전에 켜서 뒤차한테 알려줘요!"
    private const val RAIN_STOP_SUB = "비가 그치면 와이퍼도 쉬어요!"
    private const val SIGNAL_SUB = "빨간불에는 멈추고, 초록불에 출발해요!"

    // 🟡 아래 숫자 없는 대체 문구는 피그마에 없어 임시로 쓴 것
    private fun speedSub(f: MissionFacts) =
        f.speedKmh?.let { "지금은 시속 약 ${it}km예요!" } ?: "지금 속도를 같이 봐요!"

    private fun blinkMemoryRight(f: MissionFacts): Boolean? {
        val r = f.rightBlinks ?: return null
        val l = f.leftBlinks ?: return null
        return r >= l
    }

    private fun blinkMemoryTitle(f: MissionFacts) = when (blinkMemoryRight(f)) {
        true -> "정답은 오른쪽!"
        false -> "정답은 왼쪽!"
        null -> "맞아요!"
    }

    private fun blinkMemorySub(f: MissionFacts): String {
        val r = f.rightBlinks
        val l = f.leftBlinks
        if (r == null || l == null) return "깜빡이를 잘 기억했어요."
        return if (r >= l) "오른쪽 깜빡이를 ${r}번, 왼쪽은 ${l}번 켰어요."
        else "왼쪽 깜빡이를 ${l}번, 오른쪽은 ${r}번 켰어요."
    }

    private fun waitSub(f: MissionFacts): String {
        val sec = f.waitedSec ?: return "신호가 바뀌었어요."
        val answer = when (f.waitWasShort) {
            true -> " 금방이 정답!"
            false -> " 오래가 정답!"
            null -> ""
        }
        return "${sec}초 기다렸어요.$answer"
    }

    private fun speedChangeTitle(f: MissionFacts) = when {
        f.speedDiffKmh == null -> "맞아요!"
        f.speedDiffKmh >= 0 -> "빨라졌어요!"
        else -> "느려졌어요!"
    }

    private fun speedChangeSub(f: MissionFacts): String {
        val d = f.speedDiffKmh ?: return "아까랑 속도를 비교해 봤어요."
        return if (d >= 0) "아까보다 시속 ${d}km 더 빨라요." else "아까보다 시속 ${-d}km 더 느려요."
    }
}

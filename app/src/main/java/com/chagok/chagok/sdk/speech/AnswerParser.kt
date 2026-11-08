package com.chagok.chagok.sdk.speech

/**
 * F-10 아이 대답 판정 (STT 원문 → 정규화된 Answer).
 *
 * - 원문은 여기서만 잠깐 보고 버린다. 저장·로그 금지 (아동 개인정보).
 * - 자유 문장을 이해하지 않는다. 정해진 단어가 들어 있는지만 본다.
 * - 판단이 애매하면 NONE을 돌려준다. 엔진이 1회 재질문한다.
 *
 * 에뮬레이터 STT는 말 앞부분이 잘리는 일이 있어서("켰어" → "어"), 짧은 단어도 받아 준다.
 */
object AnswerParser {

    // 부정이 먼저다. "안 켰어"에는 "켰어"가 들어 있기 때문
    private val NO_WORDS = listOf(
        "아니", "아냐", "아닌", "아뇨", "아니요", "노노",
        "안켰", "안켜", "안했", "안들렸", "안보였", "안움직",
        "못봤", "못들었", "못들", "없어", "꺼졌", "꺼져",
        "몰라", "모르겠",   // 🟡 "몰라"는 대답으로 본다 (재질문하면 아이가 지친다)
    )

    private val YES_WORDS = listOf(
        "응", "웅", "엉", "어어", "네", "넹", "예", "그래", "그렇", "맞아", "맞다", "맞어",
        "켰", "켜졌", "켜져", "켜있", "들렸", "들려", "봤어", "보였", "움직", "있어", "했어", "오케이", "좋아",
    )

    // 2~3글자 대답이 통째로 이 단어이면 예 (앞부분이 잘린 경우: "켰어"→"어")
    private val YES_EXACT = setOf("어", "응", "네", "예", "웅", "엉", "yes", "ok")
    private val NO_EXACT = setOf("노", "no", "안")

    private val LEFT_WORDS = listOf("왼", "좌회전", "좌측", "좌로", "왼편", "레프트", "left")
    private val RIGHT_WORDS = listOf("오른", "우회전", "우측", "우로", "오른편", "라이트", "right")

    fun parse(text: String?, kind: AnswerKind): Answer {
        val t = normalize(text)
        if (t.isEmpty()) return Answer.NONE
        return when (kind) {
            AnswerKind.YES_NO -> yesNo(t)
            AnswerKind.LEFT_RIGHT -> leftRight(t)
            AnswerKind.FREE -> Answer.ANY
        }
    }

    private fun yesNo(t: String): Answer {
        if (t in NO_EXACT) return Answer.NO
        if (t in YES_EXACT) return Answer.YES
        val no = NO_WORDS.any { it in t }
        val yes = YES_WORDS.any { it in t }
        return when {
            no -> Answer.NO          // "아니 켰어"처럼 섞여도 부정을 우선 (틀려도 칭찬 멘트라 부담 적음)
            yes -> Answer.YES
            else -> Answer.NONE
        }
    }

    private fun leftRight(t: String): Answer {
        val left = LEFT_WORDS.any { it in t }
        val right = RIGHT_WORDS.any { it in t }
        return when {
            left && !right -> Answer.LEFT
            right && !left -> Answer.RIGHT
            else -> Answer.NONE      // 둘 다 말했거나 둘 다 없음 → 재질문
        }
    }

    /** 공백·문장부호 제거, 소문자 */
    internal fun normalize(text: String?): String =
        text.orEmpty()
            .lowercase()
            .filter { it.isLetterOrDigit() }
}

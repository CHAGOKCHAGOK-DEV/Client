package com.chagok.chagok.sdk.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerParserTest {

    private fun yn(text: String?) = AnswerParser.parse(text, AnswerKind.YES_NO)
    private fun lr(text: String?) = AnswerParser.parse(text, AnswerKind.LEFT_RIGHT)

    @Test fun `예 계열`() {
        listOf("응", "응!", "네", "켰어", "켰어요", "들렸어", "맞아요", "어 켰다", "웅웅", "그렇지", "와이퍼 움직여").forEach {
            assertEquals(it, Answer.YES, yn(it))
        }
    }

    @Test fun `아니오 계열 - 부정이 긍정 단어보다 우선`() {
        listOf("아니", "아니요", "안 켰어", "안켰어", "못 봤어", "안 들렸어", "몰라", "꺼졌어", "아니 켰어").forEach {
            assertEquals(it, Answer.NO, yn(it))
        }
    }

    @Test fun `앞부분이 잘린 짧은 대답`() {
        assertEquals(Answer.YES, yn("어"))      // "켰어" → "어"
        assertEquals(Answer.NO, yn("안"))
    }

    @Test fun `알 수 없는 말과 빈 값은 NONE`() {
        assertEquals(Answer.NONE, yn("배고파"))
        assertEquals(Answer.NONE, yn(""))
        assertEquals(Answer.NONE, yn("   "))
        assertEquals(Answer.NONE, yn(null))
    }

    @Test fun `왼쪽 오른쪽`() {
        assertEquals(Answer.LEFT, lr("왼쪽"))
        assertEquals(Answer.LEFT, lr("왼쪽이야!"))
        assertEquals(Answer.LEFT, lr("좌회전"))
        assertEquals(Answer.RIGHT, lr("오른쪽"))
        assertEquals(Answer.RIGHT, lr("오른 쪽 같아"))
        assertEquals(Answer.RIGHT, lr("우회전"))
    }

    @Test fun `둘 다 말하거나 방향이 없으면 NONE`() {
        assertEquals(Answer.NONE, lr("왼쪽 아니 오른쪽"))
        assertEquals(Answer.NONE, lr("몰라"))
        assertEquals(Answer.NONE, lr("우와"))       // "우"만으로 오른쪽으로 보지 않는다
    }

    @Test fun `자유 대답은 무엇이든 ANY, 빈 값만 NONE`() {
        assertEquals(Answer.ANY, AnswerParser.parse("기장님 고마워요", AnswerKind.FREE))
        assertEquals(Answer.NONE, AnswerParser.parse(" ", AnswerKind.FREE))
    }

    @Test fun `두 단어 판정은 앞 단어 OPTION_A, 뒤 단어 OPTION_B`() {
        fun p(t: String, k: AnswerKind) = AnswerParser.parse(t, k)
        assertEquals(Answer.OPTION_A, p("와이퍼!", AnswerKind.WIPER_LIGHT))
        assertEquals(Answer.OPTION_B, p("라이트일걸", AnswerKind.WIPER_LIGHT))
        assertEquals(Answer.OPTION_A, p("뒤차", AnswerKind.BACKCAR_SKY))
        assertEquals(Answer.OPTION_B, p("하늘이요", AnswerKind.BACKCAR_SKY))
        assertEquals(Answer.OPTION_A, p("돌기 전", AnswerKind.BEFORE_AFTER))
        assertEquals(Answer.OPTION_B, p("돈 다음", AnswerKind.BEFORE_AFTER))
        assertEquals(Answer.OPTION_A, p("비 그쳤어", AnswerKind.STOPPED_POURING))
        assertEquals(Answer.OPTION_A, p("이제 안 와", AnswerKind.STOPPED_POURING))   // "와"가 섞여도
        assertEquals(Answer.OPTION_B, p("많이 와", AnswerKind.STOPPED_POURING))
        assertEquals(Answer.OPTION_A, p("빨라", AnswerKind.FAST_SLOW))
        assertEquals(Answer.OPTION_B, p("천천히", AnswerKind.FAST_SLOW))
        assertEquals(Answer.OPTION_B, p("느려졌어", AnswerKind.FAST_SLOW))
        assertEquals(Answer.OPTION_A, p("멈춰", AnswerKind.STOP_GO))
        assertEquals(Answer.OPTION_B, p("가", AnswerKind.STOP_GO))
        assertEquals(Answer.OPTION_A, p("초록불", AnswerKind.GREEN_RED))
        assertEquals(Answer.OPTION_B, p("빨간불", AnswerKind.GREEN_RED))
        assertEquals(Answer.OPTION_A, p("금방", AnswerKind.SOON_LONG))
        assertEquals(Answer.OPTION_A, p("30초 안에", AnswerKind.SOON_LONG))
        assertEquals(Answer.OPTION_B, p("오래 기다릴 거야", AnswerKind.SOON_LONG))
    }

    @Test fun `두 단어를 다 말하거나 아무것도 없으면 NONE`() {
        assertEquals(Answer.NONE, AnswerParser.parse("와이퍼랑 라이트", AnswerKind.WIPER_LIGHT))
        assertEquals(Answer.NONE, AnswerParser.parse("몰라", AnswerKind.GREEN_RED))
        assertEquals(Answer.NONE, AnswerParser.parse("갈까", AnswerKind.STOP_GO))    // 짧은 "가"는 그 말만 했을 때
    }
}

package com.chagok.chagok.sdk.speech

import kotlinx.coroutines.delay

/** 로그만 찍고, 미리 정한 답을 돌려주는 가짜. */
class FakeSpeechPort(
    var nextAnswer: Answer = Answer.YES,
    private val speakMs: Long = 500,
) : SpeechPort {
    val spoken = mutableListOf<String>()   // 테스트에서 확인용

    override suspend fun speak(text: String) {
        println("[TTS] $text")
        spoken += text
        delay(speakMs)
    }

    override suspend fun listen(timeoutMs: Long, kind: AnswerKind): Answer {
        delay(300)
        // 자유 응답은 무엇이든 말했으면 ANY
        val answer = if (kind == AnswerKind.FREE && nextAnswer != Answer.NONE) Answer.ANY else nextAnswer
        println("[STT] -> $answer")
        return answer
    }
}
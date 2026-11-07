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

    override suspend fun listen(timeoutMs: Long): Answer {
        delay(300)
        println("[STT] -> $nextAnswer")
        return nextAnswer
    }
}
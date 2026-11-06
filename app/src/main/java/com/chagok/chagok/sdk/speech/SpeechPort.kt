package com.chagok.chagok.sdk.speech

/** 정규화된 아이의 답. 원문 텍스트는 저장하지 않는다. */
enum class Answer { YES, NO, LEFT, RIGHT, ANY, NONE }

interface SpeechPort {
    /** 문장을 소리로 읽고, 재생이 끝나면 돌아온다. */
    suspend fun speak(text: String)

    /** 듣고 정규화된 답을 돌려준다. 무응답/인식 실패면 Answer.NONE. */
    suspend fun listen(timeoutMs: Long): Answer
}
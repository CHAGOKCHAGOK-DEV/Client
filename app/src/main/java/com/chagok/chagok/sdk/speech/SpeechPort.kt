package com.chagok.chagok.sdk.speech

/**
 * 정규화된 아이의 답. 원문 텍스트는 저장하지 않는다.
 * OPTION_A / OPTION_B: 두 단어 중 하나 (AnswerKind의 앞 단어 / 뒤 단어)
 */
enum class Answer { YES, NO, LEFT, RIGHT, OPTION_A, OPTION_B, ANY, NONE }

/** 어떤 대답을 기다리는지. STT 구현이 단어 목록을 고를 때 쓴다 */
enum class AnswerKind {
    YES_NO,      // 응/아니 계열 -> YES/NO
    LEFT_RIGHT,  // 왼쪽/오른쪽 -> LEFT/RIGHT
    FREE,        // 아무 말 -> 무엇이든 인식되면 ANY

    // 두 단어 판정 (앞 단어 -> OPTION_A, 뒤 단어 -> OPTION_B). 단어 목록은 AnswerParser
    BACKCAR_SKY,      // F-02 7~8세 뒤차 / 하늘
    BEFORE_AFTER,     // F-02 9세+ 돌기 전 / 돈 다음
    WIPER_LIGHT,      // F-03 9세+ 와이퍼 / 라이트
    STOPPED_POURING,  // F-04 9세+ 그쳤어 / 많이 와
    FAST_SLOW,        // F-06 빨리 / 천천히, 빨라 / 느려 · F-11 빨라졌어 / 느려졌어
    STOP_GO,          // F-07 5~6세 멈춰 / 가
    GREEN_RED,        // F-07 9세+ 초록 / 빨강
    SOON_LONG,        // F-10 금방 / 오래, 9세+ 안에 / 오래
}

interface SpeechPort {
    /** 문장을 소리로 읽고, 재생이 끝나면 돌아온다. */
    suspend fun speak(text: String)

    /** 듣고 정규화된 답을 돌려준다. 무응답/인식 실패면 Answer.NONE. */
    suspend fun listen(timeoutMs: Long, kind: AnswerKind = AnswerKind.YES_NO): Answer

    /** 엔진 해제 (앱 종료). 가짜 구현은 할 일 없음 */
    fun release() {}
}
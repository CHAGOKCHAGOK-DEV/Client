package com.chagok.chagok.ui.state

/** 화면 목록 (화면설계서 ID) */
enum class Screen {
    STARTUP,     // S-01 시작·초기화
    PERMISSION,  // S-02 권한 안내
    CONSENT,     // S-03 부모 동의 (다른 담당)
    PROFILE,     // S-04 부기장 설정 (다른 담당)
    MAIN,        // S-05 메인 (정차)
    DRIVING,     // S-06 주행 중
    SUMMARY,     // S-07 주행 종료 요약
}

/** 화면이 한 번만 처리하면 되는 일 (토스트, 이동, 종료) */
sealed interface UiEvent {
    data class Toast(val text: String) : UiEvent          // C-03, 2.2초
    data class Navigate(val to: Screen) : UiEvent
    data object ExitApp : UiEvent
}

/**
 * 화면 문구 모음. 🟡 설계서 문구 그대로, 없는 건 임시.
 * 다국어가 필요해지면 strings.xml로 옮기면 된다.
 */
object ScreenTexts {
    // S-01
    const val STARTUP_TITLE = "부기장 출근 중!"
    const val STARTUP_CONNECT = "차와 연결하고 있어요…"
    const val STARTUP_PERMISSION = "필요한 권한을 확인하고 있어요…"
    const val STARTUP_SETTINGS = "설정을 불러오고 있어요…"
    const val STARTUP_ERROR = "차와 연결이 안 돼요"

    // S-02
    const val PERM_TITLE = "부기장이 일하려면 필요해요"
    const val PERM_VEHICLE = "차량 신호 읽기"
    const val PERM_MIC = "마이크 사용"
    const val PERM_VOICE = "음성 인식 기반 안내"
    const val PERM_BANNER = "권한이 없으면 시작할 수 없어요"
    const val PERM_ALL_DONE = "모두 확인됐어요!"
    const val PERM_LATER_TOAST = "권한이 있어야 시작할 수 있어요"

    // S-03 (법무 검토 전 임시 문구)
    const val CONSENT_TITLE = "보호자님, 확인이 필요해요"
    const val CONSENT_SUB = "아이 정보 이용에 동의해 주세요"
    const val CONSENT_ITEM_NICKNAME = "[필수] 서비스 이용약관 동의"
    const val CONSENT_ITEM_VOICE = "[필수] 개인정보 처리방침 확인"
    const val CONSENT_ITEM_FAMILY = "[필수] 아동 개인정보 수집·이용 동의"
    const val CONSENT_ALL = "전체 동의"

    // S-04
    const val PROFILE_BUBBLE_DEFAULT = "부기장, 반가워요!"
    const val PROFILE_ERROR_CHARS = "한글, 영문, 숫자만 쓸 수 있어요"
    fun profileGreeting(nickname: String) = "$nickname 부기장, 반가워요!"

    // C-04
    const val REFUSAL_TITLE = "동의가 필요해요"
    const val REFUSAL_OK = "확인"

    // S-05
    const val MAIN_DEFAULT = "자리에 앉아서 귀로 임무를!"          // 피그마 S05_1 말풍선 (부기장 수칙)
    const val MAIN_READY = "차가 출발하면 시작돼요"
    const val MAIN_DONE_TODAY = "오늘도 수고했어요!"
    const val MAIN_READY_TTS = "준비 완료! 출발해 볼까요?"
    const val CHIP_STOPPED = "정차 중 · 차와 연결됨"
    const val CHIP_PARKED = "주차 중 · 차와 연결됨"
    const val CHIP_DISCONNECTED = "차량 연결 끊김"

    // S-06 (피그마 S06 a~g, S06F 미션 화면)
    const val DRIVE_WAITING = "주행 중이에요"                          // a
    const val DRIVE_WAITING_SUB = "부기장이 다음 미션을 기다려요"
    const val DRIVE_SPEAKING = "부기장이 말하고 있어요"                // b
    const val DRIVE_SPEAKING_SUB = "질문은 소리로만 들려요"
    const val DRIVE_LISTENING = "듣고 있어요"                          // c (부제는 미션별)
    const val DRIVE_MODE_OFF = "부기장이 쉬고 있어요"                  // e
    const val DRIVE_MODE_OFF_SUB = "정차한 뒤 출발 버튼을 눌러요"
    const val DRIVE_STOPPED = "잠깐 멈췄어요"                          // f
    const val DRIVE_STOPPED_SUB = "미션은 계속할 수 있어요"
    const val DRIVE_STOP_DONE = "정차 완료"                           // S06-04
    const val DRIVE_SIGNAL = "차량 신호를 확인하고 있어요"             // g
    const val DRIVE_SIGNAL_SUB = "미션은 잠시 쉬어요"
    const val DRIVE_TOUCH_TTS = "정차 후에 눌러요"
    const val DRIVE_MODE_ON_CHIP = "부기장 모드 켜짐"
    const val DRIVE_MODE_OFF_CHIP = "부기장 모드 꺼짐"

    // S-07 주행 종료 요약 (피그마 S07 a~f). 고정 문구는 summary_strings.xml
    const val SUMMARY_BUBBLE = "같이 달려서 즐거웠어요!"              // a·e·f
    const val SUMMARY_BUBBLE_ALL = "모두 해냈어요!"                     // b
    const val SUMMARY_BUBBLE_NONE = "다음엔 더 잘할 수 있어요!"         // c
    const val SUMMARY_BUBBLE_EMPTY = "다음 주행에서 만나요!"            // d
    const val SUMMARY_LINE_EMPTY = "이번 주행에서는 미션이 없었어요"
    const val SUMMARY_SUCCESS = "성공"
    const val SUMMARY_NEXT_TIME = "다음에!"
    const val SUMMARY_FOOTER = "배지·경험치는 곧 만나요 (2차)"
    const val SUMMARY_FOOTER_RECENT = "최근 5개만 보여요 · 배지·경험치는 곧 만나요 (2차)"

    // C-02
    const val LOCK_TITLE = "주행 중이에요"
    const val LOCK_SUB = "차를 멈추면 계속할 수 있어요"
}

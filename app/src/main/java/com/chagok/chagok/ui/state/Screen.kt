package com.chagok.chagok.ui.state

/** 화면 목록 (화면설계서 ID) */
enum class Screen {
    STARTUP,     // S-01 시작·초기화
    PERMISSION,  // S-02 권한 안내
    CONSENT,     // S-03 부모 동의 (다른 담당)
    PROFILE,     // S-04 부기장 설정 (다른 담당)
    MAIN,        // S-05 메인 (정차)
    DRIVING,     // S-06 주행 중
    SUMMARY,     // S-07 정차 요약 (다른 담당)
    LIMITED,     // S-08 안전 제한 모드 (다른 담당)
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
    const val CONSENT_NOTICE = "정차 상태에서 확인해 주세요"
    const val CONSENT_ITEM_NICKNAME = "별명을 이용해요"
    const val CONSENT_ITEM_VOICE = "목소리는 인식 후 바로 지워요"
    const val CONSENT_ITEM_FAMILY = "주행 기록은 가족 안에서만 사용해요"
    const val CONSENT_ALL = "전체 동의"
    const val CONSENT_AGREE = "동의하고 계속"
    const val CONSENT_REFUSE = "동의하지 않아요"

    // C-04
    const val REFUSAL_TITLE = "동의가 필요해요"
    const val REFUSAL_OK = "확인"

    // S-05
    const val MAIN_DEFAULT = "오늘도 기장님을 잘 부탁해요!"
    const val MAIN_READY = "차가 출발하면 시작돼요"
    const val MAIN_DONE_TODAY = "오늘도 수고했어요!"
    const val MAIN_READY_TTS = "준비 완료! 출발해 볼까요?"
    const val CHIP_STOPPED = "정차 중 · 연결됨"
    const val CHIP_PARKED = "주차 중 · 연결됨"
    const val CHIP_DISCONNECTED = "차량 연결 끊김"
    const val BADGE_TOAST = "배지는 2차에서 만나요"

    // S-06
    const val DRIVE_WAITING = "주행 중이에요"
    const val DRIVE_SPEAKING = "부기장이 말하는 중"
    const val DRIVE_LISTENING = "듣고 있어요"
    const val DRIVE_RESULT = "좋았어요!"
    const val DRIVE_MODE_OFF_SUB = "부기장 모드 꺼짐 · 정차한 뒤 켜요"
    const val DRIVE_TOUCH_TTS = "정차 후에 눌러요"

    // S-08
    const val LIMITED_TITLE = "차와 연결이 안 돼요"
    const val LIMITED_SUB = "지금은 부기장 미션을 쉬어요"
    const val LIMITED_SUB2 = "차량 신호를 받으면 다시 시작해요"
    const val LIMITED_RETRY = "다시 연결"
    const val LIMITED_CONNECTING = "연결하고 있어요…"
    const val LIMITED_PROFILE = "프로필 보기"
    const val LIMITED_RECOVERED = "연결이 돌아왔어요"

    // C-02
    const val LOCK_TITLE = "주행 중이에요"
    const val LOCK_SUB = "차를 멈추면 계속할 수 있어요"
}

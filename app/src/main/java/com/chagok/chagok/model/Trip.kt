package com.chagok.chagok.model

/** 주행 종료 사유 (상태 규칙 "주행 종료") */
enum class TripEndReason {
    USER_STOP,       // ① S-06 [정차 완료] 버튼
    PARKED,          // ② 주차 브레이크 ON
    IDLE_TIMEOUT,    // ③ 정차가 오래 지속 (🟡 5분)
    APP_EXIT,        // ④ 앱 종료
}

/** 한 번의 주행. 미션 결과는 tripId로 묶는다. 업로드(POST /api/trips)는 후속. */
data class Trip(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val endReason: TripEndReason? = null,
) {
    val isEnded: Boolean get() = endedAtMs != null
}

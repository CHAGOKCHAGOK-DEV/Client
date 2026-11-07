package com.chagok.chagok.model

/** 주행 종료 사유 (상태 규칙 "주행 종료") */
enum class TripEndReason {
    PARKING_BRAKE,   // ① 주차 브레이크 ON
    LONG_STOP,       // ② 정차가 오래 지속 (🟡 5분)
    APP_EXIT,        // ③ 앱 종료
}

/** 한 번의 주행. 미션 결과는 tripId로 묶는다. 업로드(POST /api/trips)는 후속. */
data class Trip(
    val id: String,
    val startedAtMs: Long,
    val intervalMs: Long,            // 시작 시점 미션 간격 스냅샷 (프로필 강도)
    val endedAtMs: Long? = null,
    val endReason: TripEndReason? = null,
) {
    val isEnded: Boolean get() = endedAtMs != null
}

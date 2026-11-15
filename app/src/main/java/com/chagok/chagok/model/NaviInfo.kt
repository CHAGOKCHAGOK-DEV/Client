package com.chagok.chagok.model

data class NaviInfo(
    val nextTurnDir: TurnDir = TurnDir.NONE,
    val distToTurnM: Int? = null,   // 다음 회전까지 남은 거리(m)
    val remainDistM: Int? = null,   // 목적지까지 남은 거리(m)
    val etaSec: Int? = null,        // 도착 예상 시간(초)
)
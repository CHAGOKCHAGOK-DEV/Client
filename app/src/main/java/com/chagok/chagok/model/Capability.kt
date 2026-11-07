package com.chagok.chagok.model

/** 이 차량에서 지원하는 신호 (S-05 "이 차에서는 꺼져요" 표시용) */
data class Capability(
    val speed: Boolean = false,
    val blink: Boolean = false,
    val wiper: Boolean = false,
    val navi: Boolean = false,
)
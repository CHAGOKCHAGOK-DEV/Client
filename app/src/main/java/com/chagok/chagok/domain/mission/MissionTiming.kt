package com.chagok.chagok.domain.mission

import com.chagok.chagok.model.DriveState
import com.chagok.chagok.model.MissionCandidate
import com.chagok.chagok.model.MissionCode

enum class TimingDecision { PASS, HOLD }

/** 판단에 필요한 현재 상황. 엔진이 매번 채워서 넘긴다. */
data class TimingContext(
    val nowMs: Long,
    val driveState: DriveState,
    val turnSignalOn: Boolean,        // 깜빡이 켜진 동안
    val distToTurnM: Int?,            // 다음 회전까지 거리
    val naviSpeaking: Boolean,        // 내비 음성 안내 재생 중
    val lastMissionEndMs: Long?,      // 직전 미션 종료 시각
    val intervalMs: Long,             // 미션 사이 최소 간격
)

object MissionTiming {
    const val TURN_HOLD_M = 300       // 🟡 다음 회전 300m 이내면 보류

    /** 미션 사이 최소 간격. 모든 사용자 3분 고정 (열린 이슈 #18) */
    const val INTERVAL_MS = 3 * 60_000L

    fun decide(c: MissionCandidate, ctx: TimingContext): TimingDecision {
        if (ctx.turnSignalOn) return TimingDecision.HOLD
        if (ctx.naviSpeaking) return TimingDecision.HOLD

        // NEXT_TURN은 300m 도달 시 "폐기"되므로 여기서 보류 규칙을 적용하지 않는다
        val dist = ctx.distToTurnM
        if (c.code != MissionCode.NEXT_TURN && dist != null && dist <= TURN_HOLD_M) {
            return TimingDecision.HOLD
        }

        val last = ctx.lastMissionEndMs
        if (last != null && ctx.nowMs - last < ctx.intervalMs) return TimingDecision.HOLD

        return TimingDecision.PASS
    }
}
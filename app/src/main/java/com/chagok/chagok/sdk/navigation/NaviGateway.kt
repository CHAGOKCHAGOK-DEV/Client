package com.chagok.chagok.sdk.navigation

import com.chagok.chagok.model.NaviInfo
import kotlinx.coroutines.flow.Flow

/** 내비 정보(다음 회전, 남은 거리)를 받는 창구. 구현: FakeNaviGateway, PleosNaviGateway */
interface NaviGateway {
    val naviInfo: Flow<NaviInfo>

    /** 내비 서비스 연결. 실패하면 false (NEXT_TURN 미션이 "이 차에서는 꺼져요"가 된다) */
    suspend fun connect(): Boolean = true

    fun release() {}
}

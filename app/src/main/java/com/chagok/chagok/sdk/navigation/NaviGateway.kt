package com.chagok.chagok.sdk.navigation

import com.chagok.chagok.model.NaviInfo
import kotlinx.coroutines.flow.Flow

/** 내비 정보(다음 회전, 남은 거리)를 받는 창구. 구현: FakeNaviGateway, 나중에 PleosNaviGateway */
interface NaviGateway {
    val naviInfo: Flow<NaviInfo>
}
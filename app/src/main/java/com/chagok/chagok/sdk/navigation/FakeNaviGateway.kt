package com.chagok.chagok.sdk.navigation

import com.chagok.chagok.model.NaviInfo
import com.chagok.chagok.model.TurnDir
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNaviGateway : NaviGateway {
    private val _info = MutableStateFlow(NaviInfo())
    override val naviInfo: Flow<NaviInfo> = _info

    // ---- 디버그용 입력 ----
    fun setNextTurn(dir: TurnDir, distM: Int?) {
        _info.value = _info.value.copy(nextTurnDir = dir, distToTurnM = distM)
    }
    fun setRemain(distM: Int?, etaSec: Int?) {
        _info.value = _info.value.copy(remainDistM = distM, etaSec = etaSec)
    }
}
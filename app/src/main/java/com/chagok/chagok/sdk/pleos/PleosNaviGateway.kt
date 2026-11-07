package com.chagok.chagok.sdk.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.chagok.chagok.model.NaviInfo
import com.chagok.chagok.model.TurnDir
import com.chagok.chagok.sdk.navigation.NaviGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * F-07 실제 내비 연결 (NaviHelper).
 *
 * - 다음 회전: onTBTInfo 목록에서 "실제로 꺾는 안내"의 첫 항목. 직진·터널 같은 안내는 건너뛴다.
 *   그 항목의 거리(m)는 회전 300m 보류 판단에, 방향은 NEXT_TURN 미션에 쓴다.
 *   좌/우가 아닌 안내(유턴, IC 진입, 회전교차로 등)는 방향 NONE으로 두어 미션은 안 만들고 보류에만 쓴다.
 * - 경로 취소·목적지 도착: 다음 회전 정보 초기화 (대기 중인 NEXT_TURN 미션이 폐기됨)
 * - 남은 거리·ETA(NEAR_DEST용)는 DrivingInfo.destination(DrivingState) 필드 확인 후 연결 예정
 *
 * 이벤트를 보내는 건 기본 내비 앱이라, 앱마다 어떤 이벤트가 오는지 다를 수 있다 (SDK 문서).
 */
class PleosNaviGateway(context: Context) : NaviGateway {

    private val helper = NaviHelper(context.applicationContext)
    private var initialized = false

    private val _info = MutableStateFlow(NaviInfo())
    override val naviInfo: StateFlow<NaviInfo> = _info.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            val next = tbtInfoList.firstOrNull { it.type !in NOT_A_TURN }
            _info.value = _info.value.copy(
                nextTurnDir = next?.type?.toTurnDir() ?: TurnDir.NONE,
                distToTurnM = next?.distance?.toInt(),
            )
        }

        override fun onRouteCancelled() = clearRoute()

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = clearRoute()

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error $errorCode")
        }
    }

    private fun clearRoute() { _info.value = NaviInfo() }

    override suspend fun connect(): Boolean {
        if (initialized) return true
        return try {
            helper.initialize()
            helper.addListener(listener)
            initialized = true
            true
        } catch (e: Exception) {   // SecurityException(권한) 포함
            Log.w(TAG, "navi connect failed", e)
            runCatching { helper.release() }
            false
        }
    }

    override fun release() {
        if (!initialized) return
        runCatching { helper.removeListener(listener) }
        runCatching { helper.release() }
        initialized = false
        clearRoute()
    }

    private companion object {
        const val TAG = "PleosNaviGateway"

        /** 꺾는 안내가 아닌 것: 다음 회전 찾을 때 건너뜀 */
        val NOT_A_TURN = setOf(
            TurnType.TURN_UNKNOWN, TurnType.TURN_STRAIGHT, TurnType.TURN_TUNNEL,
            TurnType.TURN_TOLL, TurnType.TURN_WAY,
        )
    }
}

/** 미션에 쓰는 좌/우만 뽑는다. 급좌회전·급우회전도 같은 방향으로 본다. */
internal fun TurnType.toTurnDir(): TurnDir = when (this) {
    TurnType.TURN_LEFT, TurnType.TURN_SHARP_L -> TurnDir.LEFT
    TurnType.TURN_RIGHT, TurnType.TURN_SHARP_R -> TurnDir.RIGHT
    else -> TurnDir.NONE
}

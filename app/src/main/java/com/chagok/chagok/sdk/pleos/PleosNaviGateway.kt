package com.chagok.chagok.sdk.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
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
 * - 남은 시간·거리: onDrivingInfo의 destination(DrivingState). NEAR_DEST(곧 도착) 판단에 쓴다
 *
 * 이벤트를 보내는 건 기본 내비 앱이라, 앱마다 어떤 이벤트가 오는지 다를 수 있다 (SDK 문서).
 */
class PleosNaviGateway(context: Context) : NaviGateway {

    private val helper = NaviHelper(context.applicationContext)
    private var initialized = false
    private val handler = Handler(Looper.getMainLooper())

    // onDrivingInfo는 움직일 때만 올 수 있어 남은 거리·시간을 주기적으로 직접 묻는다
    private val pollDestination = object : Runnable {
        override fun run() {
            runCatching { helper.getDestinationInfo() }
            handler.postDelayed(this, POLL_MS)
        }
    }

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

        /** TODO: distance가 남은 거리인지 에뮬레이터로 확인 */
        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            val dest = drivingInfo.destination
            Log.d(TAG, "drivingInfo dist=${dest.distance} dur=${dest.duration}")
            setRemain(dest.distance, dest.duration)
        }

        /** getDestinationInfo() 응답. TODO: remainTime 단위(초) 확인 */
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            Log.d(TAG, "destinationInfo remainDist=${destinationInfo.remainDistance} remainTime=${destinationInfo.remainTime}")
            setRemain(destinationInfo.remainDistance, destinationInfo.remainTime)
        }

        /** 안내 시작 시점의 전체 거리·시간 = 남은 거리·시간 */
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.d(TAG, "routeStarted dist=${routeStartInfo.distance} dur=${routeStartInfo.duration}")
            setRemain(routeStartInfo.distance, routeStartInfo.duration)
        }

        override fun onNavigationStatus(navigationStatus: NavigationStatus) {
            Log.d(TAG, "navigationStatus $navigationStatus")
        }

        override fun onRouteCancelled() = clearRoute()

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = clearRoute()

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error $errorCode")
        }
    }

    private fun clearRoute() { _info.value = NaviInfo() }

    /** 0 이하는 경로 없음 */
    private fun setRemain(distM: Int, sec: Int) {
        _info.value = if (distM <= 0 && sec <= 0) _info.value.copy(etaSec = null, remainDistM = null)
        else _info.value.copy(etaSec = sec, remainDistM = distM)
    }

    override suspend fun connect(): Boolean {
        if (initialized) return true
        return try {
            helper.initialize()
            helper.addListener(listener)
            initialized = true
            handler.post(pollDestination)
            true
        } catch (e: Exception) {   // SecurityException(권한) 포함
            Log.w(TAG, "navi connect failed", e)
            runCatching { helper.release() }
            false
        }
    }

    override fun release() {
        if (!initialized) return
        handler.removeCallbacks(pollDestination)
        runCatching { helper.removeListener(listener) }
        runCatching { helper.release() }
        initialized = false
        clearRoute()
    }

    private companion object {
        const val TAG = "PleosNaviGateway"
        const val POLL_MS = 5_000L

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

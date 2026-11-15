package com.chagok.chagok.debug

import android.util.Log
import com.chagok.chagok.ui.common.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 에뮬레이터 확인용 로그 (열린 이슈 #1, #5).
 * 디버그 빌드에서 SignalLogger.start(scope, appState)를 한 번 부르고
 * Logcat에서 태그 "ChagokSignal"로 거르면 된다.
 *
 * 확인할 것
 * - SPEED: Virtual Console에서 속도를 바꾸면 계속(주기적으로) 찍히는가
 * - TURN: 깜빡이를 켜면 LEFT 한 번인가, LEFT/OFF가 반복되는가 (레버 vs 점멸)
 * - WIPER / PARKED / NAVI 값이 오는가
 */
object SignalLogger {
    private const val TAG = "ChagokSignal"

    fun start(scope: CoroutineScope, app: AppState) {
        scope.launch {
            app.vehicleSnapshot.map { it.speedKmh }.collect { Log.d(TAG, "SPEED %.1f km/h".format(it)) }
        }
        scope.launch {
            app.vehicleSnapshot.map { it.turnSignal }.distinctUntilChanged().collect { Log.d(TAG, "TURN $it") }
        }
        scope.launch {
            app.vehicleSnapshot.map { it.wiperOn }.distinctUntilChanged().collect { Log.d(TAG, "WIPER $it") }
        }
        scope.launch {
            app.vehicleSnapshot.map { it.parkingBrake }.distinctUntilChanged().collect { Log.d(TAG, "PARKED(gear P) $it") }
        }
        scope.launch { app.naviInfo.collect { Log.d(TAG, "NAVI $it") } }
        scope.launch { app.driveState.collect { Log.d(TAG, "DRIVE_STATE $it") } }
        scope.launch { app.vehicleHealth.collect { Log.d(TAG, "HEALTH $it") } }
        scope.launch { app.capability.collect { Log.d(TAG, "CAPABILITY $it") } }
        scope.launch { app.missionState.collect { Log.d(TAG, "MISSION $it ${app.missionCurrent.value}") } }
    }
}

package com.chagok.chagok.domain.driving

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/** 차량 데이터를 믿고 미션을 돌려도 되는가 (상태 규칙 "데이터 미수신") */
enum class VehicleHealth {
    OK,
    DISCONNECTED,   // SDK 연결 끊김
    SPEED_LOST,     // 연결은 됐는데 속도 값이 N초 이상 안 옴 -> 주행 판정 신뢰 불가, 미션 전체 중지
}

/** S-08 안전 제한 모드로 가야 하는 이유. null이면 정상 */
enum class LimitedReason {
    CONNECT_FAILED,   // S-01에서 3회 연결 실패
    DISCONNECTED,     // 쓰던 중 연결 끊김
    SPEED_LOST,       // 속도 미수신
    NO_MISSIONS,      // capability가 전부 미지원
}

object SignalWatchdog {
    /**
     * 값이 timeoutMs 동안 안 오면 false.
     * 감시를 시작한 순간부터 timeoutMs 유예를 준다 (연결 직후 첫 값이 오기 전에 바로 false가 되지 않게).
     *
     * 주의: 속도처럼 SDK가 주기적으로 보내는 값에만 쓴다.
     * 방향지시등·와이퍼처럼 바뀔 때만 오는 값에 쓰면 정상인데도 끊김으로 본다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun alive(values: Flow<*>, timeoutMs: Long): Flow<Boolean> =
        merge(flowOf(Unit), values.map { Unit })
            .transformLatest {
                emit(true)
                delay(timeoutMs)
                emit(false)
            }
            .distinctUntilChanged()
}

/**
 * 연결 상태 + 속도 수신 여부 -> VehicleHealth
 *
 * SPEED_LOST는 lostConfirmMs 동안 이어져야 내보낸다.
 * 앱이 뒤로 가서 안드로이드가 잠깐 얼렸다 풀면, 밀린 타이머가 속도 값보다 먼저 울려
 * 순간적으로 SPEED_LOST가 났다가 바로 OK가 된다 (에뮬레이터에서 확인). 그 깜빡임을 거른다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun vehicleHealthFlow(
    connected: Flow<Boolean>,
    speed: Flow<Float>,
    speedTimeoutMs: Long,
    lostConfirmMs: Long = 1_000,
): Flow<VehicleHealth> =
    connected.distinctUntilChanged().flatMapLatest { c ->
        if (!c) flowOf(VehicleHealth.DISCONNECTED)
        else SignalWatchdog.alive(speed, speedTimeoutMs)
            .map { alive -> if (alive) VehicleHealth.OK else VehicleHealth.SPEED_LOST }
    }
        .distinctUntilChanged()
        .transformLatest { h ->
            if (h == VehicleHealth.SPEED_LOST) delay(lostConfirmMs)   // 그사이 OK가 오면 취소됨
            emit(h)
        }
        .distinctUntilChanged()

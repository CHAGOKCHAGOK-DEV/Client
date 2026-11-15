package com.chagok.chagok.domain.driving

import com.chagok.chagok.model.DriveState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/** 🟡 임시값. 에뮬레이터에서 확인 후 여기만 고치면 된다. */
data class DriveConfig(
    val drivingKmh: Float = 5f,        // 이 속도 이상이
    val drivingHoldMs: Long = 1_000,   // 이 시간 유지되면 DRIVING
    val stoppedKmh: Float = 0.5f,      // 이 속도 이하가
    val stoppedHoldMs: Long = 3_000,   // 이 시간 유지되면 STOPPED
)

@OptIn(ExperimentalCoroutinesApi::class)
class DriveStateDetector(
    scope: CoroutineScope,
    speedKmh: Flow<Float>,
    parkingBrake: Flow<Boolean>,
    private val config: DriveConfig = DriveConfig(),
) {
    private enum class Raw { PARKED, DRIVING, STOPPED, HOLD }

    val state: StateFlow<DriveState> =
        combine(speedKmh, parkingBrake) { speed, brake ->
            when {
                brake -> Raw.PARKED                         // 주차 브레이크: 속도 무시, 즉시
                speed >= config.drivingKmh -> Raw.DRIVING
                speed <= config.stoppedKmh -> Raw.STOPPED
                else -> Raw.HOLD                            // 애매한 구간: 판단 보류
            }
        }
            .distinctUntilChanged()
            .mapLatest { raw ->                             // 값이 바뀌면 대기 중이던 전환은 취소됨
                when (raw) {
                    Raw.PARKED -> DriveState.PARKED
                    Raw.DRIVING -> { delay(config.drivingHoldMs); DriveState.DRIVING }
                    Raw.STOPPED -> { delay(config.stoppedHoldMs); DriveState.STOPPED }
                    Raw.HOLD -> null
                }
            }
            .filterNotNull()
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, DriveState.PARKED)
}
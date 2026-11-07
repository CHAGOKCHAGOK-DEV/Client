package com.chagok.chagok.sdk.vehicle

import com.chagok.chagok.model.Capability
import com.chagok.chagok.model.TurnSignal
import kotlinx.coroutines.flow.*

/** 에뮬레이터 없이 개발하기 위한 가짜. set~ 함수로 값을 직접 쏜다. */
class FakeVehicleGateway : VehicleGateway {
    private val _connected = MutableStateFlow(false)
    private val _speed = MutableStateFlow(0f)
    private val _brake = MutableStateFlow(true)      // 처음엔 주차 상태
    private val _signal = MutableStateFlow(TurnSignal.OFF)
    private val _wiper = MutableStateFlow(false)

    override val connected: StateFlow<Boolean> = _connected.asStateFlow()
    override val speedKmh: Flow<Float> = _speed
    override val parkingBrake: Flow<Boolean> = _brake
    override val turnSignal: Flow<TurnSignal> = _signal
    override val wiperOn: Flow<Boolean> = _wiper

    override suspend fun connect(): Result<Capability> {
        _connected.value = true
        return Result.success(Capability(speed = true, blink = true, wiper = true, navi = true))
    }

    override fun release() { _connected.value = false }

    // ---- 디버그용 입력 ----
    fun setSpeed(v: Float) { _speed.value = v }
    fun setParkingBrake(on: Boolean) { _brake.value = on }
    fun setTurnSignal(s: TurnSignal) { _signal.value = s }
    fun setWiper(on: Boolean) { _wiper.value = on }
}
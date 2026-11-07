package com.chagok.chagok.sdk.vehicle

import com.chagok.chagok.model.Capability
import com.chagok.chagok.model.TurnSignal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 차량 신호를 받는 창구. 앱의 나머지 코드는 이 인터페이스만 본다.
 * 구현체: FakeVehicleGateway(지금), PleosVehicleGateway(실제 SDK, 나중에)
 */
interface VehicleGateway {
    val connected: StateFlow<Boolean>
    val speedKmh: Flow<Float>
    val parkingBrake: Flow<Boolean>
    val turnSignal: Flow<TurnSignal>
    val wiperOn: Flow<Boolean>

    /** 연결하고 지원 여부(capability)를 돌려준다. 실패하면 Result.failure */
    suspend fun connect(): Result<Capability>

    /** 구독 해제 + SDK 해제 (Vehicle.release()) */
    fun release()
}
package com.chagok.chagok.domain.driving

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleHealthTest {

    @Test
    fun `연결 전에는 DISCONNECTED, 연결 직후엔 유예 시간 동안 OK`() = runTest {
        val connected = MutableStateFlow(false)
        val speed = MutableSharedFlow<Float>()
        val h = vehicleHealthFlow(connected, speed, 3_000).stateIn(backgroundScope, SharingStarted.Eagerly, VehicleHealth.DISCONNECTED)
        runCurrent()
        assertEquals(VehicleHealth.DISCONNECTED, h.value)

        connected.value = true
        runCurrent()
        assertEquals(VehicleHealth.OK, h.value)
    }

    @Test
    fun `속도 값이 계속 오면 OK, 3초 끊기면 SPEED_LOST, 다시 오면 OK`() = runTest {
        val connected = MutableStateFlow(true)
        val speed = MutableSharedFlow<Float>()
        val h = vehicleHealthFlow(connected, speed, 3_000).stateIn(backgroundScope, SharingStarted.Eagerly, VehicleHealth.DISCONNECTED)
        runCurrent()

        repeat(10) { speed.emit(30f); advanceTimeBy(500) }
        assertEquals(VehicleHealth.OK, h.value)

        advanceTimeBy(3_100)
        assertEquals(VehicleHealth.SPEED_LOST, h.value)

        speed.emit(30f); runCurrent()
        assertEquals(VehicleHealth.OK, h.value)
    }

    @Test
    fun `연결이 끊기면 바로 DISCONNECTED`() = runTest {
        val connected = MutableStateFlow(true)
        val speed = MutableSharedFlow<Float>()
        val h = vehicleHealthFlow(connected, speed, 3_000).stateIn(backgroundScope, SharingStarted.Eagerly, VehicleHealth.DISCONNECTED)
        runCurrent()
        connected.value = false
        runCurrent()
        assertEquals(VehicleHealth.DISCONNECTED, h.value)
    }
}

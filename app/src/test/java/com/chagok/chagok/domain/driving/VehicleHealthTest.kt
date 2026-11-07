package com.chagok.chagok.domain.driving

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
        assertEquals(VehicleHealth.OK, h.value)          // 끊긴 직후엔 아직 확정 전
        advanceTimeBy(1_000)
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

    @Test
    fun `앱이 얼었다 풀릴 때처럼 순간 끊김은 SPEED_LOST로 안 바뀐다`() = runTest {
        val connected = MutableStateFlow(true)
        val speed = MutableSharedFlow<Float>()
        val seen = mutableListOf<VehicleHealth>()
        val h = vehicleHealthFlow(connected, speed, 3_000)
        backgroundScope.launch { h.collect { seen += it } }
        runCurrent()

        advanceTimeBy(3_100)          // 타이머가 먼저 울리고
        speed.emit(0f)                // 곧바로 밀린 속도 값이 들어온다
        advanceTimeBy(2_000)

        assertEquals(listOf(VehicleHealth.OK), seen)
    }
}

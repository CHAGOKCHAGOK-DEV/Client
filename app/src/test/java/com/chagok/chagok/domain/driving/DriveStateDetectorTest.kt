package com.chagok.chagok.domain.driving

import com.chagok.chagok.model.DriveState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriveStateDetectorTest {

    @Test
    fun `속도 0이 3초 유지되면 STOPPED`() = runTest {
        val speed = MutableStateFlow(0f)
        val brake = MutableStateFlow(false)
        val d = DriveStateDetector(backgroundScope, speed, brake)

        advanceTimeBy(2_900)
        assertEquals(DriveState.PARKED, d.state.value)   // 아직 초기값
        advanceTimeBy(200)
        assertEquals(DriveState.STOPPED, d.state.value)
    }

    @Test
    fun `5kmh 이상이 1초 유지되면 DRIVING`() = runTest {
        val speed = MutableStateFlow(0f)
        val brake = MutableStateFlow(false)
        val d = DriveStateDetector(backgroundScope, speed, brake)
        advanceTimeBy(3_100)
        assertEquals(DriveState.STOPPED, d.state.value)

        speed.value = 6f
        advanceTimeBy(900)
        assertEquals(DriveState.STOPPED, d.state.value)
        advanceTimeBy(200)
        assertEquals(DriveState.DRIVING, d.state.value)
    }

    @Test
    fun `속도가 순간적으로 튀면 DRIVING으로 안 바뀐다`() = runTest {
        val speed = MutableStateFlow(0f)
        val brake = MutableStateFlow(false)
        val d = DriveStateDetector(backgroundScope, speed, brake)
        advanceTimeBy(3_100)

        speed.value = 8f
        advanceTimeBy(500)
        speed.value = 0f          // 1초 전에 다시 0
        advanceTimeBy(2_000)
        assertEquals(DriveState.STOPPED, d.state.value)
    }

    @Test
    fun `주차 브레이크는 즉시 PARKED`() = runTest {
        val speed = MutableStateFlow(30f)
        val brake = MutableStateFlow(false)
        val d = DriveStateDetector(backgroundScope, speed, brake)
        advanceTimeBy(1_100)
        assertEquals(DriveState.DRIVING, d.state.value)

        brake.value = true
        advanceTimeBy(1)
        assertEquals(DriveState.PARKED, d.state.value)
    }
}
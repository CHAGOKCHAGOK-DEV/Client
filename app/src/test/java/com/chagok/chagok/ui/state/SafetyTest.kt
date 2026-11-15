package com.chagok.chagok.ui.state

import com.chagok.chagok.domain.driving.VehicleHealth
import com.chagok.chagok.model.MissionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SafetyTest {

    // ---- F-15-1 ----

    @Test
    fun `주행 판정이 나면 잠기고 정차가 확정되면 풀린다`() = runTest {
        val t = TestApp(this)
        t.app.connectVehicle()
        assertFalse(t.app.uiLocked.value)
        t.drive()
        assertTrue(t.app.uiLocked.value)
        t.vehicle.setSpeed(0f)
        advanceTimeBy(1_000)
        assertTrue(t.app.uiLocked.value)        // 정차 확정(3초) 전
        advanceTimeBy(2_500)
        assertFalse(t.app.uiLocked.value)
    }

    // ---- F-15-3 ----

    @Test
    fun `속도가 5초 끊기면 SPEED_LOST, 그 전에는 정상`() = runTest {
        val t = TestApp(this)
        t.app.connectVehicle(); advanceTimeBy(1_000)
        t.vehicle.setSpeedFeed(false)
        advanceTimeBy(4_000)
        assertEquals(VehicleHealth.OK, t.app.vehicleHealth.value)
        advanceTimeBy(3_000)
        assertEquals(VehicleHealth.SPEED_LOST, t.app.vehicleHealth.value)
    }

    @Test
    fun `데이터가 끊기면 대기 중인 미션을 버린다`() = runTest {
        val t = TestApp(this)
        t.connectAndReady()
        t.drive()
        t.app.setNaviSpeaking(true)             // 보류시켜 대기 상태로 둔다
        t.vehicle.setWiper(true); advanceTimeBy(1_000)
        assertEquals(MissionState.HOLD, t.app.missionState.value)

        t.vehicle.simulateDisconnect(); advanceTimeBy(100)
        assertEquals(MissionState.EXPIRED, t.store.results.first().single().state)
        assertNull(t.app.missionState.value)
    }

    // ---- S-06 g: 신호 확인 중 ----

    @Test
    fun `주행 중 속도가 끊기면 g, 돌아오면 저절로 a`() = runTest {
        val t = TestApp(this).apply { onboarded() }
        val s = DrivingScreen(backgroundScope, t.app, t.onboarding, speak = {})
        t.connectAndReady()
        t.drive()
        assertEquals(DrivingDisplay.WAITING, s.state.value.display)

        t.vehicle.setSpeedFeed(false); advanceTimeBy(7_000)
        assertEquals(DrivingDisplay.SIGNAL_LOST, s.state.value.display)
        assertEquals(ScreenTexts.DRIVE_SIGNAL, s.state.value.headline)
        assertEquals(ScreenTexts.DRIVE_SIGNAL_SUB, s.state.value.sub)
        assertTrue(t.app.currentTrip.value != null)      // 주행 기록은 유지

        t.vehicle.setSpeedFeed(true); advanceTimeBy(1_000)
        assertEquals(DrivingDisplay.WAITING, s.state.value.display)
    }

    @Test
    fun `주행 중 연결이 끊겨도 g`() = runTest {
        val t = TestApp(this).apply { onboarded() }
        val s = DrivingScreen(backgroundScope, t.app, t.onboarding, speak = {})
        t.connectAndReady()
        t.drive()

        t.vehicle.simulateDisconnect(); advanceTimeBy(100)
        assertEquals(DrivingDisplay.SIGNAL_LOST, s.state.value.display)
    }
}

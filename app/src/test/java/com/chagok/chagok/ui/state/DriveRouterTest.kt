package com.chagok.chagok.ui.state

import com.chagok.chagok.domain.driving.LimitedReason
import com.chagok.chagok.model.TripEndReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriveRouterTest {

    @Test
    fun `주행하면 S-06, 정차해도 10초는 S-06, 그 뒤 잠깐 쉬어가요`() = runTest {
        val t = TestApp(this)
        val r = DriveRouter(backgroundScope, t.app)
        t.connectAndReady()
        assertEquals(Screen.MAIN, r.route.value.screen)

        t.drive()
        assertEquals(Screen.DRIVING, r.route.value.screen)
        assertTrue(r.lockOverlay.value)

        t.stop()
        assertEquals(Screen.DRIVING, r.route.value.screen)
        advanceTimeBy(10_100)
        assertEquals(Screen.SUMMARY, r.route.value.screen)
        assertNull(r.route.value.endedTrip)            // 잠깐 정차
        assertFalse(r.lockOverlay.value)
    }

    @Test
    fun `계속 가기를 누르면 다음 출발 전까지 S-06에 머문다`() = runTest {
        val t = TestApp(this)
        val r = DriveRouter(backgroundScope, t.app)
        t.connectAndReady(); t.drive(); t.stop()
        advanceTimeBy(10_100)

        r.continueDriving(); runCurrent()
        assertEquals(Screen.DRIVING, r.route.value.screen)
        advanceTimeBy(30_000)
        assertEquals(Screen.DRIVING, r.route.value.screen)

        t.drive(); t.stop(); advanceTimeBy(10_100)     // 다시 출발했다 서면 다시 요약
        assertEquals(Screen.SUMMARY, r.route.value.screen)
    }

    @Test
    fun `주차하면 오늘도 수고했어요, 메인으로를 누르면 S-05`() = runTest {
        val t = TestApp(this)
        val r = DriveRouter(backgroundScope, t.app)
        t.connectAndReady(); t.drive()
        t.park()

        assertEquals(Screen.SUMMARY, r.route.value.screen)
        assertEquals(TripEndReason.PARKING_BRAKE, r.route.value.endedTrip?.endReason)

        r.backToMain(); runCurrent()
        assertEquals(Screen.MAIN, r.route.value.screen)
    }

    @Test
    fun `모드 없이 주행하고 서면 요약 없이 S-05`() = runTest {
        val t = TestApp(this)
        val r = DriveRouter(backgroundScope, t.app)
        t.app.connectVehicle(); advanceTimeBy(100)
        t.drive()
        assertEquals(Screen.DRIVING, r.route.value.screen)   // e 상태
        t.stop()
        assertEquals(Screen.MAIN, r.route.value.screen)
    }

    @Test
    fun `연결이 끊기면 S-08`() = runTest {
        val t = TestApp(this)
        val r = DriveRouter(backgroundScope, t.app)
        t.connectAndReady()
        t.vehicle.simulateDisconnect(); advanceTimeBy(100)
        assertEquals(Screen.LIMITED, r.route.value.screen)
        assertEquals(LimitedReason.DISCONNECTED, r.route.value.limitedReason)
    }
}

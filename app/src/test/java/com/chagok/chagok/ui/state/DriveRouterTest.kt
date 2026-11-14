package com.chagok.chagok.ui.state

import com.chagok.chagok.model.TripEndReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriveRouterTest {

    @Test
    fun `주행하면 S-06, 신호 대기로 정차해도 S-06 그대로`() = runTest {
        val t = TestApp(this)
        val r = DriveRouter(backgroundScope, t.app)
        t.connectAndReady()
        assertEquals(Screen.MAIN, r.route.value.screen)

        t.drive()
        assertEquals(Screen.DRIVING, r.route.value.screen)
        assertTrue(r.lockOverlay.value)

        t.stop()
        assertEquals(Screen.DRIVING, r.route.value.screen)
        assertFalse(r.lockOverlay.value)
        advanceTimeBy(60_000)                          // 1분 서 있어도 요약은 안 뜬다
        assertEquals(Screen.DRIVING, r.route.value.screen)

        t.drive()
        assertEquals(Screen.DRIVING, r.route.value.screen)
    }

    @Test
    fun `정차 완료를 누르면 USER_STOP으로 S-07`() = runTest {
        val t = TestApp(this)
        val r = DriveRouter(backgroundScope, t.app)
        t.connectAndReady(); t.drive(); t.stop()

        t.app.finishTrip(); runCurrent()
        assertEquals(Screen.SUMMARY, r.route.value.screen)
        assertEquals(TripEndReason.USER_STOP, r.route.value.endedTrip?.endReason)
        assertFalse(t.app.ready.value)                 // 출발 버튼은 다시 꺼진 상태
    }

    @Test
    fun `주행 판정 중에는 정차 완료가 먹지 않는다`() = runTest {
        val t = TestApp(this)
        val r = DriveRouter(backgroundScope, t.app)
        t.connectAndReady(); t.drive()

        t.app.finishTrip(); runCurrent()
        assertEquals(Screen.DRIVING, r.route.value.screen)
        assertTrue(t.app.currentTrip.value != null)
    }

    @Test
    fun `정차가 5분 이어지면 IDLE_TIMEOUT으로 S-07`() = runTest {
        val t = TestApp(this)
        val r = DriveRouter(backgroundScope, t.app)
        t.connectAndReady(); t.drive(); t.stop()

        advanceTimeBy(300_100)
        assertEquals(Screen.SUMMARY, r.route.value.screen)
        assertEquals(TripEndReason.IDLE_TIMEOUT, r.route.value.endedTrip?.endReason)
    }

    @Test
    fun `주차하면 오늘도 수고했어요, 메인으로를 누르면 S-05`() = runTest {
        val t = TestApp(this)
        val r = DriveRouter(backgroundScope, t.app)
        t.connectAndReady(); t.drive()
        t.park()

        assertEquals(Screen.SUMMARY, r.route.value.screen)
        assertEquals(TripEndReason.PARKED, r.route.value.endedTrip?.endReason)

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
    fun `연결이 끊겨도 화면은 옮기지 않는다`() = runTest {
        val t = TestApp(this)
        val r = DriveRouter(backgroundScope, t.app)
        t.connectAndReady()
        t.vehicle.simulateDisconnect(); advanceTimeBy(100)
        assertEquals(Screen.MAIN, r.route.value.screen)       // S-05 상태 칩만 바뀐다

        t.app.connectVehicle(); advanceTimeBy(100)
        t.drive()
        t.vehicle.simulateDisconnect(); advanceTimeBy(100)
        assertEquals(Screen.DRIVING, r.route.value.screen)    // S-06 g
    }
}

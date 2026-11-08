package com.chagok.chagok.ui.state

import com.chagok.chagok.domain.driving.LimitedReason
import com.chagok.chagok.model.MissionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SafetyTest {

    private fun TestScope.limited(t: TestApp): Pair<LimitedScreen, MutableList<UiEvent>> {
        val s = LimitedScreen(backgroundScope, t.app)
        val events = mutableListOf<UiEvent>()
        backgroundScope.launch { s.events.collect { events += it } }
        runCurrent()
        return s to events
    }

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
        assertNull(t.app.limitedReason.value)
        advanceTimeBy(3_000)
        assertEquals(LimitedReason.SPEED_LOST, t.app.limitedReason.value)
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

    // ---- F-15-2 · S-08 ----

    @Test
    fun `다시 연결 성공하면 S-05`() = runTest {
        val t = TestApp(this)
        t.app.connectVehicle()
        t.vehicle.simulateDisconnect(); advanceTimeBy(100)
        val (s, events) = limited(t)
        assertEquals(LimitedReason.DISCONNECTED, s.state.value.reason)

        s.retry(); advanceTimeBy(100)
        assertEquals(listOf<UiEvent>(UiEvent.Navigate(Screen.MAIN)), events)   // 토스트 없이
        assertFalse(s.state.value.connecting)
    }

    @Test
    fun `다시 연결 실패하면 머물고 시도 횟수를 센다`() = runTest {
        val t = TestApp(this)
        t.vehicle.failConnect = true
        t.app.enterLimitedMode()
        val (s, events) = limited(t)

        s.retry(); advanceTimeBy(100)
        s.retry(); advanceTimeBy(100)
        assertEquals(2, s.state.value.failedAttempts)
        assertTrue(events.isEmpty())
        assertEquals(LimitedReason.CONNECT_FAILED, s.state.value.reason)
    }

    @Test
    fun `저절로 복구되면 토스트 후 S-05`() = runTest {
        val t = TestApp(this)
        t.app.connectVehicle(); advanceTimeBy(1_000)
        t.vehicle.setSpeedFeed(false); advanceTimeBy(7_000)
        val (_, events) = limited(t)

        t.vehicle.setSpeedFeed(true); advanceTimeBy(1_000)
        assertEquals(
            listOf(UiEvent.Toast(ScreenTexts.LIMITED_RECOVERED), UiEvent.Navigate(Screen.MAIN)),
            events,
        )
    }

    @Test
    fun `프로필 보기는 S-04, 주행 중엔 다시 연결과 프로필 보기를 막는다`() = runTest {
        val t = TestApp(this)
        t.app.enterLimitedMode()
        val (s, events) = limited(t)
        s.openProfile(); runCurrent()
        assertEquals(listOf<UiEvent>(UiEvent.Navigate(Screen.PROFILE)), events)

        t.app.connectVehicle(); t.app.enterLimitedMode()
        t.drive()
        assertTrue(s.state.value.locked)
        assertFalse(s.state.value.retryEnabled)
        s.openProfile(); s.retry(); runCurrent()
        assertEquals(1, events.size)
    }
}

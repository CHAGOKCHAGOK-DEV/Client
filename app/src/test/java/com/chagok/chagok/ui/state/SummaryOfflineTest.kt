package com.chagok.chagok.ui.state

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S07_g: 이번 주행 기록을 서버에 못 보냈을 때만 안내 칩 */
@OptIn(ExperimentalCoroutinesApi::class)
class SummaryOfflineTest {

    private suspend fun kotlinx.coroutines.test.TestScope.endedTrip(t: TestApp) {
        t.onboarded()
        t.connectAndReady()
        t.drive()
        t.park()
        advanceTimeBy(100)
    }

    @Test fun `S07_g 인터넷 있고 다 보냈으면 칩 없음`() = runTest {
        val t = TestApp(this)
        endedTrip(t)
        val s = SummaryScreen(backgroundScope, t.app, DriveRouter(backgroundScope, t.app),
            online = MutableStateFlow(true), pendingTrips = MutableStateFlow(emptySet()))
        runCurrent()
        assertFalse(s.state.value.offlineNotice)
    }

    @Test fun `S07_g 인터넷이 없으면 칩`() = runTest {
        val t = TestApp(this)
        endedTrip(t)
        val s = SummaryScreen(backgroundScope, t.app, DriveRouter(backgroundScope, t.app),
            online = MutableStateFlow(false))
        runCurrent()
        assertTrue(s.state.value.offlineNotice)
    }

    @Test fun `S07_g 보내기 실패로 남아 있으면 칩, 보내지면 사라짐`() = runTest {
        val t = TestApp(this)
        endedTrip(t)
        val tripId = t.app.lastEndedTrip.value!!.id
        val pending = MutableStateFlow(setOf(tripId))
        val s = SummaryScreen(backgroundScope, t.app, DriveRouter(backgroundScope, t.app),
            online = MutableStateFlow(true), pendingTrips = pending)
        runCurrent()
        assertTrue(s.state.value.offlineNotice)

        pending.value = emptySet()
        runCurrent()
        assertFalse(s.state.value.offlineNotice)
    }

    @Test fun `S07_g 다른 주행이 남아 있는 건 이번 주행 칩과 상관없음`() = runTest {
        val t = TestApp(this)
        endedTrip(t)
        val s = SummaryScreen(backgroundScope, t.app, DriveRouter(backgroundScope, t.app),
            online = MutableStateFlow(true), pendingTrips = MutableStateFlow(setOf("old-trip")))
        runCurrent()
        assertFalse(s.state.value.offlineNotice)
    }
}

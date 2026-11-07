package com.chagok.chagok.domain.trip

import com.chagok.chagok.data.repository.InMemoryTripStore
import com.chagok.chagok.model.DriveState
import com.chagok.chagok.model.Trip
import com.chagok.chagok.model.TripEndReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripTrackerTest {

    private class Env(
        val drive: MutableStateFlow<DriveState>,
        val mode: MutableStateFlow<Boolean>,
        val store: InMemoryTripStore,
        val tracker: TripTracker,
        val ended: MutableList<Trip>,
    )

    private fun TestScope.env(mode: Boolean = true): Env {
        val drive = MutableStateFlow(DriveState.PARKED)
        val m = MutableStateFlow(mode)
        val store = InMemoryTripStore()
        val ended = mutableListOf<Trip>()
        var n = 0
        val t = TripTracker(
            backgroundScope, drive, m, store, { testScheduler.currentTime }, { 180_000L },
            longStopMs = 300_000L, newId = { "trip-${++n}" }, onEnded = { ended += it },
        )
        runCurrent()
        return Env(drive, m, store, t, ended)
    }

    @Test
    fun `모드 ON에서 주행 판정이면 trip 시작, 주차 브레이크면 종료`() = runTest {
        val e = env()
        e.drive.value = DriveState.DRIVING; runCurrent()
        assertEquals("trip-1", e.tracker.current.value?.id)

        e.drive.value = DriveState.PARKED; runCurrent()
        assertNull(e.tracker.current.value)
        assertEquals(TripEndReason.PARKING_BRAKE, e.ended.single().endReason)
        assertEquals(TripEndReason.PARKING_BRAKE, e.store.trips.value.single().endReason)
    }

    @Test
    fun `모드 OFF면 주행해도 trip이 생기지 않는다`() = runTest {
        val e = env(mode = false)
        e.drive.value = DriveState.DRIVING; runCurrent()
        assertNull(e.tracker.current.value)
    }

    @Test
    fun `신호 대기 정차는 trip 유지, 5분 정차하면 종료`() = runTest {
        val e = env()
        e.drive.value = DriveState.DRIVING; runCurrent()
        e.drive.value = DriveState.STOPPED
        advanceTimeBy(60_000)
        e.drive.value = DriveState.DRIVING; runCurrent()
        assertEquals("trip-1", e.tracker.current.value?.id)     // 1분 정차 후 다시 출발: 같은 trip

        e.drive.value = DriveState.STOPPED
        advanceTimeBy(300_001); runCurrent()
        assertNull(e.tracker.current.value)
        assertEquals(TripEndReason.LONG_STOP, e.tracker.lastEnded.value?.endReason)
    }

    @Test
    fun `앱 종료면 APP_EXIT로 끝난다`() = runTest {
        val e = env()
        e.drive.value = DriveState.DRIVING; runCurrent()
        e.tracker.endNow(); runCurrent()
        assertNotNull(e.tracker.lastEnded.value)
        assertEquals(TripEndReason.APP_EXIT, e.tracker.lastEnded.value?.endReason)
    }
}

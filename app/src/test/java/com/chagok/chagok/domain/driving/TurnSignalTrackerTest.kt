package com.chagok.chagok.domain.driving

import com.chagok.chagok.model.TurnSignal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TurnSignalTrackerTest {

    private class Env(val raw: MutableStateFlow<TurnSignal>, val tracker: TurnSignalTracker, val events: MutableList<BlinkEvent>)

    private fun TestScope.env(offHoldMs: Long = 1_000): Env {
        val raw = MutableStateFlow(TurnSignal.OFF)
        val t = TurnSignalTracker(backgroundScope, raw, { testScheduler.currentTime }, debounceMs = 1_000, offHoldMs = offHoldMs)
        val events = mutableListOf<BlinkEvent>()
        backgroundScope.launch { t.events.collect { events += it } }
        runCurrent()
        return Env(raw, t, events)
    }

    @Test
    fun `레버처럼 계속 켜져 있으면 1초 뒤 Started, 꺼지고 유지시간 뒤 Ended`() = runTest {
        val e = env()
        e.raw.value = TurnSignal.LEFT
        advanceTimeBy(999); runCurrent()
        assertEquals(0, e.events.size)
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf(BlinkEvent.Started(TurnSignal.LEFT, 1_000)), e.events)
        assertEquals(TurnSignal.LEFT, e.tracker.active.value)

        e.raw.value = TurnSignal.OFF
        advanceTimeBy(1_100); runCurrent()
        assertEquals(BlinkEvent.Ended(TurnSignal.LEFT, 2_001), e.events.last())
        assertEquals(TurnSignal.OFF, e.tracker.active.value)
    }

    @Test
    fun `짧게 툭 친 신호는 이벤트가 없다`() = runTest {
        val e = env()
        e.raw.value = TurnSignal.RIGHT
        advanceTimeBy(300); runCurrent()
        e.raw.value = TurnSignal.OFF
        advanceTimeBy(3_000); runCurrent()
        assertEquals(emptyList<BlinkEvent>(), e.events)
    }

    @Test
    fun `램프 점멸(0_4초 간격)도 한 번의 켜짐으로 본다`() = runTest {
        val e = env()
        repeat(8) {                       // 약 3.2초 동안 점멸
            e.raw.value = TurnSignal.LEFT
            advanceTimeBy(400); runCurrent()
            e.raw.value = TurnSignal.OFF
            advanceTimeBy(400); runCurrent()
        }
        advanceTimeBy(1_100); runCurrent()   // 꺼짐 유지시간 경과

        assertEquals(2, e.events.size)
        assertEquals(true, e.events[0] is BlinkEvent.Started)
        assertEquals(true, e.events[1] is BlinkEvent.Ended)
    }

    @Test
    fun `방향이 바로 바뀌면 이전 방향을 끝내고 새 방향을 다시 잰다`() = runTest {
        val e = env()
        e.raw.value = TurnSignal.LEFT
        advanceTimeBy(1_500); runCurrent()
        e.raw.value = TurnSignal.RIGHT
        advanceTimeBy(1_500); runCurrent()

        assertEquals(
            listOf<BlinkEvent>(
                BlinkEvent.Started(TurnSignal.LEFT, 1_000),
                BlinkEvent.Ended(TurnSignal.LEFT, 1_500),
                BlinkEvent.Started(TurnSignal.RIGHT, 2_500),
            ),
            e.events,
        )
    }

    @Test
    fun `꺼짐 유지시간 0이면 OFF 즉시 Ended (레버 상태 SDK용)`() = runTest {
        val e = env(offHoldMs = 0)
        e.raw.value = TurnSignal.LEFT
        advanceTimeBy(1_100); runCurrent()
        e.raw.value = TurnSignal.OFF
        runCurrent()
        assertEquals(BlinkEvent.Ended(TurnSignal.LEFT, 1_100), e.events.last())
    }
}

package com.chagok.chagok.ui.state

import com.chagok.chagok.data.remote.DailyProgress
import com.chagok.chagok.data.remote.DailyProgressRepository
import com.chagok.chagok.data.remote.DailyProgressResponse
import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.model.MissionResult
import com.chagok.chagok.model.MissionState
import com.chagok.chagok.sdk.speech.Answer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** S05-04 오늘의 미션: 서버 값 + 아직 서버에 안 올라간 기기 기록 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDailyProgressTest {

    /** 테스트 시계 1970-01-01 19시(KST) */
    private val today = LocalDate.of(1970, 1, 1)

    private fun TestScope.screen(t: TestApp, server: MutableStateFlow<DailyProgress?>) =
        MainScreen(backgroundScope, t.app, t.onboarding, speak = {}, nowMs = { testScheduler.currentTime }, serverToday = server)

    private suspend fun TestScope.done(t: TestApp, id: String, tripId: String?) = t.store.save(
        MissionResult(
            id = id, tripId = tripId, code = MissionCode.BLINK, expectedAnswer = Answer.YES,
            givenAnswer = Answer.YES, state = MissionState.DONE, correct = true, retryCount = 0,
            triggeredAtMs = testScheduler.currentTime - 1_000, askedAtMs = null, answeredAtMs = null,
        ),
    )

    @Test fun `서버 값이 없으면 기기 기록과 기본 목표 5`() = runTest {
        val t = TestApp(this)
        val s = screen(t, MutableStateFlow(null))
        advanceTimeBy(10 * 3_600_000L)
        done(t, "a", "trip-1")
        runCurrent()
        assertEquals(1, s.state.value.todayDone)
        assertEquals(5, s.state.value.todayGoal)
    }

    @Test fun `앱을 다시 켜서 기기 기록이 없어도 서버 숫자와 목표를 보여 준다`() = runTest {
        val t = TestApp(this)
        val s = screen(t, MutableStateFlow(DailyProgress(today, goal = 6, done = 3, countedTrips = emptySet())))
        advanceTimeBy(10 * 3_600_000L)
        runCurrent()
        assertEquals(3, s.state.value.todayDone)
        assertEquals(6, s.state.value.todayGoal)
        assertEquals(50, s.state.value.todayPercent)
    }

    @Test fun `서버에 이미 올라간 주행은 두 번 세지 않고, 아직 안 올라간 주행은 더한다`() = runTest {
        val t = TestApp(this)
        val server = MutableStateFlow<DailyProgress?>(DailyProgress(today, goal = 5, done = 2, countedTrips = setOf("trip-1")))
        val s = screen(t, server)
        advanceTimeBy(10 * 3_600_000L)
        done(t, "a", "trip-1"); done(t, "b", "trip-1")      // 서버 2에 들어 있음
        done(t, "c", "trip-2")                              // 지금 주행, 아직 안 올라감
        runCurrent()
        assertEquals(3, s.state.value.todayDone)

        // trip-2가 올라가고 다시 조회
        server.value = DailyProgress(today, goal = 5, done = 3, countedTrips = setOf("trip-1", "trip-2"))
        runCurrent()
        assertEquals(3, s.state.value.todayDone)
    }

    @Test fun `날짜가 지난 서버 값은 쓰지 않는다`() = runTest {
        val t = TestApp(this)
        val s = screen(t, MutableStateFlow(DailyProgress(today.minusDays(1), goal = 5, done = 4, countedTrips = emptySet())))
        advanceTimeBy(10 * 3_600_000L)
        runCurrent()
        assertEquals(0, s.state.value.todayDone)
    }

    @Test fun `서버 응답 변환은 대답한 미션 수(participatedCount)를 완료로`() {
        val p = DailyProgressRepository.toProgress(
            DailyProgressResponse(date = "2026-11-15", goal = 5, participatedCount = 2, completedCount = 4, correctCount = 1),
            counted = setOf("t"),
        )
        assertEquals(DailyProgress(LocalDate.of(2026, 11, 15), 5, 2, setOf("t")), p)
    }
}

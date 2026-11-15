package com.chagok.chagok.ui.state

import com.chagok.chagok.domain.mission.MissionEngine
import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.model.MissionResult
import com.chagok.chagok.model.MissionState
import com.chagok.chagok.sdk.speech.Answer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** F-13: 주차로 끝나면 S-06에서 오늘의 리포트(S06F_13)를 보여 주고 S-07로 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayReportRouteTest {

    private fun TestScope.app() = TestApp(this, missions = BASIC_MISSIONS + MissionCode.TODAY_REPORT)

    private suspend fun TestScope.doneToday(t: TestApp) = t.store.save(
        MissionResult(
            id = "r1", tripId = t.app.currentTrip.value?.id, code = MissionCode.WIPER, expectedAnswer = Answer.YES,
            givenAnswer = Answer.YES, state = MissionState.DONE, correct = true, retryCount = 0,
            triggeredAtMs = testScheduler.currentTime, askedAtMs = null, answeredAtMs = null,
        ),
    )

    @Test fun `주차하면 S-06 리포트 화면 뒤 S-07`() = runTest {
        val t = app().apply { onboarded() }
        val r = DriveRouter(backgroundScope, t.app)
        val s = DrivingScreen(backgroundScope, t.app, t.onboarding, speak = {})
        t.connectAndReady(); t.drive()
        doneToday(t)
        t.park()
        runCurrent()

        assertEquals(Screen.DRIVING, r.route.value.screen)
        assertEquals(DrivingDisplay.SPEAKING, s.state.value.display)
        assertEquals(MissionCode.TODAY_REPORT, s.state.value.mission)
        assertEquals("도착! 오늘의 리포트", s.state.value.headline)

        advanceTimeBy(600)                                   // 발표(0.5초) 끝 → 마무리 멘트
        assertEquals(DrivingDisplay.CORRECT, s.state.value.display)
        assertEquals("수고했어요!", s.state.value.headline)

        advanceTimeBy(MissionEngine.REPORT_RESULT_HOLD_MS + 500)
        assertEquals(Screen.SUMMARY, r.route.value.screen)
    }

    @Test fun `오늘 한 미션이 없으면 바로 S-07`() = runTest {
        val t = app()
        val r = DriveRouter(backgroundScope, t.app)
        t.connectAndReady(); t.drive()
        t.park()
        advanceTimeBy(500)
        assertEquals(Screen.SUMMARY, r.route.value.screen)
    }
}

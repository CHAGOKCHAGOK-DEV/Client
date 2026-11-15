package com.chagok.chagok.ui.state

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.model.TripEndReason
import com.chagok.chagok.sdk.speech.Answer
import com.chagok.chagok.sdk.speech.FakeSpeechPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrivingScreenTest {

    @Test
    fun `출발 버튼 없이 주행하면 모드 꺼짐(e)`() = runTest {
        val t = TestApp(this).apply { onboarded() }
        val s = DrivingScreen(backgroundScope, t.app, t.onboarding, speak = {})
        t.app.connectVehicle(); advanceTimeBy(100)
        t.drive()

        assertEquals(DrivingDisplay.MODE_OFF, s.state.value.display)
        assertEquals(ScreenTexts.DRIVE_MODE_OFF, s.state.value.headline)
        assertEquals(ScreenTexts.DRIVE_MODE_OFF_SUB, s.state.value.sub)
        assertEquals("은우 부기장 모드 꺼짐", s.state.value.modeChip)
        assertEquals(null, s.state.value.mission)
    }

    @Test
    fun `미션 진행에 따라 a-b-c-정답, 결과는 2초 뒤 a`() = runTest {
        val t = TestApp(this).apply { onboarded() }
        val s = DrivingScreen(backgroundScope, t.app, t.onboarding, speak = {})
        val seen = mutableListOf<DrivingDisplay>()
        backgroundScope.launch { s.state.collect { if (seen.lastOrNull() != it.display) seen += it.display } }
        t.connectAndReady()
        t.drive()
        assertEquals(DrivingDisplay.WAITING, s.state.value.display)
        assertEquals("은우 부기장 모드 켜짐", s.state.value.modeChip)

        t.vehicle.setWiper(true)
        advanceTimeBy(2_500)                    // 질문 0.5초 + 듣기 0.3초 + 결과 멘트 0.5초
        assertEquals(DrivingDisplay.CORRECT, s.state.value.display)
        assertEquals(MissionCode.WIPER, s.state.value.mission)
        assertEquals("맞아요!", s.state.value.headline)
        advanceTimeBy(2_100)
        assertEquals(DrivingDisplay.WAITING, s.state.value.display)

        assertEquals(
            listOf(
                DrivingDisplay.MODE_OFF, DrivingDisplay.WAITING, DrivingDisplay.SPEAKING,
                DrivingDisplay.LISTENING, DrivingDisplay.CORRECT, DrivingDisplay.WAITING,
            ),
            seen,
        )
    }

    @Test
    fun `말하는 중·듣는 중엔 미션 이름과 대답 안내, 틀리면 WRONG`() = runTest {
        val t = TestApp(this, speech = FakeSpeechPort(nextAnswer = Answer.NO)).apply { onboarded() }
        val s = DrivingScreen(backgroundScope, t.app, t.onboarding, speak = {})
        val seen = mutableListOf<DrivingUiState>()
        backgroundScope.launch { s.state.collect { if (seen.lastOrNull()?.display != it.display) seen += it } }
        t.connectAndReady()
        t.drive()

        t.vehicle.setWiper(true)
        advanceTimeBy(5_000)                    // 질문 → 듣기("아니") → 오답 멘트 → 2초 뒤 대기

        assertEquals(
            listOf(
                DrivingDisplay.MODE_OFF, DrivingDisplay.WAITING, DrivingDisplay.SPEAKING,
                DrivingDisplay.LISTENING, DrivingDisplay.WRONG, DrivingDisplay.WAITING,
            ),
            seen.map { it.display },
        )
        val speaking = seen.first { it.display == DrivingDisplay.SPEAKING }
        assertEquals(MissionCode.WIPER, speaking.mission)
        assertEquals(ScreenTexts.DRIVE_SPEAKING, speaking.headline)

        val listening = seen.first { it.display == DrivingDisplay.LISTENING }
        assertEquals(DrivingTexts.listenHint(MissionCode.WIPER), listening.sub)

        val wrong = seen.first { it.display == DrivingDisplay.WRONG }
        assertEquals(MissionCode.WIPER, wrong.mission)
        assertEquals(DrivingTexts.wrong(MissionCode.WIPER), wrong.headline to wrong.sub)

        assertEquals(null, seen.last().mission)
    }

    @Test
    fun `정차하면 f와 정차 완료 버튼, 다시 출발하면 a`() = runTest {
        val t = TestApp(this).apply { onboarded() }
        val s = DrivingScreen(backgroundScope, t.app, t.onboarding, speak = {})
        t.connectAndReady()
        t.drive()
        assertFalse(s.state.value.stopDoneVisible)

        t.stop()
        assertEquals(DrivingDisplay.STOPPED, s.state.value.display)
        assertEquals(ScreenTexts.DRIVE_STOPPED, s.state.value.headline)
        assertTrue(s.state.value.stopDoneVisible)

        t.drive()
        assertEquals(DrivingDisplay.WAITING, s.state.value.display)
        assertFalse(s.state.value.stopDoneVisible)
    }

    @Test
    fun `정차 완료는 정차 중에만 먹고, 누르면 주행이 끝난다`() = runTest {
        val t = TestApp(this).apply { onboarded() }
        val s = DrivingScreen(backgroundScope, t.app, t.onboarding, speak = {})
        t.connectAndReady()
        t.drive()

        s.onStopDone(); runCurrent()
        assertTrue(t.app.currentTrip.value != null)      // 주행 중엔 무시

        t.stop()
        s.onStopDone(); runCurrent()
        assertEquals(null, t.app.currentTrip.value)
        assertEquals(TripEndReason.USER_STOP, t.app.lastEndedTrip.value?.endReason)
        assertEquals(DrivingDisplay.MODE_OFF, s.state.value.display)
    }

    @Test
    fun `화면을 만지면 안내 음성은 주행당 한 번`() = runTest {
        val t = TestApp(this)
        val spoken = mutableListOf<String>()
        val s = DrivingScreen(backgroundScope, t.app, t.onboarding, speak = { spoken += it })
        runCurrent()
        s.onTouch(); s.onTouch(); runCurrent()
        assertEquals(listOf(ScreenTexts.DRIVE_TOUCH_TTS), spoken)
    }
}

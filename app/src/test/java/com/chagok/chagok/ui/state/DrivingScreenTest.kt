package com.chagok.chagok.ui.state

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        assertEquals(ScreenTexts.DRIVE_MODE_OFF_SUB, s.state.value.sub)
        assertEquals("은우 부기장 모드 꺼짐", s.state.value.modeChip)
    }

    @Test
    fun `미션 진행에 따라 a-b-c-d, 결과는 2초 뒤 a`() = runTest {
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
        assertEquals(DrivingDisplay.RESULT, s.state.value.display)
        advanceTimeBy(2_100)
        assertEquals(DrivingDisplay.WAITING, s.state.value.display)

        assertEquals(
            listOf(
                DrivingDisplay.MODE_OFF, DrivingDisplay.WAITING, DrivingDisplay.SPEAKING,
                DrivingDisplay.LISTENING, DrivingDisplay.RESULT, DrivingDisplay.WAITING,
            ),
            seen,
        )
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

package com.chagok.chagok.ui.state

import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.model.MissionResult
import com.chagok.chagok.model.MissionState
import com.chagok.chagok.sdk.speech.Answer
import com.chagok.chagok.sdk.vehicle.FakeVehicleGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenTest {

    private val spoken = mutableListOf<String>()

    private fun TestScope.screen(t: TestApp) =
        MainScreen(backgroundScope, t.app, t.onboarding, speak = { spoken += it }, nowMs = { testScheduler.currentTime })

    @Test
    fun `연결 전엔 연결 끊김 칩과 출발 버튼 비활성, 연결되면 주차 중`() = runTest {
        val t = TestApp(this).apply { onboarded() }
        val s = screen(t)
        runCurrent()
        assertEquals(StatusChipUi(ScreenTexts.CHIP_DISCONNECTED, true), s.state.value.statusChip)
        assertFalse(s.state.value.startEnabled)
        assertEquals("은우 부기장 · 7~8세", s.state.value.profileChip)

        t.app.connectVehicle(); advanceTimeBy(100)
        assertEquals(StatusChipUi(ScreenTexts.CHIP_PARKED, false), s.state.value.statusChip)
        assertTrue(s.state.value.startEnabled)
    }

    @Test
    fun `출발 버튼을 켜면 준비 문구와 음성 1회, 끄면 원래대로`() = runTest {
        val t = TestApp(this)
        val s = screen(t)
        t.app.connectVehicle(); advanceTimeBy(100)

        s.onStartToggle(); runCurrent()
        assertTrue(s.state.value.startOn)
        assertEquals(ScreenTexts.MAIN_READY, s.state.value.mascotMessage)
        assertEquals(listOf(ScreenTexts.MAIN_READY_TTS), spoken)

        s.onStartToggle(); runCurrent()
        assertFalse(s.state.value.startOn)
        assertEquals(ScreenTexts.MAIN_DEFAULT, s.state.value.mascotMessage)
        assertEquals(1, spoken.size)
    }

    @Test
    fun `오늘 진행은 오늘 완료(DONE)한 미션만 센다`() = runTest {
        val t = TestApp(this)
        val s = screen(t)
        advanceTimeBy(10 * 3_600_000L)   // 테스트 시계를 1970-01-01 19시(KST)로
        fun result(state: MissionState, at: Long) = MissionResult(
            id = "$state$at", tripId = null, code = MissionCode.WIPER, expectedAnswer = Answer.YES,
            givenAnswer = null, state = state, correct = false, retryCount = 0,
            triggeredAtMs = at, askedAtMs = null, answeredAtMs = null,
        )
        val now = testScheduler.currentTime
        t.store.save(result(MissionState.DONE, now - 1_000))
        t.store.save(result(MissionState.DONE, now - 2_000))
        t.store.save(result(MissionState.SKIPPED, now - 3_000))
        t.store.save(result(MissionState.EXPIRED, now - 4_000))
        runCurrent()

        assertEquals(2, s.state.value.todayDone)
        assertEquals(40, s.state.value.todayPercent)
    }

    @Test
    fun `이 차에서 안 되는 미션은 꺼져요로 표시`() = runTest {
        val t = TestApp(this, vehicle = FakeVehicleGateway(capability = noWiper()))
        val s = screen(t)
        t.app.connectVehicle(); advanceTimeBy(100)

        val wiper = s.state.value.missions.single { it.code == MissionCode.WIPER }
        assertFalse(wiper.available)
        assertTrue(s.state.value.missions.single { it.code == MissionCode.BLINK }.available)
    }

    @Test
    fun `배지 탭은 토스트, 연결이 끊겨도 화면은 그대로이고 칩과 출발 버튼만 바뀐다`() = runTest {
        val t = TestApp(this)
        val s = screen(t)
        val events = mutableListOf<UiEvent>()
        backgroundScope.launch { s.events.collect { events += it } }
        t.app.connectVehicle(); advanceTimeBy(100)

        s.onBadgeTab(); runCurrent()
        t.vehicle.simulateDisconnect(); advanceTimeBy(100)

        assertEquals(listOf<UiEvent>(UiEvent.Toast(ScreenTexts.BADGE_TOAST)), events)   // 이동 없음
        assertEquals(StatusChipUi(ScreenTexts.CHIP_DISCONNECTED, true), s.state.value.statusChip)
        assertFalse(s.state.value.startEnabled)
    }
}

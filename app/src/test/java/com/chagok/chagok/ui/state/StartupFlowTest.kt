package com.chagok.chagok.ui.state

import com.chagok.chagok.model.PermissionState
import com.chagok.chagok.model.PermissionStatus
import com.chagok.chagok.permission.FakePermissionChecker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupFlowTest {

    private val allGranted = PermissionState(PermissionStatus.GRANTED, PermissionStatus.GRANTED, PermissionStatus.GRANTED)

    @Test
    fun `권한이 없으면 3단계를 지나 S-02로`() = runTest {
        val t = TestApp(this)
        val flow = StartupFlow(backgroundScope, t.app, t.onboarding)
        flow.start()
        advanceTimeBy(100)

        assertEquals(StartupStep.SETTINGS, flow.state.value.step)
        assertEquals("3 / 3", flow.state.value.progress)
        assertEquals(Screen.PERMISSION, flow.state.value.destination)
    }

    @Test
    fun `권한 있고 동의 없으면 S-03, 다 있으면 S-05`() = runTest {
        val t = TestApp(this, permission = FakePermissionChecker(initial = allGranted))
        val flow = StartupFlow(backgroundScope, t.app, t.onboarding)
        flow.start(); advanceTimeBy(100)
        assertEquals(Screen.CONSENT, flow.state.value.destination)

        val t2 = TestApp(this, permission = FakePermissionChecker(initial = allGranted)).apply { onboarded() }
        val flow2 = StartupFlow(backgroundScope, t2.app, t2.onboarding)
        flow2.start(); advanceTimeBy(100)
        assertEquals(Screen.MAIN, flow2.state.value.destination)
    }

    @Test
    fun `서버 미션팩 확인이 실패해도 진행한다`() = runTest {
        val t = TestApp(this, permission = FakePermissionChecker(initial = allGranted)).apply { onboarded() }
        val flow = StartupFlow(backgroundScope, t.app, t.onboarding, missionPackSync = { error("서버 없음") })
        flow.start(); advanceTimeBy(100)
        assertEquals(Screen.MAIN, flow.state.value.destination)
    }

    @Test
    fun `연결 실패 3번째부터 제한 모드 버튼, 누르면 S-08`() = runTest {
        val t = TestApp(this)
        t.vehicle.failConnect = true
        val flow = StartupFlow(backgroundScope, t.app, t.onboarding)

        flow.start(); advanceTimeBy(100)
        assertTrue(flow.state.value.error)
        assertTrue(flow.state.value.showRetry)
        assertFalse(flow.state.value.showLimitedMode)
        assertEquals(ScreenTexts.STARTUP_ERROR, flow.state.value.message)

        flow.retry(); advanceTimeBy(100)
        assertFalse(flow.state.value.showLimitedMode)
        flow.retry(); advanceTimeBy(100)
        assertEquals(3, flow.state.value.attempts)
        assertTrue(flow.state.value.showLimitedMode)

        flow.enterLimitedMode(); advanceTimeBy(100)
        assertEquals(Screen.LIMITED, flow.state.value.destination)
    }

    @Test
    fun `재시도로 연결되면 다음 화면으로 간다`() = runTest {
        val t = TestApp(this)
        t.vehicle.failConnect = true
        val flow = StartupFlow(backgroundScope, t.app, t.onboarding)
        flow.start(); advanceTimeBy(100)

        t.vehicle.failConnect = false
        flow.retry(); advanceTimeBy(100)
        assertFalse(flow.state.value.error)
        assertEquals(Screen.PERMISSION, flow.state.value.destination)
    }
}

package com.chagok.chagok.ui.state

import com.chagok.chagok.model.PermissionKind
import com.chagok.chagok.model.PermissionStatus
import com.chagok.chagok.permission.FakePermissionChecker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionGuideTest {

    @Test
    fun `행을 눌러 3종 모두 허용하면 1초 뒤 다음 화면`() = runTest {
        val t = TestApp(this)
        val g = PermissionGuide(backgroundScope, t.app, t.onboarding)
        val events = mutableListOf<UiEvent>()
        backgroundScope.launch { g.events.collect { events += it } }
        runCurrent()

        PermissionKind.entries.forEach { g.onRowClick(it) }
        runCurrent()
        assertTrue(g.state.value.allDone)
        assertTrue(g.state.value.rows.all { it.status == PermissionStatus.GRANTED })

        advanceTimeBy(900); runCurrent()
        assertEquals(emptyList<UiEvent>(), events)
        advanceTimeBy(200); runCurrent()
        assertEquals(listOf<UiEvent>(UiEvent.Navigate(Screen.CONSENT)), events)   // 동의 기록 없음
    }

    @Test
    fun `거부되면 배너와 나중에 버튼, 나중에는 토스트 후 종료`() = runTest {
        val t = TestApp(this, permission = FakePermissionChecker(grantOnRequest = false))
        val g = PermissionGuide(backgroundScope, t.app, t.onboarding)
        val events = mutableListOf<UiEvent>()
        backgroundScope.launch { g.events.collect { events += it } }
        runCurrent()

        assertFalse(g.state.value.showDeniedBanner)
        g.onRowClick(PermissionKind.MIC); runCurrent()
        assertTrue(g.state.value.showDeniedBanner)
        assertTrue(g.state.value.showLater)

        g.later(); runCurrent()
        assertEquals(listOf(UiEvent.Toast(ScreenTexts.PERM_LATER_TOAST), UiEvent.ExitApp), events)
    }

    @Test
    fun `다시 확인을 누르면 0_7초 동안 확인 중, 그동안 버튼 비활성`() = runTest {
        val t = TestApp(this)
        val g = PermissionGuide(backgroundScope, t.app, t.onboarding)
        runCurrent()

        g.recheck(); runCurrent()
        assertTrue(g.state.value.rows.all { it.status == PermissionStatus.CHECKING })
        assertFalse(g.state.value.recheckEnabled)

        advanceTimeBy(800); runCurrent()
        assertTrue(g.state.value.recheckEnabled)
        assertEquals(PermissionStatus.NEEDED, g.state.value.rows.first().status)
    }

    @Test
    fun `주행 중이면 잠금, 행을 눌러도 요청 안 함`() = runTest {
        val t = TestApp(this)
        val g = PermissionGuide(backgroundScope, t.app, t.onboarding)
        t.app.connectVehicle()
        t.drive()
        assertTrue(g.state.value.locked)

        g.onRowClick(PermissionKind.MIC); runCurrent()
        assertEquals(PermissionStatus.NEEDED, t.app.permission.value.mic)
    }
}

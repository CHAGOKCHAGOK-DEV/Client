package com.chagok.chagok.ui.state

import com.chagok.chagok.data.repository.ConsentItem
import com.chagok.chagok.data.repository.ConsentRecord
import com.chagok.chagok.data.repository.CopilotProfile
import com.chagok.chagok.model.PermissionKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsentScreenTest {

    private suspend fun TestScope.open(t: TestApp): Pair<ConsentScreen, MutableList<UiEvent>> {
        PermissionKind.entries.forEach { t.permission.request(it) }
        val s = ConsentScreen(backgroundScope, t.app, t.onboarding, nowMs = { 1_234L })
        val events = mutableListOf<UiEvent>()
        backgroundScope.launch { s.events.collect { events += it } }
        runCurrent()
        return s to events
    }

    @Test
    fun `처음엔 모두 꺼져 있고 동의하고 계속은 비활성, 동의하지 않아요는 항상 가능`() = runTest {
        val t = TestApp(this)
        val (s, _) = open(t)
        val st = s.state.value
        assertEquals(3, st.rows.size)
        assertTrue(st.rows.none { it.checked })
        assertFalse(st.allChecked)
        assertFalse(st.agreeEnabled)

        s.refuse(); runCurrent()
        assertTrue(s.state.value.showRefusalDialog)
    }

    @Test
    fun `하나씩 3개 다 켜면 전체 동의도 켜지고 버튼 활성, 하나 끄면 다시 비활성`() = runTest {
        val t = TestApp(this)
        val (s, _) = open(t)
        s.toggle(ConsentItem.NICKNAME); s.toggle(ConsentItem.VOICE_DISCARD); runCurrent()
        assertFalse(s.state.value.agreeEnabled)
        s.toggle(ConsentItem.FAMILY_ONLY); runCurrent()
        assertTrue(s.state.value.allChecked)
        assertTrue(s.state.value.agreeEnabled)

        s.toggle(ConsentItem.VOICE_DISCARD); runCurrent()
        assertFalse(s.state.value.allChecked)
        assertFalse(s.state.value.agreeEnabled)
    }

    @Test
    fun `전체 동의는 모두 켜고, 다시 누르면 모두 끈다`() = runTest {
        val t = TestApp(this)
        val (s, _) = open(t)
        s.toggle(ConsentItem.NICKNAME)
        s.toggleAll(); runCurrent()
        assertTrue(s.state.value.rows.all { it.checked })
        s.toggleAll(); runCurrent()
        assertTrue(s.state.value.rows.none { it.checked })
    }

    @Test
    fun `동의하고 계속 - 버전 항목 3개 시각을 저장하고 프로필 없으면 S-04`() = runTest {
        val t = TestApp(this)
        val (s, events) = open(t)
        s.toggleAll()
        s.agree(); runCurrent()

        assertEquals(
            ConsentRecord(ConsentRecord.CURRENT_VERSION, ConsentItem.entries.toSet(), 1_234L),
            t.onboarding.consent.value,
        )
        assertTrue(t.onboarding.consented.value)
        assertEquals(listOf<UiEvent>(UiEvent.Navigate(Screen.PROFILE)), events)

        s.agree(); runCurrent()
        assertEquals(1, events.size)
    }

    @Test
    fun `프로필이 이미 있으면 S-05로`() = runTest {
        val t = TestApp(this)
        t.onboarding.setProfile(CopilotProfile("은우", "7~8세"))
        val (s, events) = open(t)
        s.toggleAll(); s.agree(); runCurrent()
        assertEquals(listOf<UiEvent>(UiEvent.Navigate(Screen.MAIN)), events)
    }

    @Test
    fun `다 체크하지 않으면 동의하고 계속을 눌러도 저장 안 함`() = runTest {
        val t = TestApp(this)
        val (s, events) = open(t)
        s.toggle(ConsentItem.NICKNAME)
        s.agree(); runCurrent()
        assertNull(t.onboarding.consent.value)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `동의하지 않아요 - C-04를 띄우고 저장 안 함, 확인 누르면 S-03 그대로`() = runTest {
        val t = TestApp(this)
        val (s, events) = open(t)
        s.toggle(ConsentItem.NICKNAME)
        s.refuse(); runCurrent()
        assertTrue(s.state.value.showRefusalDialog)
        assertNull(t.onboarding.consent.value)
        assertFalse(t.onboarding.consented.value)

        s.toggle(ConsentItem.VOICE_DISCARD); runCurrent()
        assertEquals(listOf(true, false, false), s.state.value.rows.map { it.checked })

        s.dismissRefusal(); runCurrent()
        assertFalse(s.state.value.showRefusalDialog)
        assertEquals(listOf(true, false, false), s.state.value.rows.map { it.checked })
        assertTrue(events.isEmpty())
    }

    @Test
    fun `주행 중이면 잠금, 입력 무시하고 체크는 유지, 정차하면 풀림`() = runTest {
        val t = TestApp(this)
        val (s, events) = open(t)
        s.toggleAll(); runCurrent()
        t.app.connectVehicle()
        t.drive()
        assertTrue(s.state.value.locked)

        s.agree(); s.toggleAll(); s.refuse(); runCurrent()
        assertNull(t.onboarding.consent.value)
        assertTrue(s.state.value.rows.all { it.checked })
        assertFalse(s.state.value.showRefusalDialog)
        assertTrue(events.isEmpty())

        t.stop()
        assertFalse(s.state.value.locked)
        s.agree(); runCurrent()
        assertTrue(t.onboarding.consented.value)
    }

    @Test
    fun `저장된 동의가 예전 버전이거나 항목이 빠지면 유효하지 않다`() {
        val all = ConsentItem.entries.toSet()
        assertTrue(ConsentRecord.isValid(ConsentRecord(ConsentRecord.CURRENT_VERSION, all, 1L)))
        assertFalse(ConsentRecord.isValid(ConsentRecord(ConsentRecord.CURRENT_VERSION - 1, all, 1L)))
        assertFalse(ConsentRecord.isValid(ConsentRecord(ConsentRecord.CURRENT_VERSION, all - ConsentItem.FAMILY_ONLY, 1L)))
        assertFalse(ConsentRecord.isValid(null))
    }
}

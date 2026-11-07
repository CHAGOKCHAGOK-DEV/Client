package com.chagok.chagok.domain.mission

import com.chagok.chagok.data.repository.InMemoryMissionResultStore
import com.chagok.chagok.model.*
import com.chagok.chagok.sdk.speech.Answer
import com.chagok.chagok.sdk.speech.FakeSpeechPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MissionEngineTest {

    private fun TestEnv(
        scope: kotlinx.coroutines.CoroutineScope,
        now: () -> Long,
        wiper: MutableStateFlow<Boolean>,
        navi: MutableStateFlow<NaviInfo>,
        speech: FakeSpeechPort,
        store: InMemoryMissionResultStore,
        naviSpeaking: Boolean = false,
    ) = MissionEngine(
        scope = scope,
        driveState = MutableStateFlow(DriveState.DRIVING),
        blinkEvents = kotlinx.coroutines.flow.emptyFlow(),
        wiperOn = wiper,
        naviInfo = navi,
        speech = speech,
        store = store,
        nowMs = now,
        isNaviSpeaking = { naviSpeaking },
    )

    @Test
    fun `와이퍼 켜면 질문하고 정답이면 DONE으로 저장된다`() = runTest {
        val wiper = MutableStateFlow(false)
        val store = InMemoryMissionResultStore()
        val speech = FakeSpeechPort(nextAnswer = Answer.YES)
        val engine = TestEnv(backgroundScope, { testScheduler.currentTime }, wiper, MutableStateFlow(NaviInfo()), speech, store)

        wiper.value = true
        advanceTimeBy(4_000)

        assertEquals(MissionState.DONE, engine.state.value)
        val r = store.results.first().single()
        assertEquals(MissionCode.WIPER, r.code)
        assertEquals(true, r.correct)
    }

    @Test
    fun `무응답이면 한 번 재질문하고 SKIPPED`() = runTest {
        val wiper = MutableStateFlow(false)
        val store = InMemoryMissionResultStore()
        val speech = FakeSpeechPort(nextAnswer = Answer.NONE)
        val engine = TestEnv(backgroundScope, { testScheduler.currentTime }, wiper, MutableStateFlow(NaviInfo()), speech, store)

        wiper.value = true
        advanceTimeBy(5_000)

        assertEquals(MissionState.SKIPPED, engine.state.value)
        assertEquals(2, speech.spoken.size)   // 질문 + 재질문
        assertEquals(1, store.results.first().single().retryCount)
    }

    @Test
    fun `다음 회전 300m 이내로 들어오면 NEXT_TURN은 EXPIRED`() = runTest {
        val navi = MutableStateFlow(NaviInfo())
        val store = InMemoryMissionResultStore()
        val speech = FakeSpeechPort()
        // 내비 안내 중이라 후보가 보류 상태로 대기한다
        TestEnv(
            backgroundScope, { testScheduler.currentTime },
            MutableStateFlow(false), navi, speech, store,
            naviSpeaking = true,
        )

        navi.value = NaviInfo(nextTurnDir = TurnDir.LEFT, distToTurnM = 400)
        advanceTimeBy(100)      // 엔진이 400m 값을 보고 후보를 만들 시간
        navi.value = NaviInfo(nextTurnDir = TurnDir.LEFT, distToTurnM = 250)
        advanceTimeBy(1_000)    // tick이 돌면서 EXPIRED 처리

        val states = store.results.first().map { it.state }
        assertEquals(listOf(MissionState.EXPIRED), states)
        assertEquals(0, speech.spoken.size)
    }
}
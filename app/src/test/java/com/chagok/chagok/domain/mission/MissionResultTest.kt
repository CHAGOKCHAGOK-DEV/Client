package com.chagok.chagok.domain.mission

import com.chagok.chagok.data.repository.InMemoryMissionResultStore
import com.chagok.chagok.model.DriveState
import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.model.MissionOutcome
import com.chagok.chagok.model.MissionResult
import com.chagok.chagok.model.MissionState
import com.chagok.chagok.model.NaviInfo
import com.chagok.chagok.sdk.speech.Answer
import com.chagok.chagok.sdk.speech.FakeSpeechPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MissionResultTest {

    private fun TestScope.engine(
        wiper: MutableStateFlow<Boolean>,
        store: InMemoryMissionResultStore,
        speech: FakeSpeechPort,
        hold: () -> Boolean = { false },
    ) = MissionEngine(
        scope = backgroundScope,
        driveState = MutableStateFlow(DriveState.DRIVING),
        blinkEvents = emptyFlow(),
        wiperOn = wiper,
        naviInfo = MutableStateFlow(NaviInfo()),
        speech = speech,
        store = store,
        nowMs = { testScheduler.currentTime },
        isNaviSpeaking = hold,
    )

    private fun result(state: MissionState, correct: Boolean) = MissionResult(
        id = "1", tripId = null, code = MissionCode.BLINK, expectedAnswer = Answer.YES,
        givenAnswer = null, state = state, correct = correct, retryCount = 0,
        triggeredAtMs = 0, askedAtMs = null, answeredAtMs = null,
    )

    @Test
    fun `결과 값과 S-07 집계 - 오답도 수행, 성공은 정답만`() {
        val correct = result(MissionState.DONE, true)
        val wrong = result(MissionState.DONE, false)
        val skipped = result(MissionState.SKIPPED, false)
        val expired = result(MissionState.EXPIRED, false)

        assertEquals(
            listOf(MissionOutcome.CORRECT, MissionOutcome.WRONG, MissionOutcome.SKIPPED, MissionOutcome.EXPIRED),
            listOf(correct, wrong, skipped, expired).map { it.outcome },
        )
        assertEquals(listOf(true, true, false, false), listOf(correct, wrong, skipped, expired).map { it.attempted })
        assertEquals(listOf(true, false, false, false), listOf(correct, wrong, skipped, expired).map { it.succeeded })
    }

    @Test
    fun `오답이면 WRONG, 오답 멘트`() = runTest {
        val wiper = MutableStateFlow(false)
        val store = InMemoryMissionResultStore()
        val speech = FakeSpeechPort(nextAnswer = Answer.NO)
        engine(wiper, store, speech)

        wiper.value = true
        advanceTimeBy(4_000)

        val r = store.results.first().single()
        assertEquals(MissionOutcome.WRONG, r.outcome)
        assertEquals(Answer.NO, r.givenAnswer)
        assertEquals(MissionDefinitions.WIPER.wrongMessage, speech.spoken.last())
    }

    @Test
    fun `건너뜀은 대답 NONE으로 저장`() = runTest {
        val wiper = MutableStateFlow(false)
        val store = InMemoryMissionResultStore()
        engine(wiper, store, FakeSpeechPort(nextAnswer = Answer.NONE))

        wiper.value = true
        advanceTimeBy(5_000)

        val r = store.results.first().single()
        assertEquals(MissionOutcome.SKIPPED, r.outcome)
        assertEquals(Answer.NONE, r.givenAnswer)
        assertNull(r.answeredAtMs)
    }

    @Test
    fun `와이퍼가 묻기 전에 꺼지면 정답은 NO`() = runTest {
        val wiper = MutableStateFlow(false)
        val store = InMemoryMissionResultStore()
        var hold = true
        engine(wiper, store, FakeSpeechPort(nextAnswer = Answer.NO)) { hold }

        wiper.value = true
        advanceTimeBy(1_000)
        wiper.value = false
        hold = false
        advanceTimeBy(4_000)

        val r = store.results.first().single()
        assertEquals(Answer.NO, r.expectedAnswer)
        assertTrue(r.correct)
        assertEquals(MissionOutcome.CORRECT, r.outcome)
    }

    @Test
    fun `와이퍼가 켜진 채면 정답은 YES`() = runTest {
        val wiper = MutableStateFlow(false)
        val store = InMemoryMissionResultStore()
        engine(wiper, store, FakeSpeechPort(nextAnswer = Answer.NO))

        wiper.value = true
        advanceTimeBy(4_000)

        val r = store.results.first().single()
        assertEquals(Answer.YES, r.expectedAnswer)
        assertFalse(r.correct)
    }
}

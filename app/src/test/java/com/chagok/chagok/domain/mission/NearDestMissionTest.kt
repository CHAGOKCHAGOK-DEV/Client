package com.chagok.chagok.domain.mission

import com.chagok.chagok.data.repository.InMemoryMissionResultStore
import com.chagok.chagok.model.DriveState
import com.chagok.chagok.model.MissionCode
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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NearDestMissionTest {

    private fun TestScope.engine(
        navi: MutableStateFlow<NaviInfo>,
        store: InMemoryMissionResultStore,
        speech: FakeSpeechPort = FakeSpeechPort(nextAnswer = Answer.YES),
        naviSpeaking: () -> Boolean = { false },
    ) = MissionEngine(
        scope = backgroundScope,
        driveState = MutableStateFlow(DriveState.DRIVING),
        blinkEvents = emptyFlow(),
        wiperOn = MutableStateFlow(false),
        naviInfo = navi,
        speech = speech,
        store = store,
        nowMs = { testScheduler.currentTime },
        isNaviSpeaking = naviSpeaking,
    )

    @Test
    fun `시작 조건 - ETA 5분 이하이고 남은 거리 1km 초과`() {
        fun near(eta: Int?, dist: Int?) = MissionDefinitions.isNearDestination(NaviInfo(etaSec = eta, remainDistM = dist))
        assertTrue(near(300, 3_000))
        assertTrue(near(120, null))          // 거리 모르면 ETA만
        assertFalse(near(301, 3_000))
        assertFalse(near(200, 1_000))        // 1km 이하면 시작 안 함
        assertFalse(near(200, 500))
        assertFalse(near(0, null))           // 경로 없음
        assertFalse(near(null, 3_000))
    }

    @Test
    fun `1km 안에서 ETA 5분이 되면 미션을 만들지 않는다`() = runTest {
        val navi = MutableStateFlow(NaviInfo())
        val store = InMemoryMissionResultStore()
        val speech = FakeSpeechPort()
        engine(navi, store, speech)

        navi.value = NaviInfo(etaSec = 240, remainDistM = 900)
        advanceTimeBy(5_000)

        assertTrue(store.results.first().isEmpty())
        assertEquals(0, speech.spoken.size)
    }

    @Test
    fun `대기 중에 남은 거리 1km가 되면 EXPIRED로 버린다`() = runTest {
        val navi = MutableStateFlow(NaviInfo())
        val store = InMemoryMissionResultStore()
        val speech = FakeSpeechPort()
        var naviSpeaking = true                    // 내비 안내 중이라 보류
        engine(navi, store, speech) { naviSpeaking }

        navi.value = NaviInfo(etaSec = 280, remainDistM = 2_500)
        advanceTimeBy(1_000)
        navi.value = NaviInfo(etaSec = 120, remainDistM = 1_000)
        advanceTimeBy(1_000)
        naviSpeaking = false
        advanceTimeBy(5_000)

        val r = store.results.first().single()
        assertEquals(MissionCode.NEAR_DEST, r.code)
        assertEquals(MissionState.EXPIRED, r.state)
        assertEquals(0, speech.spoken.size)
    }

    @Test
    fun `1km 밖에서 시작하면 정상 진행된다`() = runTest {
        val navi = MutableStateFlow(NaviInfo())
        val store = InMemoryMissionResultStore()
        engine(navi, store)

        navi.value = NaviInfo(etaSec = 280, remainDistM = 2_500)
        advanceTimeBy(4_000)

        val r = store.results.first().single()
        assertEquals(MissionState.DONE, r.state)
        assertTrue(r.correct)
    }
}

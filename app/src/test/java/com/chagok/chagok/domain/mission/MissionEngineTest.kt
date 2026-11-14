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

    @Test
    fun `도착 5분 전이 되면 곧 도착 미션, 아무 말이나 하면 정답`() = runTest {
        val navi = MutableStateFlow(NaviInfo())
        val store = InMemoryMissionResultStore()
        val speech = FakeSpeechPort(nextAnswer = Answer.YES)   // 무슨 말이든
        val engine = TestEnv(backgroundScope, { testScheduler.currentTime }, MutableStateFlow(false), navi, speech, store)

        navi.value = NaviInfo(etaSec = 600)      // 10분 전: 아직
        advanceTimeBy(1_000)
        assertEquals(null, engine.state.value)

        navi.value = NaviInfo(etaSec = 290)      // 5분 이내
        advanceTimeBy(4_000)

        val r = store.results.first().single()
        assertEquals(MissionCode.NEAR_DEST, r.code)
        assertEquals(MissionState.DONE, r.state)
        assertEquals(true, r.correct)
        assertEquals(Answer.ANY, r.givenAnswer)
    }

    @Test
    fun `곧 도착은 무응답이어도 재질문 후 완료(DONE), 주행당 한 번만`() = runTest {
        val navi = MutableStateFlow(NaviInfo())
        val store = InMemoryMissionResultStore()
        val speech = FakeSpeechPort(nextAnswer = Answer.NONE)
        TestEnv(backgroundScope, { testScheduler.currentTime }, MutableStateFlow(false), navi, speech, store)

        navi.value = NaviInfo(etaSec = 280)
        advanceTimeBy(5_000)
        navi.value = NaviInfo(etaSec = 200)      // 다시 갱신돼도
        advanceTimeBy(5_000)

        val results = store.results.first()
        assertEquals(1, results.size)
        assertEquals(MissionState.DONE, results.single().state)
        assertEquals(false, results.single().correct)
        assertEquals(1, results.single().retryCount)
        assertEquals(3, speech.spoken.size)      // 질문 + 재질문 + 마무리 멘트
    }

    @Test
    fun `9세 이상은 깜빡이 방향을 묻고, 틀리면 정답 방향을 알려 준다`() = runTest {
        val blink = kotlinx.coroutines.flow.MutableSharedFlow<com.chagok.chagok.domain.driving.BlinkEvent>(extraBufferCapacity = 4)
        val store = InMemoryMissionResultStore()
        val speech = FakeSpeechPort(nextAnswer = Answer.LEFT)
        MissionEngine(
            scope = backgroundScope,
            driveState = MutableStateFlow(DriveState.DRIVING),
            blinkEvents = blink,
            wiperOn = MutableStateFlow(false),
            naviInfo = MutableStateFlow(NaviInfo()),
            speech = speech,
            store = store,
            nowMs = { testScheduler.currentTime },
            ageGroup = { AgeGroup.AGE_9P },
        )
        advanceTimeBy(100)
        blink.emit(com.chagok.chagok.domain.driving.BlinkEvent.Ended(TurnSignal.RIGHT, testScheduler.currentTime))
        advanceTimeBy(4_000)

        assertEquals("방금 깜빡이는 왼쪽이었을까, 오른쪽이었을까?", speech.spoken.first())
        assertEquals("정답은 오른쪽이었어요!", speech.spoken.last())
        val r = store.results.first().single()
        assertEquals(Answer.RIGHT, r.expectedAnswer)
        assertEquals(false, r.correct)
    }

    @Test
    fun `9세 이상 비 오는 날은 와이퍼·라이트로 묻는다`() = runTest {
        val wiper = MutableStateFlow(false)
        val store = InMemoryMissionResultStore()
        val speech = FakeSpeechPort(nextAnswer = Answer.OPTION_A)
        MissionEngine(
            scope = backgroundScope,
            driveState = MutableStateFlow(DriveState.DRIVING),
            blinkEvents = kotlinx.coroutines.flow.emptyFlow(),
            wiperOn = wiper,
            naviInfo = MutableStateFlow(NaviInfo()),
            speech = speech,
            store = store,
            nowMs = { testScheduler.currentTime },
            ageGroup = { AgeGroup.AGE_9P },
        )
        wiper.value = true
        advanceTimeBy(4_000)

        assertEquals(MissionDefinitions.WIPER.script(AgeGroup.AGE_9P).question, speech.spoken.first())
        assertEquals(true, store.results.first().single().correct)
    }

    @Test
    fun `인식 실패가 3번 이어지면 쉬는 멘트 후 이번 주행은 미션을 안 낸다`() = runTest {
        val wiper = MutableStateFlow(false)
        val store = InMemoryMissionResultStore()
        val speech = FakeSpeechPort(nextAnswer = Answer.NONE)
        val engine = MissionEngine(
            scope = backgroundScope,
            driveState = MutableStateFlow(DriveState.DRIVING),
            blinkEvents = kotlinx.coroutines.flow.emptyFlow(),
            wiperOn = wiper,
            naviInfo = MutableStateFlow(NaviInfo()),
            speech = speech,
            store = store,
            nowMs = { testScheduler.currentTime },
            intervalMs = { 0L },
        )
        repeat(3) {
            wiper.value = true
            advanceTimeBy(5_000)
            wiper.value = false
            advanceTimeBy(100)
        }
        assertEquals(MissionEngine.REST_MESSAGE, speech.spoken.last())

        wiper.value = true                 // 4번째: 쉬는 중이라 안 묻는다
        advanceTimeBy(5_000)
        assertEquals(3, store.results.first().size)

        engine.onTripEnded()               // 다음 주행은 다시 시작
        wiper.value = false; advanceTimeBy(100)
        wiper.value = true; advanceTimeBy(5_000)
        assertEquals(4, store.results.first().size)
    }

    // ---------------- 새 미션 (F-02, 04, 06, 07, 08, 09, 10, 11, 13) ----------------

    private class Env(
        val drive: MutableStateFlow<DriveState> = MutableStateFlow(DriveState.DRIVING),
        val speed: kotlinx.coroutines.flow.MutableSharedFlow<Float> = kotlinx.coroutines.flow.MutableSharedFlow(extraBufferCapacity = 64),
        val wiper: MutableStateFlow<Boolean> = MutableStateFlow(false),
        val blink: kotlinx.coroutines.flow.MutableSharedFlow<com.chagok.chagok.domain.driving.BlinkEvent> =
            kotlinx.coroutines.flow.MutableSharedFlow(extraBufferCapacity = 16),
        val store: InMemoryMissionResultStore = InMemoryMissionResultStore(),
        val speech: FakeSpeechPort = FakeSpeechPort(nextAnswer = Answer.YES),
        var trip: String? = "trip-1",
    )

    private fun kotlinx.coroutines.test.TestScope.engine(
        env: Env, missions: Set<MissionCode>, age: AgeGroup? = null, interval: Long = 0L,
    ) = MissionEngine(
        scope = backgroundScope,
        driveState = env.drive,
        blinkEvents = env.blink,
        wiperOn = env.wiper,
        naviInfo = MutableStateFlow(NaviInfo()),
        speech = env.speech,
        store = env.store,
        nowMs = { testScheduler.currentTime },
        intervalMs = { interval },
        tripId = { env.trip },
        ageGroup = { age },
        speedKmh = env.speed,
        missions = missions,
    )

    @Test
    fun `F-08 주행 첫 출발에 한 번 출동, 대답이 없어도 완료`() = runTest {
        val env = Env(speech = FakeSpeechPort(nextAnswer = Answer.NONE))
        engine(env, setOf(MissionCode.DEPART))
        advanceTimeBy(5_000)
        advanceTimeBy(5_000)
        val r = env.store.results.first().single()
        assertEquals(MissionCode.DEPART, r.code)
        assertEquals(MissionState.DONE, r.state)
        assertEquals("좋아요, 출발!", env.speech.spoken.last())
    }

    @Test
    fun `F-04 와이퍼가 1분 넘게 켜졌다 꺼지면 비가 그쳤어요`() = runTest {
        val env = Env()
        engine(env, setOf(MissionCode.RAIN_STOP))
        advanceTimeBy(100)
        env.wiper.value = true; advanceTimeBy(30_000)
        env.wiper.value = false; advanceTimeBy(3_000)
        assertEquals(0, env.store.results.first().size)          // 30초는 짧다
        env.wiper.value = true; advanceTimeBy(61_000)
        env.wiper.value = false; advanceTimeBy(3_000)
        assertEquals(MissionCode.RAIN_STOP, env.store.results.first().single().code)
    }

    @Test
    fun `F-06 속도가 30초 일정하면 묻고, 9세 이상 50km 기준`() = runTest {
        val env = Env(speech = FakeSpeechPort(nextAnswer = Answer.OPTION_A))
        engine(env, setOf(MissionCode.SPEED_GUESS), age = AgeGroup.AGE_9P)
        repeat(70) { env.speed.emit(72f); advanceTimeBy(500) }
        val r = env.store.results.first().first()
        assertEquals(MissionCode.SPEED_GUESS, r.code)
        assertEquals(Answer.OPTION_A, r.expectedAnswer)              // 72 >= 50 → 빨라
        assertEquals("맞아요! 지금은 시속 약 70km예요!", env.speech.spoken.last())
    }

    @Test
    fun `F-11 20초 사이 15km 이상 빨라지면 묻는다`() = runTest {
        val env = Env(speech = FakeSpeechPort(nextAnswer = Answer.OPTION_A))
        engine(env, setOf(MissionCode.SPEED_CHANGE))
        repeat(20) { env.speed.emit(40f); advanceTimeBy(500) }
        repeat(45) { env.speed.emit(60f); advanceTimeBy(500) }
        val r = env.store.results.first().first()
        assertEquals(MissionCode.SPEED_CHANGE, r.code)
        assertEquals(true, r.correct)
        assertEquals("빨라졌어요! 아까보다 시속 20km 더 빨라요.", env.speech.spoken.last())
    }

    @Test
    fun `F-07 정차 10초에 신호 대기, 출발하면 버린다`() = runTest {
        val env = Env(drive = MutableStateFlow(DriveState.STOPPED), speech = FakeSpeechPort(nextAnswer = Answer.OPTION_A))
        engine(env, setOf(MissionCode.SIGNAL_WAIT), age = AgeGroup.AGE_5_6)
        advanceTimeBy(9_000)
        assertEquals(0, env.speech.spoken.size)
        advanceTimeBy(5_000)
        assertEquals(MissionDefinitions.SIGNAL_WAIT.script(AgeGroup.AGE_5_6).question, env.speech.spoken.first())
        assertEquals(true, env.store.results.first().single().correct)   // "멈춰"
    }

    @Test
    fun `F-09 깜빡이 4번 이상 켜고 정차하면 더 많이 켠 쪽을 묻는다`() = runTest {
        val env = Env(speech = FakeSpeechPort(nextAnswer = Answer.RIGHT))
        engine(env, setOf(MissionCode.BLINK_MEMORY))
        advanceTimeBy(100)
        val t = { testScheduler.currentTime }
        repeat(3) { env.blink.emit(com.chagok.chagok.domain.driving.BlinkEvent.Ended(TurnSignal.RIGHT, t())) }
        env.blink.emit(com.chagok.chagok.domain.driving.BlinkEvent.Ended(TurnSignal.LEFT, t()))
        advanceTimeBy(1_000)
        env.drive.value = DriveState.STOPPED
        advanceTimeBy(5_000)
        val r = env.store.results.first().single()
        assertEquals(MissionCode.BLINK_MEMORY, r.code)
        assertEquals(true, r.correct)
        assertEquals("정답은 오른쪽! 오른쪽 깜빡이를 3번, 왼쪽은 1번 켰어요.", env.speech.spoken.last())
    }

    @Test
    fun `F-10 멈추고 5초에 묻고, 다시 출발할 때 기다린 시간으로 발표`() = runTest {
        val env = Env(drive = MutableStateFlow(DriveState.STOPPED), speech = FakeSpeechPort(nextAnswer = Answer.OPTION_A))
        val e = engine(env, setOf(MissionCode.WAIT_BET))
        advanceTimeBy(8_000)                       // 5초에 묻고 "금방" 대답
        assertEquals(1, env.speech.spoken.size)
        assertEquals(MissionState.HOLD, e.state.value)
        advanceTimeBy(10_000)
        env.drive.value = DriveState.DRIVING       // 약 18초 만에 출발
        advanceTimeBy(2_000)
        val r = env.store.results.first().single()
        assertEquals(true, r.correct)
        assertEquals(true, env.speech.spoken.last().startsWith("출발! 18초 기다렸어요. 금방이 정답!"))
    }

    @Test
    fun `F-02 하루 첫 깜빡이 미션이 끝나면 깜빡이 배우기`() = runTest {
        val env = Env()
        engine(env, setOf(MissionCode.BLINK, MissionCode.BLINK_LEARN))
        advanceTimeBy(100)
        env.blink.emit(com.chagok.chagok.domain.driving.BlinkEvent.Ended(TurnSignal.LEFT, testScheduler.currentTime))
        advanceTimeBy(8_000)
        assertEquals(listOf(MissionCode.BLINK, MissionCode.BLINK_LEARN), env.store.results.first().map { it.code })
    }

    @Test
    fun `F-13 주차로 끝나면 오늘 한 미션을 발표하고 저장은 안 한다`() = runTest {
        val env = Env()
        val e = engine(env, setOf(MissionCode.WIPER, MissionCode.TODAY_REPORT), age = AgeGroup.AGE_7_8)
        env.wiper.value = true
        advanceTimeBy(4_000)
        e.onTripEnded(TripEndReason.PARKED)
        advanceTimeBy(2_000)
        assertEquals("도착! 오늘 1개 중에 1개 맞혔어요! 기장님, 부기장에게 고맙다고 말해 주세요!", env.speech.spoken.last())
        assertEquals(1, env.store.results.first().size)
    }
}

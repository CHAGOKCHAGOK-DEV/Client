package com.chagok.chagok.domain.mission

import com.chagok.chagok.data.repository.MissionResultStore
import com.chagok.chagok.domain.driving.BlinkEvent
import com.chagok.chagok.model.*
import com.chagok.chagok.sdk.speech.Answer
import com.chagok.chagok.sdk.speech.SpeechPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * F-09 미션 엔진.
 * 후보 생성(F-03/F-04/F-07 이벤트) -> 타이밍 통과 -> 질문 -> 응답 -> 판정 -> 저장.
 * scope는 한 스레드에서 돌아야 한다 (Main 또는 limitedParallelism(1)).
 */
class MissionEngine(
    private val scope: CoroutineScope,
    private val driveState: StateFlow<DriveState>,
    blinkEvents: Flow<BlinkEvent>,
    wiperOn: Flow<Boolean>,
    naviInfo: Flow<NaviInfo>,
    private val speech: SpeechPort,
    private val store: MissionResultStore,
    private val nowMs: () -> Long,
    private val intervalMs: () -> Long = { MissionTiming.INTERVAL_MS },   // 3분 고정. 테스트에서만 바꾼다
    private val isNaviSpeaking: () -> Boolean = { false },      // 오디오 포커스 연결 전 기본값
    private val enabled: () -> Boolean = { true },              // 부기장 모드 ON일 때만
    private val isSupported: (MissionCode) -> Boolean = { true },  // capability: 이 차에서 되는 미션만
    private val tripId: () -> String? = { null },               // 결과를 묶을 주행 id
    private val tickMs: Long = 500,
    private val listenTimeoutMs: Long = 5_000,                  // 🟡 STT 대기 5초
) {
    private val _state = MutableStateFlow<MissionState?>(null)
    /** null = 미션 없음. DONE/SKIPPED는 다음 후보가 올 때까지 유지 (UI가 2초 표시 후 무시) */
    val state: StateFlow<MissionState?> = _state.asStateFlow()

    private val _current = MutableStateFlow<MissionCode?>(null)
    val current: StateFlow<MissionCode?> = _current.asStateFlow()

    private val _judged = MutableStateFlow<JudgedMission?>(null)
    /** 방금 판정한 미션. JUDGING으로 바뀌기 직전에 채운다 (다음 판정 때까지 유지) */
    val judged: StateFlow<JudgedMission?> = _judged.asStateFlow()

    private var pending: MissionCandidate? = null   // 대기 후보 최대 1개
    private var running = false
    private var tripEndedWhileRunning = false       // 묻거나 듣는 중에 주행이 끝났는지
    private var signalOn = false
    private var distToTurn: Int? = null
    private var lastEndMs: Long? = null
    private var nextTurnArmed = true
    private var naviDir: TurnDir = TurnDir.NONE
    private var lastNavi = NaviInfo()
    private var nearDestDone = false                // 곧 도착은 주행당 1회
    private var wiperNow = false

    init {
        scope.launch {
            blinkEvents.collect { e ->
                when (e) {
                    is BlinkEvent.Started -> signalOn = true
                    is BlinkEvent.Ended -> {
                        signalOn = false
                        offer(MissionDefinitions.blinkCandidate(nowMs()))   // 꺼진 직후 BLINK 후보
                    }
                }
            }
        }
        scope.launch { wiperOn.collect { wiperNow = it } }
        scope.launch {
            wiperOn.distinctUntilChanged().filter { it }.collect {
                offer(MissionDefinitions.wiperCandidate(nowMs()))
            }
        }
        scope.launch { naviInfo.collect { onNavi(it) } }
        scope.launch {
            while (isActive) {
                tick()
                delay(tickMs)
            }
        }
    }

    private fun onNavi(info: NaviInfo) {
        distToTurn = info.distToTurnM
        naviDir = info.nextTurnDir
        lastNavi = info

        // 곧 도착: 주행당 1회. 모드가 꺼져 있어 못 받았으면 다음 내비 갱신 때 다시 시도
        if (!nearDestDone && MissionDefinitions.isNearDestination(info)) {
            if (offer(MissionDefinitions.nearDestCandidate(nowMs()))) nearDestDone = true
        }

        val dist = info.distToTurnM
        if (dist == null || dist > 500) { nextTurnArmed = true; return }   // 다음 회전을 위해 재무장
        if (nextTurnArmed && dist >= 300) {
            MissionDefinitions.nextTurnCandidate(nowMs(), info.nextTurnDir)?.let {
                nextTurnArmed = false
                offer(it)
            }
        }
    }

    /** 새 후보 등록. 중복이면 무시, 기존 대기 후보는 EXPIRED로 교체 (F-09-4). 받았으면 true */
    private fun offer(c: MissionCandidate): Boolean {
        if (!enabled()) return false
        if (!isSupported(c.code)) return false
        if (pending?.key == c.key) return false
        pending?.let { saveEnded(it, MissionState.EXPIRED) }
        pending = c.copy(tripId = tripId())
        if (!running) _state.value = MissionState.CANDIDATE
        return true
    }

    private suspend fun tick() {
        if (running) return
        val c = pending ?: return

        // 만료 확인은 비활성(정차 등) 중에도 한다. 그래야 다시 출발했을 때 지난 일을 묻지 않는다.
        if (isExpired(c, nowMs())) {
            pending = null
            saveEnded(c, MissionState.EXPIRED)
            _state.value = null
            return
        }
        if (!enabled()) return

        val now = nowMs()

        val decision = MissionTiming.decide(
            c,
            TimingContext(now, driveState.value, signalOn, distToTurn, isNaviSpeaking(), lastEndMs, intervalMs()),
        )
        if (decision == TimingDecision.HOLD) {
            _state.value = MissionState.HOLD
            return
        }

        pending = null
        run(c)
    }

    private fun isExpired(c: MissionCandidate, now: Long): Boolean {
        if (c.expiresAtMs != null && now > c.expiresAtMs) return true
        if (c.code == MissionCode.NEXT_TURN) {
            val d = distToTurn ?: return true                        // 경로 안내 종료
            if (d < MissionTiming.TURN_HOLD_M) return true           // 300m 도달
            val expectedDir = if (c.expectedAnswer == Answer.LEFT) TurnDir.LEFT else TurnDir.RIGHT
            if (naviDir != expectedDir) return true                  // 경로 재탐색으로 방향이 바뀜
        }
        if (c.code == MissionCode.NEAR_DEST) {
            if (lastNavi.etaSec == null) return true                                     // 도착·경로 취소
            if (MissionDefinitions.isTooCloseToDestination(lastNavi)) return true        // 1km 도달
        }
        return false
    }

    /**
     * 주행이 끝나면 호출. 대기 후보는 EXPIRED로 버리고 간격 타이머도 초기화한다.
     * 진행 중인 미션은 끝까지 마친다 (아이 말을 끊지 않기).
     */
    /**
     * 주행(trip)이 끝나면 호출. 진행 중인 미션은 말을 끊지 않고 끝까지 하되,
     * 결과는 SKIPPED로 저장하고 S-07 요약에서 뺀다 (S07 엣지케이스 "진행 중 미션 중 종료").
     */
    fun onTripEnded() {
        if (running) tripEndedWhileRunning = true
        reset()
    }

    fun reset() {
        pending?.let { saveEnded(it, MissionState.EXPIRED) }
        pending = null
        lastEndMs = null
        nextTurnArmed = true
        nearDestDone = false
        if (!running) _state.value = null
    }

    private suspend fun run(c: MissionCandidate) {
        running = true
        tripEndedWhileRunning = false
        _current.value = c.code
        val def = MissionDefinitions.of(c.code)
        var retry = 0
        var askedAt: Long? = null
        var given = Answer.NONE
        try {
            while (true) {
                _state.value = MissionState.ASKING
                if (askedAt == null) askedAt = nowMs()
                speech.speak(def.question)                       // TTS 끝난 뒤에 STT 시작
                _state.value = MissionState.LISTENING
                given = speech.listen(listenTimeoutMs, def.answerKind)
                if (given != Answer.NONE || retry >= 1) break
                retry++                                           // 실패·무응답: 1회 재질문
            }

            if (given == Answer.NONE) {
                if (c.code == MissionCode.NEAR_DEST) {
                    // 곧 도착은 무응답이어도 완료로 친다 (설계서 미션 카탈로그)
                    _judged.value = JudgedMission(c.code, correct = false)
                    _state.value = MissionState.JUDGING
                    speech.speak(def.wrongMessage)
                    finish(c, MissionState.DONE, Answer.NONE, false, retry, askedAt, null)
                } else {
                    finish(c, MissionState.SKIPPED, Answer.NONE, false, retry, askedAt, null)
                }
                return
            }

            val judged = judgedCandidate(c)
            val correct = MissionDefinitions.isCorrect(judged, given)
            _judged.value = JudgedMission(c.code, correct)
            _state.value = MissionState.JUDGING
            speech.speak(if (correct) def.correctMessage else def.wrongMessage)
            finish(judged, MissionState.DONE, given, correct, retry, askedAt, nowMs())
        } finally {
            running = false
            _current.value = null
        }
    }

    /** 와이퍼는 대답한 시점의 상태로 정답을 정한다 (그사이 꺼졌으면 NO) */
    private fun judgedCandidate(c: MissionCandidate): MissionCandidate =
        if (c.code == MissionCode.WIPER) c.copy(expectedAnswer = if (wiperNow) Answer.YES else Answer.NO) else c

    private suspend fun finish(
        c: MissionCandidate, state: MissionState, given: Answer?, correct: Boolean,
        retry: Int, askedAt: Long?, answeredAt: Long?,
    ) {
        lastEndMs = nowMs()
        val interrupted = tripEndedWhileRunning
        tripEndedWhileRunning = false
        val savedState = if (interrupted) MissionState.SKIPPED else state
        store.save(
            MissionResult(
                id = UUID.randomUUID().toString(), tripId = c.tripId, code = c.code, expectedAnswer = c.expectedAnswer,
                givenAnswer = given, state = savedState, correct = correct && !interrupted, retryCount = retry,
                triggeredAtMs = c.triggeredAtMs, askedAtMs = askedAt, answeredAtMs = answeredAt,
                interrupted = interrupted,
            )
        )
        _state.value = savedState
    }

    private fun saveEnded(c: MissionCandidate, state: MissionState) {
        scope.launch {
            store.save(
                MissionResult(
                    id = UUID.randomUUID().toString(), tripId = c.tripId, code = c.code, expectedAnswer = c.expectedAnswer,
                    givenAnswer = null, state = state, correct = false, retryCount = 0,
                    triggeredAtMs = c.triggeredAtMs, askedAtMs = null, answeredAtMs = null,
                )
            )
        }
    }
}
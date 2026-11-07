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
    private val intervalMs: () -> Long = { 180_000L },          // 프로필 강도 연결 전 기본 3분
    private val isNaviSpeaking: () -> Boolean = { false },      // 오디오 포커스 연결 전 기본값
    private val enabled: () -> Boolean = { true },              // 부기장 모드 ON일 때만
    private val tickMs: Long = 500,
    private val listenTimeoutMs: Long = 5_000,                  // 🟡 STT 대기 5초
) {
    private val _state = MutableStateFlow<MissionState?>(null)
    /** null = 미션 없음. DONE/SKIPPED는 다음 후보가 올 때까지 유지 (UI가 2초 표시 후 무시) */
    val state: StateFlow<MissionState?> = _state.asStateFlow()

    private val _current = MutableStateFlow<MissionCode?>(null)
    val current: StateFlow<MissionCode?> = _current.asStateFlow()

    private var pending: MissionCandidate? = null   // 대기 후보 최대 1개
    private var running = false
    private var signalOn = false
    private var distToTurn: Int? = null
    private var lastEndMs: Long? = null
    private var nextTurnArmed = true

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
        val dist = info.distToTurnM
        if (dist == null || dist > 500) { nextTurnArmed = true; return }   // 다음 회전을 위해 재무장
        if (nextTurnArmed && dist >= 300) {
            MissionDefinitions.nextTurnCandidate(nowMs(), info.nextTurnDir)?.let {
                nextTurnArmed = false
                offer(it)
            }
        }
    }

    /** 새 후보 등록. 중복이면 무시, 기존 대기 후보는 EXPIRED로 교체 (F-09-4) */
    private fun offer(c: MissionCandidate) {
        if (!enabled()) return
        if (pending?.key == c.key) return
        pending?.let { saveEnded(it, MissionState.EXPIRED) }
        pending = c
        if (!running) _state.value = MissionState.CANDIDATE
    }

    private suspend fun tick() {
        if (running) return
        val c = pending ?: return
        if (!enabled()) return

        val now = nowMs()
        val expiredByTime = c.expiresAtMs != null && now > c.expiresAtMs
        val d = distToTurn
        val expiredByDistance = c.code == MissionCode.NEXT_TURN && d != null && d < MissionTiming.TURN_HOLD_M
        if (expiredByTime || expiredByDistance) {
            pending = null
            saveEnded(c, MissionState.EXPIRED)
            _state.value = null
            return
        }

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

    private suspend fun run(c: MissionCandidate) {
        running = true
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
                given = speech.listen(listenTimeoutMs)
                if (given != Answer.NONE || retry >= 1) break
                retry++                                           // 실패·무응답: 1회 재질문
            }

            if (given == Answer.NONE) {
                finish(c, MissionState.SKIPPED, null, false, retry, askedAt, null)
                return
            }

            _state.value = MissionState.JUDGING
            val correct = MissionDefinitions.isCorrect(c, given)
            speech.speak(if (correct) def.correctMessage else def.wrongMessage)
            finish(c, MissionState.DONE, given, correct, retry, askedAt, nowMs())
        } finally {
            running = false
            _current.value = null
        }
    }

    private suspend fun finish(
        c: MissionCandidate, state: MissionState, given: Answer?, correct: Boolean,
        retry: Int, askedAt: Long?, answeredAt: Long?,
    ) {
        lastEndMs = nowMs()
        store.save(
            MissionResult(
                id = UUID.randomUUID().toString(), code = c.code, expectedAnswer = c.expectedAnswer,
                givenAnswer = given, state = state, correct = correct, retryCount = retry,
                triggeredAtMs = c.triggeredAtMs, askedAtMs = askedAt, answeredAtMs = answeredAt,
            )
        )
        _state.value = state
    }

    private fun saveEnded(c: MissionCandidate, state: MissionState) {
        scope.launch {
            store.save(
                MissionResult(
                    id = UUID.randomUUID().toString(), code = c.code, expectedAnswer = c.expectedAnswer,
                    givenAnswer = null, state = state, correct = false, retryCount = 0,
                    triggeredAtMs = c.triggeredAtMs, askedAtMs = null, answeredAtMs = null,
                )
            )
        }
    }
}
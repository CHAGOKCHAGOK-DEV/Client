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
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * F-09 미션 엔진 (프론트 미션 13개, 피그마 「s06 완료완료」).
 * 후보 생성(차량·내비·속도·정차 이벤트) -> 타이밍 통과 -> 질문 -> 응답 -> 판정 -> 저장.
 * scope는 한 스레드에서 돌아야 한다 (Main 또는 limitedParallelism(1)).
 *
 * 미션별 트리거
 * - F-01 깜빡이 1초 이상 켜진 뒤 꺼짐            - F-02 하루 첫 깜빡이 미션이 끝난 뒤
 * - F-03 와이퍼 꺼짐 → 켜짐                     - F-04 와이퍼 1분 이상 켜진 뒤 꺼짐
 * - F-05 회전 500~300m 전                       - F-06 속도 30초 일정 (15분 간격)
 * - F-07 주행 중 멈춘 뒤 10초 (정차 중에 묻기)   - F-08 주행 첫 출발
 * - F-09 깜빡이 4번 이상 켜고 정차 (주행당 1회)  - F-10 멈추고 5초, 다시 출발할 때 결과
 * - F-11 20초 사이 속도 15km/h 이상 변화         - F-12 ETA 5분 이하 · 1km 밖 (주행당 1회)
 * - F-13 주차로 주행 종료 → 오늘의 리포트 발표만 (저장 안 함)
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
    private val enabled: () -> Boolean = { true },              // 부기장 모드 ON(주행 기록 중) + 신호 정상
    private val isSupported: (MissionCode) -> Boolean = { true },  // capability: 이 차에서 되는 미션만
    private val tripId: () -> String? = { null },               // 결과를 묶을 주행 id
    private val ageGroup: () -> AgeGroup? = { null },           // 부기장 연령대 (null이면 7~8세 대본)
    private val restAfterFailures: Int = REST_AFTER_FAILURES,   // 인식 실패가 이만큼 이어지면 이번 주행은 쉰다
    speedKmh: Flow<Float> = emptyFlow(),                        // F-06·F-11 속도
    private val missions: Set<MissionCode> = MissionCode.entries.toSet(),   // 켤 미션
    private val dayOf: (Long) -> Long = { localDay(it) },        // "오늘" 계산 (F-02 하루 첫, F-13 오늘)
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
    private val tripEnds = MutableStateFlow(0)      // 주행이 끝날 때마다 +1 (F-10 출발 기다리기 중단용)
    private val lastEndByCode = mutableMapOf<MissionCode, Long>()   // 미션별 간격 (F-06 15분)
    private val lastOfferByCode = mutableMapOf<MissionCode, Long>() // 속도 미션이 매 tick 다시 나오지 않게
    private var failStreak = 0                      // 인식 실패(무응답 건너뜀)가 이어진 횟수
    private var resting = false                     // 실패가 이어져 이번 주행은 쉬는 중
    private var signalOn = false
    private var distToTurn: Int? = null
    private var lastEndMs: Long? = null
    private var nextTurnArmed = true
    private var naviDir: TurnDir = TurnDir.NONE
    private var lastNavi = NaviInfo()
    private var nearDestDone = false                // 곧 도착은 주행당 1회
    private var wiperNow = false
    private var wiperOnSinceMs: Long? = null        // F-04 와이퍼 켜진 시각

    // 주행(trip)마다 새로 세는 값
    private var departedTrip: String? = null        // F-08 이미 출동한 주행
    private var leftBlinks = 0                      // F-09 이번 주행 왼쪽 깜빡이 횟수
    private var rightBlinks = 0
    private var blinkMemoryDone = false             // F-09 주행당 1회
    private var stopStartMs: Long? = null           // 지금 정차가 시작된 시각
    private var waitBetOffered = false              // F-10 이번 정차에 냈는지
    private var signalWaitOffered = false           // F-07 이번 정차에 냈는지
    private var blinkLearnDay: Long? = null         // F-02 마지막으로 낸 날

    private val speedSamples = ArrayDeque<Pair<Long, Float>>()   // (시각, km/h) 최근 70초

    init {
        scope.launch {
            blinkEvents.collect { e ->
                when (e) {
                    is BlinkEvent.Started -> signalOn = true
                    is BlinkEvent.Ended -> {
                        signalOn = false
                        if (tripId() != null) {
                            if (e.signal == TurnSignal.LEFT) leftBlinks++
                            if (e.signal == TurnSignal.RIGHT) rightBlinks++
                        }
                        offer(MissionDefinitions.blinkCandidate(nowMs(), e.signal, ageGroup()))   // 꺼진 직후 BLINK 후보
                    }
                }
            }
        }
        scope.launch { wiperOn.collect { wiperNow = it } }
        scope.launch {
            wiperOn.distinctUntilChanged().collect { on ->
                val now = nowMs()
                if (on) {
                    wiperOnSinceMs = now
                    offer(MissionDefinitions.wiperCandidate(now, ageGroup()))
                } else {
                    val since = wiperOnSinceMs
                    wiperOnSinceMs = null
                    if (since != null && now - since >= MissionDefinitions.RAIN_STOP_MIN_WIPER_MS) {
                        offer(MissionDefinitions.rainStopCandidate(now, ageGroup()))
                    }
                }
            }
        }
        scope.launch { naviInfo.collect { onNavi(it) } }
        scope.launch {
            speedKmh.collect { v ->
                val now = nowMs()
                speedSamples.addLast(now to v)
                while (speedSamples.isNotEmpty() && now - speedSamples.first().first > SPEED_KEEP_MS) {
                    speedSamples.removeFirst()
                }
            }
        }
        scope.launch {
            while (isActive) {
                checkTriggers()
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
            if (offer(MissionDefinitions.nearDestCandidate(nowMs(), ageGroup()))) nearDestDone = true
        }

        val dist = info.distToTurnM
        if (dist == null || dist > 500) { nextTurnArmed = true; return }   // 다음 회전을 위해 재무장
        if (nextTurnArmed && dist >= 300) {
            MissionDefinitions.nextTurnCandidate(nowMs(), info.nextTurnDir, ageGroup())?.let {
                nextTurnArmed = false
                offer(it)
            }
        }
    }

    /** 시간·상태로 생기는 후보 (F-06, F-07, F-08, F-09, F-10, F-11). tick마다 */
    private fun checkTriggers() {
        val now = nowMs()
        val age = ageGroup()
        val drive = driveState.value

        // F-08 부기장 출동: 주행 기록이 생기고 처음 달릴 때
        val trip = tripId()
        if (trip != null && drive == DriveState.DRIVING && departedTrip != trip) {
            departedTrip = trip
            offer(MissionDefinitions.departCandidate(now, age))
        }

        // 정차 미션 (F-07, F-09, F-10)
        if (drive == DriveState.STOPPED && trip != null) {
            val start = stopStartMs ?: now.also {
                stopStartMs = it
                waitBetOffered = false
                signalWaitOffered = false
                if (!blinkMemoryDone) {
                    MissionDefinitions.blinkMemoryCandidate(now, leftBlinks, rightBlinks, age)?.let { c ->
                        if (offer(c)) blinkMemoryDone = true
                    }
                }
            }
            val stoppedFor = now - start
            if (!waitBetOffered && stoppedFor >= MissionDefinitions.WAIT_BET_AFTER_MS) {
                waitBetOffered = true
                offer(MissionDefinitions.waitBetCandidate(now, start, age))
            }
            if (!signalWaitOffered && stoppedFor >= MissionDefinitions.SIGNAL_WAIT_AFTER_MS) {
                signalWaitOffered = true
                offer(MissionDefinitions.signalWaitCandidate(now, age))
            }
        } else if (drive != DriveState.STOPPED) {
            stopStartMs = null
        }

        // 속도 미션 (F-06, F-11): 달리는 중에만
        if (drive == DriveState.DRIVING) {
            steadySpeed(now)?.let { v ->
                if (canOfferAgain(MissionCode.SPEED_GUESS, now) && distToTurn.let { it == null || it > MissionTiming.TURN_HOLD_M }) {
                    offerThrottled(MissionDefinitions.speedGuessCandidate(now, v, age), now)
                }
            }
            speedChange(now)?.let { diff ->
                if (canOfferAgain(MissionCode.SPEED_CHANGE, now)) {
                    MissionDefinitions.speedChangeCandidate(now, diff, age)?.let { offerThrottled(it, now) }
                }
            }
        }
    }

    /** 최근 30초 동안 속도가 일정하면 그 평균 */
    private fun steadySpeed(now: Long): Float? {
        val recent = speedSamples.filter { now - it.first <= MissionDefinitions.SPEED_STEADY_MS }
        // 30초치 기록이 쌓였을 때만 (가장 오래된 기록이 30초 가까이 됐는가)
        if (recent.isEmpty() || now - recent.first().first < MissionDefinitions.SPEED_STEADY_MS - SPEED_SLACK_MS) return null
        val max = recent.maxOf { it.second }
        val min = recent.minOf { it.second }
        if (max - min > MissionDefinitions.SPEED_STEADY_RANGE_KMH) return null
        val avg = recent.map { it.second }.average().toFloat()
        return avg.takeIf { it >= MissionDefinitions.SPEED_GUESS_MIN_KMH }
    }

    /** 20초 전 속도와 지금 속도의 차이 (+ 빨라짐). 20초 전 기록이 없으면 null */
    private fun speedChange(now: Long): Float? {
        val current = speedSamples.lastOrNull()?.second ?: return null
        val before = speedSamples.lastOrNull { now - it.first >= MissionDefinitions.SPEED_CHANGE_WINDOW_MS }
            ?: return null
        if (now - before.first > MissionDefinitions.SPEED_CHANGE_WINDOW_MS + SPEED_SLACK_MS) return null
        return current - before.second
    }

    private fun canOfferAgain(code: MissionCode, now: Long): Boolean {
        if (pending?.code == code || _current.value == code) return false
        val last = lastOfferByCode[code] ?: return true
        return now - last >= SPEED_REOFFER_MS
    }

    private fun offerThrottled(c: MissionCandidate, now: Long) {
        if (offer(c)) lastOfferByCode[c.code] = now
    }

    /** 새 후보 등록. 중복이면 무시, 기존 대기 후보는 EXPIRED로 교체 (F-09-4). 받았으면 true */
    private fun offer(c: MissionCandidate): Boolean {
        if (c.code !in missions) return false
        if (!enabled()) return false
        if (resting) return false
        if (!isSupported(c.code)) return false
        if (!askableNow(MissionDefinitions.of(c.code))) return false
        if (pending?.key == c.key) return false
        if (tooSoonForSameMission(c)) return false
        pending?.let { saveEnded(it, MissionState.EXPIRED) }
        pending = c.copy(tripId = tripId())
        if (!running) _state.value = MissionState.CANDIDATE
        return true
    }

    /** 지금 차 상태에서 물을 수 있는 미션인가 */
    private fun askableNow(def: MissionDefinition): Boolean = when (def.askWhen) {
        AskWhen.DRIVING -> driveState.value == DriveState.DRIVING
        AskWhen.STOPPED -> driveState.value == DriveState.STOPPED
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
        if (!askableNow(MissionDefinitions.of(c.code))) return

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
        // 정차 미션은 다시 출발하면 버린다 (F-07, F-09, F-10)
        if (MissionDefinitions.of(c.code).askWhen == AskWhen.STOPPED && driveState.value != DriveState.STOPPED) return true
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

    /** 같은 미션 사이 최소 간격(MissionDefinition.minIntervalMs)이 안 지났으면 true */
    private fun tooSoonForSameMission(c: MissionCandidate): Boolean {
        val min = MissionDefinitions.of(c.code).minIntervalMs ?: return false
        val last = lastEndByCode[c.code] ?: return false
        return c.triggeredAtMs - last < min
    }

    /**
     * 주행(trip)이 끝나면 호출. 진행 중인 미션은 말을 끊지 않고 끝까지 하되,
     * 결과는 SKIPPED로 저장하고 S-07 요약에서 뺀다 (S07 엣지케이스 "진행 중 미션 중 종료").
     * 주차(PARKED)로 끝나면 F-13 오늘의 리포트를 발표한다.
     * 인식 실패 횟수, 쉬는 상태, 주행별 횟수도 여기서 풀린다 (다음 주행은 새로 시작).
     */
    fun onTripEnded(reason: TripEndReason? = null) {
        if (running) tripEndedWhileRunning = true
        tripEnds.value++
        failStreak = 0
        resting = false
        leftBlinks = 0
        rightBlinks = 0
        blinkMemoryDone = false
        stopStartMs = null
        reset()
        if (reason == TripEndReason.PARKED && MissionCode.TODAY_REPORT in missions) {
            scope.launch { announceTodayReport() }
        }
    }

    /**
     * 대기 후보는 EXPIRED로 버리고 간격 타이머도 초기화한다 (주행 종료, 신호 끊김).
     * 진행 중인 미션은 끝까지 마친다 (아이 말을 끊지 않기).
     */
    fun reset() {
        pending?.let { saveEnded(it, MissionState.EXPIRED) }
        pending = null
        lastEndMs = null
        nextTurnArmed = true
        nearDestDone = false
        if (!running) _state.value = null
    }

    /** F-13: 오늘 한 미션(정답·오답)을 세서 발표. 진행 중인 미션이 끝난 뒤에 말한다 */
    private suspend fun announceTodayReport() {
        while (running) delay(tickMs)
        val now = nowMs()
        val today = dayOf(now)
        val done = store.results.first().filter {
            !it.interrupted && it.attempted && dayOf(it.triggeredAtMs) == today && it.code != MissionCode.TODAY_REPORT
        }
        val best = done.filter { it.succeeded }.groupingBy { it.code }.eachCount().maxByOrNull { it.value }?.key
        val text = MissionDefinitions.todayReport(ageGroup(), done.size, done.count { it.succeeded }, best) ?: return
        speech.speak(text)
    }

    private suspend fun run(c: MissionCandidate) {
        running = true
        tripEndedWhileRunning = false
        _current.value = c.code
        val def = MissionDefinitions.of(c.code)
        val script = def.script(c.age ?: ageGroup())   // 후보가 생길 때의 연령대 대본
        var retry = 0
        var askedAt: Long? = null
        var given = Answer.NONE
        try {
            while (true) {
                _state.value = MissionState.ASKING
                if (askedAt == null) askedAt = nowMs()
                speech.speak(script.question)                    // TTS 끝난 뒤에 STT 시작
                _state.value = MissionState.LISTENING
                given = speech.listen(listenTimeoutMs, script.answerKind)
                if (given != Answer.NONE || retry >= 1) break
                retry++                                           // 실패·무응답: 1회 재질문
            }

            if (given == Answer.NONE) {
                if (def.noAnswerCompletes) {
                    // 곧 도착·부기장 출동은 무응답이어도 완료로 친다
                    _judged.value = JudgedMission(c.code, correct = false, values = c.values)
                    _state.value = MissionState.JUDGING
                    speech.speak(MissionDefinitions.fill(script.wrongMessage, MissionDefinitions.messageValues(c)))
                    finish(c, MissionState.DONE, Answer.NONE, false, retry, askedAt, null)
                } else {
                    finish(c, MissionState.SKIPPED, Answer.NONE, false, retry, askedAt, null)
                    onRecognitionFailed()
                }
                return
            }
            failStreak = 0
            val answeredAt = nowMs()

            // F-10은 대답만 받아 두고, 다시 출발할 때 정답을 정해 발표한다
            val judged = if (c.code == MissionCode.WAIT_BET) {
                val departed = waitForDeparture(c)
                if (departed == null) {
                    finish(c, MissionState.SKIPPED, given, false, retry, askedAt, answeredAt, interrupted = true)
                    return
                }
                departed
            } else judgedCandidate(c)

            val correct = MissionDefinitions.isCorrect(judged, given)
            _current.value = c.code
            _judged.value = JudgedMission(c.code, correct, judged.values)
            _state.value = MissionState.JUDGING
            val message = if (correct) script.correctMessage else script.wrongMessage
            speech.speak(MissionDefinitions.fill(message, MissionDefinitions.messageValues(judged)))
            finish(judged, MissionState.DONE, given, correct, retry, askedAt, answeredAt)
            if (c.code == MissionCode.BLINK) offerBlinkLearn()
        } finally {
            running = false
            _current.value = null
        }
    }

    /**
     * F-10: 대답을 받은 뒤 다시 출발할 때까지 기다린다. 그동안 화면은 정차(f)로 둔다.
     * 출발하면 기다린 시간으로 정답을 정한 후보, 주차·주행 종료·10분 초과면 null (버림)
     */
    private suspend fun waitForDeparture(c: MissionCandidate): MissionCandidate? {
        _current.value = null
        _state.value = MissionState.HOLD
        val endsAtStart = tripEnds.value
        val departed = withTimeoutOrNull(MissionDefinitions.WAIT_BET_GIVE_UP_MS) {
            combine(driveState, tripEnds) { d, e -> d to e }
                .first { (d, e) -> d != DriveState.STOPPED || e != endsAtStart }
                .let { (d, e) -> d == DriveState.DRIVING && e == endsAtStart }
        } ?: false
        return if (departed) MissionDefinitions.waitBetJudged(c, nowMs()) else null
    }

    /** F-02: 하루 첫 깜빡이 미션이 끝나면 깜빡이 배우기 후보 */
    private fun offerBlinkLearn() {
        val now = nowMs()
        val today = dayOf(now)
        if (blinkLearnDay == today) return
        if (offer(MissionDefinitions.blinkLearnCandidate(now, ageGroup()))) blinkLearnDay = today
    }

    /**
     * 인식 실패(재질문 후에도 무응답)가 restAfterFailures번 이어지면 이번 주행은 미션을 쉰다.
     * 아이를 지치게 하지 않기. 다음 주행에서 다시 시작한다.
     */
    private suspend fun onRecognitionFailed() {
        failStreak++
        if (failStreak < restAfterFailures || resting) return
        resting = true
        pending?.let { saveEnded(it, MissionState.EXPIRED) }
        pending = null
        speech.speak(REST_MESSAGE)
    }

    /** 와이퍼(예/아니오)는 대답한 시점의 상태로 정답을 정한다 (그사이 꺼졌으면 NO). 9세+ 와이퍼/라이트는 그대로 */
    private fun judgedCandidate(c: MissionCandidate): MissionCandidate =
        if (c.code == MissionCode.WIPER && c.expectedAnswer != Answer.OPTION_A) {
            c.copy(expectedAnswer = if (wiperNow) Answer.YES else Answer.NO)
        } else c

    private suspend fun finish(
        c: MissionCandidate, state: MissionState, given: Answer?, correct: Boolean,
        retry: Int, askedAt: Long?, answeredAt: Long?, interrupted: Boolean = false,
    ) {
        val now = nowMs()
        lastEndMs = now
        lastEndByCode[c.code] = now
        val cut = interrupted || tripEndedWhileRunning
        tripEndedWhileRunning = false
        val savedState = if (cut) MissionState.SKIPPED else state
        store.save(
            MissionResult(
                id = UUID.randomUUID().toString(), tripId = c.tripId, code = c.code, expectedAnswer = c.expectedAnswer,
                givenAnswer = given, state = savedState, correct = correct && !cut, retryCount = retry,
                triggeredAtMs = c.triggeredAtMs, askedAtMs = askedAt, answeredAtMs = answeredAt,
                interrupted = cut,
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

    companion object {
        const val REST_AFTER_FAILURES = 3
        /** 🟡 인식 실패가 이어졌을 때 멘트. 대본 문서에 문구가 없어 임시 */
        const val REST_MESSAGE = "부기장님, 잠깐 쉬어요. 다음에 또 같이 해요!"

        private const val SPEED_KEEP_MS = 70_000L       // 속도 기록 보관
        private const val SPEED_SLACK_MS = 1_500L       // 속도 수신 간격 여유
        private const val SPEED_REOFFER_MS = 60_000L    // 속도 미션 후보를 다시 낼 수 있는 간격

        /** 기기 시간대 기준 날짜 (오늘 판단) */
        fun localDay(ms: Long): Long =
            Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    }
}

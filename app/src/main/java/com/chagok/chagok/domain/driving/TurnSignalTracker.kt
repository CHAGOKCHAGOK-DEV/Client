package com.chagok.chagok.domain.driving

import com.chagok.chagok.model.TurnSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** 방향지시등 "켰다" 이벤트. 회전 완료가 아니라 신호를 켰다는 뜻뿐이다. */
sealed interface BlinkEvent {
    val signal: TurnSignal
    data class Started(override val signal: TurnSignal, val atMs: Long) : BlinkEvent
    data class Ended(override val signal: TurnSignal, val atMs: Long) : BlinkEvent
}

/**
 * F-03 방향지시등 추적.
 *
 * SDK가 "레버 상태"를 줄지 "램프 점멸(ON/OFF 반복)"을 줄지 아직 모른다 (열린 이슈 #5).
 * 둘 다 처리하려고 두 시간을 쓴다.
 * - debounceMs: 신호가 처음 들어온 뒤 이만큼 지난 시점 이후에도 ON이 보여야 Started
 *   (짧게 툭 친 것 걸러냄. 점멸이면 debounce가 끝난 뒤 다음 ON에서 Started)
 * - offHoldMs : OFF가 이만큼 이어져야 Ended. 점멸 중 잠깐 꺼지는 구간을 꺼짐으로 보지 않는다.
 *   레버 상태가 오는 SDK면 0으로 두면 OFF 즉시 종료(기존 동작).
 *
 * 방향이 LEFT -> RIGHT로 바로 바뀌면 이전 방향 Ended 후 새 방향을 다시 잰다.
 * scope는 한 스레드에서 돌아야 한다.
 */
class TurnSignalTracker(
    private val scope: CoroutineScope,
    turnSignal: Flow<TurnSignal>,
    private val nowMs: () -> Long,
    private val debounceMs: Long = 1_000,   // 🟡 임시값
    private val offHoldMs: Long = 1_000,    // 🟡 점멸 주기(보통 0.7~1초)보다 길게
) {
    private val _events = MutableSharedFlow<BlinkEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<BlinkEvent> = _events.asSharedFlow()

    private val _active = MutableStateFlow(TurnSignal.OFF)
    /** Started~Ended 사이의 방향. 깜빡이 켜진 동안 보류 판단에 쓴다. */
    val active: StateFlow<TurnSignal> = _active.asStateFlow()

    private var candidate: TurnSignal = TurnSignal.OFF   // 재고 있는 방향 (Started 전 포함)
    private var lastRaw: TurnSignal = TurnSignal.OFF
    private var debounced = false                         // debounceMs가 지났는가
    private var startJob: Job? = null
    private var endJob: Job? = null

    init {
        scope.launch {
            turnSignal.distinctUntilChanged().collect { onRaw(it) }
        }
    }

    private fun onRaw(s: TurnSignal) {
        lastRaw = s
        if (s == TurnSignal.OFF) {
            if (candidate == TurnSignal.OFF || endJob?.isActive == true) return
            endJob = scope.launch {
                delay(offHoldMs)
                finish()
            }
            return
        }

        // 켜짐: 꺼짐 대기 중이었으면 취소 (점멸 중 OFF 구간이었던 것)
        endJob?.cancel()
        endJob = null

        if (s == candidate) {
            if (debounced) start(s)                        // 점멸: debounce 뒤 다시 켜진 순간
            return
        }
        if (candidate != TurnSignal.OFF) finish()          // 방향 전환: 이전 것 종료

        candidate = s
        debounced = false
        startJob = scope.launch {
            delay(debounceMs)
            debounced = true
            if (candidate == s && lastRaw == s) start(s)   // 지금도 켜져 있으면 바로
        }
    }

    private fun start(s: TurnSignal) {
        if (_active.value == s) return
        _active.value = s
        _events.tryEmit(BlinkEvent.Started(s, nowMs()))
    }

    /** 현재 후보를 끝낸다. Started가 났던 것만 Ended를 보낸다. */
    private fun finish() {
        startJob?.cancel()
        startJob = null
        val started = _active.value
        candidate = TurnSignal.OFF
        debounced = false
        _active.value = TurnSignal.OFF
        if (started != TurnSignal.OFF) {
            _events.tryEmit(BlinkEvent.Ended(started, nowMs()))
        }
    }
}

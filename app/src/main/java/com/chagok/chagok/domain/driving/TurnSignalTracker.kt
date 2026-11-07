package com.chagok.chagok.domain.driving

import com.chagok.chagok.model.TurnSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/** 방향지시등 "켰다" 이벤트. 회전 완료가 아니라 신호를 켰다는 뜻뿐이다. */
sealed interface BlinkEvent {
    val signal: TurnSignal
    data class Started(override val signal: TurnSignal, val atMs: Long) : BlinkEvent
    data class Ended(override val signal: TurnSignal, val atMs: Long) : BlinkEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class TurnSignalTracker(
    scope: CoroutineScope,
    turnSignal: Flow<TurnSignal>,
    private val nowMs: () -> Long,
    private val debounceMs: Long = 1_000,   // 🟡 임시값
) {
    /** 1초 이상 유지된 신호만 통과 (OFF는 즉시 통과) */
    private val stable: Flow<TurnSignal> = turnSignal
        .distinctUntilChanged()
        .mapLatest { s ->
            if (s != TurnSignal.OFF) delay(debounceMs)
            s
        }
        .distinctUntilChanged()

    val events: SharedFlow<BlinkEvent> = stable
        .runningFold<TurnSignal, Pair<TurnSignal, BlinkEvent?>>(TurnSignal.OFF to null) { (prev, _), cur ->
            val ev: BlinkEvent? = when {
                cur != TurnSignal.OFF -> BlinkEvent.Started(cur, nowMs())
                prev != TurnSignal.OFF -> BlinkEvent.Ended(prev, nowMs())
                else -> null
            }
            cur to ev
        }
        .mapNotNull { it.second }
        .shareIn(scope, SharingStarted.Eagerly, replay = 0)
}
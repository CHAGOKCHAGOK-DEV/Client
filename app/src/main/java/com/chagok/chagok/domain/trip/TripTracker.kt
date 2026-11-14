package com.chagok.chagok.domain.trip

import com.chagok.chagok.data.repository.TripStore
import com.chagok.chagok.model.DriveState
import com.chagok.chagok.model.Trip
import com.chagok.chagok.model.TripEndReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 주행(trip) 시작·종료.
 * - 시작: 부기장 모드 ON 상태에서 DRIVING 판정 (S-05 "주행 판정")
 * - 종료: 주차 브레이크(PARKED) / 정차 longStopMs 지속(IDLE_TIMEOUT) / endNow() ([정차 완료] USER_STOP, 앱 종료 APP_EXIT)
 * 잠깐 정차(신호 대기)는 trip을 유지한다.
 * scope는 한 스레드에서 돌아야 한다.
 */
class TripTracker(
    private val scope: CoroutineScope,
    driveState: StateFlow<DriveState>,
    mode: StateFlow<Boolean>,
    private val store: TripStore,
    private val nowMs: () -> Long,
    private val longStopMs: Long = 5 * 60_000L,          // 🟡 열린 이슈 #3
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val onEnded: (Trip) -> Unit = {},
) {
    private val _current = MutableStateFlow<Trip?>(null)
    /** 진행 중인 주행. 없으면 null */
    val current: StateFlow<Trip?> = _current.asStateFlow()

    private val _lastEnded = MutableStateFlow<Trip?>(null)
    /** 가장 최근에 끝난 주행 (S-07 "오늘도 수고했어요!" 표시용) */
    val lastEnded: StateFlow<Trip?> = _lastEnded.asStateFlow()

    init {
        scope.launch {
            combine(driveState, mode) { d, m -> d to m }
                .distinctUntilChanged()
                .collectLatest { (d, m) ->
                    when (d) {
                        DriveState.DRIVING -> if (_current.value == null && m) start()
                        DriveState.PARKED -> end(TripEndReason.PARKED)
                        DriveState.STOPPED -> if (_current.value != null) {
                            delay(longStopMs)               // 다시 출발하면 취소됨
                            end(TripEndReason.IDLE_TIMEOUT)
                        }
                    }
                }
        }
    }

    // collectLatest가 상태 변화로 취소해도 저장은 끝까지 하도록 NonCancellable
    private suspend fun start() = withContext(NonCancellable) {
        val trip = Trip(id = newId(), startedAtMs = nowMs())
        _current.value = trip
        store.save(trip)
    }

    private suspend fun end(reason: TripEndReason) = withContext(NonCancellable) {
        val trip = _current.value ?: return@withContext
        val ended = trip.copy(endedAtMs = nowMs(), endReason = reason)
        _current.value = null
        _lastEnded.value = ended
        store.save(ended)
        onEnded(ended)
    }

    /** 외부에서 끝낼 때: [정차 완료](USER_STOP), 앱 종료(APP_EXIT) */
    fun endNow(reason: TripEndReason = TripEndReason.APP_EXIT) {
        scope.launch { end(reason) }
    }
}

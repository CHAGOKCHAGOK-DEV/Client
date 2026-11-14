package com.chagok.chagok.data.remote

import android.content.Context
import android.util.Log
import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.model.MissionResult
import com.chagok.chagok.model.MissionState
import com.chagok.chagok.model.Trip
import com.chagok.chagok.model.TripEndReason
import com.chagok.chagok.sdk.speech.Answer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.time.Instant

/**
 * 끝난 주행을 서버에 올린다 (POST /api/v1/trips/sync). 설계서 4-3 "주행 종료 후 POST /api/trips".
 *
 * - 주행이 끝나면 enqueue(): 요청을 만들어 기기에 저장하고 바로 보낸다
 * - 실패(네트워크 끊김 등)하면 저장해 둔 채로, 앱 시작·인터넷 연결 때 flush()로 다시 보낸다
 * - 다시 보낼 때도 처음 만든 요청을 그대로 보낸다 (서버가 같은 내용인지 해시로 확인)
 * - 아이 음성·원문은 없다. 정규화된 결과만 보낸다
 */
class TripSyncRepository(
    context: Context,
    private val api: ChagokApi,
    private val installations: InstallationRepository,
    private val missionPacks: MissionPackRepository,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _pending = MutableStateFlow(load().keys)
    /**
     * 보내 봤는데 아직 못 보낸 주행 id (S-07 "인터넷이 연결되면 기록을 저장할게요").
     * 막 저장해서 보내는 중인 주행은 보내기가 끝난 뒤에야 들어간다 (성공하면 칩이 깜빡이지 않게)
     */
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

    /** 주행 종료: 요청을 만들어 저장하고 보내 본다. 미션팩을 아직 못 받았으면 보내지 않는다 */
    suspend fun enqueue(trip: Trip, results: List<MissionResult>) {
        val pack = missionPacks.cached()
        if (pack == null) {
            Log.w(TAG, "미션팩이 없어 주행을 보내지 않음: ${trip.id}")
            return
        }
        val request = TripSyncMapper.build(
            trip = trip,
            results = results,
            packCode = pack.code,
            packVersion = pack.version,
            packMissionCodes = pack.missions.map { it.code }.toSet(),
        ) ?: return
        mutex.withLock {
            val queue = load()
            if (trip.id in queue) return@withLock          // 이미 저장됨 (처음 만든 요청 유지)
            save(queue + (trip.id to ApiClient.json.encodeToString(request)), publish = false)
        }
        flush()
    }

    /** 저장된 주행을 모두 보낸다. 네트워크 실패는 남겨 두고, 서버가 거절한 것(형식·충돌)은 버린다 */
    suspend fun flush(): Unit = mutex.withLock {
        val queue = load()
        if (queue.isEmpty()) return@withLock
        if (installations.ensureRegistered().isFailure) { _pending.value = queue.keys; return@withLock }
        val remaining = queue.toMutableMap()
        for ((tripId, json) in queue) {
            val request = runCatching { ApiClient.json.decodeFromString<TripSyncRequest>(json) }.getOrNull()
            if (request == null) { remaining.remove(tripId); continue }
            apiCall { api.syncTrip(request) }
                .onSuccess { remaining.remove(tripId) }
                .onFailure { e ->
                    when {
                        e is IOException -> Unit                                   // 다음에 다시
                        e is ApiException && e.httpStatus?.let { it in DROP_STATUSES } == true -> {
                            Log.w(TAG, "주행 업로드 거절, 버림: ${e.code} ${e.message}")
                            remaining.remove(tripId)
                        }
                        else -> Log.w(TAG, "주행 업로드 실패, 다음에 다시: ${e.message}")   // 401·403(동의)·5xx
                    }
                }
        }
        save(remaining)
    }

    private fun load(): Map<String, String> =
        prefs.getString(KEY_QUEUE, null)
            ?.let { runCatching { ApiClient.json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
            ?: emptyMap()

    private fun save(queue: Map<String, String>, publish: Boolean = true) {
        prefs.edit().putString(KEY_QUEUE, ApiClient.json.encodeToString(queue)).apply()
        if (publish) _pending.value = queue.keys
    }

    private companion object {
        const val TAG = "TripSync"
        const val PREFS = "chagok_trip_sync"
        const val KEY_QUEUE = "queue"
        /** 다시 보내도 안 되는 거절: 형식 오류(400), 다른 설치의 주행(404), 같은 id 다른 내용(409) */
        val DROP_STATUSES = setOf(400, 404, 409, 422)
    }
}

/** 앱 값 → 서버 값. 이름이 서로 다른 건 여기서만 바꾼다 */
object TripSyncMapper {

    /** 앱 미션 코드 → 서버 미션팩 코드. 🟡 서버 팩에 13개 미션이 생기면 여기서 맞춘다 */
    fun serverCode(code: MissionCode): String = code.name

    fun endReason(r: TripEndReason?): String = when (r) {
        TripEndReason.USER_STOP -> "MANUAL_STOP"
        TripEndReason.PARKED -> "PARKING_BRAKE"
        TripEndReason.IDLE_TIMEOUT -> "STOP_TIMEOUT"
        TripEndReason.APP_EXIT -> "APP_INTERRUPTED"
        null -> "UNKNOWN"
    }

    /** 정상 종료(정차 완료·주차·정차 5분)만 COMPLETED. 서버도 이 조합만 받는다 */
    fun tripStatus(r: TripEndReason?): String = when (r) {
        TripEndReason.USER_STOP, TripEndReason.PARKED, TripEndReason.IDLE_TIMEOUT -> "COMPLETED"
        else -> "INTERRUPTED"
    }

    /**
     * 끝난 주행 → 요청. 끝나지 않은 주행이면 null.
     * 미션팩에 없는 미션 결과는 서버가 통째로 거절하므로 뺀다 (🟡 서버 팩이 13개가 되면 다 들어간다).
     * 결과 시각은 주행 시간 안으로 맞춘다 (서버 검증: 결과가 주행 밖이면 거절).
     */
    fun build(
        trip: Trip, results: List<MissionResult>,
        packCode: String, packVersion: Int, packMissionCodes: Set<String>,
    ): TripSyncRequest? {
        val ended = trip.endedAtMs ?: return null
        val start = trip.startedAtMs
        fun clamp(ms: Long) = ms.coerceIn(start, ended)
        val mine = results.filter { it.tripId == trip.id }
        val sendable = mine.filter { serverCode(it.code) in packMissionCodes }
        return TripSyncRequest(
            tripId = trip.id,
            missionPackCode = packCode,
            missionPackVersion = packVersion,
            status = tripStatus(trip.endReason),
            endReason = endReason(trip.endReason),
            startedAt = iso(start),
            endedAt = iso(ended),
            missionResults = sendable.map { r ->
                val s = clamp(r.askedAtMs ?: r.triggeredAtMs)
                val e = clamp(r.answeredAtMs ?: s).coerceAtLeast(s)
                val participated = r.state == MissionState.DONE && !r.interrupted &&
                    r.givenAnswer != null && r.givenAnswer != Answer.NONE
                TripSyncResult(
                    resultId = r.id,
                    missionCode = serverCode(r.code),
                    status = resultStatus(r),
                    participated = participated,
                    correct = if (participated) r.correct else null,
                    startedAt = iso(s),
                    endedAt = iso(e),
                )
            },
        )
    }

    /**
     * 완료(정답·오답·무응답 건너뜀) → COMPLETED, 묻지 못하고 버림 → EXPIRED, 묻는 중 주행 종료 → CANCELLED.
     * 🟡 무응답 건너뜀(SKIPPED)을 COMPLETED + participated=false로 보낸다 (백엔드 확인 필요)
     */
    fun resultStatus(r: MissionResult): String = when {
        r.interrupted -> "CANCELLED"
        r.state == MissionState.EXPIRED -> "EXPIRED"
        else -> "COMPLETED"
    }

    private fun iso(ms: Long): String = Instant.ofEpochMilli(ms).toString()
}

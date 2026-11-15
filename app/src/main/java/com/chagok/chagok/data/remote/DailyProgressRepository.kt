package com.chagok.chagok.data.remote

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * 서버 기준 오늘의 미션 현황.
 * @param countedTrips 조회하기 전에 이미 서버에 올라간 주행. 서버 숫자에 들어 있으니 기기 기록에서 빼고 센다
 */
data class DailyProgress(
    val date: LocalDate,
    val goal: Int,
    val done: Int,
    val countedTrips: Set<String>,
)

/** S05-04 GET /api/v1/missions/daily-progress. 실패하면 마지막 값 그대로 (없으면 null → 기기 기록만) */
class DailyProgressRepository(
    private val api: ChagokApi,
    private val installations: InstallationRepository,
    private val syncedTrips: () -> Set<String>,
) {
    private val mutex = Mutex()
    private val _progress = MutableStateFlow<DailyProgress?>(null)
    val progress: StateFlow<DailyProgress?> = _progress.asStateFlow()

    suspend fun refresh(): Result<DailyProgress> = mutex.withLock {
        installations.ensureRegistered().onFailure { return@withLock Result.failure(it) }
        val counted = syncedTrips()          // 요청 전에 잡아야 서버 숫자와 맞는다
        apiCall { api.dailyProgress() }
            .mapCatching { toProgress(it, counted) }
            .onSuccess { _progress.value = it }
            .onFailure { Log.w(TAG, "오늘의 미션 현황 조회 실패: ${it.message}") }
    }

    companion object {
        private const val TAG = "DailyProgress"

        fun toProgress(r: DailyProgressResponse, counted: Set<String>) = DailyProgress(
            date = LocalDate.parse(r.date),
            goal = r.goal,
            done = r.participatedCount,
            countedTrips = counted,
        )
    }
}

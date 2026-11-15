package com.chagok.chagok.data.repository

import com.chagok.chagok.model.MissionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** 미션 결과 저장 창구. 나중에 Room 구현으로 교체. */
interface MissionResultStore {
    suspend fun save(result: MissionResult)
    val results: Flow<List<MissionResult>>
}

class InMemoryMissionResultStore : MissionResultStore {
    private val _results = MutableStateFlow<List<MissionResult>>(emptyList())
    override val results: Flow<List<MissionResult>> = _results

    override suspend fun save(result: MissionResult) {
        _results.value = _results.value + result
    }
}
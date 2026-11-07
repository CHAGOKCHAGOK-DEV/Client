package com.chagok.chagok.data.repository

import com.chagok.chagok.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 주행 기록 저장 창구. 같은 id면 덮어쓴다. 나중에 Room 구현으로 교체. */
interface TripStore {
    suspend fun save(trip: Trip)
    val trips: Flow<List<Trip>>
}

class InMemoryTripStore : TripStore {
    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    override val trips: StateFlow<List<Trip>> = _trips

    override suspend fun save(trip: Trip) {
        val others = _trips.value.filterNot { it.id == trip.id }
        _trips.value = others + trip
    }
}

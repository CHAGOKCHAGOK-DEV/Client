package com.chagok.chagok.ui.common

import com.chagok.chagok.data.repository.InMemoryMissionResultStore
import com.chagok.chagok.model.*
import com.chagok.chagok.permission.FakePermissionChecker
import com.chagok.chagok.sdk.navigation.FakeNaviGateway
import com.chagok.chagok.sdk.speech.Answer
import com.chagok.chagok.sdk.speech.FakeSpeechPort
import com.chagok.chagok.sdk.vehicle.FakeVehicleGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppStateTest {

    private fun TestScope.newState(
        vehicle: FakeVehicleGateway,
        store: InMemoryMissionResultStore = InMemoryMissionResultStore(),
        speech: FakeSpeechPort = FakeSpeechPort(nextAnswer = Answer.YES),
        navi: com.chagok.chagok.sdk.navigation.NaviGateway = FakeNaviGateway(),
    ) = AppState(
        scope = backgroundScope,
        vehicle = vehicle,
        navi = navi,
        speech = speech,
        store = store,
        permissionChecker = FakePermissionChecker(),
        nowMs = { testScheduler.currentTime },
    )

    @Test
    fun `연결 전에는 출발 버튼이 켜지지 않는다`() = runTest {
        val state = newState(FakeVehicleGateway())
        advanceTimeBy(100)

        state.toggleReady()
        assertFalse(state.ready.value)
    }

    @Test
    fun `출발 후 주행 중 와이퍼를 켜면 미션이 완료되어 저장된다`() = runTest {
        val vehicle = FakeVehicleGateway()
        val store = InMemoryMissionResultStore()
        val state = newState(vehicle, store)

        state.connectVehicle()
        advanceTimeBy(100)
        state.toggleReady()

        vehicle.setParkingBrake(false)
        vehicle.setSpeed(40f)
        advanceTimeBy(1_500)
        assertEquals(DriveState.DRIVING, state.driveState.value)

        vehicle.setWiper(true)
        advanceTimeBy(4_000)

        assertEquals(MissionState.DONE, state.missionState.value)
        assertEquals(MissionCode.WIPER, store.results.first().single().code)
    }

    @Test
    fun `내비 연결이 실패해도 차량 연결은 성공, NEXT_TURN만 꺼진다`() = runTest {
        val noNavi = object : com.chagok.chagok.sdk.navigation.NaviGateway {
            override val naviInfo = kotlinx.coroutines.flow.MutableStateFlow(NaviInfo())
            override suspend fun connect(): Boolean = false
        }
        val state = newState(FakeVehicleGateway(), navi = noNavi)
        val result = state.connectVehicle()

        assertEquals(true, result.isSuccess)
        assertEquals(false, state.capability.value.navi)
        assertEquals(true, state.capability.value.blink)
    }

    @Test
    fun `차량 연결 실패면 capability는 그대로`() = runTest {
        val vehicle = FakeVehicleGateway().apply { failConnect = true }
        val state = newState(vehicle)
        assertEquals(true, state.connectVehicle().isFailure)
        assertEquals(false, state.capability.value.blink)
    }
}

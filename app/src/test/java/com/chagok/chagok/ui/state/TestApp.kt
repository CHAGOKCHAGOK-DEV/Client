package com.chagok.chagok.ui.state

import com.chagok.chagok.data.repository.CopilotProfile
import com.chagok.chagok.data.repository.InMemoryMissionResultStore
import com.chagok.chagok.data.repository.InMemoryOnboardingRepository
import com.chagok.chagok.model.Capability
import com.chagok.chagok.permission.FakePermissionChecker
import com.chagok.chagok.sdk.navigation.FakeNaviGateway
import com.chagok.chagok.sdk.speech.Answer
import com.chagok.chagok.sdk.speech.FakeSpeechPort
import com.chagok.chagok.sdk.vehicle.FakeVehicleGateway
import com.chagok.chagok.ui.common.AppState
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy

/** 화면 상태 테스트용 묶음 */
class TestApp(
    val scope: TestScope,
    val vehicle: FakeVehicleGateway = FakeVehicleGateway(),
    val navi: FakeNaviGateway = FakeNaviGateway(),
    val speech: FakeSpeechPort = FakeSpeechPort(nextAnswer = Answer.YES),
    val store: InMemoryMissionResultStore = InMemoryMissionResultStore(),
    val permission: FakePermissionChecker = FakePermissionChecker(),
    val onboarding: InMemoryOnboardingRepository = InMemoryOnboardingRepository(),
) {
    val app = AppState(
        scope = scope.backgroundScope,
        vehicle = vehicle, navi = navi, speech = speech, store = store,
        permissionChecker = permission,
        nowMs = { scope.testScheduler.currentTime },
    )

    fun onboarded() {
        onboarding.setConsented(true)
        onboarding.setProfile(CopilotProfile("은우", "7~8세"))
    }

    suspend fun connectAndReady() {
        app.connectVehicle()
        scope.advanceTimeBy(100)
        app.toggleReady()
    }

    /** 출발해서 주행 판정(1초)까지 */
    fun drive(speed: Float = 40f) {
        vehicle.setParkingBrake(false)
        vehicle.setSpeed(speed)
        scope.advanceTimeBy(1_500)
    }

    /** 멈춰서 정차 판정(3초)까지 */
    fun stop() {
        vehicle.setSpeed(0f)
        scope.advanceTimeBy(3_100)
    }

    fun park() {
        vehicle.setSpeed(0f)
        vehicle.setParkingBrake(true)
        scope.advanceTimeBy(100)
    }
}

fun noWiper() = Capability(speed = true, blink = true, wiper = false, navi = true)

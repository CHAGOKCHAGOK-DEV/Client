package com.chagok.chagok.sdk

import android.content.Context
import com.chagok.chagok.data.repository.InMemoryMissionResultStore
import com.chagok.chagok.permission.AndroidPermissionChecker
import com.chagok.chagok.sdk.navigation.FakeNaviGateway
import com.chagok.chagok.sdk.pleos.PleosNaviGateway
import com.chagok.chagok.sdk.pleos.PleosSpeechPort
import com.chagok.chagok.sdk.pleos.PleosVehicleGateway
import com.chagok.chagok.sdk.speech.FakeSpeechPort
import com.chagok.chagok.sdk.speech.SpeechPort
import com.chagok.chagok.sdk.vehicle.FakeVehicleGateway
import com.chagok.chagok.model.AgeGroup
import com.chagok.chagok.ui.common.AppState
import kotlinx.coroutines.CoroutineScope

/**
 * 앱에서 AppState를 만드는 곳. Application.onCreate 등에서 한 번만 만든다.
 * useFake = true면 에뮬레이터 없이 Fake로 돈다 (UI 개발용).
 * scope는 Main 디스패처여야 한다 (AppState는 한 스레드 전제).
 */
object AppGraph {
    fun create(
        context: Context,
        scope: CoroutineScope,
        useFake: Boolean = false,
        // F-11·F-12: 실제 TTS/STT. useFake면 로그만 찍는 가짜
        speech: SpeechPort = if (useFake) FakeSpeechPort() else PleosSpeechPort(context),
        ageGroup: () -> AgeGroup? = { null },   // S-04 연령대 (미션 대본 선택)
    ): AppState = AppState(
        scope = scope,
        vehicle = if (useFake) FakeVehicleGateway() else PleosVehicleGateway(context),
        navi = if (useFake) FakeNaviGateway() else PleosNaviGateway(context),
        speech = speech,
        store = InMemoryMissionResultStore(),
        permissionChecker = AndroidPermissionChecker(context),
        nowMs = { System.currentTimeMillis() },
        ageGroup = ageGroup,
    )
}

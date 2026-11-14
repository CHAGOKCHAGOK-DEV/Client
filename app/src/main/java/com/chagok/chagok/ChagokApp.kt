package com.chagok.chagok

import android.app.Application
import com.chagok.chagok.model.AgeGroup
import com.chagok.chagok.data.repository.PrefsOnboardingRepository
import com.chagok.chagok.sdk.AppGraph
import com.chagok.chagok.ui.common.AppState
import com.chagok.chagok.ui.state.DriveRouter
import kotlinx.coroutines.MainScope
import com.chagok.chagok.data.remote.ApiClient
import com.chagok.chagok.data.remote.ChagokApi
import com.chagok.chagok.data.remote.InstallationRepository
import com.chagok.chagok.data.remote.InstallationStore
import com.chagok.chagok.data.remote.AgreementRepository
import com.chagok.chagok.data.remote.CopilotRepository
import com.chagok.chagok.data.remote.MissionPackRepository
import com.chagok.chagok.data.remote.PendingSyncStore
import com.chagok.chagok.data.remote.ServerSync
import com.chagok.chagok.data.remote.NetworkMonitor
import com.chagok.chagok.data.remote.TripSyncRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 앱 전체에서 한 개만 쓰는 객체를 들고 있는 곳 (AppGraph 주석: "Application.onCreate 등에서 한 번만").
 * 화면에서는 (application as ChagokApp).appState 로 꺼내 쓴다.
 */
class ChagokApp : Application() {

    /** AppState는 Main 한 스레드 전제라 MainScope 사용 */
    val appScope = MainScope()

    val onboarding: PrefsOnboardingRepository by lazy { PrefsOnboardingRepository(this) }

    val appState: AppState by lazy {
        AppGraph.create(
            this, appScope, useFake = USE_FAKE,
            // 미션을 묻는 순간의 연령대. S-04에서 바꾸면 다음 미션부터 바뀐다
            ageGroup = { AgeGroup.fromCode(onboarding.profile.value?.ageGroup) },
        ).also { uploadEndedTrips(it) }
    }

    /**
     * 주행이 끝나면 서버에 올린다 (POST /api/v1/trips/sync).
     * 묻던 미션이 끝까지 저장되도록 잠깐 기다린 뒤 보낸다. 실패분은 인터넷이 돌아오면 다시 보낸다.
     */
    private fun uploadEndedTrips(app: AppState) {
        appScope.launch {
            app.lastEndedTrip.filterNotNull().distinctUntilChangedBy { it.id }.collect { trip ->
                delay(TRIP_UPLOAD_DELAY_MS)
                tripSync.enqueue(trip, app.missionStore.results.first())
            }
        }
        appScope.launch {
            network.online.drop(1).filter { it }.collect { tripSync.flush() }
        }
    }

    /** S-05 / S-06 / S-07 자동 전환. 화면이 바뀌어도 [메인으로] 기록이 남도록 앱에 하나만 둔다 */
    val driveRouter: DriveRouter by lazy { DriveRouter(appScope, appState) }

    // ---------- 서버 ----------
    val installationStore: InstallationStore by lazy { InstallationStore(this) }
    val api: ChagokApi by lazy { ApiClient.create(this, installationStore) }
    val installations: InstallationRepository by lazy { InstallationRepository(api, installationStore) }
    val pendingSync: PendingSyncStore by lazy { PendingSyncStore(this) }
    val missionPacks: MissionPackRepository by lazy { MissionPackRepository(this, api) }
    val agreements: AgreementRepository by lazy { AgreementRepository(api, installations, pendingSync, onboarding) }
    val copilots: CopilotRepository by lazy { CopilotRepository(api, installations, pendingSync, onboarding) }
    val network: NetworkMonitor by lazy { NetworkMonitor(this, appScope) }
    val tripSync: TripSyncRepository by lazy { TripSyncRepository(this, api, installations, missionPacks) }
    val serverSync: ServerSync by lazy { ServerSync(appScope, installations, missionPacks, agreements, copilots, tripSync) }

    companion object {
        /** true면 Pleos SDK 없이 가짜 차량으로 돈다 (UI 확인용). 실차·에뮬레이터 테스트는 false */
        const val USE_FAKE = false

        /** 주행 종료 뒤 업로드까지 기다리는 시간 (묻던 미션 결과가 저장될 시간) */
        private const val TRIP_UPLOAD_DELAY_MS = 8_000L
    }
}

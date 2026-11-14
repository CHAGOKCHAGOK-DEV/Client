package com.chagok.chagok.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * S-01 3/3 단계의 서버 작업. 하나가 실패해도 나머지는 계속하고, 전부 실패해도 앱은 진행한다.
 * - 기다림: 설치 등록, 미션팩 (다음 화면에서 필요할 수 있음)
 * - 기다리지 않음: 전에 실패한 동의·프로필 재전송 (뒤에서 조용히)
 */
class ServerSync(
    private val appScope: CoroutineScope,
    private val installations: InstallationRepository,
    private val missionPacks: MissionPackRepository,
    private val agreements: AgreementRepository,
    private val copilots: CopilotRepository,
    private val tripSync: TripSyncRepository,
) {
    suspend fun onStartup() {
        installations.ensureRegistered().onFailure { Log.w(TAG, "설치 등록 실패", it) }
        missionPacks.sync().onFailure { Log.w(TAG, "미션팩 조회 실패", it) }
        // 다음 화면을 정하기 전에 확인해야 하므로 기다린다 (동의 재전송도 여기서 함께)
        agreements.verifyWithServer().onFailure { Log.w(TAG, "동의 상태 확인 실패", it) }

        appScope.launch {
            copilots.retryPending().onFailure { Log.w(TAG, "프로필 재전송 실패", it) }
            tripSync.flush()       // 전에 못 보낸 주행 기록
        }
    }

    private companion object {
        const val TAG = "ServerSync"
    }
}
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
) {
    suspend fun onStartup() {
        installations.ensureRegistered().onFailure { Log.w(TAG, "설치 등록 실패", it) }
        missionPacks.sync().onFailure { Log.w(TAG, "미션팩 조회 실패", it) }

        appScope.launch {
            agreements.retryPending().onFailure { Log.w(TAG, "동의 재전송 실패", it) }
            copilots.retryPending().onFailure { Log.w(TAG, "프로필 재전송 실패", it) }
        }
    }

    private companion object {
        const val TAG = "ServerSync"
    }
}
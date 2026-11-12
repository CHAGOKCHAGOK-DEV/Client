package com.chagok.chagok.data.remote

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 설치 등록. 다른 API를 부르기 전에 ensureRegistered()로 토큰을 확보한다.
 * 여러 곳에서 동시에 불러도 등록은 한 번만 일어난다.
 */
class InstallationRepository(
    private val api: ChagokApi,
    private val store: InstallationStore,
) {
    private val mutex = Mutex()

    val isRegistered: Boolean get() = store.isRegistered

    suspend fun ensureRegistered(): Result<Unit> = mutex.withLock {
        if (store.isRegistered) return@withLock Result.success(Unit)
        apiCall { api.registerInstallation() }.map { store.save(it.installationId, it.token) }
    }
}
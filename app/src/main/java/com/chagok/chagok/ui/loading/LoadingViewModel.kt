package com.chagok.chagok.ui.loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** 로딩이 끝난 뒤 갈 화면 */
enum class Destination { PERMISSION, CONSENT, PROFILE, MAIN }

sealed interface LoadingUiState {
    /** step: 지금 하는 단계(1~3), completed: 끝난 단계 수(0~3) */
    data class Loading(val step: Int, val completed: Int) : LoadingUiState
    data class Done(val next: Destination) : LoadingUiState
    data class Failed(val reason: FailReason) : LoadingUiState
}

class LoadingViewModel(
    // TODO: Pleos SDK 연결되면 실제 구현으로 교체
    private val repo: BootRepository = FakeBootRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<LoadingUiState>(LoadingUiState.Loading(1, 0))
    val state: StateFlow<LoadingUiState> = _state.asStateFlow()

    var retryCount = 0
        private set

    private var job: Job? = null

    init { start() }

    /** [다시 시도]. 진행 중이면 무시(연타 방지) */
    fun retry() {
        if (job?.isActive == true) return
        retryCount++
        start()
    }

    private fun start() {
        job = viewModelScope.launch {
            try {
                _state.value = LoadingUiState.Loading(step = 1, completed = 0)
                runStep(FailReason.CAR_CONN) { repo.connectCar() }

                _state.value = LoadingUiState.Loading(step = 2, completed = 1)
                val allGranted = runStep(FailReason.PERM_READ) { repo.readPermissions() }

                _state.value = LoadingUiState.Loading(step = 3, completed = 2)
                val settings = runStep(FailReason.STORE_READ) { repo.loadSettings() }

                _state.value = LoadingUiState.Done(decide(allGranted, settings))
            } catch (e: BootException) {
                _state.value = LoadingUiState.Failed(e.reason)
            }
        }
    }

    /** 설계서 순서: 권한 → 동의 → 프로필 → 메인 (TODO: 주행 중이면 S-06) */
    private fun decide(allGranted: Boolean, s: BootSettings): Destination = when {
        !allGranted -> Destination.PERMISSION
        !s.consentDone -> Destination.CONSENT
        !s.profileExists -> Destination.PROFILE
        else -> Destination.MAIN
    }

    /** 한 단계 실행. 10초 넘거나 예외가 나면 그 단계의 실패 사유로 바꿔 던진다 */
    private suspend fun <T> runStep(reason: FailReason, block: suspend () -> T): T =
        try {
            withTimeout(STEP_TIMEOUT_MS) { block() }
        } catch (e: BootException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            throw BootException(reason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw BootException(reason)
        }

    companion object {
        private const val STEP_TIMEOUT_MS = 10_000L // 설계서: 한 단계 제한 10초(임시)
    }
}
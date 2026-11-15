package com.chagok.chagok.permission

import com.chagok.chagok.model.PermissionKind
import com.chagok.chagok.model.PermissionState
import com.chagok.chagok.model.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * F-01-1 권한 확인·요청 창구. 실제 구현은 Activity가 필요하므로 UI 단계에서 만든다.
 * 앱의 나머지는 이 인터페이스만 본다.
 */
interface PermissionChecker {
    val state: StateFlow<PermissionState>

    /** 3종 권한 상태를 다시 읽는다 (S-01 2/3 단계, S-02 [다시 확인]) */
    suspend fun refresh()

    /** 한 종류 권한을 요청한다 (S-02 행 탭 → C-01). 결과는 state로 반영 */
    suspend fun request(kind: PermissionKind)
}

/** 개발용 가짜. 요청하면 grantOnRequest 값대로 결과가 나온다. */
class FakePermissionChecker(
    initial: PermissionState = PermissionState(
        vehicle = PermissionStatus.NEEDED,
        mic = PermissionStatus.NEEDED,
        voice = PermissionStatus.NEEDED,
    ),
    var grantOnRequest: Boolean = true,
) : PermissionChecker {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<PermissionState> = _state.asStateFlow()

    override suspend fun refresh() { /* 가짜는 현재 상태 유지 */ }

    override suspend fun request(kind: PermissionKind) {
        val result = if (grantOnRequest) PermissionStatus.GRANTED else PermissionStatus.DENIED
        _state.value = _state.value.with(kind, result)
    }
}
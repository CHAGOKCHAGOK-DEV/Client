package com.chagok.chagok.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.chagok.chagok.model.PermissionKind
import com.chagok.chagok.model.PermissionState
import com.chagok.chagok.model.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * F-01-1 실제 권한 확인·요청.
 *
 * - refresh(): 지금 권한 상태 읽기. 화면 없이 동작 (S-01 2/3 단계, 설정 앱에서 돌아왔을 때)
 * - request(): 시스템 권한 창(C-01)을 띄워야 해서 Activity가 필요하다.
 *   S-02 화면이 ActivityResult 런처로 [requester]를 채워 주면 동작한다.
 *   예) checker.requester = { perms -> launcher.launchAndAwait(perms) }
 *
 * pleos.car.* 권한은 protectionLevel normal이라 설치 시 자동 허용,
 * 마이크(RECORD_AUDIO)와 속도(android.car.permission.CAR_SPEED)는 런타임 요청 대상이다.
 */
class AndroidPermissionChecker(context: Context) : PermissionChecker {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(PermissionState())
    override val state: StateFlow<PermissionState> = _state.asStateFlow()

    /** 시스템 권한 창을 띄우고 결과(권한별 허용 여부)를 돌려주는 함수. UI(S-02)가 채운다 */
    var requester: (suspend (Array<String>) -> Map<String, Boolean>)? = null

    override suspend fun refresh() {
        var s = _state.value
        for (kind in PermissionKind.entries) {
            val granted = permissionsOf(kind).all { isGranted(it) }
            val prev = s.statusOf(kind)
            val next = when {
                granted -> PermissionStatus.GRANTED
                prev == PermissionStatus.DENIED -> PermissionStatus.DENIED   // 거부 기록 유지
                else -> PermissionStatus.NEEDED
            }
            s = s.with(kind, next)
        }
        _state.value = s
    }

    override suspend fun request(kind: PermissionKind) {
        val missing = permissionsOf(kind).filterNot { isGranted(it) }
        if (missing.isEmpty()) {
            _state.value = _state.value.with(kind, PermissionStatus.GRANTED)
            return
        }
        val ask = requester ?: return          // 화면이 아직 연결 안 됨
        _state.value = _state.value.with(kind, PermissionStatus.CHECKING)
        val result = ask(missing.toTypedArray())
        val ok = missing.all { result[it] == true || isGranted(it) }
        _state.value = _state.value.with(kind, if (ok) PermissionStatus.GRANTED else PermissionStatus.DENIED)
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** S-02 행별 권한. 매니페스트 선언과 맞춰야 한다 */
        fun permissionsOf(kind: PermissionKind): List<String> = when (kind) {
            PermissionKind.VEHICLE -> listOf(
                "pleos.car.permission.CAR_LIGHTS",    // 방향지시등
                "pleos.car.permission.CAR_WINDOWS",   // 와이퍼
                "pleos.car.permission.CAR_DRIVE",
                "android.car.permission.CAR_SPEED",   // 속도 (런타임)
            )
            PermissionKind.MIC -> listOf(Manifest.permission.RECORD_AUDIO)
            PermissionKind.VOICE -> listOf(
                "pleos.car.permission.STT_SERVICE",
                "pleos.car.permission.TTS_SERVICE",
            )
        }
    }
}

private fun PermissionState.statusOf(kind: PermissionKind): PermissionStatus = when (kind) {
    PermissionKind.VEHICLE -> vehicle
    PermissionKind.MIC -> mic
    PermissionKind.VOICE -> voice
}

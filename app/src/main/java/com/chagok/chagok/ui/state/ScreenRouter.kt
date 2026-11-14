package com.chagok.chagok.ui.state

import com.chagok.chagok.model.DriveState
import com.chagok.chagok.model.PermissionState

/**
 * 시작·온보딩이 끝난 뒤 어느 화면으로 갈지 (1. 전체 플로우).
 * S-01 3/3 단계와 S-02 "모두 완료" 뒤에 같이 쓴다.
 */
object ScreenRouter {
    fun afterOnboarding(
        permission: PermissionState,
        consented: Boolean,
        hasProfile: Boolean,
        drive: DriveState,
    ): Screen = when {
        !permission.allGranted -> Screen.PERMISSION
        !consented -> Screen.CONSENT
        !hasProfile -> Screen.PROFILE
        drive == DriveState.DRIVING -> Screen.DRIVING
        else -> Screen.MAIN
    }
}

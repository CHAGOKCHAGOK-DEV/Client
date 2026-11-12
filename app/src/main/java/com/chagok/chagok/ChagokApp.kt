package com.chagok.chagok

import android.app.Application
import com.chagok.chagok.data.repository.PrefsOnboardingRepository
import com.chagok.chagok.sdk.AppGraph
import com.chagok.chagok.ui.common.AppState
import kotlinx.coroutines.MainScope

/**
 * 앱 전체에서 한 개만 쓰는 객체를 들고 있는 곳 (AppGraph 주석: "Application.onCreate 등에서 한 번만").
 * 화면에서는 (application as ChagokApp).appState 로 꺼내 쓴다.
 */
class ChagokApp : Application() {

    /** AppState는 Main 한 스레드 전제라 MainScope 사용 */
    val appScope = MainScope()

    val onboarding: PrefsOnboardingRepository by lazy { PrefsOnboardingRepository(this) }

    val appState: AppState by lazy { AppGraph.create(this, appScope, useFake = USE_FAKE) }

    companion object {
        /** true면 Pleos SDK 없이 가짜 차량으로 돈다 (UI 확인용). 실차·에뮬레이터 테스트는 false */
        const val USE_FAKE = false
    }
}

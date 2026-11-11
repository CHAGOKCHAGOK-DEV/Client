package com.chagok.chagok.ui.landing

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chagok.chagok.MainActivity
import com.chagok.chagok.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.view.doOnPreDraw

/** S01_1 랜딩: 민트 배경 + 로고를 1초 보여준 뒤 다음 화면으로 이동 */
class LandingActivity : AppCompatActivity() {

    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // super.onCreate 전에 호출
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)

        window.decorView.doOnPreDraw {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    delay(SPLASH_DURATION_MS)
                    goNext()
                }
            }
        }
    }

    private fun goNext() {
        if (navigated) return
        navigated = true
        // TODO: S01_2 로딩 화면이 생기면 그 Activity로 교체
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(0, 0)
        finish()
    }

    companion object {
        private const val SPLASH_DURATION_MS = 1_000L
    }
}
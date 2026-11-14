package com.chagok.chagok.ui.driving

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chagok.chagok.ChagokApp
import com.chagok.chagok.R
import com.chagok.chagok.ui.common.intentFor
import com.chagok.chagok.ui.state.Screen
import kotlinx.coroutines.launch

/**
 * S-06 주행 중 (피그마 S06 a~g, S06F 미션 화면).
 * - 상태 아이콘 타일 + 큰 제목 한 줄 + 부제 + 모드 칩. 미션 중엔 위에 미션 이름 칩
 * - 정답이면 파티클(정지 이미지), 틀림은 효과 없이
 * - 화면을 만지면 "정차 후에 눌러요" 음성 1회. 정차(f)일 때만 [정차 완료]
 * - 주행이 끝나거나 멈추면 DriveRouter를 따라 S-05 / S-07로
 * 그리기는 DrivingView가 한다.
 */
class DrivingActivity : AppCompatActivity() {

    private val viewModel: DrivingViewModel by viewModels()
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driving)

        val view = DrivingView(this)
        view.btnStopDone.setOnClickListener { viewModel.onStopDone() }
        view.root.setOnClickListener { viewModel.onTouch() }
        onBackPressedDispatcher.addCallback(this) { viewModel.onTouch() }   // 주행 화면에선 뒤로가기도 막는다

        val router = (application as ChagokApp).driveRouter
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { view.render(it) } }
                launch {
                    router.route.collect { route ->
                        when (route.screen) {
                            Screen.DRIVING -> Unit
                            // TODO(S-07): SUMMARY는 S-07 화면이 생기면 intentFor가 그쪽으로 보낸다
                            else -> navigate(route.screen)
                        }
                    }
                }
            }
        }
    }

    private fun navigate(next: Screen) {
        if (navigated) return
        navigated = true
        startActivity(intentFor(next))
        overridePendingTransition(0, 0)
        finish()
    }
}

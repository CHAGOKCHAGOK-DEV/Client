package com.chagok.chagok.ui.state

import com.chagok.chagok.data.repository.OnboardingRepository
import com.chagok.chagok.domain.driving.VehicleHealth
import com.chagok.chagok.model.DriveState
import com.chagok.chagok.model.MissionState
import com.chagok.chagok.ui.common.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** S-06 일곱 가지 표시 (와이어프레임 a~g). 아이콘만 바꾸고 움직임은 없다 */
enum class DrivingDisplay {
    WAITING,      // a 대기
    SPEAKING,     // b 말하는 중 (TTS)
    LISTENING,    // c 듣는 중 (STT)
    RESULT,       // d 결과 2초
    MODE_OFF,     // e 부기장 모드 꺼짐
    STOPPED,      // f 정차 중 "잠깐 멈췄어요" + [정차 완료]
    SIGNAL_LOST,  // g 차량 신호 확인 중 (연결 끊김·속도 미수신)
}

/** S-06이 그릴 값 */
data class DrivingUiState(
    val display: DrivingDisplay,
    val headline: String,          // S06-02 한 줄 (Headline 크기)
    val sub: String?,              // e·g 상태의 보조 문구
    val modeChip: String,          // S06-03 "은우 부기장 모드 켜짐"
    val stopDoneVisible: Boolean,  // S06-04 [정차 완료]. 주행(trip) 중 정차했을 때만
)

/**
 * S-06 주행 중 (F-02, F-10, F-11, F-12, F-15). 주행 중엔 버튼·입력 없음.
 * 미션 질문·정답은 화면에 쓰지 않는다. 지금 앱이 무엇을 하는지만 알린다.
 * 모드 ON 여부 = 진행 중인 주행(trip)이 있는가 (출발 버튼을 누르고 출발한 경우).
 *
 * 표시 우선순위: 모드 꺼짐(e) > 신호 확인 중(g) > 진행 중인 미션(b·c·d) > 정차 중(f) > 대기(a)
 * - g: 신호가 돌아오면 저절로 a(또는 f)로 돌아간다
 * - f: 정차 3초 판정 뒤. 다시 출발하면 a로. [정차 완료]를 누르면 주행 종료 → S-07
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DrivingScreen(
    private val scope: CoroutineScope,
    private val app: AppState,
    onboarding: OnboardingRepository,
    private val speak: suspend (String) -> Unit,
    private val resultHoldMs: Long = 2_000,
) {
    /** 미션 상태 → a~d. 결과(d)는 2초 보여 준 뒤 a로 */
    private val missionDisplay: Flow<DrivingDisplay> = app.missionState
        .transformLatest { s ->
            when (s) {
                MissionState.ASKING -> emit(DrivingDisplay.SPEAKING)
                MissionState.LISTENING -> emit(DrivingDisplay.LISTENING)
                MissionState.JUDGING -> emit(DrivingDisplay.RESULT)      // 결과 멘트 재생 중
                MissionState.DONE -> {
                    emit(DrivingDisplay.RESULT)
                    delay(resultHoldMs)
                    emit(DrivingDisplay.WAITING)
                }
                else -> emit(DrivingDisplay.WAITING)                    // 후보·보류·건너뜀·만료·없음
            }
        }
        .distinctUntilChanged()

    val state: StateFlow<DrivingUiState> = combine(
        missionDisplay, app.currentTrip, onboarding.profile, app.driveState, app.vehicleHealth,
    ) { mission, trip, profile, drive, health ->
        val modeOn = trip != null
        val stopped = drive == DriveState.STOPPED
        val name = profile?.nickname?.let { "$it 부기장" } ?: "부기장"
        val display = when {
            !modeOn -> DrivingDisplay.MODE_OFF
            health != VehicleHealth.OK -> DrivingDisplay.SIGNAL_LOST
            mission != DrivingDisplay.WAITING -> mission          // 하던 미션은 정차해도 끝까지 보여 준다
            stopped -> DrivingDisplay.STOPPED
            else -> DrivingDisplay.WAITING
        }
        DrivingUiState(
            display = display,
            headline = when (display) {
                DrivingDisplay.WAITING, DrivingDisplay.MODE_OFF -> ScreenTexts.DRIVE_WAITING
                DrivingDisplay.SPEAKING -> ScreenTexts.DRIVE_SPEAKING
                DrivingDisplay.LISTENING -> ScreenTexts.DRIVE_LISTENING
                DrivingDisplay.RESULT -> ScreenTexts.DRIVE_RESULT
                DrivingDisplay.STOPPED -> ScreenTexts.DRIVE_STOPPED
                DrivingDisplay.SIGNAL_LOST -> ScreenTexts.DRIVE_SIGNAL
            },
            sub = when (display) {
                DrivingDisplay.MODE_OFF -> ScreenTexts.DRIVE_MODE_OFF_SUB
                DrivingDisplay.SIGNAL_LOST -> ScreenTexts.DRIVE_SIGNAL_SUB
                else -> null
            },
            modeChip = if (modeOn) "$name 모드 켜짐" else "$name 모드 꺼짐",
            stopDoneVisible = modeOn && stopped,
        )
    }.stateIn(
        scope, SharingStarted.Eagerly,
        DrivingUiState(
            DrivingDisplay.MODE_OFF, ScreenTexts.DRIVE_WAITING, ScreenTexts.DRIVE_MODE_OFF_SUB,
            "부기장 모드 꺼짐", stopDoneVisible = false,
        ),
    )

    private var touchHintSpoken = false

    init {
        // 다음 주행에서 다시 안내할 수 있게, 주행이 바뀌면 초기화
        scope.launch { app.currentTrip.map { it?.id }.distinctUntilChanged().collect { touchHintSpoken = false } }
    }

    /** S06-04 [정차 완료]: 주행 종료(USER_STOP) → DriveRouter가 S-07로 보낸다 */
    fun onStopDone() {
        if (!state.value.stopDoneVisible) return
        app.finishTrip()
    }

    /** 화면 터치: 동작 없음. "정차 후에 눌러요" 음성은 주행당 1회만 */
    fun onTouch() {
        if (touchHintSpoken) return
        touchHintSpoken = true
        scope.launch { speak(ScreenTexts.DRIVE_TOUCH_TTS) }
    }
}

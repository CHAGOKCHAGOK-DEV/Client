package com.chagok.chagok.ui.state

import com.chagok.chagok.data.repository.CopilotProfile
import com.chagok.chagok.data.repository.OnboardingRepository
import com.chagok.chagok.model.AgeGroup
import com.chagok.chagok.ui.common.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** S-04가 그릴 값 */
data class ProfileUiState(
    val nickname: String,
    val nicknameError: String?,     // S04-01 오류 문구
    val age: AgeGroup?,
    val bubble: String,             // S04-03
    val saveEnabled: Boolean,       // S04-04
    val editMode: Boolean,
    val locked: Boolean,            // C-02
)

/**
 * S-04 부기장 설정. 첫 실행 입력과 메인에서 수정이 같은 화면.
 * - 별명: 1~8자, 한글·영문·숫자만. 입력 즉시 오류 문구·말풍선 갱신
 * - 연령대: 1개 필수, 기본 선택 없음
 * - [시작]/[저장]: 별명 유효 + 연령대 선택 시에만. 첫 저장은 인사 TTS 1회
 * - [취소] (수정 모드): 입력 버리고 S-05
 * 주행 중엔 저장 무시.
 *
 * @param saveProfile 로컬 저장
 * @param say 화면이 넘어가도 끝까지 말하는 TTS
 * @param onProfileSaved 서버 저장. 첫 저장은 POST, 이후 PUT. 실패하면 다음 시작 때 재시도
 */
class ProfileScreen(
    private val scope: CoroutineScope,
    private val app: AppState,
    private val onboarding: OnboardingRepository,
    private val saveProfile: (CopilotProfile) -> Unit,
    private val say: (String) -> Unit,
    private val editMode: Boolean = false,
    private val onProfileSaved: () -> Unit = {},
) {
    private val saved = onboarding.profile.value.takeIf { editMode }
    private val nickname = MutableStateFlow(saved?.nickname ?: "")
    private val age = MutableStateFlow(AgeGroup.fromCode(saved?.ageGroup))
    private var submitted = false

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val state: StateFlow<ProfileUiState> =
        combine(nickname, age, app.uiLocked) { n, a, locked -> build(n, a, locked) }
            .stateIn(scope, SharingStarted.Eagerly, build(nickname.value, age.value, app.uiLocked.value))

    fun onNicknameChanged(text: String) {
        nickname.value = text.trim()
    }

    fun selectAge(a: AgeGroup) {
        if (app.uiLocked.value) return
        age.value = a
    }

    /** [시작] / [저장] */
    fun save() {
        val s = state.value
        val a = s.age
        if (s.locked || !s.saveEnabled || a == null || submitted) return
        submitted = true

        saveProfile(CopilotProfile(nickname = s.nickname, ageGroup = a.code))
        onProfileSaved()
        if (!editMode) say(ScreenTexts.profileGreeting(s.nickname))

        scope.launch { _events.emit(UiEvent.Navigate(nextScreen())) }
    }

    /** [취소] (수정 모드): 입력 버림 */
    fun cancel() {
        scope.launch { _events.emit(UiEvent.Navigate(nextScreen())) }
    }

    private fun nextScreen(): Screen = ScreenRouter.afterOnboarding(
        permission = app.permission.value,
        consented = onboarding.consented.value,
        hasProfile = onboarding.profile.value != null,
        drive = app.driveState.value,
    )

    private fun build(n: String, a: AgeGroup?, locked: Boolean): ProfileUiState {
        val valid = VALID.matches(n)
        return ProfileUiState(
            nickname = n,
            nicknameError = if (n.isNotEmpty() && !ALLOWED.matches(n)) ScreenTexts.PROFILE_ERROR_CHARS else null,
            age = a,
            bubble = if (valid) ScreenTexts.profileGreeting(n) else ScreenTexts.PROFILE_BUBBLE_DEFAULT,
            saveEnabled = valid && a != null,
            editMode = editMode,
            locked = locked,
        )
    }

    companion object {
        /** 입력 중 허용 (한글 조합 중인 자음·모음 포함). 이 밖의 문자면 오류 문구 */
        private val ALLOWED = Regex("[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]*")
        /** 저장 가능: 완성된 한글·영문·숫자 1~8자 */
        private val VALID = Regex("[가-힣a-zA-Z0-9]{1,8}")
    }
}
package com.chagok.chagok.domain.mission

import com.chagok.chagok.model.AgeGroup
import com.chagok.chagok.model.MissionCandidate
import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.model.NaviInfo
import com.chagok.chagok.model.TurnDir
import com.chagok.chagok.model.TurnSignal
import com.chagok.chagok.sdk.speech.Answer
import com.chagok.chagok.sdk.speech.AnswerKind
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 연령대 하나의 대본 (피그마 「s06 완료완료」 미션 설명 F-01~F-13).
 * 질문은 화면에 띄우지 않고 소리로만 전한다. 멘트의 {key}는 fill()로 값을 넣는다.
 */
data class AgeScript(
    val question: String,
    val answerKind: AnswerKind,
    val correctMessage: String,
    val wrongMessage: String,      // 오답 멘트 (무응답이어도 완료로 치는 미션은 무응답 멘트)
)

/** 언제 물을 수 있는 미션인가 */
enum class AskWhen {
    DRIVING,    // 달리는 중에만 (대부분)
    STOPPED,    // 주행 중 정차했을 때만. 다시 출발하면 버린다 (F-07, F-09, F-10)
}

/** 미션 한 종류의 연령대별 대본과 규칙 */
data class MissionDefinition(
    val code: MissionCode,
    val name: String,                   // 화면·발표에 쓰는 미션 이름
    val scripts: Map<AgeGroup, AgeScript>,
    val validMs: Long?,                 // 후보 유효 시간. null이면 상태로 폐기 (내비, 정차)
    val minIntervalMs: Long? = null,    // 같은 미션 사이 최소 간격 (F-06 15분). null이면 공통 3분만
    val askWhen: AskWhen = AskWhen.DRIVING,
    val noAnswerCompletes: Boolean = false,   // 무응답이어도 완료(DONE)로 친다 (F-08, F-12)
) {
    /** 연령대 대본. 연령대를 모르면 7~8세 */
    fun script(age: AgeGroup?): AgeScript = scripts[age ?: DEFAULT_AGE] ?: scripts.getValue(DEFAULT_AGE)

    companion object {
        val DEFAULT_AGE = AgeGroup.AGE_7_8
    }
}

/** 세 연령대 대본을 한 번에 */
private fun scripts(a56: AgeScript, a78: AgeScript, a9: AgeScript) =
    mapOf(AgeGroup.AGE_5_6 to a56, AgeGroup.AGE_7_8 to a78, AgeGroup.AGE_9P to a9)

object MissionDefinitions {

    const val BLINK_LOOKBACK_MS = 30_000L   // 직전 30초 안에 깜빡이 기록이 있으면 정답 YES

    // ---------------- F-01 깜빡이 소리 ----------------
    // 5~8세 예/아니오, 9세+ 실제 깜빡이 방향
    private const val BLINK_OK = "정확해요! 기장님이 깜빡이를 켰어요."
    val BLINK = MissionDefinition(
        code = MissionCode.BLINK,
        name = "깜빡이 소리",
        scripts = scripts(
            AgeScript("똑딱똑딱! 이 소리 들렸어요?", AnswerKind.YES_NO, BLINK_OK,
                "앗, 기장님이 켰어요. 다음엔 더 잘 들어봐요!"),
            AgeScript("방금 똑딱 소리가 났어요. 기장님이 깜빡이를 켰나요?", AnswerKind.YES_NO, BLINK_OK,
                "앗, 기장님이 켰어요. 다음엔 더 잘 들어봐요!"),
            AgeScript("방금 깜빡이는 왼쪽이었을까, 오른쪽이었을까?", AnswerKind.LEFT_RIGHT, BLINK_OK,
                "정답은 {side}이었어요!"),
        ),
        validMs = 30_000L,
    )

    // ---------------- F-02 깜빡이 배우기 ----------------
    // 하루 첫 깜빡이 미션이 끝난 뒤. 5~6세 아무 말 / 7~8세 뒤차 / 9세+ 돌기 전. 오답 전용 멘트 없음
    private const val LEARN_INFO = "깜빡이는 돌기 전에 켜서 뒤차한테 알려주는 불이에요!"
    val BLINK_LEARN = MissionDefinition(
        code = MissionCode.BLINK_LEARN,
        name = "깜빡이 배우기",
        scripts = scripts(
            AgeScript("깜빡이는 차가 어디로 갈지 알려주는 불이에요. 똑딱 소리 또 찾아볼까요?",
                AnswerKind.FREE, "맞아요! $LEARN_INFO", LEARN_INFO),
            AgeScript("깜빡이는 누구한테 알려주는 불일까? 뒤차일까, 하늘일까?",
                AnswerKind.BACKCAR_SKY, "맞아요! $LEARN_INFO", LEARN_INFO),
            AgeScript("깜빡이는 언제 켜야 할까? 돌기 전일까, 돈 다음일까?",
                AnswerKind.BEFORE_AFTER, "맞아요! $LEARN_INFO", LEARN_INFO),
        ),
        validMs = 10 * 60_000L,
    )

    // ---------------- F-03 비 오는 날 ----------------
    // 5~8세 예/아니오, 9세+ 와이퍼/라이트. 오답 전용 멘트 없음
    private const val WIPER_MSG = "맞아요! 비 오는 날엔 천천히 가요."
    val WIPER = MissionDefinition(
        code = MissionCode.WIPER,
        name = "비 오는 날",
        scripts = scripts(
            AgeScript("앞유리에서 와이퍼가 왔다 갔다 해요?", AnswerKind.YES_NO, WIPER_MSG, WIPER_MSG),
            AgeScript("비가 오나 봐요! 와이퍼가 켜졌나요?", AnswerKind.YES_NO, WIPER_MSG, WIPER_MSG),
            AgeScript("비가 와요! 기장님이 무엇을 켰을까? 와이퍼일까, 라이트일까?", AnswerKind.WIPER_LIGHT,
                WIPER_MSG, WIPER_MSG),
        ),
        validMs = 30_000L,
    )

    // ---------------- F-04 비가 그쳤어요 ----------------
    // 와이퍼 1분 이상 켜진 뒤 꺼질 때. 5~6세 예 / 7~8세 아무 말 / 9세+ 그쳤어. 오답 전용 멘트 없음
    const val RAIN_STOP_MIN_WIPER_MS = 60_000L
    private const val RAIN_INFO = "비가 그치면 와이퍼도 쉬어요!"
    val RAIN_STOP = MissionDefinition(
        code = MissionCode.RAIN_STOP,
        name = "비가 그쳤어요",
        scripts = scripts(
            AgeScript("와이퍼가 멈췄어요! 와이퍼가 쉬고 있나요?", AnswerKind.YES_NO, "맞아요! $RAIN_INFO", RAIN_INFO),
            AgeScript("와이퍼가 멈췄어요. 창밖이 맑아 보여요?", AnswerKind.FREE, "맞아요! $RAIN_INFO", RAIN_INFO),
            AgeScript("기장님이 와이퍼를 껐어요. 비가 그쳤을까, 더 많이 올까?", AnswerKind.STOPPED_POURING,
                "맞아요! $RAIN_INFO", RAIN_INFO),
        ),
        validMs = 30_000L,
    )

    // ---------------- F-05 다음 회전 ----------------
    // 모든 연령 왼/오 (정답 = 내비 방향)
    private const val TURN_OK = "맞혔어요! 곧 만나요."
    private const val TURN_NG = "곧 알 수 있어요, 같이 봐요!"
    val NEXT_TURN = MissionDefinition(
        code = MissionCode.NEXT_TURN,
        name = "다음 회전",
        scripts = scripts(
            AgeScript("곧 차가 돌아요! 숟가락 드는 손이 오른쪽이에요. 왼쪽일까, 오른쪽일까?",
                AnswerKind.LEFT_RIGHT, TURN_OK, TURN_NG),
            AgeScript("곧 차가 돌아요! 왼쪽일까, 오른쪽일까?", AnswerKind.LEFT_RIGHT, TURN_OK, TURN_NG),
            AgeScript("곧 길에서 돌아요. 기장님이 어느 쪽 깜빡이를 켤까?", AnswerKind.LEFT_RIGHT, TURN_OK, TURN_NG),
        ),
        validMs = null,
    )

    // ---------------- F-06 빠를까 느릴까 ----------------
    // 직진 중 속도 30초 일정, 미션 간격 15분. 5~6세 60km↑ 빨리 / 7~8세 60km 기준 / 9세+ 50km 기준
    const val SPEED_STEADY_MS = 30_000L
    const val SPEED_STEADY_RANGE_KMH = 5f        // 🟡 "일정" = 30초 동안 최고·최저 차이 5km/h 이내
    const val SPEED_GUESS_MIN_KMH = 20f          // 🟡 너무 느리면(막히는 길) 묻지 않는다
    private const val SPEED_INFO = "지금은 시속 약 {speed}km예요!"
    val SPEED_GUESS = MissionDefinition(
        code = MissionCode.SPEED_GUESS,
        name = "빠를까 느릴까",
        scripts = scripts(
            AgeScript("지금 차가 빨리 갈까, 천천히 갈까?", AnswerKind.FAST_SLOW, "맞아요! $SPEED_INFO", SPEED_INFO),
            AgeScript("창밖 나무가 휘휘 지나가요? 우리 차는 빠를까, 느릴까?", AnswerKind.FAST_SLOW,
                "맞아요! $SPEED_INFO", SPEED_INFO),
            AgeScript("지금 속도가 50km보다 빠를까, 느릴까?", AnswerKind.FAST_SLOW, "맞아요! $SPEED_INFO", SPEED_INFO),
        ),
        validMs = 30_000L,
        minIntervalMs = 15 * 60_000L,
    )

    /** F-06 빠름 기준 속도 */
    fun speedThreshold(age: AgeGroup?): Float = if (age == AgeGroup.AGE_9P) 50f else 60f

    // ---------------- F-07 신호 대기 ----------------
    // 주행 중 멈춘 뒤 10초. 정차 중에 묻고, 출발하면 버림. 5~6세 멈춰 / 7~8세 아무 말 / 9세+ 초록
    const val SIGNAL_WAIT_AFTER_MS = 10_000L
    private const val SIGNAL_INFO = "빨간불에는 멈추고, 초록불에 출발해요!"
    val SIGNAL_WAIT = MissionDefinition(
        code = MissionCode.SIGNAL_WAIT,
        name = "신호 대기",
        scripts = scripts(
            AgeScript("차가 멈췄어요! 빨간불에는 멈출까, 갈까?", AnswerKind.STOP_GO, "맞아요! $SIGNAL_INFO", SIGNAL_INFO),
            AgeScript("차가 멈췄어요. 지금 신호등 불이 보여요?", AnswerKind.FREE, "맞아요! $SIGNAL_INFO", SIGNAL_INFO),
            AgeScript("다시 출발하려면 신호등이 무슨 색이 돼야 할까? 초록불일까, 빨간불일까?", AnswerKind.GREEN_RED,
                "맞아요! $SIGNAL_INFO", SIGNAL_INFO),
        ),
        validMs = null,
        askWhen = AskWhen.STOPPED,
    )

    // ---------------- F-08 부기장 출동 ----------------
    // 주행 기록의 첫 출발. 판정 없음, 아무 말이나 OK. 마무리 "좋아요, 출발!"
    private const val DEPART_MSG = "좋아요, 출발!"
    val DEPART = MissionDefinition(
        code = MissionCode.DEPART,
        name = "부기장 출동",
        scripts = scripts(
            AgeScript("부기장 출동! 자리에 앉아서 귀로 임무를 해요. 준비됐어요?", AnswerKind.FREE, DEPART_MSG, DEPART_MSG),
            AgeScript("부기장 출동! 오늘 목표는 똑딱 소리 찾기예요. 준비됐어요?", AnswerKind.FREE, DEPART_MSG, DEPART_MSG),
            AgeScript("부기장 수칙 1조! 자리에 앉아서 귀로 임무를! 준비됐나요?", AnswerKind.FREE, DEPART_MSG, DEPART_MSG),
        ),
        validMs = 60_000L,
        noAnswerCompletes = true,
    )

    // ---------------- F-09 깜빡이 기억 게임 ----------------
    // 깜빡이 4번 이상 켜고 정차. 좌우 횟수가 같으면 안 물음. 정답 = 더 많이 켠 쪽
    const val BLINK_MEMORY_MIN_COUNT = 4
    private const val MEMORY_INFO = "정답은 {side}! {side} 깜빡이를 {more}번, {other}은 {less}번 켰어요."
    val BLINK_MEMORY = MissionDefinition(
        code = MissionCode.BLINK_MEMORY,
        name = "깜빡이 기억 게임",
        scripts = scripts(
            AgeScript("오늘 깜빡이 소리, 왼쪽에서 더 많이 났을까, 오른쪽에서 더 많이 났을까?",
                AnswerKind.LEFT_RIGHT, MEMORY_INFO, MEMORY_INFO),
            AgeScript("기억력 퀴즈! 오늘 기장님은 왼쪽 깜빡이를 더 많이 켰을까, 오른쪽을 더 많이 켰을까?",
                AnswerKind.LEFT_RIGHT, MEMORY_INFO, MEMORY_INFO),
            AgeScript("기억력 퀴즈! 오늘 차가 왼쪽으로 더 많이 돌았을까, 오른쪽으로 더 많이 돌았을까?",
                AnswerKind.LEFT_RIGHT, MEMORY_INFO, MEMORY_INFO),
        ),
        validMs = null,
        askWhen = AskWhen.STOPPED,
    )

    // ---------------- F-10 기다리기 내기 ----------------
    // 주행 중 멈추고 5초에 묻고, 다시 출발할 때 결과 발표. 30초 안 = 금방 / 넘으면 오래
    // 주차하거나 10분 넘으면 버림 (🟡 정차 5분이면 주행이 끝나는 규칙과 겹침)
    const val WAIT_BET_AFTER_MS = 5_000L
    const val WAIT_BET_SHORT_MS = 30_000L
    const val WAIT_BET_GIVE_UP_MS = 10 * 60_000L
    private const val WAIT_INFO = "출발! {sec}초 기다렸어요. {answer} 정답!"
    val WAIT_BET = MissionDefinition(
        code = MissionCode.WAIT_BET,
        name = "기다리기 내기",
        scripts = scripts(
            AgeScript("차가 멈췄어요! 금방 출발할까, 오래 기다릴까?", AnswerKind.SOON_LONG, WAIT_INFO, WAIT_INFO),
            AgeScript("내기해요! 이번엔 금방 출발할까, 오래 기다릴까? 출발하면 알려줄게요!",
                AnswerKind.SOON_LONG, WAIT_INFO, WAIT_INFO),
            AgeScript("내기! 30초 안에 출발할까, 30초보다 오래 기다릴까?", AnswerKind.SOON_LONG, WAIT_INFO, WAIT_INFO),
        ),
        validMs = null,
        askWhen = AskWhen.STOPPED,
    )

    // ---------------- F-11 빨라졌을까 느려졌을까 ----------------
    // 20초 동안 속도가 15km/h 이상 변하면. 정답 = 실제 변화 방향
    // 🟡 9세+ 질문은 "1분 전보다"인데 판정은 트리거와 같은 20초 비교로 한다 (팀 확인 필요)
    const val SPEED_CHANGE_WINDOW_MS = 20_000L
    const val SPEED_CHANGE_MIN_KMH = 15f
    private const val CHANGE_INFO = "{change}! 아까보다 시속 {diff}km 더 {more}."
    val SPEED_CHANGE = MissionDefinition(
        code = MissionCode.SPEED_CHANGE,
        name = "빨라졌을까 느려졌을까",
        scripts = scripts(
            AgeScript("창밖을 봐요! 아까보다 차가 빨라졌을까, 느려졌을까?", AnswerKind.FAST_SLOW, CHANGE_INFO, CHANGE_INFO),
            AgeScript("몸으로 느껴봐요! 아까보다 빨라졌을까, 느려졌을까?", AnswerKind.FAST_SLOW, CHANGE_INFO, CHANGE_INFO),
            AgeScript("지금 속도가 1분 전보다 빨라졌을까, 느려졌을까?", AnswerKind.FAST_SLOW, CHANGE_INFO, CHANGE_INFO),
        ),
        validMs = 30_000L,
    )

    // ---------------- F-12 곧 도착 ----------------
    // ETA 5분 이하 · 도착지 1km 밖. 판정 없음, 아무 말이나 정답. 무응답도 완료
    const val NEAR_DEST_ETA_SEC = 300       // ETA 5분 이하면 시작
    const val NEAR_DEST_MIN_DIST_M = 1_000  // 남은 거리 1km 이하면 시작 안 함, 대기 중이면 폐기
    private const val DEST_OK = "멋진 말이에요! 기장님이 기뻐할 거예요."
    private const val DEST_NONE = "괜찮아요, 도착하면 같이 말해 봐요!"
    val NEAR_DEST = MissionDefinition(
        code = MissionCode.NEAR_DEST,
        name = "곧 도착",
        scripts = scripts(
            AgeScript("곧 도착이에요! 기장님께 \"고마워요\" 해볼까요?", AnswerKind.FREE, DEST_OK, DEST_NONE),
            AgeScript("곧 도착이에요! 도착하면 기장님께 뭐라고 말할까요?", AnswerKind.FREE, DEST_OK, DEST_NONE),
            AgeScript("곧 도착해요! 오늘 운전해 준 기장님께 한마디 해 주세요!", AnswerKind.FREE, DEST_OK, DEST_NONE),
        ),
        validMs = null,
        noAnswerCompletes = true,
    )

    // ---------------- F-13 오늘의 리포트 ----------------
    // 주차(P)로 주행이 끝나면 발표만 (질문·판정·저장 없음). question = 발표 문장, correctMessage = 마무리
    private const val REPORT_END = "기장님, 부기장에게 고맙다고 말해 주세요!"
    val TODAY_REPORT = MissionDefinition(
        code = MissionCode.TODAY_REPORT,
        name = "오늘의 리포트",
        scripts = scripts(
            AgeScript("도착! 오늘 부기장님은 미션을 {n}개 했어요. 최고예요!", AnswerKind.FREE, REPORT_END, REPORT_END),
            AgeScript("도착! 오늘 {n}개 중에 {m}개 맞혔어요!", AnswerKind.FREE, REPORT_END, REPORT_END),
            AgeScript("도착! 오늘 {n}개 중에 {m}개 맞혔어요. 가장 잘한 미션은 {best}예요!",
                AnswerKind.FREE, REPORT_END, REPORT_END),
        ),
        validMs = null,
    )

    fun of(code: MissionCode): MissionDefinition = when (code) {
        MissionCode.BLINK -> BLINK
        MissionCode.BLINK_LEARN -> BLINK_LEARN
        MissionCode.WIPER -> WIPER
        MissionCode.RAIN_STOP -> RAIN_STOP
        MissionCode.NEXT_TURN -> NEXT_TURN
        MissionCode.SPEED_GUESS -> SPEED_GUESS
        MissionCode.SIGNAL_WAIT -> SIGNAL_WAIT
        MissionCode.DEPART -> DEPART
        MissionCode.BLINK_MEMORY -> BLINK_MEMORY
        MissionCode.WAIT_BET -> WAIT_BET
        MissionCode.SPEED_CHANGE -> SPEED_CHANGE
        MissionCode.NEAR_DEST -> NEAR_DEST
        MissionCode.TODAY_REPORT -> TODAY_REPORT
    }

    // ---------------- 멘트 값 ----------------

    /** 멘트의 {key}를 값으로 바꾼다. 값이 없는 키는 그대로 둔다 */
    fun fill(template: String, values: Map<String, String>): String =
        values.entries.fold(template) { acc, (k, v) -> acc.replace("{$k}", v) }

    /** 멘트에 넣을 값: 후보가 들고 있는 값 + 9세+ 깜빡이 정답 방향 */
    fun messageValues(candidate: MissionCandidate): Map<String, String> {
        val side = when {
            candidate.code != MissionCode.BLINK -> null
            candidate.expectedAnswer == Answer.LEFT -> "왼쪽"
            candidate.expectedAnswer == Answer.RIGHT -> "오른쪽"
            else -> null
        }
        return if (side == null) candidate.values else candidate.values + ("side" to side)
    }

    /** 대답 방식에 맞는 정답. 두 단어 미션은 앞 단어(OPTION_A)가 정답이 되도록 대본을 썼다 */
    private fun expectedFor(kind: AnswerKind, optionA: Boolean = true): Answer = when (kind) {
        AnswerKind.YES_NO -> Answer.YES
        AnswerKind.FREE -> Answer.ANY
        AnswerKind.LEFT_RIGHT -> Answer.NONE      // 방향은 호출 쪽에서 정한다
        else -> if (optionA) Answer.OPTION_A else Answer.OPTION_B
    }

    private fun candidate(
        def: MissionDefinition, nowMs: Long, age: AgeGroup?, expected: Answer,
        values: Map<String, String> = emptyMap(),
    ) = MissionCandidate(
        code = def.code,
        triggeredAtMs = nowMs,
        expectedAnswer = expected,
        expiresAtMs = def.validMs?.let { nowMs + it },
        age = age,
        values = values,
    )

    // ---------------- 후보 만들기 ----------------

    /**
     * F-01 방향지시등 이벤트 → 후보. 켜졌다 꺼진 직후라 5~8세 정답은 YES,
     * 9세+는 실제 깜빡이 방향 (방향을 모르면 YES로 둔다)
     */
    fun blinkCandidate(nowMs: Long, signal: TurnSignal = TurnSignal.OFF, age: AgeGroup? = null): MissionCandidate {
        val expected = if (BLINK.script(age).answerKind == AnswerKind.LEFT_RIGHT) {
            when (signal) {
                TurnSignal.LEFT -> Answer.LEFT
                TurnSignal.RIGHT -> Answer.RIGHT
                TurnSignal.OFF -> Answer.YES
            }
        } else Answer.YES
        return candidate(BLINK, nowMs, age, expected)
    }

    /** F-02 깜빡이 배우기 */
    fun blinkLearnCandidate(nowMs: Long, age: AgeGroup? = null) =
        candidate(BLINK_LEARN, nowMs, age, expectedFor(BLINK_LEARN.script(age).answerKind))

    /** F-03 와이퍼 켜짐 → 후보. 켜진 상태이므로 5~8세 정답은 YES, 9세+는 "와이퍼"(OPTION_A) */
    fun wiperCandidate(nowMs: Long, age: AgeGroup? = null) =
        candidate(WIPER, nowMs, age, expectedFor(WIPER.script(age).answerKind))

    /** F-04 와이퍼가 1분 이상 켜졌다 꺼짐 → 후보 */
    fun rainStopCandidate(nowMs: Long, age: AgeGroup? = null) =
        candidate(RAIN_STOP, nowMs, age, expectedFor(RAIN_STOP.script(age).answerKind))

    /** F-05 내비 다음 회전이 좌/우일 때 후보. 정답은 내비 방향. */
    fun nextTurnCandidate(nowMs: Long, dir: TurnDir, age: AgeGroup? = null): MissionCandidate? {
        val expected = when (dir) {
            TurnDir.LEFT -> Answer.LEFT
            TurnDir.RIGHT -> Answer.RIGHT
            TurnDir.NONE -> return null
        }
        return candidate(NEXT_TURN, nowMs, age, expected)
    }

    /** F-06 일정한 속도 → 후보. 기준보다 빠르면 "빨리/빨라"(OPTION_A). 멘트 속도는 10km 단위 */
    fun speedGuessCandidate(nowMs: Long, speedKmh: Float, age: AgeGroup? = null) = candidate(
        SPEED_GUESS, nowMs, age,
        expected = if (speedKmh >= speedThreshold(age)) Answer.OPTION_A else Answer.OPTION_B,
        values = mapOf(SPEED to ((speedKmh / 10f).roundToInt() * 10).toString()),
    )

    /** F-07 정차 10초 → 후보 */
    fun signalWaitCandidate(nowMs: Long, age: AgeGroup? = null) =
        candidate(SIGNAL_WAIT, nowMs, age, expectedFor(SIGNAL_WAIT.script(age).answerKind))

    /** F-08 주행 첫 출발 → 후보 */
    fun departCandidate(nowMs: Long, age: AgeGroup? = null) = candidate(DEPART, nowMs, age, Answer.ANY)

    /** F-09 이번 주행 깜빡이 횟수 → 후보. 4번 미만이거나 좌우가 같으면 null */
    fun blinkMemoryCandidate(nowMs: Long, left: Int, right: Int, age: AgeGroup? = null): MissionCandidate? {
        if (left + right < BLINK_MEMORY_MIN_COUNT || left == right) return null
        val rightMore = right > left
        return candidate(
            BLINK_MEMORY, nowMs, age,
            expected = if (rightMore) Answer.RIGHT else Answer.LEFT,
            values = mapOf(
                "side" to if (rightMore) "오른쪽" else "왼쪽",
                "other" to if (rightMore) "왼쪽" else "오른쪽",
                "more" to maxOf(left, right).toString(),
                "less" to minOf(left, right).toString(),
                RIGHT_COUNT to right.toString(),
                LEFT_COUNT to left.toString(),
            ),
        )
    }

    /** F-10 정차 5초 → 후보. 정답은 다시 출발할 때 정한다 (waitBetJudged) */
    fun waitBetCandidate(nowMs: Long, stopStartMs: Long, age: AgeGroup? = null) = candidate(
        WAIT_BET, nowMs, age, expected = Answer.NONE,
        values = mapOf(STOP_START to stopStartMs.toString()),
    )

    /** F-10 다시 출발: 기다린 시간으로 정답과 멘트 값을 채운다 */
    fun waitBetJudged(c: MissionCandidate, departedAtMs: Long): MissionCandidate {
        val start = c.values[STOP_START]?.toLongOrNull() ?: c.triggeredAtMs
        val waited = (departedAtMs - start).coerceAtLeast(0)
        val short = waited <= WAIT_BET_SHORT_MS
        return c.copy(
            expectedAnswer = if (short) Answer.OPTION_A else Answer.OPTION_B,
            values = c.values + mapOf(
                WAITED_SEC to (waited / 1000).toString(),
                WAIT_SHORT to short.toString(),
                "sec" to (waited / 1000).toString(),
                "answer" to if (short) "금방이" else "오래가",
            ),
        )
    }

    /** F-11 20초 사이 속도 변화 → 후보. 빨라졌으면 OPTION_A. 15km/h 미만이면 null */
    fun speedChangeCandidate(nowMs: Long, diffKmh: Float, age: AgeGroup? = null): MissionCandidate? {
        if (abs(diffKmh) < SPEED_CHANGE_MIN_KMH) return null
        val up = diffKmh > 0
        val diff = abs(diffKmh).roundToInt()
        return candidate(
            SPEED_CHANGE, nowMs, age,
            expected = if (up) Answer.OPTION_A else Answer.OPTION_B,
            values = mapOf(
                SPEED_DIFF to (if (up) diff else -diff).toString(),
                "diff" to diff.toString(),
                "change" to if (up) "빨라졌어요" else "느려졌어요",
                "more" to if (up) "빨라요" else "느려요",
            ),
        )
    }

    /** F-12 곧 도착 후보. 아무 말이나 하면 정답 */
    fun nearDestCandidate(nowMs: Long, age: AgeGroup? = null) = candidate(NEAR_DEST, nowMs, age, Answer.ANY)

    /** F-13 발표 문장 (발표 + 마무리). 오늘 한 미션이 없으면 null */
    fun todayReport(age: AgeGroup?, done: Int, correct: Int, best: MissionCode?): String? {
        if (done <= 0) return null
        val s = TODAY_REPORT.script(age)
        val values = mapOf(
            "n" to done.toString(),
            "m" to correct.toString(),
            "best" to (best?.let { of(it).name } ?: BLINK.name),
        )
        return fill(s.question, values) + " " + s.correctMessage
    }

    // ---------------- 화면(S-06)에 넘기는 값의 키 ----------------
    const val SPEED = "speed"
    const val SPEED_DIFF = "speedDiff"
    const val RIGHT_COUNT = "rightCount"
    const val LEFT_COUNT = "leftCount"
    const val WAITED_SEC = "waitedSec"
    const val WAIT_SHORT = "waitShort"
    private const val STOP_START = "stopStartMs"

    // ---------------- 곧 도착 조건 ----------------

    /** NEAR_DEST 시작 조건. ETA 0은 경로 없음으로 본다 */
    fun isNearDestination(info: NaviInfo): Boolean {
        val eta = info.etaSec ?: return false
        return eta in 1..NEAR_DEST_ETA_SEC && !isTooCloseToDestination(info)
    }

    /** 남은 거리를 모르면 false (ETA만으로 판단) */
    fun isTooCloseToDestination(info: NaviInfo): Boolean =
        info.remainDistM?.let { it <= NEAR_DEST_MIN_DIST_M } ?: false

    /** 응답 정답 여부. 무응답(NONE)은 오답 취급이 아니라 호출 쪽에서 재질문 처리한다. */
    fun isCorrect(candidate: MissionCandidate, given: Answer): Boolean = when (candidate.expectedAnswer) {
        Answer.ANY -> given != Answer.NONE
        else -> given == candidate.expectedAnswer
    }
}

package com.chagok.chagok.sdk.pleos

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.media.AudioAttributes
import android.util.Log
import com.chagok.chagok.sdk.speech.Answer
import com.chagok.chagok.sdk.speech.AnswerKind
import com.chagok.chagok.sdk.speech.AnswerParser
import com.chagok.chagok.sdk.speech.SpeechPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener

/**
 * F-11 TTS · F-12 STT 실제 연결 (Pleos TextToSpeech / SpeechToText SDK 3.3.1).
 *
 * - 기본 ON_DEVICE 모드: 차량 내장 엔진이라 네트워크·앱 등록(registerApp)이 필요 없다.
 *   11/2 에뮬레이터 테스트에서 ON_DEVICE 초기화·인식 동작 확인.
 * - speak(): 재생이 끝나야(onDone/onStop/onError) 돌아온다. 그래야 엔진이 TTS 뒤에 STT를 켠다.
 *   여러 곳(미션 엔진, 화면 안내 음성)에서 불러도 한 번에 하나씩 읽도록 줄을 세운다.
 * - listen(): timeoutMs 안에 말이 시작되지 않으면 NONE. 시작했으면 maxUtteranceMs까지 기다린다.
 *   ON_DEVICE는 onEndedRecognition 뒤에 최종 결과(onUpdated completed=true)가 오는 일이 있어
 *   끝난 뒤에도 endGraceMs 동안 최종 결과를 기다린다 (11/2 테스트 로그).
 * - 아이 원문은 AnswerParser에서 Answer로 바꾸고 버린다. 원문은 로그에도 남기지 않는다.
 * - SDK 콜백은 어느 스레드에서 올지 모르므로 CompletableDeferred로만 결과를 넘긴다.
 * - 초기화에 실패하면 말하기는 건너뛰고 듣기는 NONE을 돌려준다. 앱은 멈추지 않는다 (F-15).
 */
class PleosSpeechPort(
    context: Context,
    ttsMode: TtsMode = TtsMode.ON_DEVICE,
    sttMode: SttMode = SttMode.ON_DEVICE,
    private val locale: Locale = Locale.KOREA,
    private val initTimeoutMs: Long = 5_000,
    private val retryAfterFailMs: Long = 30_000,      // 초기화 실패 후 이 시간 동안은 다시 시도하지 않는다
    private val maxSpeakMs: Long = 15_000,             // onDone이 안 와도 이 시간이 지나면 다음으로
    private val maxUtteranceMs: Long = 6_000,          // 말이 시작된 뒤 최대로 기다리는 시간
    private val endGraceMs: Long = 800,                // 인식 종료 후 최종 결과를 기다리는 시간
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : SpeechPort {

    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, ttsMode)
    private val stt = SpeechToText(appContext, sttMode)

    private val initMutex = Mutex()
    private val speakMutex = Mutex()
    private val listenMutex = Mutex()

    private var ttsReady = false
    private var sttReady = false
    private var ttsFailedAt: Long? = null
    private var sttFailedAt: Long? = null

    @Volatile private var speakDone: CompletableDeferred<Unit>? = null
    @Volatile private var session: ListenSession? = null

    /** 듣기 한 번의 진행 상태 */
    private class ListenSession {
        val started = CompletableDeferred<Unit>()      // 말이 시작됐거나 이미 끝남
        val ended = CompletableDeferred<Unit>()        // 인식 종료 (최종 결과는 아직일 수 있음)
        val finished = CompletableDeferred<String?>()  // 최종 결과 또는 오류
        @Volatile var lastText: String? = null         // 중간 결과 (시간 초과 때 대신 씀)
        @Volatile var gotRms = false                   // 진단용: 마이크 입력이 왔는지
    }

    // ---- TTS 콜백 ----
    private val ttsListener = object : EventListener {
        override fun onStart() {}
        override fun onReady() {}
        override fun onDone() { speakDone?.complete(Unit) }
        override fun onStop() { speakDone?.complete(Unit) }
        override fun onError(errorType: TtsError) {
            Log.w(TAG, "tts error $errorType")
            speakDone?.complete(Unit)
        }
        override fun onUpdatedRms(rms: Double) {}
    }

    // ---- STT 콜백 ----
    private val sttListener = object : ResultListener {
        override fun onReady() { Log.d(TAG, "stt ready") }
        override fun onStartedRecognition() {
            Log.d(TAG, "stt started")
            session?.started?.complete(Unit)
        }
        override fun onUpdated(text: String, completed: Boolean) {
            Log.d(TAG, "stt updated len=${text.length} completed=$completed")   // 원문은 남기지 않는다
            val s = session ?: return
            s.lastText = text
            if (completed) {
                s.started.complete(Unit)
                s.finished.complete(text)
            }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) { Log.d(TAG, "stt epd on=$on off=$off") }
        override fun onUpdatedRms(rms: Double) {
            val s = session ?: return
            if (!s.gotRms) { s.gotRms = true; Log.d(TAG, "stt first rms=$rms") }   // 마이크 소리가 들어오는지
        }
        override fun onEndedRecognition() {
            Log.d(TAG, "stt ended")
            val s = session ?: return
            s.started.complete(Unit)
            s.ended.complete(Unit)
        }
        override fun onError(errorType: SttError) {
            Log.w(TAG, "stt error $errorType")
            val s = session ?: return
            s.started.complete(Unit)
            s.finished.complete(null)
        }
    }

    // ---- 초기화 ----

    /** 앱 시작 때 미리 불러 두면 첫 질문이 늦지 않다 (선택). 둘 다 되면 true */
    suspend fun connect(): Boolean = ensureTts() and ensureStt()

    private suspend fun ensureTts(): Boolean = initMutex.withLock {
        if (ttsReady) return@withLock true
        if (recentlyFailed(ttsFailedAt)) return@withLock false
        val ok = awaitInit { done ->
            tts.initialize(object : TtsInitListener {
                override fun onSuccess() = done(true)
                override fun onFailure(errorType: TtsError) {
                    Log.w(TAG, "tts init failed $errorType")
                    done(false)
                }
            })
        }
        if (ok) {
            runSafely("tts listener") { tts.addEventListener(ttsListener) }
            runSafely("tts locale") { tts.setLocale(locale) }
            runSafely("tts audio") {
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            ttsReady = true
            ttsFailedAt = null
        } else {
            ttsFailedAt = nowMs()
        }
        ok
    }

    private suspend fun ensureStt(): Boolean = initMutex.withLock {
        if (sttReady) return@withLock true
        if (recentlyFailed(sttFailedAt)) return@withLock false
        val ok = awaitInit { done ->
            stt.initialize(object : SttInitListener {
                override fun onSuccess() = done(true)
                override fun onFailure(errorType: SttError) {
                    Log.w(TAG, "stt init failed $errorType")
                    done(false)
                }
            })
        }
        if (ok) {
            runSafely("stt listener") { stt.addListener(sttListener) }
            runSafely("stt locale") { stt.setLocale(locale) }
            sttReady = true
            sttFailedAt = null
        } else {
            sttFailedAt = nowMs()
        }
        ok
    }

    private fun recentlyFailed(at: Long?) = at != null && nowMs() - at < retryAfterFailMs

    /** SDK initialize(listener)를 기다린다. 시간 초과·예외는 실패 */
    private suspend fun awaitInit(start: (done: (Boolean) -> Unit) -> Unit): Boolean =
        withTimeoutOrNull(initTimeoutMs) {
            suspendCancellableCoroutine { cont ->
                try {
                    start { ok -> if (cont.isActive) cont.resume(ok) }
                } catch (e: Exception) {          // SecurityException(권한 없음) 포함
                    Log.w(TAG, "init threw", e)
                    if (cont.isActive) cont.resume(false)
                }
            }
        } ?: false

    // ---- F-11 말하기 ----

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        speakMutex.withLock {
            if (!ensureTts()) return@withLock     // 소리 없이 넘어간다. 미션은 계속 진행
            val done = CompletableDeferred<Unit>()
            speakDone = done
            try {
                tts.speak(text, true)
                if (withTimeoutOrNull(maxSpeakMs) { done.await() } == null) {
                    Log.w(TAG, "tts done timeout")
                    runSafely("tts stop") { tts.stop() }
                }
            } catch (e: CancellationException) {
                runSafely("tts stop") { tts.stop() }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "tts speak failed", e)
            } finally {
                if (speakDone === done) speakDone = null
            }
        }
    }

    // ---- F-12 듣기 ----

    override suspend fun listen(timeoutMs: Long, kind: AnswerKind): Answer = listenMutex.withLock {
        if (!ensureStt()) return@withLock Answer.NONE
        val s = ListenSession()
        session = s
        try {
            Log.d(TAG, "stt request kind=$kind")
            stt.request()

            // 1단계: 말이 시작되기를 기다린다
            val gotSpeech = withTimeoutOrNull(timeoutMs) { s.started.await(); true } ?: false

            // 2단계: 최종 결과를 기다린다. 인식이 끝났으면 잠깐만 더 기다린다
            val text: String? = if (!gotSpeech) s.lastText else
                withTimeoutOrNull(maxUtteranceMs) {
                    select<String?> {
                        s.finished.onAwait { it }
                        s.ended.onAwait { withTimeoutOrNull(endGraceMs) { s.finished.await() } ?: s.lastText }
                    }
                } ?: s.lastText

            val answer = AnswerParser.parse(text, kind)
            Log.d(TAG, "listen kind=$kind -> $answer")   // 원문은 남기지 않는다
            answer
        } catch (e: CancellationException) {
            runSafely("stt stop") { stt.stop() }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "stt listen failed", e)
            Answer.NONE
        } finally {
            // 다음 request()가 무시되지 않도록 매번 엔진을 멈춰 둔다 (11/8: 두 번째 듣기부터 콜백이 안 옴)
            runSafely("stt stop") { stt.stop() }
            if (session === s) session = null
        }
    }

    // ---- 해제 ----

    override fun release() {
        runSafely("tts stop") { if (ttsReady) tts.stop() }
        runSafely("stt stop") { if (sttReady) stt.stop() }
        runSafely("tts remove") { if (ttsReady) tts.removeEventListener(ttsListener) }
        runSafely("stt remove") { if (sttReady) stt.removeListener(sttListener) }
        runSafely("tts release") { tts.release() }
        runSafely("stt release") { stt.release() }
        ttsReady = false
        sttReady = false
        speakDone?.complete(Unit)
        session?.finished?.complete(null)
    }

    private inline fun runSafely(what: String, block: () -> Unit) {
        try { block() } catch (e: Exception) { Log.w(TAG, "$what failed", e) }
    }

    private companion object { const val TAG = "PleosSpeechPort" }
}

package com.chagok.chagok.data.remote

import com.chagok.chagok.domain.mission.AgeScript
import com.chagok.chagok.domain.mission.AskWhen
import com.chagok.chagok.domain.mission.MissionDefinition
import com.chagok.chagok.domain.mission.MissionDefinitions
import com.chagok.chagok.model.AgeGroup
import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.sdk.speech.AnswerKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 서버 미션팩(스키마 3) → 미션 정의. 앱 기본값(MissionDefinitions.builtIn) 위에 팩 값을 덮는다.
 * - 대본: ageContent 연령대별 question·answerKind·correctMessage·wrongMessage (+ thresholdKph, comparisonSeconds)
 * - 규칙: execution.validity(TIME이면 유효 시간), askWhen, noAnswerCompletes, 미션 강도별 간격
 * - 숫자 설정: trigger.*, execution.resolution.* → MissionDefinition.params
 * 한 미션을 못 읽으면 그 미션만 앱 기본값. 앱이 모르는 미션 코드는 무시한다.
 * 정답(expectedAnswer)은 차 신호로 앱이 정하므로 팩 값은 쓰지 않는다.
 */
object MissionPackMapper {

    /** 미션 강도. S-04에서 고르는 화면을 없애서 NORMAL 고정 (CopilotRepository와 같음) */
    const val INTENSITY = "NORMAL"

    data class Result(val definitions: Map<MissionCode, MissionDefinition>, val codes: Set<MissionCode>)

    fun map(pack: MissionPackResponse): Result {
        val defs = mutableMapOf<MissionCode, MissionDefinition>()
        val codes = mutableSetOf<MissionCode>()
        for (m in pack.missions) {
            val code = MissionCode.entries.firstOrNull { it.name == m.code } ?: continue
            codes += code
            val config = runCatching { m.config?.jsonObject }.getOrNull() ?: continue
            runCatching { definition(code, config) }.getOrNull()?.let { defs[code] = it }
        }
        return Result(defs, codes)
    }

    fun definition(code: MissionCode, config: JsonObject): MissionDefinition {
        val base = MissionDefinitions.builtIn(code)
        val execution = config.obj("execution")
        val validity = execution?.obj("validity")
        val ageContent = config.obj("ageContent")

        val scripts = AgeGroup.entries.associateWith { age ->
            val fallback = base.script(age)
            val c = ageContent?.obj(age.code) ?: return@associateWith fallback
            AgeScript(
                question = c.str("question") ?: fallback.question,
                answerKind = c.str("answerKind")?.let(::answerKind) ?: fallback.answerKind,
                correctMessage = c.str("correctMessage") ?: fallback.correctMessage,
                wrongMessage = c.str("wrongMessage") ?: fallback.wrongMessage,
                thresholdKph = c.num("thresholdKph")?.toFloat() ?: fallback.thresholdKph,
                comparisonMs = c.num("comparisonSeconds")?.let { (it * 1000).toLong() } ?: fallback.comparisonMs,
            )
        }

        val validMs = when (validity?.str("mode")) {
            "TIME" -> validity.num("durationMs")?.toLong() ?: base.validMs
            null -> base.validMs
            else -> null                     // ROUTE_ACTIVE, NAVIGATION_DISTANCE, STOPPED … 상태로 폐기
        }
        val askWhen = when (execution?.str("askWhen")) {
            "DRIVING" -> AskWhen.DRIVING
            "STOPPED" -> AskWhen.STOPPED
            else -> base.askWhen             // TRIP_END(F-13)는 엔진이 따로 발표
        }
        val cooldownMs = execution?.obj("cooldownSecondsByIntensity")?.num(INTENSITY)?.let { (it * 1000).toLong() }

        return base.copy(
            name = config.str("title") ?: base.name,
            scripts = scripts,
            validMs = validMs,
            minIntervalMs = cooldownMs ?: base.minIntervalMs,
            askWhen = askWhen,
            noAnswerCompletes = execution?.bool("noAnswerCompletes") ?: base.noAnswerCompletes,
            params = numbers("trigger", config.obj("trigger")) + numbers("resolution", execution?.obj("resolution")),
        )
    }

    /** 서버 answerKind → 앱. NONE(F-13 발표만)은 FREE로. 모르는 값이면 null (앱 기본값 유지) */
    fun answerKind(s: String): AnswerKind? =
        if (s == "NONE") AnswerKind.FREE else AnswerKind.entries.firstOrNull { it.name == s }

    private fun numbers(prefix: String, o: JsonObject?): Map<String, Double> =
        o?.entries?.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.let { "$prefix.$k" to it }
        }?.toMap() ?: emptyMap()

    private fun JsonObject.obj(key: String): JsonObject? = runCatching { this[key]?.jsonObject }.getOrNull()
    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
}

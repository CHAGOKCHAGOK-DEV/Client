package com.chagok.chagok.data.remote

import com.chagok.chagok.domain.mission.AskWhen
import com.chagok.chagok.domain.mission.MissionDefinitions
import com.chagok.chagok.model.AgeGroup
import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.model.NaviInfo
import com.chagok.chagok.sdk.speech.AnswerKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 서버 미션팩(스키마 3) → 미션 정의 */
class MissionPackMapperTest {

    @After fun reset() = MissionDefinitions.resetPack()

    private fun json(s: String): JsonElement = Json.parseToJsonElement(s)

    /** 서버 팩 v4에서 가져온 모양 (값 일부를 바꿔 적용되는지 본다) */
    private val speedGuess = json("""
        {"title":"빠를까 느릴까","trigger":{"event":"SPEED_STABLE","minSpeedKph":25,"toleranceKph":4,"stableSeconds":20,"intervalSeconds":900,"straightRoadRequired":true},
         "execution":{"askWhen":"DRIVING","validity":{"mode":"TIME","durationMs":45000},"noAnswerCompletes":false,"cooldownSecondsByIntensity":{"LOW":900,"HIGH":900,"NORMAL":900}},
         "ageContent":{
           "AGE_5_6":{"question":"Q56","answerKind":"FAST_SLOW","thresholdKph":55,"wrongMessage":"W {speed}","correctMessage":"C {speed}","expectedAnswer":"SPEED_THRESHOLD"},
           "AGE_9_PLUS":{"question":"Q9","answerKind":"FAST_SLOW","thresholdKph":45,"wrongMessage":"W9","correctMessage":"C9","expectedAnswer":"SPEED_THRESHOLD"}}}
    """)

    private val speedChange = json("""
        {"title":"빨라졌을까 느려졌을까","trigger":{"event":"SPEED_CHANGED","windowSeconds":20,"deltaKphExclusive":15},
         "execution":{"askWhen":"DRIVING","validity":{"mode":"TIME","durationMs":30000},"cooldownSecondsByIntensity":{"NORMAL":180}},
         "ageContent":{"AGE_9_PLUS":{"question":"1분 전보다?","answerKind":"FAST_SLOW","wrongMessage":"w","correctMessage":"c","comparisonSeconds":60}}}
    """)

    private val waitBet = json("""
        {"title":"기다리기 내기","trigger":{"event":"VEHICLE_STOPPED","stoppedSeconds":7},
         "execution":{"askWhen":"STOPPED","validity":{"mode":"UNTIL_RESTART"},"resolution":{"event":"VEHICLE_RESTARTED","discardOnParking":true,"thresholdSeconds":40,"discardAfterSeconds":300},
                      "cooldownSecondsByIntensity":{"NORMAL":180}},
         "ageContent":{}}
    """)

    private val report = json("""
        {"title":"오늘의 리포트","execution":{"askWhen":"TRIP_END","validity":{"mode":"TRIP_END"}},
         "ageContent":{"AGE_7_8":{"question":"도착! {n}개 중 {m}개","answerKind":"NONE","wrongMessage":"끝","correctMessage":"끝"}}}
    """)

    private fun pack(vararg missions: Pair<String, JsonElement?>) = MissionPackResponse(
        code = "DEFAULT", version = 4, schemaVersion = 3,
        missions = missions.map { (code, config) -> MissionDto(code, "X", config) },
    )

    @Test fun `대본과 기준 속도를 연령대별로 팩에서 가져온다`() {
        val d = MissionPackMapper.definition(MissionCode.SPEED_GUESS, speedGuess.let { it as kotlinx.serialization.json.JsonObject })
        assertEquals("Q56", d.script(AgeGroup.AGE_5_6).question)
        assertEquals(55f, d.script(AgeGroup.AGE_5_6).thresholdKph)
        assertEquals(45f, d.script(AgeGroup.AGE_9_PLUS).thresholdKph)
        // 팩에 없는 연령대(7~8세)는 앱 기본 대본
        assertEquals(MissionDefinitions.SPEED_GUESS.script(AgeGroup.AGE_7_8).question, d.script(AgeGroup.AGE_7_8).question)
        assertEquals(45_000L, d.validMs)
        assertEquals(900_000L, d.minIntervalMs)          // NORMAL 간격
        assertEquals(AskWhen.DRIVING, d.askWhen)
        assertEquals(25.0, d.param("trigger.minSpeedKph"))
    }

    @Test fun `팩을 적용하면 엔진이 쓰는 값이 바뀌고, 지우면 기본값으로`() {
        val m = MissionPackMapper.map(pack("SPEED_GUESS" to speedGuess, "SPEED_CHANGE" to speedChange, "WAIT_BET" to waitBet))
        MissionDefinitions.applyPack(m.definitions, m.codes)

        assertEquals(55f, MissionDefinitions.speedThreshold(AgeGroup.AGE_5_6))
        assertEquals(60f, MissionDefinitions.speedThreshold(AgeGroup.AGE_7_8))   // 팩에 없는 연령대 → 기본 60
        assertEquals(20_000L, MissionDefinitions.speedSteadyMs())
        assertEquals(4f, MissionDefinitions.speedSteadyRangeKmh())
        assertEquals(25f, MissionDefinitions.speedGuessMinKmh())
        assertEquals(60_000L, MissionDefinitions.speedChangeComparisonMs(AgeGroup.AGE_9_PLUS))
        assertEquals(20_000L, MissionDefinitions.speedChangeComparisonMs(AgeGroup.AGE_7_8))
        assertEquals(7_000L, MissionDefinitions.waitBetAfterMs())
        assertEquals(40_000L, MissionDefinitions.waitBetShortMs())
        assertEquals(300_000L, MissionDefinitions.waitBetGiveUpMs())
        assertEquals(AskWhen.STOPPED, MissionDefinitions.of(MissionCode.WAIT_BET).askWhen)
        assertNull(MissionDefinitions.of(MissionCode.WAIT_BET).validMs)          // 출발까지

        MissionDefinitions.resetPack()
        assertEquals(60f, MissionDefinitions.speedThreshold(AgeGroup.AGE_5_6))
        assertEquals(MissionDefinitions.WAIT_BET_SHORT_MS, MissionDefinitions.waitBetShortMs())
    }

    @Test fun `팩에 없는 미션은 끄고, 팩이 없으면 다 켠다`() {
        assertTrue(MissionDefinitions.isEnabled(MissionCode.RAIN_STOP))
        val m = MissionPackMapper.map(pack("BLINK" to null, "WIPER" to null))
        MissionDefinitions.applyPack(m.definitions, m.codes)
        assertTrue(MissionDefinitions.isEnabled(MissionCode.BLINK))
        assertFalse(MissionDefinitions.isEnabled(MissionCode.RAIN_STOP))
    }

    @Test fun `모르는 미션 코드는 무시하고, 못 읽는 미션은 기본값`() {
        val m = MissionPackMapper.map(pack("V-01" to json("{}"), "WIPER" to json("\"깨진 값\"")))
        assertEquals(setOf(MissionCode.WIPER), m.codes)
        assertTrue(m.definitions.isEmpty())
        MissionDefinitions.applyPack(m.definitions, m.codes)
        assertEquals(MissionDefinitions.WIPER, MissionDefinitions.of(MissionCode.WIPER))
    }

    @Test fun `발표만 하는 F-13의 answerKind NONE은 FREE로, 제목·대본 반영`() {
        val m = MissionPackMapper.map(pack("TODAY_REPORT" to report))
        MissionDefinitions.applyPack(m.definitions, m.codes)
        val d = MissionDefinitions.of(MissionCode.TODAY_REPORT)
        assertEquals(AnswerKind.FREE, d.script(AgeGroup.AGE_7_8).answerKind)
        assertEquals("도착! 3개 중 2개 끝", MissionDefinitions.todayReport(AgeGroup.AGE_7_8, 3, 2, null))
    }

    @Test fun `곧 도착 기준도 팩 값으로`() {
        val nd = json("""{"trigger":{"etaSecondsAtMost":200,"minDistanceMeters":500},"execution":{"validity":{"mode":"ROUTE_ACTIVE"}}}""")
        val m = MissionPackMapper.map(pack("NEAR_DEST" to nd))
        MissionDefinitions.applyPack(m.definitions, m.codes)
        assertFalse(MissionDefinitions.isNearDestination(NaviInfo(etaSec = 250, remainDistM = 3_000)))
        assertTrue(MissionDefinitions.isNearDestination(NaviInfo(etaSec = 150, remainDistM = 800)))
    }
}

package com.chagok.chagok.data.remote

import com.chagok.chagok.model.MissionCode
import com.chagok.chagok.model.MissionResult
import com.chagok.chagok.model.MissionState
import com.chagok.chagok.model.Trip
import com.chagok.chagok.model.TripEndReason
import com.chagok.chagok.sdk.speech.Answer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 앱 값 → 서버 값 (POST /api/v1/trips/sync). 서버 검증 규칙에 걸리지 않는지 본다 */
class TripSyncMapperTest {

    private val pack = setOf("BLINK", "WIPER", "NEXT_TURN", "NEAR_DEST")
    private val start = 1_700_000_000_000L
    private val end = start + 600_000L

    private fun trip(reason: TripEndReason? = TripEndReason.PARKED, endedAt: Long? = end) =
        Trip(id = "trip-1", startedAtMs = start, endedAtMs = endedAt, endReason = reason)

    private fun result(
        id: String = "r1",
        code: MissionCode = MissionCode.BLINK,
        given: Answer? = Answer.LEFT,
        state: MissionState = MissionState.DONE,
        correct: Boolean = true,
        triggered: Long = start + 10_000,
        asked: Long? = start + 11_000,
        answered: Long? = start + 15_000,
        interrupted: Boolean = false,
        tripId: String? = "trip-1",
    ) = MissionResult(
        id = id, tripId = tripId, code = code, expectedAnswer = Answer.LEFT, givenAnswer = given,
        state = state, correct = correct, retryCount = 0,
        triggeredAtMs = triggered, askedAtMs = asked, answeredAtMs = answered, interrupted = interrupted,
    )

    private fun build(t: Trip = trip(), rs: List<MissionResult>) =
        TripSyncMapper.build(t, rs, packCode = "DEFAULT", packVersion = 1, packMissionCodes = pack)

    @Test fun `종료 사유와 주행 상태`() {
        assertEquals("MANUAL_STOP", TripSyncMapper.endReason(TripEndReason.USER_STOP))
        assertEquals("PARKING_BRAKE", TripSyncMapper.endReason(TripEndReason.PARKED))
        assertEquals("STOP_TIMEOUT", TripSyncMapper.endReason(TripEndReason.IDLE_TIMEOUT))
        assertEquals("APP_INTERRUPTED", TripSyncMapper.endReason(TripEndReason.APP_EXIT))
        assertEquals("UNKNOWN", TripSyncMapper.endReason(null))

        assertEquals("COMPLETED", TripSyncMapper.tripStatus(TripEndReason.USER_STOP))
        assertEquals("COMPLETED", TripSyncMapper.tripStatus(TripEndReason.PARKED))
        assertEquals("COMPLETED", TripSyncMapper.tripStatus(TripEndReason.IDLE_TIMEOUT))
        assertEquals("INTERRUPTED", TripSyncMapper.tripStatus(TripEndReason.APP_EXIT))
        assertEquals("INTERRUPTED", TripSyncMapper.tripStatus(null))
    }

    @Test fun `끝나지 않은 주행은 보내지 않는다`() {
        assertNull(build(trip(endedAt = null), listOf(result())))
    }

    @Test fun `정답 결과는 참여+정답, 시각은 ISO 밀리초`() {
        val req = build(rs = listOf(result()))!!
        assertEquals("trip-1", req.tripId)
        assertEquals("DEFAULT", req.missionPackCode)
        assertEquals(1, req.missionPackVersion)
        assertEquals("COMPLETED", req.status)
        assertEquals("PARKING_BRAKE", req.endReason)
        assertEquals("2023-11-14T22:13:20Z", req.startedAt)
        val r = req.missionResults.single()
        assertEquals("BLINK", r.missionCode)
        assertEquals("COMPLETED", r.status)
        assertTrue(r.participated)
        assertEquals(true, r.correct)
        assertEquals("2023-11-14T22:13:31Z", r.startedAt)   // 물어본 시각부터
        assertEquals("2023-11-14T22:13:35Z", r.endedAt)     // 대답한 시각까지
    }

    @Test fun `오답은 참여+오답`() {
        val r = build(rs = listOf(result(given = Answer.RIGHT, correct = false)))!!.missionResults.single()
        assertTrue(r.participated)
        assertEquals(false, r.correct)
    }

    @Test fun `대답 없이 넘어가면 COMPLETED, 미참여, correct는 비운다`() {
        val r = build(rs = listOf(result(given = Answer.NONE, state = MissionState.SKIPPED, correct = false)))!!
            .missionResults.single()
        assertEquals("COMPLETED", r.status)
        assertFalse(r.participated)
        assertNull(r.correct)
    }

    @Test fun `묻지 못하고 버린 후보는 EXPIRED`() {
        val r = build(rs = listOf(result(given = null, state = MissionState.EXPIRED, correct = false, asked = null, answered = null)))!!
            .missionResults.single()
        assertEquals("EXPIRED", r.status)
        assertFalse(r.participated)
        assertNull(r.correct)
        assertEquals(r.startedAt, r.endedAt)   // 묻지 않았으면 생긴 시각 하나
    }

    @Test fun `묻는 중 주행이 끝나면 CANCELLED, 미참여`() {
        val r = build(
            t = trip(TripEndReason.APP_EXIT),
            rs = listOf(result(given = null, state = MissionState.SKIPPED, correct = false, answered = null, interrupted = true)),
        )!!
        assertEquals("INTERRUPTED", r.status)
        assertEquals("APP_INTERRUPTED", r.endReason)
        assertEquals("CANCELLED", r.missionResults.single().status)
        assertFalse(r.missionResults.single().participated)
        assertNull(r.missionResults.single().correct)
    }

    @Test fun `미션팩에 없는 미션과 다른 주행 결과는 뺀다`() {
        val req = build(rs = listOf(
            result(id = "a", code = MissionCode.BLINK),
            result(id = "b", code = MissionCode.SPEED_GUESS),     // 지금 서버 팩에 없음
            result(id = "c", code = MissionCode.WIPER, tripId = "other"),
        ))!!
        assertEquals(listOf("a"), req.missionResults.map { it.resultId })
    }

    @Test fun `결과 시각은 주행 시간 안으로 맞춘다`() {
        val r = build(rs = listOf(result(triggered = start - 5_000, asked = start - 1_000, answered = end + 9_000)))!!
            .missionResults.single()
        assertEquals("2023-11-14T22:13:20Z", r.startedAt)   // = 주행 시작
        assertEquals("2023-11-14T22:23:20Z", r.endedAt)     // = 주행 끝
    }

    @Test fun `요청은 JSON으로 저장했다가 그대로 되살린다`() {
        val req = build(rs = listOf(result(), result(id = "r2", given = Answer.NONE, state = MissionState.SKIPPED, correct = false)))!!
        val json = ApiClient.json.encodeToString(TripSyncRequest.serializer(), req)
        assertEquals(req, ApiClient.json.decodeFromString(TripSyncRequest.serializer(), json))
    }
}

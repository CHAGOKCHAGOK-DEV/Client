package com.chagok.chagok.data.remote

import com.chagok.chagok.data.repository.CopilotProfile
import com.chagok.chagok.data.repository.InMemoryOnboardingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** GET / POST / PUT /api/v1/copilot */
class CopilotRepositoryTest {

    private class FakeApi(var server: CopilotResponse? = null, var offline: Boolean = false) : CopilotApi {
        val calls = mutableListOf<String>()
        private fun fail(code: String) = ApiResponse<CopilotResponse>(isSuccess = false, code = code)
        private fun net() { if (offline) throw IOException("offline") }

        override suspend fun get(): ApiResponse<CopilotResponse> {
            calls += "GET"; net()
            return server?.let { ApiResponse(true, "OK", data = it) } ?: fail("COPILOT_404")
        }
        override suspend fun create(body: CopilotRequest): ApiResponse<CopilotResponse> {
            calls += "POST"; net()
            if (server != null) return fail("COPILOT_409")
            server = CopilotResponse(body.nickname, body.ageGroup, body.missionIntensity)
            return ApiResponse(true, "OK", data = server)
        }
        override suspend fun update(body: CopilotRequest): ApiResponse<CopilotResponse> {
            calls += "PUT"; net()
            if (server == null) return fail("COPILOT_404")
            server = CopilotResponse(body.nickname, body.ageGroup, body.missionIntensity)
            return ApiResponse(true, "OK", data = server)
        }
    }

    private class Flags : CopilotSyncFlags {
        override var copilot = false
        override var copilotCreated = false
    }

    private fun repo(api: FakeApi, onboarding: InMemoryOnboardingRepository, flags: Flags = Flags()) =
        CopilotRepository(api, { Result.success(Unit) }, flags, onboarding) { onboarding.setProfile(it) }

    // ---------- 복원 ----------

    @Test fun `기기에 프로필이 없고 서버에 있으면 가져와 저장한다`() = runTest {
        val onboarding = InMemoryOnboardingRepository(consented = true)
        val flags = Flags()
        val api = FakeApi(CopilotResponse("은우", "AGE_9_PLUS", "NORMAL"))
        assertTrue(repo(api, onboarding, flags).restore().getOrThrow())
        assertEquals(CopilotProfile("은우", "AGE_9_PLUS"), onboarding.profile.value)
        assertTrue(flags.copilotCreated)
    }

    @Test fun `서버에도 없으면 그대로 (S-04로 간다)`() = runTest {
        val onboarding = InMemoryOnboardingRepository(consented = true)
        val flags = Flags().apply { copilotCreated = true }
        assertFalse(repo(FakeApi(), onboarding, flags).restore().getOrThrow())
        assertNull(onboarding.profile.value)
        assertFalse(flags.copilotCreated)
    }

    @Test fun `기기에 프로필이 있거나 동의 전이면 서버에 묻지 않는다`() = runTest {
        val api = FakeApi(CopilotResponse("서버", "AGE_5_6", "NORMAL"))
        val has = InMemoryOnboardingRepository(consented = true, profile = CopilotProfile("기기", "AGE_7_8"))
        repo(api, has).restore()
        repo(api, InMemoryOnboardingRepository(consented = false)).restore()
        assertTrue(api.calls.isEmpty())
        assertEquals("기기", has.profile.value?.nickname)      // 기기 값이 기준
    }

    @Test fun `인터넷이 없으면 실패로 끝나고 프로필은 그대로`() = runTest {
        val onboarding = InMemoryOnboardingRepository(consented = true)
        assertTrue(repo(FakeApi(offline = true), onboarding).restore().isFailure)
        assertNull(onboarding.profile.value)
    }

    // ---------- 저장 ----------

    @Test fun `처음 만들면 POST, 그다음 수정은 PUT`() = runTest {
        val onboarding = InMemoryOnboardingRepository(consented = true, profile = CopilotProfile("은우", "AGE_7_8"))
        val flags = Flags()
        val api = FakeApi()
        val r = repo(api, onboarding, flags)
        r.sync().getOrThrow()
        onboarding.setProfile(CopilotProfile("은우", "AGE_9_PLUS"))
        r.sync().getOrThrow()
        assertEquals(listOf("POST", "PUT"), api.calls)
        assertEquals("AGE_9_PLUS", api.server?.ageGroup)
        assertFalse(flags.copilot)
    }

    @Test fun `서버에 이미 있는데 앱이 모르면 POST 409 뒤 PUT`() = runTest {
        val onboarding = InMemoryOnboardingRepository(consented = true, profile = CopilotProfile("은우", "AGE_7_8"))
        val flags = Flags()
        val api = FakeApi(CopilotResponse("옛이름", "AGE_5_6", "NORMAL"))
        repo(api, onboarding, flags).sync().getOrThrow()
        assertEquals(listOf("POST", "PUT"), api.calls)
        assertEquals("은우", api.server?.nickname)
        assertTrue(flags.copilotCreated)
    }

    @Test fun `앱은 있다고 아는데 서버에 없으면 PUT 404 뒤 POST`() = runTest {
        val onboarding = InMemoryOnboardingRepository(consented = true, profile = CopilotProfile("은우", "AGE_7_8"))
        val flags = Flags().apply { copilotCreated = true }
        val api = FakeApi()
        repo(api, onboarding, flags).sync().getOrThrow()
        assertEquals(listOf("PUT", "POST"), api.calls)
    }

    @Test fun `옛 코드 AGE_9P로 저장돼 있어도 서버에는 AGE_9_PLUS`() = runTest {
        val onboarding = InMemoryOnboardingRepository(consented = true, profile = CopilotProfile("은우", "AGE_9P"))
        val api = FakeApi()
        repo(api, onboarding).sync().getOrThrow()
        assertEquals("AGE_9_PLUS", api.server?.ageGroup)
    }

    @Test fun `못 보내면 표시가 남아 다음 시작 때 다시 보낸다`() = runTest {
        val onboarding = InMemoryOnboardingRepository(consented = true, profile = CopilotProfile("은우", "AGE_7_8"))
        val flags = Flags()
        val api = FakeApi(offline = true)
        val r = repo(api, onboarding, flags)
        assertTrue(r.sync().isFailure)
        assertTrue(flags.copilot)

        api.offline = false
        r.retryPending().getOrThrow()
        assertFalse(flags.copilot)
        assertEquals("은우", api.server?.nickname)
    }
}

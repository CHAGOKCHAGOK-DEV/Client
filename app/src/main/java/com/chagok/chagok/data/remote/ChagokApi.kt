package com.chagok.chagok.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * 차곡차곡 서버 API (Swagger: /swagger-ui/index.html).
 * 인증 헤더(Bearer 설치 토큰, X-Installation-Id)는 AuthInterceptor가 붙인다.
 */
interface ChagokApi {

    /** 서버 상태 확인 (인증 없음) */
    @GET("api/v1/health")
    suspend fun health(): ApiResponse<HealthResponse>

    // ---------- S-01 ----------

    /** 앱 설치 등록 (인증 없음). 토큰은 이 응답에서만 */
    @POST("api/v1/installations")
    suspend fun registerInstallation(): ApiResponse<InstallationRegistrationResponse>

    /** 현재 미션팩 (인증 없음) */
    @GET("api/v1/mission-packs/current")
    suspend fun currentMissionPack(
        @Query("code") code: String,
        @Query("supportedSchemaVersion") supportedSchemaVersion: Int? = null,
    ): ApiResponse<MissionPackResponse>

    /** 서버 기준 동의 필요 여부 */
    @GET("api/v1/agreements/status")
    suspend fun agreementStatus(): ApiResponse<AgreementStatusResponse>

    // ---------- S-03 ----------

    /** 현재 동의 문서 (코드·버전) */
    @GET("api/v1/agreements/current")
    suspend fun currentAgreements(): ApiResponse<AgreementCurrentResponse>

    /** 동의 기록 */
    @POST("api/v1/agreements/consents")
    suspend fun recordConsents(@Body body: AgreementConsentRequest): ApiResponse<AgreementConsentResponse>

    // ---------- S-04 ----------

    @GET("api/v1/copilot")
    suspend fun getCopilot(): ApiResponse<CopilotResponse>

    @POST("api/v1/copilot")
    suspend fun createCopilot(@Body body: CopilotRequest): ApiResponse<CopilotResponse>

    @PUT("api/v1/copilot")
    suspend fun updateCopilot(@Body body: CopilotRequest): ApiResponse<CopilotResponse>

    // ---------- 주행 종료 ----------

    /** 끝난 주행과 미션 결과 업로드. 같은 내용을 다시 보내도 처음 기록을 돌려준다 (멱등) */
    @POST("api/v1/trips/sync")
    suspend fun syncTrip(@Body body: TripSyncRequest): ApiResponse<TripSyncResponse>
}

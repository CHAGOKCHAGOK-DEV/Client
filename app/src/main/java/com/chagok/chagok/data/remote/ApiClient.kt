package com.chagok.chagok.data.remote

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    const val BASE_URL = "https://api.hyundai-little-copoilot.store/"

    private val TOKEN_IN_BODY = Regex("\"token\"\\s*:\\s*\"[^\"]*\"")

    /** 서버에 필드가 추가돼도 앱이 죽지 않게 모르는 키는 무시 */
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun create(context: Context, store: InstallationStore): ChagokApi {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        // 로그에 토큰이 찍히지 않게: 헤더는 redactHeader, 응답 본문의 "token"은 직접 가림
        val logging = HttpLoggingInterceptor { message ->
            android.util.Log.i("okhttp", message.replace(TOKEN_IN_BODY, "\"token\":\"██\""))
        }.apply {
            level = if (debuggable) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ChagokApi::class.java)
    }
}
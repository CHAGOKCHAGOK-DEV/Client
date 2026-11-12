package com.chagok.chagok.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/** 등록된 설치가 있으면 모든 요청에 Bearer 토큰과 X-Installation-Id를 붙인다 */
class AuthInterceptor(private val store: InstallationStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = store.token
        val id = store.installationId
        val request = chain.request().newBuilder().apply {
            if (token != null) header("Authorization", "Bearer $token")
            if (id != null) header("X-Installation-Id", id)
        }.build()
        return chain.proceed(request)
    }
}
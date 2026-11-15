package com.chagok.chagok.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import retrofit2.HttpException
import java.io.IOException

/**
 * API 호출을 Result로 감싼다. 화면·로직 코드는 try-catch 없이
 *   apiCall { api.getCopilot() }.onSuccess { ... }.onFailure { ... }
 * - 성공: data
 * - 실패: ApiException(서버 오류) / IOException(네트워크 끊김·시간 초과)
 * 코루틴 취소는 잡지 않고 그대로 넘긴다.
 */
suspend fun <T> apiCall(block: suspend () -> ApiResponse<T>): Result<T> = try {
    val r = block()
    val data = r.data
    if (r.isSuccess && data != null) Result.success(data)
    else Result.failure(ApiException(r.code, r.message))
} catch (e: HttpException) {
    Result.failure(e.toApiException())
} catch (e: IOException) {
    Result.failure(e)
} catch (e: SerializationException) {
    Result.failure(ApiException(code = "PARSE_ERROR", message = e.message))
}

/** 4xx·5xx 응답 본문도 ApiResponse 형식이면 code·message를 꺼낸다 */
private fun HttpException.toApiException(): ApiException {
    val body = response()?.errorBody()?.string()
    val parsed = body?.let {
        runCatching { ApiClient.json.decodeFromString<ApiResponse<JsonElement>>(it) }.getOrNull()
    }
    return ApiException(code = parsed?.code, message = parsed?.message ?: message(), httpStatus = code())
}
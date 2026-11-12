package com.chagok.chagok.data.remote

import kotlinx.serialization.Serializable

/** 서버 공통 응답 { isSuccess, code, message, data } */
@Serializable
data class ApiResponse<T>(
    val isSuccess: Boolean,
    val code: String? = null,
    val message: String? = null,
    val data: T? = null,
)

/** 서버가 isSuccess=false 또는 HTTP 오류를 준 경우 */
class ApiException(
    val code: String?,
    override val message: String?,
    val httpStatus: Int? = null,
) : Exception(message)
package com.chagok.chagok.data.remote

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * S-01 3/3 미션팩. 서버 버전이 더 높으면 앱에 저장해 둔다.
 * 실패하면 저장된 팩(없으면 앱 내장팩)을 쓴다.
 */
class MissionPackRepository(context: Context, private val api: ChagokApi) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 저장된 미션팩 버전 (없으면 0) */
    val cachedVersion: Int get() = prefs.getInt(KEY_VERSION, 0)

    /** 저장된 미션팩. TODO: 미션 엔진(MissionDefinitions)이 이걸 쓰도록 연결 */
    fun cached(): MissionPackResponse? =
        prefs.getString(KEY_JSON, null)?.let {
            runCatching { ApiClient.json.decodeFromString<MissionPackResponse>(it) }.getOrNull()
        }

    suspend fun sync(): Result<MissionPackResponse> =
        apiCall { api.currentMissionPack(PACK_CODE, SUPPORTED_SCHEMA_VERSION) }
            .onSuccess { pack ->
                if (pack.version > cachedVersion) {
                    prefs.edit()
                        .putInt(KEY_VERSION, pack.version)
                        .putString(KEY_JSON, ApiClient.json.encodeToString(pack))
                        .apply()
                }
            }

    companion object {
        /** 🟡 TODO: 백엔드에서 받은 값으로 교체 */
        const val PACK_CODE = "DEFAULT"
        const val SUPPORTED_SCHEMA_VERSION = 1

        private const val PREFS = "chagok_mission_pack"
        private const val KEY_VERSION = "version"
        private const val KEY_JSON = "json"
    }
}
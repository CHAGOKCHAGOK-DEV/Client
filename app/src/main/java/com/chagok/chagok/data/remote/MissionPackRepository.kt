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
                // 버전이 올랐거나, 예전 스키마 팩이 저장돼 있으면 바꾼다
                if (pack.version > cachedVersion || cached()?.schemaVersion != pack.schemaVersion) {
                    prefs.edit()
                        .putInt(KEY_VERSION, pack.version)
                        .putString(KEY_JSON, ApiClient.json.encodeToString(pack))
                        .apply()
                }
            }

    companion object {
        const val PACK_CODE = "DEFAULT"
        /** 3 = 13개 미션 팩 (BLINK … WAIT_BET). 1은 예전 4개 미션 팩 */
        const val SUPPORTED_SCHEMA_VERSION = 3

        private const val PREFS = "chagok_mission_pack"
        private const val KEY_VERSION = "version"
        private const val KEY_JSON = "json"
    }
}
package com.chagok.chagok.model

enum class AgeGroup(val label: String) {
    AGE_5_6("5 ~ 6세"),
    AGE_7_8("7 ~ 8세"),
    AGE_9_PLUS("9세 이상");

    /** 서버 코드와 같다 (POST/PUT /api/v1/copilot, 미션팩 ageContent 키) */
    val code: String get() = name

    companion object {
        /** 예전 앱이 저장한 코드. 이미 프로필을 만든 사용자도 읽히게 */
        private const val LEGACY_9P = "AGE_9P"

        fun fromCode(code: String?): AgeGroup? =
            if (code == LEGACY_9P) AGE_9_PLUS else entries.firstOrNull { it.code == code }
    }
}
package com.chagok.chagok.model

enum class AgeGroup(val label: String) {
    AGE_5_6("5 ~ 6세"),
    AGE_7_8("7 ~ 8세"),
    AGE_9P("9세 이상");

    val code: String get() = name

    companion object {
        fun fromCode(code: String?): AgeGroup? = entries.firstOrNull { it.code == code }
    }
}
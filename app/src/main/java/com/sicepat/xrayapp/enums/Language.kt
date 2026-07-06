package com.sicepat.xrayapp.enums

enum class Language(val code: String) {
    AUTO("auto"),
    ENGLISH("en"),
    INDONESIA("id"),
    ALIEN("al");

    companion object {
        fun fromCode(code: String): Language {
            return entries.find { it.code == code } ?: AUTO
        }
    }
}

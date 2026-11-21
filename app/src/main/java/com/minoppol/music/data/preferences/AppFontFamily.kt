package com.minoppol.music.data.preferences

enum class AppFontFamily(val storageKey: String) {
    GOOGLE_SANS_FLEX("google_sans_flex"),
    SYSTEM("system"),
    CUSTOM("custom");
    companion object {
        val DEFAULT = GOOGLE_SANS_FLEX
        fun fromStorageKey(value: String?): AppFontFamily =
            entries.firstOrNull { it.storageKey == value } ?: DEFAULT
    }
}
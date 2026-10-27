package com.minoppol.music.data.preferences

enum class LyricsWordHighlightStyle(val storageValue: String) {
    WIPE("wipe"),
    FADE("fade"),
    CLASSIC("classic");

    companion object {
        fun fromStorage(value: String?): LyricsWordHighlightStyle =
            entries.find { it.storageValue == value } ?: WIPE
    }
}

const val MIN_LYRICS_FONT_SIZE_SP = 16f
const val MAX_LYRICS_FONT_SIZE_SP = 42f
const val DEFAULT_LYRICS_FONT_SIZE_SP = 22f

package com.minoppol.music.data.preferences

const val PREVIOUS_BUTTON_RESTART_THRESHOLD_MS = 10_000L

enum class PreviousButtonBehavior(val storageKey: String) {
    RESTART_TRACK("restart_track"),

    PREVIOUS_TRACK("previous_track");

    companion object {
        val DEFAULT = RESTART_TRACK

        fun fromStorageKey(value: String?): PreviousButtonBehavior =
            entries.firstOrNull { it.storageKey == value } ?: DEFAULT
    }
}

fun PreviousButtonBehavior.restartsAtPosition(positionMs: Long): Boolean =
    this == PreviousButtonBehavior.RESTART_TRACK && positionMs > PREVIOUS_BUTTON_RESTART_THRESHOLD_MS

fun PreviousButtonBehavior.shouldRestartCurrentTrack(positionMs: Long, hasPrevious: Boolean): Boolean =
    !hasPrevious || restartsAtPosition(positionMs)

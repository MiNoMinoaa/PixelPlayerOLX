package com.minoppol.music.ui.theme

import com.minoppol.music.presentation.viewmodel.ColorSchemePair

internal fun resolveAppWideNowPlayingColorSchemePair(
    enabled: Boolean,
    currentSongId: String?,
    isPlaying: Boolean,
    currentSongScheme: ColorSchemePair?,
    awaitingScheme: Boolean,
    lastValidSongId: String?,
    lastValidScheme: ColorSchemePair?
): ColorSchemePair? {
    if (!enabled || currentSongId == null) return null
    if (currentSongScheme != null) return currentSongScheme
    if (lastValidScheme != null && (awaitingScheme || (!isPlaying && currentSongId == lastValidSongId))) {
        return lastValidScheme
    }
    return null
}

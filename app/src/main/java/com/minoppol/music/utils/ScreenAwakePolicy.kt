package com.minoppol.music.utils

internal fun shouldKeepScreenAwake(
    preferenceEnabled: Boolean,
    isPlaying: Boolean,
): Boolean = preferenceEnabled && isPlaying

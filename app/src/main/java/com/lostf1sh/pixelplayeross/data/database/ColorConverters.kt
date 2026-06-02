package com.lostf1sh.pixelplayeross.data.database

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

fun String.toComposeColor(): Color {
    // Add error handling for color parsing
    return try {
        Color(this.toColorInt())
    } catch (e: IllegalArgumentException) {
        // Log the error or return a default color if the string isn't valid
        Color.Black // Fallback color
    }
}

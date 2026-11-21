package com.minoppol.music.ui.theme

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import com.minoppol.music.data.preferences.AppFontFamily

const val CUSTOM_FONT_FILE_NAME = "custom_font.ttf"

fun copyCustomFontToFiles(context: android.content.Context, uri: Uri): Boolean {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return false
        val outputFile = context.getFileStreamPath(CUSTOM_FONT_FILE_NAME)
        val outputStream = outputFile.outputStream()
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}

@OptIn(ExperimentalTextApi::class)
fun resolveCustomFontFamilyOrNull(context: android.content.Context): FontFamily? {
    val fontFile = context.getFileStreamPath(CUSTOM_FONT_FILE_NAME)
    if (!fontFile.exists()) return null
    if (!fontFile.canRead()) return null
    return FontFamily(
        Font(
            file = fontFile,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))
        ),
        Font(
            file = fontFile,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500))
        ),
        Font(
            file = fontFile,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600))
        ),
        Font(
            file = fontFile,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700))
        )
    )
}

@Composable
fun resolveAppFontFamily(
    preference: AppFontFamily,
    customFileAvailable: Boolean
): FontFamily {
    val context = LocalContext.current
    return when (preference) {
        AppFontFamily.SYSTEM -> FontFamily.SansSerif
        AppFontFamily.CUSTOM -> {
            if (customFileAvailable) {
                remember(preference, customFileAvailable, context) {
                    resolveCustomFontFamilyOrNull(context) ?: RoundedSans
                }
            } else {
                RoundedSans
            }
        }
        AppFontFamily.GOOGLE_SANS_FLEX -> RoundedSans
    }
}
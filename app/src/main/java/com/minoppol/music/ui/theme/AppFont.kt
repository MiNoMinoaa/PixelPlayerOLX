package com.minoppol.music.ui.theme

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import com.minoppol.music.data.preferences.AppFontFamily

private const val CUSTOM_FONT_FILE_PREFIX = "custom_font"
private const val CUSTOM_FONT_FILE_EXTENSION = ".ttf"
const val LEGACY_CUSTOM_FONT_FILE_NAME = "custom_font.ttf"

fun createCustomFontFileName(): String =
    "${CUSTOM_FONT_FILE_PREFIX}_${System.currentTimeMillis()}$CUSTOM_FONT_FILE_EXTENSION"

fun resolveCustomFontDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        val displayName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
        val trimmed = displayName?.trim().orEmpty()
        val fallback = uri.lastPathSegment?.substringAfterLast('/')?.trim().orEmpty()
        val candidate = if (trimmed.isNotBlank()) trimmed else fallback
        stripFontFileExtension(candidate)
    } catch (e: Exception) {
        null
    }
}

fun stripFontFileExtension(fileName: String?): String? {
    val trimmed = fileName?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    val withoutExtension = trimmed.substringBeforeLast('.', trimmed)
    val resolved = withoutExtension.trim()
    return resolved.ifBlank { null }
}

fun resolveCustomFontFileFamilyName(context: android.content.Context, fileName: String?): String? {
    if (fileName.isNullOrBlank()) return null
    return try {
        val fontFile = context.getFileStreamPath(fileName)
        if (!fontFile.exists() || !fontFile.canRead()) return null
        stripFontFileExtension(readFontFamilyName(fontFile.readBytes()))
    } catch (e: Exception) {
        null
    }
}

private fun readFontFamilyName(fontData: ByteArray): String? {
    if (fontData.size < 12) return null
    fun u16(offset: Int): Int =
        ((fontData[offset].toInt() and 0xFF) shl 8) or (fontData[offset + 1].toInt() and 0xFF)

    fun u32(offset: Int): Long =
        ((fontData[offset].toLong() and 0xFF) shl 24) or
            ((fontData[offset + 1].toLong() and 0xFF) shl 16) or
            ((fontData[offset + 2].toLong() and 0xFF) shl 8) or
            (fontData[offset + 3].toLong() and 0xFF)

    val tableCount = u16(4)
    var nameTableOffset = -1
    for (index in 0 until tableCount) {
        val recordOffset = 12 + index * 16
        if (recordOffset + 16 > fontData.size) break
        val tag = String(fontData, recordOffset, 4, Charsets.ISO_8859_1)
        if (tag == "name") {
            nameTableOffset = u32(recordOffset + 8).toInt()
            break
        }
    }
    if (nameTableOffset < 0 || nameTableOffset + 6 > fontData.size) return null

    val nameCount = u16(nameTableOffset + 2)
    val stringOffset = u16(nameTableOffset + 4)
    var fallback: String? = null
    for (index in 0 until nameCount) {
        val recordOffset = nameTableOffset + 6 + index * 12
        if (recordOffset + 12 > fontData.size) break
        val platformId = u16(recordOffset)
        val nameId = u16(recordOffset + 6)
        if (nameId != 1 && nameId != 4 && nameId != 16) continue
        val length = u16(recordOffset + 8)
        val offset = u16(recordOffset + 10)
        val start = nameTableOffset + stringOffset + offset
        if (start < 0 || start + length > fontData.size || length <= 0) continue
        val raw = fontData.copyOfRange(start, start + length)
        val decoded = if (platformId == 3 || platformId == 0) {
            String(raw, Charsets.UTF_16BE)
        } else {
            String(raw, Charsets.ISO_8859_1)
        }
        val cleaned = decoded.trim().replace('\u0000', ' ').trim()
        if (cleaned.isBlank()) continue
        if (nameId == 1) return cleaned
        if (fallback == null) fallback = cleaned
    }
    return fallback
}

fun copyCustomFontToFiles(context: android.content.Context, uri: Uri, fileName: String): Boolean {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return false
        val outputFile = context.getFileStreamPath(fileName)
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

fun deleteOtherCustomFontFiles(context: android.content.Context, keepFileName: String?) {
    try {
        context.filesDir.listFiles { file ->
            file.name.startsWith(CUSTOM_FONT_FILE_PREFIX) &&
                file.name != keepFileName &&
                file.name != LEGACY_CUSTOM_FONT_FILE_NAME
        }?.forEach { it.delete() }
    } catch (e: Exception) {
    }
}

fun migrateLegacyCustomFontFile(context: android.content.Context): String? {
    return try {
        val legacyFile = context.getFileStreamPath(LEGACY_CUSTOM_FONT_FILE_NAME)
        if (!legacyFile.exists() || !legacyFile.canRead()) return null
        val fileName = createCustomFontFileName()
        val targetFile = context.getFileStreamPath(fileName)
        legacyFile.inputStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        legacyFile.delete()
        fileName
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalTextApi::class)
fun resolveCustomFontFamilyOrNull(
    context: android.content.Context,
    fileName: String?
): FontFamily? {
    if (fileName.isNullOrBlank()) return null
    val fontFile = context.getFileStreamPath(fileName)
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
    customFontFileName: String?
): FontFamily {
    val context = LocalContext.current
    return when (preference) {
        AppFontFamily.SYSTEM -> FontFamily.SansSerif
        AppFontFamily.CUSTOM -> {
            if (!customFontFileName.isNullOrBlank()) {
                remember(preference, customFontFileName, context) {
                    resolveCustomFontFamilyOrNull(context, customFontFileName) ?: RoundedSans
                }
            } else {
                RoundedSans
            }
        }
        AppFontFamily.GOOGLE_SANS_FLEX -> RoundedSans
    }
}

package com.lostf1sh.pixelplayeross.data.service.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.data.model.AudioOutputMode
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
internal object AudioDecoderPolicy {
    private const val AUDIO_MIDI = "audio/midi"
    private val extensionOnlyMimeTypes = setOf(
        MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_EXOPLAYER_MIDI,
        AUDIO_MIDI
    )

    fun shouldUseExtensionRenderer(mimeType: String, outputMode: AudioOutputMode): Boolean {
        // Some platform FLAC decoders produce corrupt audio when Media3 requests PCM_FLOAT
        // (reported on the Galaxy S25 Ultra, issue #122). FFmpeg can decode FLAC directly to
        // float PCM without that platform negotiation. Keep the normal decoder order in the
        // integer output modes, where the reporter confirmed playback works.
        return (outputMode.usesFloatOutput && MimeTypes.AUDIO_FLAC.equals(mimeType, ignoreCase = true)) ||
            extensionOnlyMimeTypes.any { it.equals(mimeType, ignoreCase = true) }
    }

    fun <T> selectPlatformDecoders(
        mimeType: String,
        decoderInfos: List<T>,
        outputMode: AudioOutputMode
    ): List<T> {
        return if (shouldUseExtensionRenderer(mimeType, outputMode)) {
            emptyList()
        } else {
            decoderInfos
        }
    }

    fun isLikelyHardwareDecoder(decoderName: String): Boolean {
        val normalized = decoderName.lowercase(Locale.US)
        val knownSoftwareTokens = listOf(
            "omx.google.",
            "c2.android.",
            "ffmpeg",
            "midi",
            "jsyn",
            "libgav1",
            "dav1d"
        )
        if (knownSoftwareTokens.any(normalized::contains)) return false

        return normalized.startsWith("omx.") ||
            normalized.startsWith("c2.") ||
            normalized.contains(".qti.") ||
            normalized.contains(".qcom.") ||
            normalized.contains(".sec.") ||
            normalized.contains(".mtk.") ||
            normalized.contains(".exynos.") ||
            normalized.contains(".dolby.")
    }
}

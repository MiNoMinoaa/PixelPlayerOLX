package com.lostf1sh.pixelplayeross.data.service.player

import androidx.media3.common.MimeTypes
import com.lostf1sh.pixelplayeross.data.model.AudioOutputMode
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class AudioDecoderPolicyTest {

    @ParameterizedTest
    @EnumSource(AudioOutputMode::class)
    fun selectPlatformDecoders_routesAlacToExtensionRenderer(outputMode: AudioOutputMode) {
        val decoders = listOf("c2.qti.alac.decoder", "c2.android.alac.decoder")

        val selected = AudioDecoderPolicy.selectPlatformDecoders(MimeTypes.AUDIO_ALAC, decoders, outputMode)

        assertThat(selected).isEmpty()
    }

    @ParameterizedTest
    @EnumSource(AudioOutputMode::class)
    fun selectPlatformDecoders_routesMidiToExtensionRenderer(outputMode: AudioOutputMode) {
        val decoders = listOf("platform-midi-decoder")

        val selected = AudioDecoderPolicy.selectPlatformDecoders(MimeTypes.AUDIO_EXOPLAYER_MIDI, decoders, outputMode)

        assertThat(selected).isEmpty()
    }

    @ParameterizedTest
    @EnumSource(AudioOutputMode::class)
    fun selectPlatformDecoders_preservesMedia3OrderForCoreFormats(outputMode: AudioOutputMode) {
        val decoders = listOf("c2.android.aac.decoder", "c2.qti.aac.decoder")

        val selected = AudioDecoderPolicy.selectPlatformDecoders(MimeTypes.AUDIO_AAC, decoders, outputMode)

        assertThat(selected).containsExactlyElementsIn(decoders).inOrder()
    }

    @Test
    fun selectPlatformDecoders_routesFlacToExtensionRendererForFloatOutput() {
        val decoders = listOf("c2.qti.flac.decoder", "c2.android.flac.decoder")

        val selected = AudioDecoderPolicy.selectPlatformDecoders(
            MimeTypes.AUDIO_FLAC, decoders, AudioOutputMode.PCM_FLOAT
        )

        assertThat(selected).isEmpty()
    }

    @ParameterizedTest
    @EnumSource(value = AudioOutputMode::class, names = ["SYSTEM_DEFAULT", "DIRECT"])
    fun selectPlatformDecoders_preservesFlacDecodersForIntegerOutput(outputMode: AudioOutputMode) {
        val decoders = listOf("c2.qti.flac.decoder", "c2.android.flac.decoder")

        val selected = AudioDecoderPolicy.selectPlatformDecoders(MimeTypes.AUDIO_FLAC, decoders, outputMode)

        assertThat(selected).containsExactlyElementsIn(decoders).inOrder()
    }

    @Test
    fun selectPlatformDecoders_matchesFlacMimeTypeIgnoringCase() {
        val selected = AudioDecoderPolicy.selectPlatformDecoders(
            "AUDIO/FLAC", listOf("c2.qti.flac.decoder"), AudioOutputMode.PCM_FLOAT
        )

        assertThat(selected).isEmpty()
    }

    @Test
    fun isLikelyHardwareDecoder_marksSoftwareRenderersAsSoftware() {
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("OMX.google.aac.decoder")).isFalse()
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("c2.android.aac.decoder")).isFalse()
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("ffmpegAudioRenderer")).isFalse()
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("MidiRenderer(JSyn)")).isFalse()
    }

    @Test
    fun isLikelyHardwareDecoder_marksVendorCodecsAsHardware() {
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("c2.qti.aac.decoder")).isTrue()
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("c2.qti.flac.decoder")).isTrue()
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("OMX.qcom.audio.decoder.aac")).isTrue()
        assertThat(AudioDecoderPolicy.isLikelyHardwareDecoder("c2.sec.aac.decoder")).isTrue()
    }
}

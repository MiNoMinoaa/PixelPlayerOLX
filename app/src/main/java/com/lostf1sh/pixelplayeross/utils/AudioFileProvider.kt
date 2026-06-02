package com.lostf1sh.pixelplayeross.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import timber.log.Timber

object AudioFileProvider {

    private const val TIMEOUT_US = 1000L

    suspend fun getWavFile(context: Context, uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)
            val trackIndex = findAudioTrack(extractor)
            if (trackIndex == -1) {
                extractor.release()
                error("No audio track found.")
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("MIME type not found.")
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val tempWavFile = File.createTempFile("input_mono", ".wav", context.cacheDir)
            val fileOutputStream = FileOutputStream(tempWavFile)
            // Write an empty WAV header (for 1 channel, mono)
            val wavHeader = WavHeader(0, 0, 0, 0, 1)
            fileOutputStream.write(wavHeader.asByteArray())

            var totalBytesWritten = 0
            val bufferInfo = MediaCodec.BufferInfo()
            var isEndOfStream = false

            while (!isEndOfStream) {
                val inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                    if (inputBuffer == null) {
                        Timber.tag("AudioFileProvider").w("Decoder input buffer was null, ending decode early")
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEndOfStream = true
                        continue
                    }
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEndOfStream = true
                    } else {
                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                var outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                while (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer == null) {
                        Timber.tag("AudioFileProvider").w("Decoder output buffer was null, skipping chunk")
                        decoder.releaseOutputBuffer(outputBufferIndex, false)
                        outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                        continue
                    }
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.get(chunk)

                    // --- CONVERSION TO MONO ---
                    val monoChunk = stereoToMono(chunk)
                    fileOutputStream.write(monoChunk)
                    totalBytesWritten += monoChunk.size

                    decoder.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                }
            }

            fileOutputStream.close()
            decoder.stop()
            decoder.release()
            extractor.release()

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val finalHeader = WavHeader(
                fileSize = totalBytesWritten + 36,
                subchunk2Size = totalBytesWritten,
                sampleRate = sampleRate,
                bitsPerSample = 16,
                numChannels = 1 // MONO
            )
            finalHeader.updateHeader(tempWavFile)

            Timber.tag("AudioFileProvider").d("Mono WAV file created at: ${tempWavFile.absolutePath}")
            tempWavFile
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }

    // Mixes a 16-bit stereo PCM audio buffer down to mono by averaging the channels
    private fun stereoToMono(stereoPcm: ByteArray): ByteArray {
        val monoPcm = ByteArray(stereoPcm.size / 2)
        var monoIndex = 0
        var stereoIndex = 0
        while (stereoIndex < stereoPcm.size) {
            // Read the 16-bit samples for the left and right channels
            val left = (stereoPcm[stereoIndex].toInt() and 0xFF) or (stereoPcm[stereoIndex + 1].toInt() shl 8)
            val right = (stereoPcm[stereoIndex + 2].toInt() and 0xFF) or (stereoPcm[stereoIndex + 3].toInt() shl 8)

            // Average the samples
            val avg = (left + right) / 2

            // Write the averaged 16-bit sample
            monoPcm[monoIndex] = (avg and 0xFF).toByte()
            monoPcm[monoIndex + 1] = (avg shr 8 and 0xFF).toByte()

            stereoIndex += 4
            monoIndex += 2
        }
        return monoPcm
    }
}

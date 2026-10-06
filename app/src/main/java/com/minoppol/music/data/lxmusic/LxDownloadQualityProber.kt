package com.minoppol.music.data.lxmusic

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

// 下载前探一遍：脚本说有这档还不够，真发请求看文件大小和音频头，防假无损假 hires
@Singleton
class LxDownloadQualityProber @Inject constructor(
    private val repository: LxMusicRepository,
    baseOkHttpClient: OkHttpClient,
) {
    enum class QualityAvailability { PROBING, AVAILABLE, UNAVAILABLE }

    data class QualityProbe(
        val quality: String,
        val availability: QualityAvailability,
        val bytes: Long? = null,
        val bytesEstimated: Boolean = false,
        val assetPath: String? = null,
    )

    private val probeClient = baseOkHttpClient.newBuilder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val cache = ConcurrentHashMap<String, ConcurrentHashMap<String, QualityProbe>>()

    private fun key(song: LxSong) = "${song.source}/${song.songmid}"

    fun cached(song: LxSong): Map<String, QualityProbe> =
        cache[key(song)]
            ?.filterValues { it.availability == QualityAvailability.AVAILABLE }
            ?.toMap()
            .orEmpty()

    suspend fun probe(
        song: LxSong,
        qualities: List<String>,
        scripts: List<String>,
        onUpdate: (QualityProbe) -> Unit,
    ) {
        val songCache = cache.getOrPut(key(song)) { ConcurrentHashMap() }
        for (quality in qualities) {
            val existing = songCache[quality]
            if (existing != null && existing.availability == QualityAvailability.AVAILABLE) {
                onUpdate(existing)
                continue
            }
            val result = probeOne(song, quality, scripts)
            if (result.availability == QualityAvailability.AVAILABLE) {
                songCache[quality] = result
            } else {
                songCache.remove(quality)
            }
            onUpdate(result)
        }
    }

    private suspend fun probeOne(
        song: LxSong,
        quality: String,
        scripts: List<String>,
    ): QualityProbe {
        for (assetPath in scripts) {
            val url = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                runCatching { repository.resolveMusicUrlStrict(assetPath, song, quality) }
                    .getOrNull()
            }?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                ?: continue

            val remote = runCatching { probeRemote(url) }.getOrNull() ?: RemoteProbe(null, null)
            val provenFake = judge(
                quality = quality,
                totalBytes = remote.totalBytes,
                head = remote.head,
                fallbackDurationSec = song.interval,
            ) == false
            if (provenFake) {
                Timber.d(
                    "download probe: %s rejected as fake (bytes=%s, interval=%s, head=%s)",
                    quality, remote.totalBytes, song.interval,
                    remote.head?.let { runCatching { LxAudioHeadInspector.inspect(it) }.getOrNull() },
                )
                continue
            }
            val estimatedBytes = remote.totalBytes
                ?: estimateBytes(quality, song.interval)
            return QualityProbe(
                quality = quality,
                availability = QualityAvailability.AVAILABLE,
                bytes = estimatedBytes,
                bytesEstimated = remote.totalBytes == null && estimatedBytes != null,
                assetPath = assetPath,
            )
        }
        return QualityProbe(quality, QualityAvailability.UNAVAILABLE)
    }

    private fun estimateBytes(quality: String, durationSec: Long): Long? {
        if (durationSec <= 0L) return null
        val typicalBps = when (quality) {
            LxQualities.Q128 -> 128_000L
            LxQualities.Q320 -> 320_000L
            LxQualities.FLAC -> 1_000_000L
            LxQualities.FLAC24 -> 3_000_000L
            LxQualities.ATMOS -> 2_000_000L
            LxQualities.MASTER -> 6_000_000L
            else -> 320_000L
        }
        return typicalBps * durationSec / 8L
    }

    private data class RemoteProbe(val totalBytes: Long?, val head: ByteArray?)

    private fun probeRemote(url: String): RemoteProbe {
        var total: Long? = null
        var headBytes: ByteArray? = null
        runCatching {
            val req = Request.Builder().url(url)
                .header("Range", "bytes=0-$HEAD_FETCH_BYTES")
                .build()
            probeClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return RemoteProbe(null, null)
                if (resp.code == 206) {
                    total = resp.header("Content-Range")
                        ?.substringAfterLast('/')
                        ?.toLongOrNull()
                        ?.takeIf { it > 0 }
                    headBytes = resp.body?.byteStream()?.use { readHead(it) }
                } else {
                    total = resp.header("Content-Length")
                        ?.toLongOrNull()
                        ?.takeIf { it > 0 }
                    headBytes = resp.body?.byteStream()?.use { readHead(it) }
                }
            }
        }
        return RemoteProbe(total, headBytes)
    }

    private fun readHead(input: java.io.InputStream): ByteArray {
        val buffer = ByteArray(HEAD_FETCH_BYTES + 1)
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
        }
        return buffer.copyOf(read)
    }

    // 要无损却给 MP3、24bit 头里不到 24、码率×时长对不上总大小，都算假
    internal fun judge(
        quality: String,
        totalBytes: Long?,
        head: ByteArray?,
        fallbackDurationSec: Long,
    ): Boolean {
        val info = head?.let { runCatching { LxAudioHeadInspector.inspect(it) }.getOrNull() }
        if (info != null) {
            if (LxAudioHeadInspector.expectsLossless(quality) &&
                info.container == LxAudioHeadInspector.Container.MP3
            ) return false
            if (quality == LxQualities.FLAC24 &&
                info.container == LxAudioHeadInspector.Container.FLAC &&
                info.bitsPerSample != null && info.bitsPerSample < 24
            ) return false
            if (info.container == LxAudioHeadInspector.Container.MP3 &&
                info.bitrateBps != null && !info.vbr
            ) {
                return info.bitrateBps >= LxAudioHeadInspector.minPlausibleBitrateBps(quality)
            }
        }
        val duration = info?.durationSec?.takeIf { it > 0 } ?: fallbackDurationSec.takeIf { it > 0 }
        if (totalBytes != null && totalBytes > 0 && duration != null) {
            val avgBps = totalBytes * 8 / duration
            return avgBps >= LxAudioHeadInspector.minPlausibleBitrateBps(quality)
        }
        return true
    }

    companion object {
        private const val RESOLVE_TIMEOUT_MS = 12_000L
        private const val HEAD_FETCH_BYTES = 262_143 // 256KB - 1
    }
}

package com.minoppol.music.data.worker

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.minoppol.music.data.database.MusicDao
import com.minoppol.music.data.database.OfflineTrackDao
import com.minoppol.music.data.jellyfin.JellyfinRepository
import com.minoppol.music.data.lxmusic.LxAudioHeadInspector
import com.minoppol.music.data.lxmusic.LxMusicRepository
import com.minoppol.music.data.lxmusic.LxMusicUrlEvent
import com.minoppol.music.data.lxmusic.LxQualities
import com.minoppol.music.data.lxmusic.LxSong
import com.minoppol.music.data.lxmusic.LxSongMapper
import com.minoppol.music.data.media.LxDownloadTagWriter
import com.minoppol.music.data.navidrome.NavidromeRepository
import com.minoppol.music.data.offline.CloudOfflineRepository
import com.minoppol.music.data.offline.OfflineDownloadStatus
import com.minoppol.music.data.stream.CloudStreamSecurity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

@HiltWorker
class CloudTrackDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: OfflineTrackDao,
    private val musicDao: MusicDao,
    private val navidromeRepository: NavidromeRepository,
    private val jellyfinRepository: JellyfinRepository,
    private val lxMusicRepository: LxMusicRepository,
    private val tagWriter: LxDownloadTagWriter,
    private val downloadConcurrencyLimiter: DownloadConcurrencyLimiter,
    baseOkHttpClient: OkHttpClient
) : CoroutineWorker(appContext, workerParams) {
    private val client = baseOkHttpClient.newBuilder()
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID)
            ?: return@withContext Result.failure()
        val attemptId = inputData.getString(KEY_ATTEMPT_ID)
            ?: return@withContext Result.failure()
        val sourceUri = inputData.getString(KEY_SOURCE_URI)
            ?: return@withContext Result.failure()
        val entity = dao.getByDownloadId(downloadId)
            ?: return@withContext Result.success()
        if (entity.attemptId != attemptId || entity.sourceUri != sourceUri) {
            return@withContext Result.success()
        }
        if (!hasActiveNetwork()) {
            return@withContext Result.retry()
        }

        downloadConcurrencyLimiter.acquire()
        try {
            val now = System.currentTimeMillis()
            if (dao.updateState(
                    downloadId = downloadId,
                    attemptId = attemptId,
                    state = OfflineDownloadStatus.DOWNLOADING.storageValue,
                    bytesDownloaded = 0L,
                    totalBytes = null,
                    localPath = null,
                    errorMessage = null,
                    updatedAt = now
                ) == 0) return@withContext Result.success()

            if (sourceUri.startsWith("lxmusic://")) {
                executeLxDownload(downloadId, attemptId, sourceUri)
            } else {
                executeStandardDownload(downloadId, attemptId, sourceUri)
            }
        } finally {
            downloadConcurrencyLimiter.release()
        }
    }


    private suspend fun executeStandardDownload(
        downloadId: String,
        attemptId: String,
        sourceUri: String,
    ): Result {
        val tempFile = CloudOfflineRepository.downloadDirectory(applicationContext)
            .resolve("${CloudOfflineRepository.attemptFileStem(downloadId, attemptId)}.part")
        tempFile.delete()
        var finalizedFile: File? = null

        return try {
            val source = resolveSource(sourceUri)
            val requestBuilder = Request.Builder().url(source.url)
            source.headers.forEach { (name, value) -> requestBuilder.header(name, value) }

            client.newCall(requestBuilder.get().build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DownloadHttpException(response.code)
                }
                if (!CloudStreamSecurity.isSupportedAudioContentType(response.header("Content-Type"))) {
                    throw IOException("Server returned a non-audio response")
                }
                if (!CloudStreamSecurity.isAcceptableContentLength(response.header("Content-Length"))) {
                    throw IOException("Audio file is too large")
                }

                val body = response.body
                val total = body.contentLength().takeIf { it >= 0L }
                val extension = extensionFor(response.header("Content-Type"), dao.getByDownloadId(downloadId)?.mimeType)
                val finalFile = CloudOfflineRepository.downloadDirectory(applicationContext)
                    .resolve(
                        "${CloudOfflineRepository.attemptFileStem(downloadId, attemptId)}.$extension"
                    )
                var copied = 0L
                var lastPublished = 0L

                body.byteStream().use { input ->
                    tempFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            if (copied > CloudStreamSecurity.MAX_STREAM_CONTENT_LENGTH_BYTES) {
                                throw IOException("Audio file is too large")
                            }
                            if (copied - lastPublished >= PROGRESS_STEP_BYTES) {
                                publishProgress(downloadId, attemptId, copied, total)
                                lastPublished = copied
                            }
                        }
                    }
                }

                if (copied <= 0L) throw IOException("Downloaded file is empty")
                if (total != null && copied != total) {
                    throw IOException("Download ended early ($copied/$total bytes)")
                }
                coroutineContext.ensureActive()
                if (!dao.isCurrentAttempt(downloadId, attemptId)) {
                    throw StaleDownloadAttemptException()
                }
                finalFile.delete()
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
                finalizedFile = finalFile
                coroutineContext.ensureActive()
                val completed = dao.updateState(
                    downloadId = downloadId,
                    attemptId = attemptId,
                    state = OfflineDownloadStatus.COMPLETE.storageValue,
                    bytesDownloaded = copied,
                    totalBytes = total ?: copied,
                    localPath = finalFile.absolutePath,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
                if (completed == 0) {
                    finalFile.delete()
                }
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            tempFile.delete()
            finalizedFile?.delete()
            throw cancelled
        } catch (_: StaleDownloadAttemptException) {
            tempFile.delete()
            finalizedFile?.delete()
            Result.success()
        } catch (error: Throwable) {
            tempFile.delete()
            finalizedFile?.delete()
            failOrRetry(downloadId, attemptId, error)
        }
    }

    private fun resolveSource(sourceUri: String): DownloadSource {
        val parsed = sourceUri.toUri()
        val id = parsed.host ?: parsed.path?.removePrefix("/")
            ?: throw IOException("Cloud track identifier is missing")
        return when (parsed.scheme?.lowercase()) {
            "navidrome" -> {
                if (!CloudStreamSecurity.validateNavidromeSongId(id)) {
                    throw IOException("Invalid Navidrome track identifier")
                }
                DownloadSource(
                    url = navidromeRepository.getStreamUrl(id),
                    allowedHost = navidromeRepository.serverUrl
                )
            }
            "jellyfin" -> {
                if (!CloudStreamSecurity.validateJellyfinItemId(id)) {
                    throw IOException("Invalid Jellyfin item identifier")
                }
                DownloadSource(
                    url = jellyfinRepository.getStreamUrl(id),
                    headers = jellyfinRepository.getAuthorizationHeader()
                        ?.let { mapOf("Authorization" to it) }
                        .orEmpty(),
                    allowedHost = jellyfinRepository.serverUrl
                )
            }
            else -> throw IOException("Unsupported cloud provider")
        }.also { source ->
            val host = source.allowedHost
                ?.toHttpUrlOrNull()
                ?.host
                ?: throw IOException("Cloud account is not connected")
            if (!CloudStreamSecurity.isSafeRemoteStreamUrl(
                    url = source.url,
                    allowedHostSuffixes = setOf(host),
                    allowHttpForAllowedHosts = true
                )
            ) {
                throw IOException("Unsafe cloud download URL")
            }
        }
    }


    private suspend fun executeLxDownload(
        downloadId: String,
        attemptId: String,
        sourceUri: String,
    ): Result {
        val requestedQuality = inputData.getString(KEY_QUALITY)
            ?: return failOrRetry(downloadId, attemptId, IOException("Missing quality for lx download"))
        val preferredScript = inputData.getString(KEY_SCRIPT_PATH)
            ?: return failOrRetry(downloadId, attemptId, IOException("Missing script path for lx download"))
        val orderedScripts = (listOf(preferredScript) +
            inputData.getStringArray(KEY_SCRIPT_PATHS).orEmpty().toList()).distinct()

        val (source, songmid) = LxSongMapper.parseContentUri(sourceUri)
            ?: return failOrRetry(downloadId, attemptId, IOException("Bad lx uri: $sourceUri"))
        val entityTitle = dao.getByDownloadId(downloadId)?.title.orEmpty()
        val songEntity = musicDao.getSongIdByContentUri(sourceUri)
            ?.let { musicDao.getSongByIdOnce(it) }
        val lxSong = LxSong(
            source = source,
            songmid = songmid,
            name = songEntity?.title ?: entityTitle,
            singer = songEntity?.artistName.orEmpty(),
            albumName = songEntity?.albumName.orEmpty(),
            interval = (songEntity?.duration ?: 0L) / 1000L,
            pic = songEntity?.albumArtUriString,
            hash = songEntity?.lxHash,
        )

        val startIndex = LxQualities.ALL.indexOf(requestedQuality).let { if (it < 0) LxQualities.ALL.size - 1 else it }
        val qualityChain = LxQualities.ALL.drop(startIndex)
        val marked = dao.updateState(
            downloadId = downloadId,
            attemptId = attemptId,
            state = OfflineDownloadStatus.DOWNLOADING.storageValue,
            bytesDownloaded = 0L,
            totalBytes = null,
            localPath = null,
            errorMessage = null,
            updatedAt = System.currentTimeMillis(),
        )
        if (marked == 0) return Result.success()
        var sawFake = false
        var sawNetworkError = false
        var scriptRejectMessage: String? = null
        for (quality in qualityChain) {
            for (script in orderedScripts) {
                when (val attempt = attemptLxQuality(downloadId, attemptId, lxSong, quality, script)) {
                    is LxAttempt.Ok ->
                        return finalizeLxDownload(downloadId, attemptId, lxSong, quality, attempt)
                    LxAttempt.Fake -> {
                        sawFake = true
                        Timber.tag(TAG).w(
                            "lx download: %s from %s is fake/low-bitrate, downgrade",
                            quality, script,
                        )
                    }
                    LxAttempt.Unavailable -> sawNetworkError = true
                    is LxAttempt.ScriptRejected -> scriptRejectMessage = attempt.message
                }
                if (!dao.isCurrentAttempt(downloadId, attemptId)) {
                    return Result.success()
                }
            }
        }
        val error = scriptRejectMessage?.let { LxScriptRejectedException(it) }
            ?: if (sawFake && !sawNetworkError) {
                LxFakeQualityException("当前音源各音质档实际码率均不足，换个音源脚本试试")
            } else {
                IOException("All lx qualities failed to download")
            }
        return failOrRetry(downloadId, attemptId, error)
    }

    private sealed interface LxAttempt {
        data class Ok(val file: File, val bytes: Long, val audio: VerifiedAudio) : LxAttempt
        data object Fake : LxAttempt
        data object Unavailable : LxAttempt
        data class ScriptRejected(val message: String) : LxAttempt
    }

    private data class VerifiedAudio(
        val mediaStoreMime: String,
        val extension: String,
        val avgBitrateBps: Long,
    )

    private suspend fun attemptLxQuality(
        downloadId: String,
        attemptId: String,
        song: LxSong,
        quality: String,
        scriptPath: String,
    ): LxAttempt {
        val defaultExt = if (quality == LxQualities.Q128 || quality == LxQualities.Q320) "mp3" else "flac"
        val tempFile = CloudOfflineRepository.downloadDirectory(applicationContext)
            .resolve("${CloudOfflineRepository.attemptFileStem(downloadId, attemptId)}.$defaultExt.tmp")
        tempFile.delete()
        try {
            val event = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                runCatching { lxMusicRepository.resolveMusicUrlEvent(scriptPath, song, quality) }.getOrNull()
            } ?: return LxAttempt.Unavailable
            val url = when (event) {
                is LxMusicUrlEvent.ScriptRejected -> return LxAttempt.ScriptRejected(event.message)
                LxMusicUrlEvent.Unavailable -> return LxAttempt.Unavailable
                is LxMusicUrlEvent.Success -> event.url
            }

            var copied = 0L
            var lastPublished = 0L
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return LxAttempt.Unavailable
                val body = response.body
                val total = body.contentLength().takeIf { it >= 0L }
                body.byteStream().use { input ->
                    tempFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            if (copied > CloudStreamSecurity.MAX_STREAM_CONTENT_LENGTH_BYTES) {
                                throw IOException("Audio file is too large")
                            }
                            if (copied - lastPublished >= PROGRESS_STEP_BYTES) {
                                publishProgress(downloadId, attemptId, copied, total)
                                lastPublished = copied
                            }
                        }
                    }
                }
                if (copied <= 0L) return LxAttempt.Unavailable
                if (total != null && copied != total) {
                    throw IOException("Download ended early ($copied/$total bytes)")
                }
            }
            coroutineContext.ensureActive()
            if (!dao.isCurrentAttempt(downloadId, attemptId)) throw StaleDownloadAttemptException()

            val verified = verifyDownloadedFile(tempFile, quality, song.interval)
                ?: return LxAttempt.Fake
            val realFile = if (verified.extension != defaultExt) {
                val renamed = tempFile.resolveSibling(
                    "${CloudOfflineRepository.attemptFileStem(downloadId, attemptId)}.${verified.extension}.tmp"
                )
                renamed.delete()
                if (!tempFile.renameTo(renamed)) {
                    tempFile.copyTo(renamed, overwrite = true)
                    tempFile.delete()
                }
                renamed
            } else tempFile
            return LxAttempt.Ok(realFile, copied, verified)
        } catch (cancelled: CancellationException) {
            tempFile.delete()
            throw cancelled
        } catch (_: StaleDownloadAttemptException) {
            tempFile.delete()
            throw StaleDownloadAttemptException()
        } catch (error: Throwable) {
            tempFile.delete()
            Timber.tag(TAG).w(error, "lx quality %s via %s failed", quality, scriptPath)
            return LxAttempt.Unavailable
        }
    }

    private fun verifyDownloadedFile(file: File, requestedQuality: String, fallbackDurationSec: Long): VerifiedAudio? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    format = f
                    break
                }
            }
            val fmt = format ?: return null
            val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()

            if (LxAudioHeadInspector.expectsLossless(requestedQuality) &&
                mime == "audio/mpeg"
            ) return null

            if (requestedQuality == LxQualities.FLAC24 && mime.contains("flac")) {
                val bits = runCatching {
                    file.inputStream().use { input ->
                        val head = ByteArray(262_144)
                        var read = 0
                        while (read < head.size) {
                            val n = input.read(head, read, head.size - read)
                            if (n < 0) break
                            read += n
                        }
                        LxAudioHeadInspector.inspect(head.copyOf(read)).bitsPerSample
                    }
                }.getOrNull()
                if (bits != null && bits < 24) return null
            }

            val bitrate = if (fmt.containsKey(MediaFormat.KEY_BIT_RATE)) {
                fmt.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
            } else -1L
            val durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                fmt.getLong(MediaFormat.KEY_DURATION)
            } else -1L
            val avgBps = when {
                bitrate > 0L -> bitrate
                durationUs > 0L -> file.length() * 8L * 1_000_000L / durationUs
                fallbackDurationSec > 0L -> file.length() * 8L / fallbackDurationSec
                else -> -1L
            }
            if (avgBps in 0 until LxAudioHeadInspector.minPlausibleBitrateBps(requestedQuality)) return null

            val extension = when {
                mime.contains("flac") -> "flac"
                mime == "audio/mpeg" -> "mp3"
                mime.contains("mp4") || mime.contains("aac") -> "m4a"
                else -> if (requestedQuality == LxQualities.Q128 || requestedQuality == LxQualities.Q320) "mp3" else "flac"
            }
            val storeMime = when (extension) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                else -> "audio/flac"
            }
            return VerifiedAudio(storeMime, extension, avgBps)
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "verify downloaded file failed: %s", file.absolutePath)
            return null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private suspend fun finalizeLxDownload(
        downloadId: String,
        attemptId: String,
        lxSong: LxSong,
        actualQuality: String,
        attempt: LxAttempt.Ok,
    ): Result {
        val tempFile = attempt.file
        return try {
            runCatching { embedTags(lxSong, tempFile) }
                .onFailure { Timber.tag(TAG).w(it, "embed tags failed (non-fatal)") }

            val displayName = buildDisplayName(lxSong, attempt.audio.extension)
            val localUri = publishToMediaStore(tempFile, displayName, attempt.audio.mediaStoreMime, lxSong)
                ?: throw IOException("MediaStore insert failed")
            tempFile.delete()
            coroutineContext.ensureActive()

            val completed = dao.updateState(
                downloadId = downloadId,
                attemptId = attemptId,
                state = OfflineDownloadStatus.COMPLETE.storageValue,
                bytesDownloaded = attempt.bytes,
                totalBytes = attempt.bytes,
                localPath = localUri,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
            if (completed != 0) {
                dao.updateActualQuality(downloadId, attemptId, actualQuality)
            } else {
                runCatching {
                    applicationContext.contentResolver.delete(localUri.toUri(), null, null)
                }
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            tempFile.delete()
            throw cancelled
        } catch (error: Throwable) {
            tempFile.delete()
            failOrRetry(downloadId, attemptId, error)
        }
    }

    private suspend fun embedTags(lxSong: LxSong, file: File) {
        val coverBytes = lxSong.pic?.takeIf { it.isNotBlank() }?.let { url ->
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.bytes()?.takeIf { it.isNotEmpty() } else null
                }
            }.getOrNull()
        }
        val lyrics = runCatching { lxMusicRepository.fetchLyric(lxSong)?.lyric }.getOrNull()
        tagWriter.writeTags(
            file = file,
            title = lxSong.name,
            artist = lxSong.singer,
            album = lxSong.albumName,
            coverBytes = coverBytes,
            lyrics = lyrics,
        )
    }

    private fun publishToMediaStore(
        tempFile: File,
        displayName: String,
        mime: String,
        song: LxSong,
    ): String? {
        val resolver = applicationContext.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$LX_MUSIC_DIR")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.TITLE, song.name)
            put(MediaStore.Audio.Media.ARTIST, song.singer.ifBlank { UNKNOWN_ARTIST })
            put(MediaStore.Audio.Media.ALBUM, song.albumName.ifBlank { UNKNOWN_ALBUM })
            put(MediaStore.Audio.Media.DURATION, song.interval * 1000L)
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri, "w")?.use { out ->
                tempFile.inputStream().use { it.copyTo(out, DEFAULT_BUFFER_SIZE * 8) }
            } ?: throw IOException("openOutputStream returned null")
            val publish = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, publish, null, null)
            uri.toString()
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    private fun buildDisplayName(song: LxSong, extension: String): String {
        val artist = song.singer.ifBlank { UNKNOWN_ARTIST }.replace(ILLEGAL_FILE_CHARS, "_").trim()
        val title = song.name.ifBlank { song.songmid }.replace(ILLEGAL_FILE_CHARS, "_").trim()
        val raw = "$artist - $title"
        return "${raw.take(120)}.$extension"
    }


    private fun hasActiveNetwork(): Boolean {
        val manager = applicationContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return true
        return manager.activeNetwork != null
    }

    private suspend fun failOrRetry(downloadId: String, attemptId: String, error: Throwable): Result {
        val shouldRetry = runAttemptCount < MAX_RETRIES &&
            (error is IOException || (error is DownloadHttpException && error.code >= 500))
        Timber.tag(TAG).w(error, "Cloud track download failed: %s",
            dao.getByDownloadId(downloadId)?.sourceUri)
        val updated = dao.updateState(
            downloadId = downloadId,
            attemptId = attemptId,
            state = if (shouldRetry) {
                OfflineDownloadStatus.QUEUED.storageValue
            } else {
                OfflineDownloadStatus.FAILED.storageValue
            },
            bytesDownloaded = 0L,
            totalBytes = null,
            localPath = null,
            errorMessage = error.message ?: error.javaClass.simpleName,
            updatedAt = System.currentTimeMillis()
        )
        return when {
            updated == 0 -> Result.success()
            shouldRetry -> Result.retry()
            else -> Result.failure(
                workDataOf(KEY_ERROR to (error.message ?: "Download failed"))
            )
        }
    }

    private suspend fun publishProgress(
        downloadId: String,
        attemptId: String,
        copied: Long,
        total: Long?
    ) {
        val updated = dao.updateState(
            downloadId = downloadId,
            attemptId = attemptId,
            state = OfflineDownloadStatus.DOWNLOADING.storageValue,
            bytesDownloaded = copied,
            totalBytes = total,
            localPath = null,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        )
        if (updated == 0) throw StaleDownloadAttemptException()
        setProgress(workDataOf(KEY_BYTES to copied, KEY_TOTAL_BYTES to (total ?: -1L)))
    }

    private fun extensionFor(responseType: String?, fallbackType: String?): String {
        val type = responseType?.substringBefore(';')?.lowercase()
            ?: fallbackType?.substringBefore(';')?.lowercase()
        return when (type) {
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/mp4", "audio/m4a", "audio/x-m4a", "application/mp4", "video/mp4" -> "m4a"
            "audio/aac", "audio/aacp" -> "aac"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/webm" -> "webm"
            else -> "mp3"
        }
    }

    private data class DownloadSource(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val allowedHost: String?
    )

    private class DownloadHttpException(val code: Int) : IOException("Server returned HTTP $code")
    private class LxFakeQualityException(message: String) : Exception(message)

    private class LxScriptRejectedException(message: String) : Exception(message)
    private class StaleDownloadAttemptException : Exception()

    companion object {
        const val TAG = "cloud_track_download"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_ATTEMPT_ID = "attempt_id"
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_QUALITY = "quality"
        const val KEY_SCRIPT_PATH = "script_path"
        const val KEY_SCRIPT_PATHS = "script_paths"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error"
        private const val PROGRESS_STEP_BYTES = 512L * 1024L
        private const val MAX_RETRIES = 3
        private const val RESOLVE_TIMEOUT_MS = 25_000L
        private const val LX_MUSIC_DIR = "PixelPlayer"
        private const val UNKNOWN_ARTIST = "未知艺术家"
        private const val UNKNOWN_ALBUM = "未知专辑"
        private val ILLEGAL_FILE_CHARS = Regex("[/\\\\:*?\"<>|\\r\\n\\t]")
    }
}

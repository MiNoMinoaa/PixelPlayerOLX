package com.lostf1sh.pixelplayeross.data.offline

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lostf1sh.pixelplayeross.data.database.OfflineTrackDao
import com.lostf1sh.pixelplayeross.data.database.OfflineTrackEntity
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.worker.CloudTrackDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

enum class OfflineDownloadStatus(val storageValue: String) {
    QUEUED("queued"),
    DOWNLOADING("downloading"),
    COMPLETE("complete"),
    FAILED("failed");

    companion object {
        fun fromStorage(value: String): OfflineDownloadStatus =
            entries.firstOrNull { it.storageValue == value } ?: FAILED
    }
}

data class OfflineDownload(
    val downloadId: String,
    val sourceUri: String,
    val status: OfflineDownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val localPath: String?,
    val errorMessage: String?
) {
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0L }
            ?.let { (bytesDownloaded.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() }
}

@Singleton
class CloudOfflineRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: OfflineTrackDao,
    private val workManager: WorkManager
) {
    fun observe(song: Song): Flow<OfflineDownload?> = observe(song.contentUriString)

    fun observe(sourceUri: String): Flow<OfflineDownload?> =
        dao.observeBySourceUri(sourceUri).map { it?.toModel() }

    fun observeCompleted(): Flow<List<OfflineDownload>> =
        dao.observeCompleted().map { rows -> rows.map(OfflineTrackEntity::toModel) }

    suspend fun enqueue(song: Song) = withContext(Dispatchers.IO) {
        val provider = providerFor(song.contentUriString) ?: return@withContext
        val downloadId = downloadId(song.contentUriString)
        val existing = dao.getBySourceUri(song.contentUriString)
        if (existing?.state == OfflineDownloadStatus.COMPLETE.storageValue &&
            existing.localPath?.let(::File)?.isFile == true
        ) {
            return@withContext
        }

        val now = System.currentTimeMillis()
        dao.upsert(
            OfflineTrackEntity(
                downloadId = downloadId,
                songId = song.id,
                sourceUri = song.contentUriString,
                provider = provider,
                title = song.title,
                mimeType = song.mimeType,
                localPath = null,
                state = OfflineDownloadStatus.QUEUED.storageValue,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )

        val request = OneTimeWorkRequestBuilder<CloudTrackDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setInputData(
                workDataOf(
                    CloudTrackDownloadWorker.KEY_DOWNLOAD_ID to downloadId,
                    CloudTrackDownloadWorker.KEY_SOURCE_URI to song.contentUriString
                )
            )
            .addTag(CloudTrackDownloadWorker.TAG)
            .addTag(workName(downloadId))
            .build()

        workManager.enqueueUniqueWork(
            workName(downloadId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun enqueueAll(songs: Collection<Song>) {
        songs.asSequence()
            .filter { isCloudSong(it) }
            .distinctBy { it.contentUriString }
            .forEach { enqueue(it) }
    }

    suspend fun remove(song: Song) = remove(song.contentUriString)

    suspend fun remove(sourceUri: String) = withContext(Dispatchers.IO) {
        val entity = dao.getBySourceUri(sourceUri) ?: return@withContext
        workManager.cancelUniqueWork(workName(entity.downloadId))
        entity.localPath?.let(::File)?.takeIf { it.exists() }?.delete()
        downloadDirectory(context).resolve("${entity.downloadId}.part").takeIf { it.exists() }?.delete()
        dao.deleteBySourceUri(sourceUri)
    }

    /** Called on ExoPlayer's loading thread; Room I/O is dispatched by the caller. */
    suspend fun resolveLocalUri(sourceUri: String): Uri? = withContext(Dispatchers.IO) {
        val entity = dao.getBySourceUri(sourceUri) ?: return@withContext null
        if (entity.state != OfflineDownloadStatus.COMPLETE.storageValue) return@withContext null
        val file = entity.localPath?.let(::File)
        if (file?.isFile == true && file.length() > 0L) {
            Uri.fromFile(file)
        } else {
            dao.deleteBySourceUri(sourceUri)
            null
        }
    }

    companion object {
        fun isCloudSong(song: Song): Boolean = providerFor(song.contentUriString) != null

        fun providerFor(sourceUri: String): String? = when (sourceUri.substringBefore(':', "").lowercase()) {
            "navidrome" -> "navidrome"
            "jellyfin" -> "jellyfin"
            else -> null
        }

        fun downloadId(sourceUri: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(sourceUri.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        fun workName(downloadId: String): String = "cloud_track_download_$downloadId"

        fun downloadDirectory(context: Context): File =
            File(context.filesDir, "cloud_downloads").apply { mkdirs() }
    }
}

private fun OfflineTrackEntity.toModel() = OfflineDownload(
    downloadId = downloadId,
    sourceUri = sourceUri,
    status = OfflineDownloadStatus.fromStorage(state),
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    localPath = localPath,
    errorMessage = errorMessage
)

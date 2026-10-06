package com.minoppol.music.presentation.viewmodel

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minoppol.music.R
import com.minoppol.music.data.database.MusicDao
import com.minoppol.music.data.database.toArtist
import com.minoppol.music.data.lxmusic.LxDownloadQualityProber
import com.minoppol.music.data.lxmusic.LxMusicRepository
import com.minoppol.music.data.lxmusic.LxQualities
import com.minoppol.music.data.lxmusic.LxSong
import com.minoppol.music.data.lxmusic.LxSongMapper
import com.minoppol.music.data.lxmusic.LxSources
import com.minoppol.music.data.model.Artist
import com.minoppol.music.data.model.Song
import com.minoppol.music.data.offline.CloudOfflineRepository
import com.minoppol.music.data.offline.OfflineDownload
import com.minoppol.music.data.offline.OfflineDownloadStatus
import com.minoppol.music.data.preferences.UserPreferencesRepository
import com.minoppol.music.utils.AudioMeta
import com.minoppol.music.utils.AudioMetaUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

@HiltViewModel
class SongInfoBottomSheetViewModel @Inject constructor(
    private val musicDao: MusicDao,
    private val cloudOfflineRepository: CloudOfflineRepository,
    private val lxMusicRepository: LxMusicRepository,
    private val lxDownloadQualityProber: LxDownloadQualityProber,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    data class SongLocationInfo(
        val label: String,
        val value: String,
        val isCloud: Boolean,
    )

    enum class ToneTarget {
        Ringtone,
        Notification,
        Alarm,
    }

    sealed interface ToneActionResult {
        data class Success(val message: String) : ToneActionResult
        data class NeedsSystemWritePermission(val message: String) : ToneActionResult
        data class Error(val message: String) : ToneActionResult
    }

    private val _audioMeta = MutableStateFlow<AudioMeta?>(null)
    private val _resolvedArtists = MutableStateFlow<ImmutableList<Artist>>(persistentListOf())
    private val _offlineDownload = MutableStateFlow<OfflineDownload?>(null)
    private var offlineObservationJob: Job? = null
    val resolvedArtists: StateFlow<ImmutableList<Artist>> = _resolvedArtists.asStateFlow()

    val audioMeta: StateFlow<AudioMeta?> = _audioMeta.asStateFlow()
    val offlineDownload: StateFlow<OfflineDownload?> = _offlineDownload.asStateFlow()

    fun bindSong(song: Song) {
        offlineObservationJob?.cancel()
        _offlineDownload.value = null
        if (!CloudOfflineRepository.isCloudSong(song)) return
        offlineObservationJob = viewModelScope.launch {
            cloudOfflineRepository.observe(song).collect { download ->
                _offlineDownload.value = download
                val pending = pendingLxRequest
                if (download != null && pending != null && pending.first == song.contentUriString) {
                    when (download.status) {
                        OfflineDownloadStatus.COMPLETE -> {
                            val actual = download.quality
                            pendingLxRequest = null
                            if (actual != null && actual != pending.second) {
                                _toastEvents.tryEmit(
                                    appContext.getString(
                                        R.string.lx_msg_download_downgraded,
                                        appContext.getString(qualityLabelRes(pending.second)),
                                        appContext.getString(qualityLabelRes(actual)),
                                    )
                                )
                            }
                        }
                        OfflineDownloadStatus.FAILED -> pendingLxRequest = null
                        else -> Unit
                    }
                }
            }
        }
    }

    fun toggleOfflineDownload(song: Song) {
        if (!CloudOfflineRepository.isCloudSong(song)) return
        viewModelScope.launch {
            if (_offlineDownload.value != null) {
                cloudOfflineRepository.remove(song)
            } else {
                cloudOfflineRepository.enqueue(song)
            }
        }
    }

    fun retryOfflineDownload(song: Song) {
        viewModelScope.launch { cloudOfflineRepository.enqueue(song) }
    }


    data class LxDownloadDialogState(
        val song: Song,
        val lxSong: LxSong,
        val probes: ImmutableMap<String, LxDownloadQualityProber.QualityProbe>,
        val selectedQuality: String?,
        val allProbed: Boolean,
    ) {
        val canDownload: Boolean
            get() = selectedQuality
                ?.let { probes[it]?.availability == LxDownloadQualityProber.QualityAvailability.AVAILABLE }
                ?: false

        val selectedAssetPath: String?
            get() = selectedQuality?.let { probes[it]?.assetPath }
    }

    private val _downloadQualityDialog = MutableStateFlow<LxDownloadDialogState?>(null)
    val downloadQualityDialog: StateFlow<LxDownloadDialogState?> =
        _downloadQualityDialog.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private var probeJob: Job? = null

    private var pendingLxRequest: Pair<String, String>? = null

    private fun qualityLabelRes(quality: String): Int = when (quality) {
        LxQualities.MASTER -> R.string.lxmusic_quality_master
        LxQualities.ATMOS -> R.string.lxmusic_quality_atmos
        LxQualities.FLAC24 -> R.string.lxmusic_quality_flac24bit
        LxQualities.FLAC -> R.string.lxmusic_quality_flac
        LxQualities.Q320 -> R.string.lxmusic_quality_320k
        LxQualities.Q128 -> R.string.lxmusic_quality_128k
        else -> R.string.lxmusic_quality_320k
    }

    fun isLxOnlineSong(song: Song): Boolean = song.contentUriString.startsWith("lxmusic://")

    fun onDownloadAction(song: Song) {
        if (!isLxOnlineSong(song)) {
            toggleOfflineDownload(song)
            return
        }
        if (_offlineDownload.value != null) {
            toggleOfflineDownload(song)
            return
        }
        val lxSong = LxSongMapper.toLxSong(song) ?: return
        val scripts = lxMusicRepository.activeRuntimeScriptPathsOrdered(lxSong.source)
        if (scripts.isEmpty()) {
            _toastEvents.tryEmit(appContext.getString(R.string.lx_msg_download_needs_script))
            return
        }
        openQualityDialog(song, lxSong, scripts)
    }

    fun lxDownloadEnabled(song: Song): Boolean {
        if (!isLxOnlineSong(song)) return CloudOfflineRepository.isCloudSong(song)
        if (_offlineDownload.value != null) return true
        val lxSong = LxSongMapper.toLxSong(song) ?: return false
        return lxMusicRepository.activeRuntimeScriptPathsOrdered(lxSong.source).isNotEmpty()
    }

    private fun openQualityDialog(song: Song, lxSong: LxSong, scripts: List<String>) {
        probeJob?.cancel()
        val cached = lxDownloadQualityProber.cached(lxSong) ?: persistentMapOf()
        val initialSelected = cached.values
            .filter { it.availability == LxDownloadQualityProber.QualityAvailability.AVAILABLE }
            .maxByOrNull { probe -> LxQualities.ALL.indexOf(probe.quality) }
            ?.quality
        _downloadQualityDialog.value = LxDownloadDialogState(
            song = song,
            lxSong = lxSong,
            probes = cached.toImmutableMap(),
            selectedQuality = initialSelected,
            allProbed = cached.size >= LxQualities.ALL.size,
        )
        probeJob = viewModelScope.launch {
            val preferred = runCatching {
                userPreferencesRepository.lxMusicPreferredQualityFlow.first()
            }.getOrDefault(LxQualities.Q320)
            val ordered = (listOf(preferred) + LxQualities.ALL).distinct()
            lxDownloadQualityProber.probe(lxSong, ordered, scripts) { probe ->
                val current = _downloadQualityDialog.value ?: return@probe
                if (current.song.contentUriString != song.contentUriString) return@probe
                val probes = (current.probes + (probe.quality to probe)).toImmutableMap()
                val keepSelected = current.selectedQuality
                    ?.takeIf { probes[it]?.availability == LxDownloadQualityProber.QualityAvailability.AVAILABLE }
                val autoSelected = keepSelected
                    ?: probe.takeIf {
                        it.availability == LxDownloadQualityProber.QualityAvailability.AVAILABLE
                    }?.quality
                _downloadQualityDialog.value = current.copy(
                    probes = probes,
                    selectedQuality = autoSelected,
                    allProbed = probes.size >= LxQualities.ALL.size,
                )
            }
            _downloadQualityDialog.value = _downloadQualityDialog.value?.copy(allProbed = true)
        }
    }

    fun selectDownloadQuality(quality: String) {
        val current = _downloadQualityDialog.value ?: return
        if (current.probes[quality]?.availability !=
            LxDownloadQualityProber.QualityAvailability.AVAILABLE
        ) return
        _downloadQualityDialog.value = current.copy(selectedQuality = quality)
    }

    fun dismissDownloadQualityDialog() {
        probeJob?.cancel()
        _downloadQualityDialog.value = null
    }

    fun confirmLxDownload() {
        val state = _downloadQualityDialog.value ?: return
        val quality = state.selectedQuality ?: return
        val assetPath = state.selectedAssetPath ?: return
        val song = state.song
        val lxSong = state.lxSong
        probeJob?.cancel()
        _downloadQualityDialog.value = null
        viewModelScope.launch {
            runCatching { lxMusicRepository.persistLxSong(lxSong) }
            pendingLxRequest = song.contentUriString to quality
            val scripts = lxMusicRepository.activeRuntimeScriptPathsOrdered(lxSong.source)
            cloudOfflineRepository.enqueue(song, quality, assetPath, scripts)
        }
    }

    fun loadArtistsForSong(song: Song) {
        val refs = song.artists
        if (refs.isEmpty() || refs.size < 2) {
            _resolvedArtists.value = persistentListOf()
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ids = refs.map { it.id }.filter { it > 0L }.distinct()
            val entitiesById = if (ids.isNotEmpty()) {
                musicDao.getArtistsByIds(ids).associateBy { it.id }
            } else {
                emptyMap()
            }
            val resolved = refs.map { ref ->
                entitiesById[ref.id]?.toArtist()
                    ?: Artist(id = ref.id, name = ref.name, songCount = 0)
            }
            _resolvedArtists.value = resolved.toImmutableList()
        }
    }

    fun loadAudioMeta(song: Song) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val meta = AudioMetaUtils.getAudioMetadata(
                musicDao = musicDao,
                id = song.id.toLongOrNull() ?: -1L,
                filePath = song.path,
                deepScan = false
            )
            _audioMeta.value = meta
        }
    }

    fun getSongLocationInfo(song: Song): SongLocationInfo {
        val provider = getCloudProviderLabel(song.contentUriString)
        return when {
            provider != null -> SongLocationInfo(
                label = "Provider",
                value = provider,
                isCloud = true,
            )
            !song.lxMusicSource.isNullOrBlank() -> SongLocationInfo(
                label = LxSources.englishName(song.lxMusicSource!!),
                value = LxSongMapper.webSongUrl(song) ?: song.contentUriString.orEmpty(),
                isCloud = true,
            )
            else -> SongLocationInfo(
                label = "Path",
                value = song.path,
                isCloud = false,
            )
        }
    }

    fun hasSystemWritePermission(): Boolean {
        return Settings.System.canWrite(appContext)
    }

    fun createSystemWriteSettingsIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = "package:${appContext.packageName}".toUri()
        }
    }

    fun setSongAsTone(song: Song, target: ToneTarget, onComplete: (ToneActionResult) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                setSongAsToneInternal(song, target)
            }
            onComplete(result)
        }
    }

    fun isSongEditable(song: Song): Boolean {
        if (getCloudProviderLabel(song.contentUriString) != null) return false

        if (song.path.isNotBlank()) {
            val file = File(song.path)
            return file.exists() && file.isFile
        }

        val uri = song.contentUriString
        return uri.startsWith("content://") || uri.startsWith("file://")
    }

    private fun getCloudProviderLabel(contentUriString: String): String? {
        val normalized = contentUriString.lowercase().trim()
        return when {
            normalized.startsWith("navidrome://") || normalized.startsWith("navidrome:") -> "Navidrome"
            normalized.startsWith("jellyfin://") || normalized.startsWith("jellyfin:") -> "Jellyfin"
            else -> null
        }
    }

    private suspend fun setSongAsToneInternal(song: Song, target: ToneTarget): ToneActionResult {
        if (getCloudProviderLabel(song.contentUriString) != null) {
            return ToneActionResult.Error(
                appContext.getString(R.string.song_info_ringtone_local_only)
            )
        }

        val ringtoneUri = runCatching { resolveMediaStoreAudioUri(song) }.getOrNull()
            ?: return ToneActionResult.Error(
                appContext.getString(R.string.song_info_ringtone_missing_file)
            )

        if (!Settings.System.canWrite(appContext)) {
            return ToneActionResult.NeedsSystemWritePermission(
                appContext.getString(R.string.song_info_ringtone_permission_prompt)
            )
        }

        return runCatching {
            markAsToneCandidate(ringtoneUri, target)
            RingtoneManager.setActualDefaultRingtoneUri(
                appContext,
                target.ringtoneManagerType,
                ringtoneUri,
            )
            ToneActionResult.Success(
                appContext.getString(
                    R.string.song_info_tone_success,
                    song.title,
                    appContext.getString(target.successLabelResId),
                )
            )
        }.getOrElse { throwable ->
            ToneActionResult.Error(
                appContext.getString(
                    R.string.song_info_ringtone_failed,
                    throwable.localizedMessage ?: throwable.javaClass.simpleName
                )
            )
        }
    }

    private suspend fun resolveMediaStoreAudioUri(song: Song): Uri? {
        song.id.toLongOrNull()
            ?.takeIf { it > 0L }
            ?.let { id ->
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            }
            ?.takeIf(::mediaStoreAudioExists)
            ?.let { return it }

        song.contentUriString
            .takeIf { it.startsWith("content://") }
            ?.toUri()
            ?.takeIf { it.authority == MediaStore.AUTHORITY }
            ?.let { return it }

        findMediaStoreAudioUriByPath(song.path)?.let { return it }

        val file = File(song.path)
        if (!file.exists()) return null

        return scanAudioFile(file, song.mimeType)
            ?.takeIf { it.authority == MediaStore.AUTHORITY }
            ?: findMediaStoreAudioUriByPath(song.path)
    }

    private fun findMediaStoreAudioUriByPath(path: String): Uri? {
        if (path.isBlank()) return null
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(path)

        return runCatching {
            appContext.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        }.getOrNull()
    }

    private suspend fun scanAudioFile(file: File, mimeType: String?): Uri? =
        suspendCancellableCoroutine { continuation ->
            val mimeTypes = mimeType
                ?.takeIf { it.isNotBlank() }
                ?.let { arrayOf(it) }
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(file.absolutePath),
                mimeTypes,
            ) { _, uri ->
                if (continuation.isActive) {
                    continuation.resume(uri)
                }
            }
        }

    private fun mediaStoreAudioExists(uri: Uri): Boolean {
        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Media._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                cursor.moveToFirst()
            } == true
        }.getOrDefault(false)
    }

    private fun markAsToneCandidate(uri: Uri, target: ToneTarget) {
        runCatching {
            val values = ContentValues().apply {
                when (target) {
                    ToneTarget.Ringtone -> put(MediaStore.Audio.Media.IS_RINGTONE, true)
                    ToneTarget.Notification -> put(MediaStore.Audio.Media.IS_NOTIFICATION, true)
                    ToneTarget.Alarm -> put(MediaStore.Audio.Media.IS_ALARM, true)
                }
            }
            appContext.contentResolver.update(uri, values, null, null)
        }
    }

    private val ToneTarget.ringtoneManagerType: Int
        get() = when (this) {
            ToneTarget.Ringtone -> RingtoneManager.TYPE_RINGTONE
            ToneTarget.Notification -> RingtoneManager.TYPE_NOTIFICATION
            ToneTarget.Alarm -> RingtoneManager.TYPE_ALARM
        }

    private val ToneTarget.successLabelResId: Int
        get() = when (this) {
            ToneTarget.Ringtone -> R.string.song_info_tone_ringtone_label
            ToneTarget.Notification -> R.string.song_info_tone_notification_label
            ToneTarget.Alarm -> R.string.song_info_tone_alarm_label
        }
}

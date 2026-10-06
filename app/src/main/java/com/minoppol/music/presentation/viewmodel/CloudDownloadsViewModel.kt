package com.minoppol.music.presentation.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minoppol.music.R
import com.minoppol.music.data.cache.PlaybackCacheManager
import com.minoppol.music.data.lxmusic.LxMusicRepository
import com.minoppol.music.data.lxmusic.LxQualities
import com.minoppol.music.data.lxmusic.LxSongMapper
import com.minoppol.music.data.offline.CloudOfflineRepository
import com.minoppol.music.data.offline.OfflineDownload
import com.minoppol.music.data.offline.OfflineDownloadStatus
import com.minoppol.music.data.model.Song
import com.minoppol.music.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@Immutable
data class CloudDownloadsUiState(
    val completed: ImmutableList<OfflineDownload> = persistentListOf(),
    val downloading: ImmutableList<OfflineDownload> = persistentListOf(),
    val queued: ImmutableList<OfflineDownload> = persistentListOf(),
    val failed: ImmutableList<OfflineDownload> = persistentListOf(),
    val usedBytes: Long = 0L
) {
    val totalCount: Int get() = completed.size + downloading.size + queued.size + failed.size
}

internal fun List<OfflineDownload>.toCloudDownloadsUiState(): CloudDownloadsUiState =
    CloudDownloadsUiState(
        completed = filter { it.status == OfflineDownloadStatus.COMPLETE }.toImmutableList(),
        downloading = filter { it.status == OfflineDownloadStatus.DOWNLOADING }.toImmutableList(),
        queued = filter { it.status == OfflineDownloadStatus.QUEUED }.toImmutableList(),
        failed = filter { it.status == OfflineDownloadStatus.FAILED }.toImmutableList(),
        usedBytes = asSequence()
            .filter {
                it.status == OfflineDownloadStatus.COMPLETE ||
                    it.status == OfflineDownloadStatus.DOWNLOADING
            }
            .sumOf { it.bytesDownloaded.coerceAtLeast(0L) }
    )

@HiltViewModel
class CloudDownloadsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: CloudOfflineRepository,
    private val playbackCacheManager: PlaybackCacheManager,
    private val lxMusicRepository: LxMusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    val uiState: StateFlow<CloudDownloadsUiState> = repository.observeAll()
        .map(List<OfflineDownload>::toCloudDownloadsUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CloudDownloadsUiState()
        )

    private val _downloadEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val downloadEvents: SharedFlow<String> = _downloadEvents.asSharedFlow()

    private val _playbackCacheSpaceBytes = MutableStateFlow(0L)
    val playbackCacheSpaceBytes: StateFlow<Long> = _playbackCacheSpaceBytes.asStateFlow()

    val playbackCacheLimitMb: StateFlow<Int> = playbackCacheManager.limitMbFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = 1024)

    val downloadMaxThreads: StateFlow<Int> = userPreferencesRepository.downloadMaxThreadsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserPreferencesRepository.DEFAULT_DOWNLOAD_MAX_THREADS,
        )

    init {
        refreshPlaybackCacheSpace()
    }

    fun refreshPlaybackCacheSpace() {
        _playbackCacheSpaceBytes.value = playbackCacheManager.cacheSpaceBytes()
    }

    fun setPlaybackCacheLimitMb(limitMb: Int) {
        viewModelScope.launch { playbackCacheManager.setLimitMb(limitMb) }
    }

    fun setDownloadMaxThreads(threads: Int) {
        viewModelScope.launch { userPreferencesRepository.setDownloadMaxThreads(threads) }
    }

    fun clearPlaybackCache() {
        viewModelScope.launch {
            playbackCacheManager.clearAll()
            refreshPlaybackCacheSpace()
        }
    }

    fun remove(download: OfflineDownload) {
        viewModelScope.launch { repository.remove(download.sourceUri) }
    }

    fun retry(download: OfflineDownload) {
        if (download.status != OfflineDownloadStatus.FAILED) return
        viewModelScope.launch { repository.retry(download.sourceUri) }
    }

    fun downloadSelected(songs: List<Song>, quality: String? = null) {
        val cloudSongs = CloudOfflineRepository.downloadCandidates(songs)
        if (cloudSongs.isEmpty()) return
        viewModelScope.launch {
            val (lxUiSongs, standardSongs) = cloudSongs.partition {
                it.contentUriString.startsWith("lxmusic://")
            }
            var successCount = 0
            var failCount = 0

            standardSongs.forEach { song ->
                runCatching { repository.enqueue(song) }
                    .onSuccess { successCount++ }
                    .onFailure { failCount++ }
            }

            if (lxUiSongs.isNotEmpty()) {
                val activeScriptPath = lxMusicRepository.activeScriptPath()
                if (activeScriptPath == null) {
                    _downloadEvents.tryEmit(appContext.getString(R.string.lx_msg_download_needs_script))
                } else {
                    val effectiveQuality = quality ?: LxQualities.Q320
                    lxUiSongs.forEach { uiSong ->
                        val lxSong = LxSongMapper.toLxSong(uiSong)
                        if (lxSong == null) {
                            failCount++
                            return@forEach
                        }
                        runCatching {
                            lxMusicRepository.persistLxSong(lxSong)
                            val scripts = lxMusicRepository.activeRuntimeScriptPathsOrdered(lxSong.source)
                            val assetPath = scripts.firstOrNull()
                            if (assetPath != null) {
                                repository.enqueue(uiSong, effectiveQuality, assetPath, scripts)
                                successCount++
                            } else {
                                failCount++
                            }
                        }.onFailure { e ->
                            Timber.w(e, "downloadSelected: failed to enqueue lx song ${uiSong.contentUriString}")
                            failCount++
                        }
                    }
                }
            }

            val enqueuedTotal = successCount + failCount
            if (enqueuedTotal > 0) {
                val msg = when {
                    failCount == 0 -> appContext.getString(R.string.lx_msg_batch_download_started, successCount)
                    successCount == 0 -> appContext.getString(R.string.lx_msg_batch_download_failed)
                    else -> appContext.getString(R.string.lx_msg_batch_download_partial, successCount, failCount)
                }
                _downloadEvents.tryEmit(msg)
            }
        }
    }
}

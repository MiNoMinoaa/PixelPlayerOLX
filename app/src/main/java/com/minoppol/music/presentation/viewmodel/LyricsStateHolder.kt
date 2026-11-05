package com.minoppol.music.presentation.viewmodel

import com.minoppol.music.R
import com.minoppol.music.data.media.AudioMetadataReader
import com.minoppol.music.data.media.CoverArtUpdate
import com.minoppol.music.data.media.SongMetadataEditor
import com.minoppol.music.data.model.Lyrics
import com.minoppol.music.data.model.LyricsSourcePreference
import com.minoppol.music.data.model.Song
import com.minoppol.music.data.preferences.UserPreferencesRepository
import com.minoppol.music.data.repository.LyricsSearchEntry
import com.minoppol.music.data.repository.LyricsSearchResult
import com.minoppol.music.data.repository.MusicRepository
import com.minoppol.music.data.repository.NoLyricsFoundException
import com.minoppol.music.data.repository.PlatformLyricsFetch
import com.minoppol.music.data.repository.PlatformLyricsSearchEntry
import com.minoppol.music.utils.LyricsImportSecurity
import com.minoppol.music.utils.LyricsImportValidationResult
import com.minoppol.music.utils.LyricsUtils
import com.minoppol.music.utils.ValidatedLyricsImport
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.collections.immutable.toImmutableList

/**
 * Callback interface for lyrics loading results.
 * Used to update StablePlayerState in PlayerViewModel.
 */
interface LyricsLoadCallback {
    fun onLoadingStarted(songId: String)
    fun onLyricsLoaded(songId: String, lyrics: Lyrics?)
}

/**
 * Manages lyrics loading, search state, and sync offset.
 * Extracted from PlayerViewModel to improve modularity.
 */
@Singleton
class LyricsStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val songMetadataEditor: SongMetadataEditor
) {
    private var scope: CoroutineScope? = null
    private var loadingJob: Job? = null
    private var probeJob: Job? = null
    private var lastPick: LyricsSearchUiState.PickResult? = null
    private var loadCallback: LyricsLoadCallback? = null

    private val _currentSongSyncOffset = MutableStateFlow(0)
    val currentSongSyncOffset: StateFlow<Int> = _currentSongSyncOffset.asStateFlow()

    private val _searchUiState = MutableStateFlow<LyricsSearchUiState>(LyricsSearchUiState.Idle)
    val searchUiState: StateFlow<LyricsSearchUiState> = _searchUiState.asStateFlow()

    private val _songUpdates = kotlinx.coroutines.flow.MutableSharedFlow<Pair<Song, Lyrics?>>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val songUpdates = _songUpdates.asSharedFlow()

    private val _messageEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val messageEvents = _messageEvents.asSharedFlow()

    /**
     * Initialize with coroutine scope and callback from ViewModel.
     */
    fun initialize(
        coroutineScope: CoroutineScope,
        callback: LyricsLoadCallback,
        stablePlayerState: StateFlow<com.minoppol.music.presentation.viewmodel.StablePlayerState>
    ) {
        scope = coroutineScope
        loadCallback = callback

        coroutineScope.launch {
            stablePlayerState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .collect { songId ->
                    if (songId != null) {
                        updateSyncOffsetForSong(songId)
                    }
                }
        }
    }

    /**
     * Load lyrics for a song.
     * @param song The song to load lyrics for
     * @param sourcePreference The preferred source for lyrics
     */
    fun loadLyricsForSong(song: Song, sourcePreference: LyricsSourcePreference) {
        loadingJob?.cancel()
        val targetSongId = song.id

        loadingJob = scope?.launch {
            loadCallback?.onLoadingStarted(targetSongId)

            val fetchedLyrics = try {
                withContext(Dispatchers.IO) {
                    musicRepository.getLyrics(
                        song = song,
                        sourcePreference = sourcePreference
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }

            loadCallback?.onLyricsLoaded(targetSongId, fetchedLyrics)
        }
    }

    /**
     * Cancel any ongoing lyrics loading.
     */
    fun cancelLoading() {
        loadingJob?.cancel()
    }

    /**
     * Set sync offset for a song.
     */
    fun setSyncOffset(songId: String, offsetMs: Int) {
        scope?.launch {
            userPreferencesRepository.setLyricsSyncOffset(songId, offsetMs)
            _currentSongSyncOffset.value = offsetMs
        }
    }

    /**
     * Update sync offset from song ID (called when song changes).
     */
    suspend fun updateSyncOffsetForSong(songId: String) {
        val offset = userPreferencesRepository.getLyricsSyncOffset(songId)
        _currentSongSyncOffset.value = offset
    }

    /**
     * Reset the lyrics search state to idle.
     */
    fun resetSearchState() {
        probeJob?.cancel()
        lastPick = null
        _searchUiState.value = LyricsSearchUiState.Idle
    }

    fun markNoMatch() {
        probeJob?.cancel()
        lastPick = null
        _searchUiState.value = LyricsSearchUiState.NotFound("Lyrics not found")
    }

    /**
     * Fetch lyrics for the given song, respecting the user's source preference.
     */
    fun fetchLyricsForSong(
        song: Song,
        forcePickResults: Boolean,
        sourcePreference: LyricsSourcePreference,
        contextHelper: (Int) -> String
    ) {
        loadingJob?.cancel()
        probeJob?.cancel()
        loadingJob = scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Loading

            if (!forcePickResults) {
                val storedLyrics = withContext(Dispatchers.IO) {
                    musicRepository.getStoredLyrics(song)
                }
                if (storedLyrics != null) {
                    val (lyrics, rawLyrics) = storedLyrics
                    _searchUiState.value = LyricsSearchUiState.Success(lyrics)
                    _songUpdates.emit(song.withPersistedLyrics(rawLyrics, refreshedAlbumArtUri = null) to lyrics)
                    _messageEvents.emit(contextHelper(R.string.lyrics_already_available))
                    return@launch
                }
            }

            val localSourceChecks: List<suspend () -> Pair<String, Int>?> = when (sourcePreference) {
                LyricsSourcePreference.API_FIRST -> emptyList()
                LyricsSourcePreference.EMBEDDED_FIRST -> listOf(
                    { readEmbeddedLyricsFromFile(song)?.let { it to R.string.lyrics_embedded_already_available } },
                    { readLocalLyricsFile(song)?.let { it to R.string.local_lrc_already_available } }
                )
                LyricsSourcePreference.LOCAL_FIRST -> listOf(
                    { readLocalLyricsFile(song)?.let { it to R.string.local_lrc_already_available } },
                    { readEmbeddedLyricsFromFile(song)?.let { it to R.string.lyrics_embedded_already_available } }
                )
            }

            for (sourceCheck in localSourceChecks) {
                val result = withContext(Dispatchers.IO) { sourceCheck() }
                if (result != null) {
                    val (rawLyrics, messageResId) = result
                    val parsed = LyricsUtils.parseLyrics(rawLyrics)
                    if (hasValidLyrics(parsed)) {
                        val lyrics = parsed.copy(areFromRemote = false)
                        _searchUiState.value = LyricsSearchUiState.Success(lyrics)

                        val songId = song.id.toLongOrNull()
                        if (songId != null) {
                            musicRepository.updateLyrics(songId, rawLyrics)
                        }

                        _songUpdates.emit(song.copy(lyrics = rawLyrics) to lyrics)
                        _messageEvents.emit(contextHelper(messageResId))
                        return@launch
                    }
                }
            }

            if (forcePickResults) {
                musicRepository.searchRemoteLyrics(song)
                    .onSuccess { (query, results) ->
                        setPickResult(query, results)
                    }
                    .onFailure { error ->
                        handleError(error)
                    }
            } else {
                musicRepository.getLyricsFromRemote(song)
                    .onSuccess { (lyrics, rawLyrics) ->
                        _searchUiState.value = LyricsSearchUiState.Success(lyrics)
                        val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(song, rawLyrics)
                        val updatedSong = song.withPersistedLyrics(rawLyrics, refreshedAlbumArtUri)
                        _songUpdates.emit(updatedSong to lyrics)
                    }
                    .onFailure { error ->
                        if (error is NoLyricsFoundException) {
                            musicRepository.searchRemoteLyrics(song)
                                .onSuccess { (query, results) ->
                                    setPickResult(query, results)
                                }
                                .onFailure { searchError -> handleError(searchError) }
                        } else {
                            handleError(error)
                        }
                    }
            }
        }
    }

    /**
     * Manual search by query.
     */
    fun searchLyricsManually(title: String, artist: String?) {
        if (title.isBlank()) return
        loadingJob?.cancel()
        probeJob?.cancel()
        loadingJob = scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Loading
            musicRepository.searchRemoteLyricsByQuery(title, artist)
                .onSuccess { (q, results) ->
                    setPickResult(q, results)
                }
                .onFailure { error -> handleError(error) }
        }
    }

    /**
     * Accept a search result.
     */
    fun acceptLyricsSearchResult(result: LyricsSearchResult, currentSong: Song) {
        scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Success(result.lyrics)

            currentSong.id.toLongOrNull()?.let { songId ->
                musicRepository.updateLyrics(songId, result.rawLyrics)
            }

            val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(currentSong, result.rawLyrics)
            val updatedSong = currentSong.withPersistedLyrics(result.rawLyrics, refreshedAlbumArtUri)

            _songUpdates.emit(updatedSong to result.lyrics)
        }
    }

    fun acceptPlatformLyricsSearchResult(entry: PlatformLyricsSearchEntry, currentSong: Song) {
        scope?.launch {
            val fetch = entry.probe ?: run {
                _searchUiState.value = LyricsSearchUiState.Loading
                musicRepository.fetchPlatformLyrics(entry).getOrNull()
            }
            if (fetch == null) {
                lastPick?.let { _searchUiState.value = it }
                return@launch
            }
            _searchUiState.value = LyricsSearchUiState.Success(fetch.lyrics)

            currentSong.id.toLongOrNull()?.let { songId ->
                musicRepository.updateLyrics(songId, fetch.rawLyrics)
            }

            val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(currentSong, fetch.rawLyrics)
            val updatedSong = currentSong.withPersistedLyrics(fetch.rawLyrics, refreshedAlbumArtUri)

            _songUpdates.emit(updatedSong to fetch.lyrics)
        }
    }

    private fun setPickResult(query: String, results: List<LyricsSearchEntry>) {
        val immediate = results.filterIsInstance<LyricsSearchResult>()
        if (immediate.isEmpty()) {
            lastPick = null
            _searchUiState.value = LyricsSearchUiState.Loading
        } else {
            val state = LyricsSearchUiState.PickResult(query, immediate.toImmutableList())
            lastPick = state
            _searchUiState.value = state
        }
        startPlatformProbes(query, results)
    }

    private fun startPlatformProbes(query: String, results: List<LyricsSearchEntry>) {
        probeJob?.cancel()
        val platformEntries = results.filterIsInstance<PlatformLyricsSearchEntry>()
        if (platformEntries.isEmpty()) {
            if (_searchUiState.value is LyricsSearchUiState.Loading) {
                _searchUiState.value = LyricsSearchUiState.NotFound("Lyrics not found")
            }
            return
        }
        probeJob = scope?.launch {
            val semaphore = Semaphore(4)
            val mutex = Mutex()
            var remaining = platformEntries.size
            coroutineScope {
                platformEntries.forEach { entry ->
                    launch {
                        semaphore.withPermit {
                            val probe = musicRepository.fetchPlatformLyrics(entry).getOrNull()
                            var song = entry.song
                            if (probe != null && song.pic.isNullOrBlank()) {
                                musicRepository.ensureLxSearchCover(song)?.let { cover ->
                                    song = song.copy(pic = cover)
                                }
                            }
                            mutex.withLock {
                                remaining--
                                if (probe != null) {
                                    val verified = entry.copy(song = song, probe = probe)
                                    val current = _searchUiState.value
                                    when {
                                        current is LyricsSearchUiState.PickResult && current.query == query -> {
                                            val newState = LyricsSearchUiState.PickResult(query, (current.results + verified).toImmutableList())
                                            lastPick = newState
                                            _searchUiState.value = newState
                                        }
                                        current is LyricsSearchUiState.Loading -> {
                                            val newState = LyricsSearchUiState.PickResult(query, listOf(verified).toImmutableList())
                                            lastPick = newState
                                            _searchUiState.value = newState
                                        }
                                    }
                                }
                                if (remaining == 0 && _searchUiState.value is LyricsSearchUiState.Loading) {
                                    _searchUiState.value = LyricsSearchUiState.NotFound("Lyrics not found")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Import from file.
     */
    fun importLyricsFromFile(songId: Long, validatedImport: ValidatedLyricsImport, currentSong: Song?) {
        scope?.launch {
            val sanitizedContent = validatedImport.sanitizedContent
            val parsedLyrics = validatedImport.parsedLyrics

            musicRepository.updateLyrics(songId, sanitizedContent)

            if (currentSong != null && currentSong.id.toLongOrNull() == songId) {
                val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(currentSong, sanitizedContent)
                val updatedSong = currentSong.withPersistedLyrics(sanitizedContent, refreshedAlbumArtUri)
                _songUpdates.emit(updatedSong to parsedLyrics.takeIf(::hasValidLyrics))
            }

            _messageEvents.emit("Lyrics imported successfully!")
        }
    }

    fun resetLyrics(songId: Long) {
        resetSearchState()
        scope?.launch {
            musicRepository.resetLyrics(songId)
            _songUpdates.emit(Song.emptySong().copy(id = songId.toString()) to null)
        }
    }

    fun resetAllLyrics() {
        resetSearchState()
        scope?.launch {
            musicRepository.resetAllLyrics()
        }
    }

    private fun handleError(error: Throwable) {
        _searchUiState.value = if (error is NoLyricsFoundException) {
            LyricsSearchUiState.NotFound("Lyrics not found")
        } else {
            LyricsSearchUiState.Error(error.message ?: "Unknown error")
        }
    }

    private fun hasValidLyrics(lyrics: Lyrics?): Boolean {
        if (lyrics == null) return false
        return !lyrics.synced.isNullOrEmpty() || !lyrics.plain.isNullOrEmpty()
    }

    private fun readEmbeddedLyricsFromFile(song: Song): String? {
        song.lyrics
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return runCatching {
            AudioMetadataReader.read(File(song.path))
                ?.lyrics
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun readLocalLyricsFile(song: Song): String? {
        return runCatching {
            val songFile = File(song.path)
            val directory = songFile.parentFile ?: return@runCatching null
            for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                val lyricsFile = File(directory, "${songFile.nameWithoutExtension}.$extension")
                if (!lyricsFile.exists() || !lyricsFile.canRead()) continue

                when (val validation = LyricsImportSecurity.validateLocalLyricsFile(lyricsFile)) {
                    is LyricsImportValidationResult.Valid -> return@runCatching validation.value.sanitizedContent
                    is LyricsImportValidationResult.Invalid -> continue
                }
            }
            null
        }.getOrNull()
    }

    private suspend fun persistLyricsToFileMetadataIfPossible(song: Song, rawLyrics: String): String? {
        val songId = song.id.toLongOrNull() ?: return null
        val normalizedLyrics = rawLyrics.trim()
        if (normalizedLyrics.isBlank()) return null

        return withContext(Dispatchers.IO) {
            val existingArtwork = runCatching {
                AudioMetadataReader.read(File(song.path))?.artwork
            }.getOrNull()

            val coverArtUpdate = existingArtwork?.let { artwork ->
                CoverArtUpdate(
                    bytes = artwork.bytes,
                    mimeType = artwork.mimeType ?: "image/jpeg"
                )
            }

            runCatching {
                songMetadataEditor.editSongMetadata(
                    songId = songId,
                    newTitle = song.title,
                    newArtist = song.artist,
                    newAlbum = song.album,
                    newGenre = song.genre ?: "",
                    newLyrics = normalizedLyrics,
                    newTrackNumber = song.trackNumber,
                    newDiscNumber = song.discNumber,
                    coverArtUpdate = coverArtUpdate
                )
            }.getOrNull()?.updatedAlbumArtUri
        }
    }

    fun onCleared() {
        loadingJob?.cancel()
        scope = null
        loadCallback = null
    }
}

internal fun Song.withPersistedLyrics(rawLyrics: String, refreshedAlbumArtUri: String?): Song {
    return copy(
        lyrics = rawLyrics,
        albumArtUriString = refreshedAlbumArtUri ?: albumArtUriString
    )
}

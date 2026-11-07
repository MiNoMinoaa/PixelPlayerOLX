package com.minoppol.music.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minoppol.music.data.lxmusic.LxMusicRepository
import com.minoppol.music.data.lxmusic.LxPlaylist
import com.minoppol.music.data.lxmusic.LxSong
import com.minoppol.music.data.lxmusic.LxSources
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class LxOnlineSearchTab { SONGS, PLAYLISTS }

data class LxOnlineHotItem(
    val keyword: String,
    val source: String,
)

data class LxOnlinePlaylistHit(
    val source: String,
    val playlist: LxPlaylist,
)

data class LxOnlinePlaylistDetail(
    val source: String,
    val playlist: LxPlaylist,
    val loading: Boolean,
    val songs: List<LxSong>,
    val error: String?,
)

data class LxOnlineSearchUiState(
    val submittedQuery: String = "",
    val searching: Boolean = false,
    val songs: List<LxSong> = emptyList(),
    val songsLoadingMore: Boolean = false,
    val songsHasMore: Boolean = false,
    val playlists: List<LxOnlinePlaylistHit> = emptyList(),
    val playlistsLoading: Boolean = false,
    val playlistsLoadingMore: Boolean = false,
    val playlistsHasMore: Boolean = false,
    val tab: LxOnlineSearchTab = LxOnlineSearchTab.SONGS,
    val error: String? = null,
)

@HiltViewModel
class LxOnlineSearchViewModel @Inject constructor(
    private val repository: LxMusicRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LxOnlineSearchUiState())
    val uiState: StateFlow<LxOnlineSearchUiState> = _uiState.asStateFlow()

    private val suggestionInput = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val suggestions = suggestionInput
        .debounce(150L)
        .distinctUntilChanged()
        .mapLatest { keyword ->
            if (keyword.isEmpty()) {
                flow { emit(emptyList<String>()) }
            } else {
                fetchSuggestionsFlow(keyword)
            }
        }
        .flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _hotItems = MutableStateFlow<List<LxOnlineHotItem>>(emptyList())
    val hotItems: StateFlow<List<LxOnlineHotItem>> = _hotItems.asStateFlow()

    private val _hotLoading = MutableStateFlow(false)
    val hotLoading: StateFlow<Boolean> = _hotLoading.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<LxOnlinePlaylistDetail?>(null)
    val selectedPlaylist: StateFlow<LxOnlinePlaylistDetail?> = _selectedPlaylist.asStateFlow()

    private var searchJobId = 0L

    private var playlistPicJobId = 0L

    private var songsBySource: List<List<LxSong>> = emptyList()
    private var playlistsBySource: List<List<LxPlaylist>> = emptyList()
    private var songsPage = 1
    private var playlistsPage = 1
    private var songsHasMore = false
    private var playlistsHasMore = false
    private var songsLoadingMore = false
    private var playlistsLoadingMore = false

    fun refreshHotSearches(force: Boolean = false) {
        if (!force && _hotItems.value.isNotEmpty()) return
        if (_hotLoading.value) return
        viewModelScope.launch {
            _hotLoading.value = true
            val merged = withContext(Dispatchers.IO) {
                coroutineScope {
                    LxSources.ALL.map { source ->
                        async {
                            withTimeoutOrNull(6_000L) {
                                runCatching { repository.fetchHotSearches(source) }.getOrDefault(emptyList())
                            }?.take(5).orEmpty()
                        }
                    }.awaitAll()
                }
            }
            _hotItems.value = interleaveLists(merged.mapIndexed { index, list ->
                list.map { keyword -> LxOnlineHotItem(keyword, LxSources.ALL[index]) }
            }).distinctBy { it.keyword.lowercase() }.take(15)
            _hotLoading.value = false
        }
    }

    fun onQueryInput(keyword: String) {
        suggestionInput.value = keyword.trim()
    }

    fun searchOnline(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val jobId = ++searchJobId
        suggestionInput.value = ""
        _uiState.value = LxOnlineSearchUiState(
            submittedQuery = trimmed,
            searching = true,
            tab = _uiState.value.tab,
        )
        viewModelScope.launch {
            val songsDeferred = async {
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        LxSources.ALL.map { source ->
                            async {
                                withTimeoutOrNull(10_000L) {
                                    runCatching { repository.searchMusic("", source, trimmed, 1) }
                                        .getOrDefault(emptyList())
                                }.orEmpty()
                            }
                        }.awaitAll()
                    }
                }
            }
            val playlistsDeferred = async {
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        LxSources.ALL.map { source ->
                            async {
                                withTimeoutOrNull(10_000L) {
                                    runCatching { repository.searchPlaylists(source, trimmed, 1) }
                                        .getOrDefault(emptyList())
                                }.orEmpty()
                            }
                        }.awaitAll()
                    }
                }
            }

            val songs = songsDeferred.await()
            songsBySource = songs
            songsPage = 1
            songsHasMore = songs.any { it.isNotEmpty() }
            val mergedSongs = interleaveListsWithLead(songsBySource, 2)
                .distinctBy { "${it.source}_${it.songmid}" }
                .take(40)
            if (jobId == searchJobId) {
                _uiState.value = _uiState.value.copy(
                    songs = mergedSongs,
                    songsHasMore = songsHasMore,
                    error = if (songs.all { it.isEmpty() }) "no_result" else null,
                )
            }
            viewModelScope.launch {
                mergedSongs.filter { it.pic.isNullOrBlank() }.forEach { song ->
                    launch {
                        val cover = withTimeoutOrNull(6_000L) {
                            runCatching { repository.ensureSearchCover(song) }.getOrNull()
                        }
                        if (jobId != searchJobId || cover.isNullOrBlank()) return@launch
                        val current = _uiState.value
                        if (current.songs.none {
                                it.source == song.source && it.songmid == song.songmid
                            }
                        ) {
                            return@launch
                        }
                        _uiState.value = current.copy(
                            songs = current.songs.map {
                                if (it.source == song.source &&
                                    it.songmid == song.songmid &&
                                    it.pic.isNullOrBlank()
                                ) {
                                    it.copy(pic = cover)
                                } else {
                                    it
                                }
                            }
                        )
                    }
                }
            }

            val playlistLists = playlistsDeferred.await()
            playlistsBySource = playlistLists
            playlistsPage = 1
            playlistsHasMore = playlistLists.any { it.isNotEmpty() }
            if (jobId == searchJobId) {
                _uiState.value = _uiState.value.copy(
                    playlists = mergePlaylists(),
                    playlistsLoading = false,
                    searching = false,
                    playlistsHasMore = playlistsHasMore,
                )
            }
        }
    }

    fun setTab(tab: LxOnlineSearchTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    fun resetSearch() {
        searchJobId++
        suggestionInput.value = ""
        _uiState.value = LxOnlineSearchUiState(tab = _uiState.value.tab)
    }

    fun clearError() {
        val current = _uiState.value
        if (current.error != null) {
            _uiState.value = current.copy(error = null, searching = false)
        }
    }

    fun loadMoreSongs() {
        val current = _uiState.value
        if (current.searching || songsLoadingMore || !songsHasMore) return
        val query = current.submittedQuery
        if (query.isBlank()) return
        val jobId = searchJobId
        songsLoadingMore = true
        _uiState.value = current.copy(songsLoadingMore = true)
        viewModelScope.launch {
            val nextPage = songsPage + 1
            val lists = withContext(Dispatchers.IO) {
                coroutineScope {
                    LxSources.ALL.map { source ->
                        async {
                            withTimeoutOrNull(10_000L) {
                                runCatching { repository.searchMusic("", source, query, nextPage) }
                                    .getOrDefault(emptyList())
                            }.orEmpty()
                        }
                    }.awaitAll()
                }
            }
            songsLoadingMore = false
            if (jobId != searchJobId) return@launch
            songsBySource = songsBySource.mapIndexed { index, acc -> acc + lists[index] }
            songsPage = nextPage
            songsHasMore = lists.any { it.isNotEmpty() }
            val merged = interleaveListsWithLead(songsBySource, 2)
                .distinctBy { "${it.source}_${it.songmid}" }
                .take(40 * songsPage)
            _uiState.value = _uiState.value.copy(
                songs = merged,
                songsLoadingMore = false,
                songsHasMore = songsHasMore,
            )
            viewModelScope.launch {
                merged.filter { it.pic.isNullOrBlank() }.forEach { song ->
                    launch {
                        val cover = withTimeoutOrNull(6_000L) {
                            runCatching { repository.ensureSearchCover(song) }.getOrNull()
                        }
                        if (jobId != searchJobId || cover.isNullOrBlank()) return@launch
                        val cur = _uiState.value
                        if (cur.songs.none { it.source == song.source && it.songmid == song.songmid }) return@launch
                        _uiState.value = cur.copy(
                            songs = cur.songs.map {
                                if (it.source == song.source && it.songmid == song.songmid && it.pic.isNullOrBlank()) {
                                    it.copy(pic = cover)
                                } else {
                                    it
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    fun loadMorePlaylists() {
        val current = _uiState.value
        if (current.playlistsLoading || playlistsLoadingMore || !playlistsHasMore) return
        val query = current.submittedQuery
        if (query.isBlank()) return
        val jobId = searchJobId
        playlistsLoadingMore = true
        _uiState.value = current.copy(playlistsLoadingMore = true)
        viewModelScope.launch {
            val nextPage = playlistsPage + 1
            val lists = withContext(Dispatchers.IO) {
                coroutineScope {
                    LxSources.ALL.map { source ->
                        async {
                            withTimeoutOrNull(10_000L) {
                                runCatching { repository.searchPlaylists(source, query, nextPage) }
                                    .getOrDefault(emptyList())
                            }.orEmpty()
                        }
                    }.awaitAll()
                }
            }
            playlistsLoadingMore = false
            if (jobId != searchJobId) return@launch
            playlistsBySource = playlistsBySource.mapIndexed { index, acc -> acc + lists[index] }
            playlistsPage = nextPage
            playlistsHasMore = lists.any { it.isNotEmpty() }
            _uiState.value = _uiState.value.copy(
                playlists = mergePlaylists(),
                playlistsLoadingMore = false,
                playlistsHasMore = playlistsHasMore,
            )
        }
    }

    private fun mergePlaylists(): List<LxOnlinePlaylistHit> =
        interleaveLists(playlistsBySource.mapIndexed { index, list ->
            list.map { LxOnlinePlaylistHit(LxSources.ALL[index], it) }
        }).distinctBy { "${it.source}:${it.playlist.id}" }.take(20 * playlistsPage)

    fun openPlaylist(hit: LxOnlinePlaylistHit) {
        if (_selectedPlaylist.value?.playlist?.id == hit.playlist.id &&
            _selectedPlaylist.value?.source == hit.source
        ) return
        _selectedPlaylist.value = LxOnlinePlaylistDetail(
            source = hit.source,
            playlist = hit.playlist,
            loading = true,
            songs = emptyList(),
            error = null,
        )
        val picJobId = ++playlistPicJobId
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                withTimeoutOrNull(20_000L) {
                    runCatching {
                        repository.fetchPlaylistSongs(hit.source, hit.playlist.id, 1, 200)
                    }.getOrElse { emptyList<LxSong>() to false }
                } ?: (emptyList<LxSong>() to false)
            }
            val songs = result.first
            if (_selectedPlaylist.value?.playlist?.id != hit.playlist.id) return@launch
            _selectedPlaylist.value = _selectedPlaylist.value?.copy(
                loading = false,
                songs = songs,
                error = if (songs.isEmpty()) "load_failed" else null,
            )
            viewModelScope.launch {
                songs.filter { it.pic.isNullOrBlank() }.forEach { song ->
                    launch {
                        val cover = withTimeoutOrNull(6_000L) {
                            runCatching { repository.ensureSearchCover(song) }.getOrNull()
                        }
                        if (picJobId != playlistPicJobId || cover.isNullOrBlank()) return@launch
                        val cur = _selectedPlaylist.value ?: return@launch
                        if (cur.playlist.id != hit.playlist.id) return@launch
                        if (cur.songs.none { it.source == song.source && it.songmid == song.songmid }) return@launch
                        _selectedPlaylist.value = cur.copy(
                            songs = cur.songs.map {
                                if (it.source == song.source && it.songmid == song.songmid && it.pic.isNullOrBlank()) {
                                    it.copy(pic = cover)
                                } else {
                                    it
                                }
                            }
                        )
                        if (cur.playlist.pic.isNullOrBlank()) {
                            val cur2 = _selectedPlaylist.value
                            if (cur2 != null && cur2.playlist.id == hit.playlist.id) {
                                _selectedPlaylist.value = cur2.copy(
                                    playlist = cur2.playlist.copy(pic = cover)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun closePlaylist() {
        searchJobId++
        playlistPicJobId++
        _selectedPlaylist.value = null
    }

    private suspend fun fetchMergedSuggestions(keyword: String): List<String> = withContext(Dispatchers.IO) {
        coroutineScope {
            LxSources.ALL.map { source ->
                async {
                    withTimeoutOrNull(5_000L) {
                        runCatching { repository.fetchSearchSuggestions(source, keyword) }
                            .getOrDefault(emptyList())
                    }.orEmpty()
                }
            }.awaitAll()
        }.let { interleaveLists(it) }.distinctBy { it.lowercase() }.take(10)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun fetchSuggestionsFlow(keyword: String): Flow<List<String>> = channelFlow {
        val lists = MutableList<List<String>?>(LxSources.ALL.size) { null }
        withContext(Dispatchers.IO) {
            coroutineScope {
                LxSources.ALL.forEachIndexed { index, source ->
                    launch {
                        val result = withTimeoutOrNull(5_000L) {
                            runCatching { repository.fetchSearchSuggestions(source, keyword) }
                                .getOrDefault(emptyList())
                        }.orEmpty()
                        lists[index] = result
                        val merged = interleaveLists(lists.filterNotNull())
                            .distinctBy { it.lowercase() }
                            .take(10)
                        send(merged)
                    }
                }
            }
        }
    }

    fun refreshSuggestions(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        suggestionInput.value = ""
        suggestionInput.value = trimmed
    }

    private fun <T> interleaveLists(lists: List<List<T>>): List<T> {
        val result = mutableListOf<T>()
        var index = 0
        while (true) {
            var added = false
            for (list in lists) {
                if (index < list.size) {
                    result.add(list[index])
                    added = true
                }
            }
            if (!added) break
            index++
        }
        return result
    }

    private fun <T> interleaveListsWithLead(lists: List<List<T>>, leadCount: Int): List<T> {
        val result = mutableListOf<T>()
        for (list in lists) {
            result.addAll(list.take(leadCount))
        }
        var index = leadCount
        while (true) {
            var added = false
            for (list in lists) {
                if (index < list.size) {
                    result.add(list[index])
                    added = true
                }
            }
            if (!added) break
            index++
        }
        return result
    }
}

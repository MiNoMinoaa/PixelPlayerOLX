package com.minoppol.music.presentation.screens.lxmusic

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minoppol.music.R
import com.minoppol.music.data.lxmusic.LxAddToPlaylistResult
import com.minoppol.music.data.lxmusic.LxAlbum
import com.minoppol.music.data.lxmusic.LxArtist
import com.minoppol.music.data.lxmusic.LxBoard
import com.minoppol.music.data.lxmusic.LxMusicRepository
import com.minoppol.music.data.lxmusic.LxMusicStreamProxy
import com.minoppol.music.data.lxmusic.LxPlaylist
import com.minoppol.music.data.lxmusic.LxPlaylistTag
import com.minoppol.music.data.lxmusic.LxScriptCapabilities
import com.minoppol.music.data.lxmusic.LxScriptMeta
import com.minoppol.music.data.lxmusic.LxSong
import com.minoppol.music.data.lxmusic.LxSongMapper
import com.minoppol.music.data.lxmusic.LxSources
import com.minoppol.music.data.lxmusic.LxTxQrPoll
import com.minoppol.music.data.lxmusic.LxTxQrType
import com.minoppol.music.data.lxmusic.netease.NeteaseCookieStore
import com.minoppol.music.data.lxmusic.netease.NeteaseOfficialApi
import com.minoppol.music.data.offline.CloudOfflineRepository
import com.minoppol.music.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class LxMusicViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LxMusicRepository,
    private val streamProxy: LxMusicStreamProxy,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val neteaseCookieStore: NeteaseCookieStore,
    private val neteaseOfficialApi: NeteaseOfficialApi,
    private val tencentCookieStore: com.minoppol.music.data.lxmusic.tencent.TencentCookieStore,
    private val kugouCookieStore: com.minoppol.music.data.lxmusic.kugou.KugouCookieStore,
    private val kuwoCookieStore: com.minoppol.music.data.lxmusic.kuwo.KuwoCookieStore,
    private val cloudOfflineRepository: CloudOfflineRepository,
) : ViewModel() {

    val scripts: StateFlow<List<LxScriptMeta>> = repository.scripts
    val loaded: StateFlow<Map<String, LxScriptCapabilities>> = repository.loaded

    val accountWritesEnabled: Boolean = repository.accountWritesEnabled

    val runtimePaths: StateFlow<Set<String>> = repository.runtimePaths

    val neteaseCookie: StateFlow<String?> = neteaseCookieStore.cookieFlow
    val neteaseVipType: StateFlow<Int> = neteaseCookieStore.vipTypeFlow
    val neteaseCookieValid: StateFlow<Boolean> = neteaseCookieStore.cookieValidFlow
    val neteaseNickname: StateFlow<String?> = neteaseCookieStore.nicknameFlow
    val neteaseAvatar: StateFlow<String?> = neteaseCookieStore.avatarUrlFlow

    val neteaseLoggedIn: StateFlow<Boolean> = neteaseCookieStore.cookieFlow
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, neteaseCookieStore.hasCookie())

    val tencentCookie: StateFlow<String?> = tencentCookieStore.cookieFlow
    val tencentVipType: StateFlow<Int> = tencentCookieStore.vipTypeFlow
    val tencentCookieValid: StateFlow<Boolean> = tencentCookieStore.cookieValidFlow
    val tencentNickname: StateFlow<String?> = tencentCookieStore.nicknameFlow
    val tencentAvatar: StateFlow<String?> = tencentCookieStore.avatarUrlFlow
    val tencentLoggedIn: StateFlow<Boolean> = tencentCookieStore.cookieFlow
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, tencentCookieStore.hasCookie())

    val kugouCookie: StateFlow<String?> = kugouCookieStore.cookieFlow
    val kugouVipType: StateFlow<Int> = kugouCookieStore.vipTypeFlow
    val kugouCookieValid: StateFlow<Boolean> = kugouCookieStore.cookieValidFlow
    val kugouNickname: StateFlow<String?> = kugouCookieStore.nicknameFlow
    val kugouAvatar: StateFlow<String?> = kugouCookieStore.avatarUrlFlow
    val kugouLoggedIn: StateFlow<Boolean> = kugouCookieStore.loggedInFlow

    val kuwoCookie: StateFlow<String?> = kuwoCookieStore.cookieFlow
    val kuwoVipType: StateFlow<Int> = kuwoCookieStore.vipTypeFlow
    val kuwoCookieValid: StateFlow<Boolean> = kuwoCookieStore.cookieValidFlow
    val kuwoNickname: StateFlow<String?> = kuwoCookieStore.nicknameFlow
    val kuwoAvatar: StateFlow<String?> = kuwoCookieStore.avatarUrlFlow
    val kuwoLoggedIn: StateFlow<Boolean> = kuwoCookieStore.cookieFlow
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, kuwoCookieStore.hasCookie())

    val txLikedSongIds: StateFlow<Set<String>> = repository.txLikedSongIds
    val kgLikedSongIds: StateFlow<Set<String>> = repository.kgLikedSongIds
    val kwLikedSongIds: StateFlow<Set<String>> = repository.kwLikedSongIds

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    fun emitToast(message: String) {
        _toastMessage.tryEmit(message)
    }

    private val _activeScript = MutableStateFlow<String?>(null)
    val activeScript: StateFlow<String?> = _activeScript.asStateFlow()

    val disabledScripts: StateFlow<Set<String>> =
        userPreferencesRepository.lxMusicDisabledScriptsFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _selectedSource = MutableStateFlow(LxSources.NETEASE)
    val selectedSource: StateFlow<String> = _selectedSource.asStateFlow()

    val currentSourceLoggedIn: StateFlow<Boolean> = combine(
        selectedSource, neteaseLoggedIn, tencentLoggedIn, kugouLoggedIn, kuwoLoggedIn,
    ) { source, wy, tx, kg, kw ->
        when (source) {
            LxSources.NETEASE -> wy
            LxSources.TENCENT -> tx
            LxSources.KUGOU -> kg
            LxSources.KUWO -> kw
            else -> false
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    data class LoadState(
        val loading: Boolean = false,
        val error: String? = null,
        val loadedSources: List<String> = emptyList(),
    )

    private val _loadState = MutableStateFlow(LoadState())
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    private val _browseState = MutableStateFlow(BrowseState())
    val browseState: StateFlow<BrowseState> = _browseState.asStateFlow()

    private var loadEpoch = 0L
    private var currentLoadJob: Job? = null
    private var playlistListDirty = false

    enum class SearchType { SONG, ARTIST, ALBUM }

    data class SearchState(
        val keyword: String = "",
        val type: SearchType = SearchType.SONG,
        val results: List<LxSong> = emptyList(),
        val searching: Boolean = false,
        val error: String? = null,
        val searchedSource: String? = null,
        val page: Int = 1,
        val loadingMore: Boolean = false,
        val canLoadMore: Boolean = false,
        val artistResults: List<LxArtist> = emptyList(),
        val artistTotal: Int = 0,
        val artistPage: Int = 1,
        val searchingArtists: Boolean = false,
        val loadingMoreArtists: Boolean = false,
        val canLoadMoreArtists: Boolean = false,
        val artistUnsupported: Boolean = false,
        val albumResults: List<LxAlbum> = emptyList(),
        val albumTotal: Int = 0,
        val albumPage: Int = 1,
        val searchingAlbums: Boolean = false,
        val loadingMoreAlbums: Boolean = false,
        val canLoadMoreAlbums: Boolean = false,
        val albumUnsupported: Boolean = false,
        val searchEpoch: Long = 0L,
    )

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    data class SearchArtistPageState(
        val artist: LxArtist? = null,
        val followed: Boolean? = null,
        val followBusy: Boolean = false,
        val songs: List<LxSong> = emptyList(),
        val albums: List<LxAlbum> = emptyList(),
        val albumsLoading: Boolean = false,
        val tab: String = "songs",  // songs / albums
        val loading: Boolean = false,
        val error: String? = null,
        val songsPage: Int = 1,
        val songsTotal: Int = 0,
        val songsHasMore: Boolean = false,
        val songsLoadingMore: Boolean = false,
        val selectedAlbum: LxAlbum? = null,
        val albumSongs: List<LxSong> = emptyList(),
        val albumLoading: Boolean = false,
        val source: String = LxSources.NETEASE,
    )
    private val _searchArtistPageState = MutableStateFlow(SearchArtistPageState())
    val searchArtistPageState: StateFlow<SearchArtistPageState> = _searchArtistPageState.asStateFlow()

    data class SearchAlbumPageState(
        val album: LxAlbum? = null,
        val songs: List<LxSong> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val source: String = LxSources.NETEASE,
    )
    private val _searchAlbumPageState = MutableStateFlow(SearchAlbumPageState())
    val searchAlbumPageState: StateFlow<SearchAlbumPageState> = _searchAlbumPageState.asStateFlow()
    private val _searchAlbumOverlay = MutableStateFlow(false)
    val searchAlbumOverlay: StateFlow<Boolean> = _searchAlbumOverlay.asStateFlow()

    private val _mineState = MutableStateFlow(MineState())
    val mineState: StateFlow<MineState> = _mineState.asStateFlow()
    private val mineBackStack = ArrayDeque<MinePageSnapshot>().apply { addLast(MineState().pageSnapshot()) }
    private var mineJob: Job? = null
    private var mineNavSession = 0L

    init {
        viewModelScope.launch {
            var prevWy = repository.neteaseLikedSongIds.value
            var prevKg = repository.kgLikedSongIds.value
            var prevTx = repository.txLikedSongIds.value
            var prevKw = repository.kwLikedSongIds.value
            combine(
                repository.neteaseLikedSongIds,
                repository.kgLikedSongIds,
                repository.txLikedSongIds,
                repository.kwLikedSongIds,
            ) { wy, kg, tx, kw -> LikedSets(wy, kg, tx, kw) }.collect { cur ->
                val source = _selectedSource.value
                val st = _mineState.value
                if (st.homeLoaded) {
                    val count = when (source) {
                        LxSources.KUGOU -> cur.kg.count { it.length == 32 && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' } }
                        LxSources.TENCENT -> cur.tx.size
                        LxSources.KUWO -> cur.kw.size
                        LxSources.NETEASE -> cur.wy.size
                        else -> null
                    }
                    if (count != null) {
                        _mineState.value = st.copy(
                            myPlaylists = st.myPlaylists.map {
                                if (it.specialType == 5) it.copy(trackCount = count) else it
                            },
                        )
                    }
                }
                val (removed, currentSet) = when (source) {
                    LxSources.NETEASE -> (prevWy - cur.wy) to cur.wy
                    LxSources.KUGOU -> (prevKg - cur.kg) to cur.kg
                    LxSources.TENCENT -> (prevTx - cur.tx) to cur.tx
                    LxSources.KUWO -> (prevKw - cur.kw) to cur.kw
                    else -> emptySet<String>() to emptySet()
                }
                if (removed.isNotEmpty()) {
                    val mst = _mineState.value
                    val liked = mst.likedPlaylist()
                    val page = mst.page
                    if (source == LxSources.NETEASE && liked != null) {
                        removed.forEach { repository.removeTrackFromNeteasePlaylistCache(liked.id, it) }
                    }
                    if (liked != null && page is MinePage.SongList &&
                        page.kind == "playlist" && page.playlistId == liked.id
                    ) {
                        _mineState.value = mst.copy(
                            pageSongs = mst.pageSongs.filterNot { it.isUnlikedIn(source, currentSet) },
                        )
                    }
                    val bst = _browseState.value
                    if (liked != null && bst.selectedPlaylist?.id == liked.id) {
                        _browseState.value = bst.copy(
                            playlistSongs = bst.playlistSongs.filterNot { it.isUnlikedIn(source, currentSet) },
                        )
                    }
                }
                if (prevWy != cur.wy || prevKg != cur.kg || prevTx != cur.tx || prevKw != cur.kw) {
                    playlistListDirty = true
                }
                prevWy = cur.wy; prevKg = cur.kg; prevTx = cur.tx; prevKw = cur.kw
            }
        }
        viewModelScope.launch {
            var prevLoggedIn: Map<String, Boolean> = emptyMap()
            combine(
                _selectedSource,
                neteaseLoggedIn,
                tencentLoggedIn,
                kugouLoggedIn,
                kuwoLoggedIn,
            ) { source, wy, tx, kg, kw ->
                source to mapOf(
                    LxSources.NETEASE to wy,
                    LxSources.TENCENT to tx,
                    LxSources.KUGOU to kg,
                    LxSources.KUWO to kw,
                )
            }.collect { (source, loggedMap) ->
                val loggedIn = loggedMap[source] == true
                val wasLoggedIn = prevLoggedIn[source] == true
                prevLoggedIn = loggedMap
                if (loggedIn && !wasLoggedIn) ensurePlatformLikedIds(source)
            }
        }
        viewModelScope.launch {
            val savedSource = runCatching {
                userPreferencesRepository.lxMusicSelectedSourceFlow.first()
            }.getOrNull()
            _selectedSource.value = if (savedSource != null && savedSource in LxSources.ALL) savedSource
            else LxSources.NETEASE

            val savedActiveScript = runCatching {
                userPreferencesRepository.lxMusicActiveScriptFlow.first()
            }.getOrNull()

            val disabledScripts = runCatching {
                userPreferencesRepository.lxMusicDisabledScriptsFlow.first()
            }.getOrElse { emptySet() }
            repository.setDisabledPaths(disabledScripts)

            repository.setPreferredScript(savedActiveScript)

            val cachedCaps = repository.loaded.value
            if (cachedCaps.isNotEmpty()) {
                val restored = savedActiveScript?.takeIf { it in cachedCaps && it !in disabledScripts }
                val chosen = restored ?: cachedCaps.keys.firstOrNull { it !in disabledScripts }
                if (chosen != null) {
                    _activeScript.value = chosen
                    repository.setPreferredScript(chosen)
                    val caps = cachedCaps[chosen]
                    _loadState.value = LoadState(loadedSources = caps?.sources?.keys?.toList() ?: emptyList())
                }
            }

            if (savedActiveScript != null && savedActiveScript !in disabledScripts) {
                repository.loadAllScripts(activeScriptPath = savedActiveScript)
            } else if (savedActiveScript == null && disabledScripts.isEmpty()) {
                if (repository.scripts.value.any { !it.isImported }) {
                    repository.loadAllScripts(activeScriptPath = null)
                }
            }

            val allCaps = repository.loaded.value
            if (allCaps.isNotEmpty() && _activeScript.value == null) {
                val restored = savedActiveScript?.takeIf { it in allCaps && it !in disabledScripts }
                val chosen = restored ?: allCaps.keys.firstOrNull { it !in disabledScripts }
                if (chosen != null) {
                    _activeScript.value = chosen
                    repository.setPreferredScript(chosen)
                    val caps = allCaps[chosen]
                    _loadState.value = LoadState(loadedSources = caps?.sources?.keys?.toList() ?: emptyList())
                }
            } else if (_activeScript.value != null) {
                val caps = allCaps[_activeScript.value]
                if (caps != null) {
                    _loadState.value = LoadState(loadedSources = caps.sources.keys.toList())
                }
            }
            if (_selectedSource.value == LxSources.NETEASE && neteaseCookieStore.hasCookie()) {
                _browseState.value = _browseState.value.copy(module = "mine")
                ensureMineHome()
            } else {
                startModuleLoad("board", _selectedSource.value, force = false)
            }
        }
        viewModelScope.launch {
            if (neteaseCookieStore.hasCookie()) {
                val updatedAt = neteaseCookieStore.getAccountUpdatedAt()
                val ttlMs = when (neteaseCookieStore.getVipType()) {
                    11, 13 -> ACCOUNT_INFO_TTL_MS
                    else -> ACCOUNT_INFO_NON_VIP_TTL_MS
                }
                if (updatedAt == 0L || System.currentTimeMillis() - updatedAt > ttlMs) {
                    refreshNeteaseAccountInfo()
                }
            }
        }
        viewModelScope.launch {
            neteaseCookieStore.cookieValidFlow.collect { valid ->
                if (!valid && neteaseCookieStore.hasCookie()) {
                    Timber.w("Netease cookie invalidated by server, performing auto logout")
                    logoutNetease()
                    _toastMessage.tryEmit(context.getString(R.string.lx_msg_netease_login_expired))
                }
            }
        }
    }

    companion object {
        const val ACCOUNT_INFO_TTL_MS = 24L * 60 * 60 * 1000
        const val ACCOUNT_INFO_NON_VIP_TTL_MS = 60L * 60 * 1000
        const val MINE_COOKIE_CHECK_TTL_MS = 60L * 60 * 1000
        val DEFAULT_PLATFORM_ORDER = listOf("wy", "kw", "tx", "kg")
    }

    fun loadScript(assetPath: String) {
        viewModelScope.launch {
            _loadState.value = LoadState(loading = true)
            val result = repository.load(assetPath)
            result.onSuccess { caps ->
                if (_activeScript.value == null) {
                    _activeScript.value = assetPath
                    persistActiveScript(assetPath)
                }
                repository.setPreferredScript(_activeScript.value)
                streamProxy.clearUrlCache()
                repository.clearCrossSourceCaches()
                _loadState.value = LoadState(loadedSources = caps.sources.keys.toList())
            }.onFailure { e ->
                _loadState.value = LoadState(error = context.getString(R.string.lx_error_load_failed, e.message ?: ""))
            }
        }
    }

    fun unloadScript(assetPath: String) {
        viewModelScope.launch {
            val cur = userPreferencesRepository.lxMusicDisabledScriptsFlow.first()
            val newDisabled = cur + assetPath
            userPreferencesRepository.setLxMusicDisabledScripts(newDisabled)
            repository.unload(assetPath)
            if (_activeScript.value == assetPath) {
                val next = repository.scripts.value
                    .map { it.assetPath }
                    .firstOrNull { it != assetPath && it !in newDisabled }
                if (next != null) {
                    becomePreferred(next)
                    if (next !in repository.runtimePaths.value) loadScriptQuietly(next)
                } else {
                    _activeScript.value = null
                    userPreferencesRepository.setLxMusicActiveScript(null)
                    _loadState.value = LoadState()
                }
            }
            streamProxy.clearUrlCache()
            repository.clearCrossSourceCaches()
        }
    }

    fun makePreferred(assetPath: String) {
        if (assetPath in disabledScripts.value) return
        if (_activeScript.value == assetPath) return
        viewModelScope.launch {
            becomePreferred(assetPath)
            if (assetPath !in repository.runtimePaths.value) {
                _loadState.value = _loadState.value.copy(loading = true, error = null)
                loadScriptQuietly(assetPath)
            }
            streamProxy.clearUrlCache()
            repository.clearCrossSourceCaches()
        }
    }

    private fun becomePreferred(assetPath: String) {
        _activeScript.value = assetPath
        persistActiveScript(assetPath)
        repository.setPreferredScript(assetPath)
        val caps = repository.loaded.value[assetPath]
        _loadState.value = LoadState(loadedSources = caps?.sources?.keys?.toList() ?: emptyList())
    }

    private suspend fun loadScriptQuietly(assetPath: String) {
        repository.load(assetPath)
            .onSuccess { caps ->
                if (_activeScript.value == assetPath) {
                    _loadState.value = LoadState(loadedSources = caps.sources.keys.toList())
                }
            }
            .onFailure { e ->
                if (_activeScript.value == assetPath) {
                    _loadState.value = LoadState(error = context.getString(R.string.lx_error_load_failed, e.message ?: ""))
                }
            }
    }

    fun deleteScript(assetPath: String) {
        viewModelScope.launch {
            val result = repository.deleteScript(assetPath)
            if (result) {
                if (_activeScript.value == assetPath) {
                    _activeScript.value = null
                    userPreferencesRepository.setLxMusicActiveScript(null)
                }
                val newActive = repository.activeScriptPath()
                if (newActive != null && newActive != assetPath) {
                    _activeScript.value = newActive
                    persistActiveScript(newActive)
                }
                streamProxy.clearUrlCache()
                repository.clearCrossSourceCaches()
            }
        }
    }

    fun cacheSongsMetadata(songs: List<LxSong>) {
        for (song in songs) {
            streamProxy.cacheMetadata(
                source = song.source,
                songmid = song.songmid,
                name = song.name,
                singer = song.singer,
                albumName = song.albumName,
                pic = song.pic,
                hash = song.hash,
                copyrightId = song.copyrightId,
                extraFields = song.extraFields,
            )
        }
    }

    suspend fun ensureActiveScriptForPlayback(): String? {
        _activeScript.value?.let { return it }
        val disabled = runCatching {
            userPreferencesRepository.lxMusicDisabledScriptsFlow.first()
        }.getOrElse { emptySet() }
        if (disabled.isNotEmpty()) return null
        repository.ensureDefaultScriptLoaded()
        val path = repository.activeScriptPath()
        if (path != null && path !in disabled) {
            _activeScript.value = path
            persistActiveScript(path)
            repository.setPreferredScript(path)
        }
        return path?.takeIf { it !in disabled }
    }

    suspend fun hasUserDisabledScripts(): Boolean =
        runCatching { userPreferencesRepository.lxMusicDisabledScriptsFlow.first().isNotEmpty() }.getOrDefault(false)

    fun setNeteaseCookie(cookie: String) {
        neteaseCookieStore.setCookie(cookie)
        viewModelScope.launch {
            refreshNeteaseAccountInfo()
            reloadScriptsForCookieChange()
        }
    }

    fun clearNeteaseCookie() {
        neteaseCookieStore.clear()
        reloadScriptsForCookieChange()
    }

    suspend fun refreshNeteaseAccountInfo() {
        if (!neteaseCookieStore.hasCookie()) return
        when (val account = neteaseOfficialApi.fetchAccountInfo()) {
            is NeteaseOfficialApi.AccountResult.Ok -> {
                val oldVipType = neteaseCookieStore.getVipType()
                val albums = when (val p = neteaseOfficialApi.fetchPurchasedAlbumIds()) {
                    is NeteaseOfficialApi.PurchasedResult.Ok -> p.albumIds
                    else -> neteaseCookieStore.purchasedAlbumIdsFlow.value
                }
                neteaseCookieStore.setAccountInfo(account.vipType, account.uid, albums)
                if (vipRank(account.vipType) > vipRank(oldVipType)) {
                    invalidatePlaybackCaches()
                }
            }
            is NeteaseOfficialApi.AccountResult.CookieInvalid -> neteaseCookieStore.markCookieInvalid()
            is NeteaseOfficialApi.AccountResult.NetworkError -> { }
        }
    }

    private fun vipRank(vipType: Int): Int = when (vipType) {
        13 -> 2
        11 -> 1
        else -> 0
    }

    private fun invalidatePlaybackCaches() {
        streamProxy.clearUrlCache()
        repository.clearCrossSourceCaches()
        repository.clearOfficialUnavailableCache()
    }

    private fun reloadScriptsForCookieChange() {
        viewModelScope.launch {
            invalidatePlaybackCaches()
            val loaded = repository.loaded.value.keys.toList()
            if (loaded.isEmpty()) return@launch
            val active = _activeScript.value
            loaded.forEach { repository.unload(it) }
            repository.loadAllScripts(activeScriptPath = active)
            val caps = active?.let { repository.loaded.value[it] }
            _loadState.value = LoadState(loadedSources = caps?.sources?.keys?.toList() ?: emptyList())
        }
    }

    val preferredQuality: StateFlow<String> = userPreferencesRepository.lxMusicPreferredQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "320k")


    val platformNameStyle: StateFlow<String> = userPreferencesRepository.lxPlatformNameStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "alias")

    val platformOrder: StateFlow<List<String>> = userPreferencesRepository.lxPlatformOrderFlow
        .map { json ->
            if (json.isNullOrBlank()) {
                DEFAULT_PLATFORM_ORDER
            } else {
                runCatching {
                    kotlinx.serialization.json.Json.decodeFromString<List<String>>(json)
                }.getOrDefault(DEFAULT_PLATFORM_ORDER)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_PLATFORM_ORDER)

    fun setPlatformNameStyle(style: String) {
        if (style == platformNameStyle.value) return
        viewModelScope.launch {
            userPreferencesRepository.saveLxPlatformNameStyle(style)
        }
    }

    fun savePlatformOrder(order: List<String>) {
        viewModelScope.launch {
            val json = kotlinx.serialization.json.Json.encodeToString(order)
            userPreferencesRepository.saveLxPlatformOrder(json)
        }
    }

    fun resetPlatformOrder() {
        viewModelScope.launch {
            userPreferencesRepository.resetLxPlatformOrder()
        }
    }

    fun setPreferredQuality(quality: String) {
        if (quality == preferredQuality.value) return
        viewModelScope.launch {
            userPreferencesRepository.setLxMusicPreferredQuality(quality)
            streamProxy.clearUrlCache()
            repository.clearCrossSourceCaches()
        }
    }

    fun selectScript(assetPath: String) {
        viewModelScope.launch {
            val cur = userPreferencesRepository.lxMusicDisabledScriptsFlow.first()
            if (assetPath in cur) {
                userPreferencesRepository.setLxMusicDisabledScripts(cur - assetPath)
            }
            val active = _activeScript.value
            val becomesPreferred = active == null || active in cur
            if (becomesPreferred) {
                becomePreferred(assetPath)
                if (assetPath !in repository.runtimePaths.value) {
                    _loadState.value = _loadState.value.copy(loading = true, error = null)
                    loadScriptQuietly(assetPath)
                }
            } else if (assetPath !in repository.runtimePaths.value) {
                loadScriptQuietly(assetPath)
            }
            streamProxy.clearUrlCache()
            repository.clearCrossSourceCaches()
            _searchState.value = _searchState.value.copy(results = emptyList(), error = null, searchedSource = null)
        }
    }

    private val perSourceModule = mutableMapOf<String, String>()

    fun selectSource(source: String) {
        if (source == _selectedSource.value) return
        currentLoadJob?.cancel()
        loadEpoch++

        val oldSource = _selectedSource.value
        _selectedSource.value = source
        persistSelectedSource()

        val st = _browseState.value
        perSourceModule[oldSource] = st.module
        resetMineNavigation()
        val restoredModule = when (val remembered = perSourceModule[source]) {
            null -> "board"
            "mine" -> if (isSourceLoggedIn(source)) "mine" else "board"
            else -> remembered
        }
        _browseState.value = st.copy(
            module = restoredModule,
            moduleSource = source,
            boards = emptyList(),
            selectedBoard = null,
            boardSongs = emptyList(),
            loadingBoard = false,
            playlistTags = emptyList(),
            selectedTag = null,
            playlists = emptyList(),
            loadingPlaylists = false,
            selectedPlaylist = null,
            playlistSongs = emptyList(),
            loadingPlaylistSongs = false,
            error = null,
            isPlaylistSearchMode = false,
            playlistSearchKeyword = "",
            playlistSearchResults = emptyList(),
            searchingPlaylists = false,
            userPlaylists = emptyList(),
            loadingUserPlaylists = false,
            userPlaylistUid = null,
            userPlaylistName = null,
        )

        _searchState.value = _searchState.value.copy(
            keyword = "",
            results = emptyList(),
            error = null,
            searchedSource = null,
            page = 1,
            canLoadMore = false,
            loadingMore = false,
            searchEpoch = _searchState.value.searchEpoch + 1,
            artistResults = emptyList(),
            artistTotal = 0,
            artistPage = 1,
            loadingMoreArtists = false,
            canLoadMoreArtists = false,
            artistUnsupported = false,
            albumResults = emptyList(),
            albumTotal = 0,
            albumPage = 1,
            loadingMoreAlbums = false,
            canLoadMoreAlbums = false,
            albumUnsupported = false,
        )
        _searchArtistOverlay.value = false
        _searchArtistPageState.value = SearchArtistPageState()
        dismissSearchAlbumOverlay()

        when (restoredModule) {
            "board", "playlist" -> startModuleLoad(restoredModule, source, force = true)
            "mine" -> ensureMineHome()
        }
    }

    private fun persistSelectedSource() {
        viewModelScope.launch {
            userPreferencesRepository.setLxMusicSelectedSource(_selectedSource.value)
        }
    }


    private val gson = com.google.gson.Gson()

    val playHistory: StateFlow<List<LxSong>> = userPreferencesRepository.lxMusicPlayHistoryFlow
        .map { json ->
            if (json.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching {
                    com.google.gson.JsonParser.parseString(json).asJsonArray
                        .mapNotNull { el ->
                            runCatching { gson.fromJson(el, LxSong::class.java) }.getOrNull()
                        }
                }.getOrElse { emptyList() }
            }
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    fun recordPlay(song: LxSong) {
        viewModelScope.launch {
            repository.persistLxSong(song)
            addToPlayHistory(song)
        }
    }

    private val playbackQueueLxSongs = mutableMapOf<String, LxSong>()

    fun setPlaybackQueueLxSongs(songs: List<LxSong>) {
        playbackQueueLxSongs.clear()
        for (song in songs) {
            val id = com.minoppol.music.data.lxmusic.LxSongMapper
                .unifiedSongId(song.source, song.songmid).toString()
            playbackQueueLxSongs[id] = song
        }
    }

    fun onPlaybackSongChanged(songId: String?) {
        if (songId == null) return
        val lxSong = playbackQueueLxSongs[songId] ?: return
        recordPlay(lxSong)
    }

    fun persistSongs(songs: List<LxSong>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.persistLxSongs(songs) }
                .onFailure { Timber.w(it, "persistSongs failed, size=${songs.size}") }
        }
    }

    private suspend fun addToPlayHistory(song: LxSong) {
        val current = playHistory.value.toMutableList()
        current.removeAll { it.source == song.source && it.songmid == song.songmid }
        current.add(0, song)
        val capped = current.take(100)
        userPreferencesRepository.setLxMusicPlayHistory(
            gson.toJson(capped.map { com.google.gson.JsonParser.parseString(gson.toJson(it)) })
        )
    }

    fun removeFromPlayHistory(source: String, songmid: String) {
        viewModelScope.launch {
            val current = playHistory.value.toMutableList()
            if (current.removeAll { it.source == source && it.songmid == songmid }) {
                userPreferencesRepository.setLxMusicPlayHistory(
                    gson.toJson(current.map { com.google.gson.JsonParser.parseString(gson.toJson(it)) })
                )
            }
        }
    }


    data class BrowseState(
        val module: String = "board",
        val moduleSource: String? = null,
        val boards: List<LxBoard> = emptyList(),
        val selectedBoard: LxBoard? = null,
        val boardSongs: List<LxSong> = emptyList(),
        val loadingBoard: Boolean = false,
        val playlistTags: List<LxPlaylistTag> = emptyList(),
        val selectedTag: LxPlaylistTag? = null,
        val playlists: List<LxPlaylist> = emptyList(),
        val loadingPlaylists: Boolean = false,
        val selectedPlaylist: LxPlaylist? = null,
        val playlistSongs: List<LxSong> = emptyList(),
        val loadingPlaylistSongs: Boolean = false,
        val playlistSongsPage: Int = 1,
        val playlistSongsHasMore: Boolean = false,
        val playlistSongsLoadingMore: Boolean = false,
        val error: String? = null,
        val playlistSearchKeyword: String = "",
        val isPlaylistSearchMode: Boolean = false,
        val playlistSearchResults: List<LxPlaylist> = emptyList(),
        val searchingPlaylists: Boolean = false,
        val userPlaylists: List<LxPlaylist> = emptyList(),
        val loadingUserPlaylists: Boolean = false,
        val userPlaylistUid: String? = null,
        val userPlaylistName: String? = null,
    )

    fun selectModule(module: String) {
        if (_browseState.value.module == module) return
        currentLoadJob?.cancel()
        loadEpoch++

        if (_browseState.value.module == "search") {
            _searchArtistOverlay.value = false
        }

        if (module == "mine") {
            if (!isSourceLoggedIn(_selectedSource.value)) return
            _browseState.value = _browseState.value.copy(module = "mine", error = null)
            perSourceModule[_selectedSource.value] = "mine"
            ensureMineHome()
            return
        }
        val source = _selectedSource.value
        _browseState.value = _browseState.value.copy(module = module, error = null)
        perSourceModule[source] = module
        if (module == "search") return
        startModuleLoad(module, source, force = false)
    }

    private fun startModuleLoad(module: String, source: String, force: Boolean) {
        currentLoadJob = viewModelScope.launch {
            val myEpoch = loadEpoch
            runCatching {
                ensureModuleDataInternal(module, source, force, myEpoch)
            }.onFailure {
                Timber.w(it, "startModuleLoad $module/$source failed (epoch=$myEpoch)")
            }
        }
    }


    private suspend fun ensureModuleDataInternal(module: String, source: String, force: Boolean, epoch: Long) {
        val st = _browseState.value
        val stale = st.moduleSource != source
        if (!force && !stale) {
            if (module == "board" && st.boards.isNotEmpty()) return
            if (module == "playlist" && st.playlistTags.isNotEmpty()) {
                if (!playlistListDirty || st.selectedPlaylist != null || st.userPlaylistUid != null) return
                playlistListDirty = false
                val tag = st.selectedTag ?: st.playlistTags.firstOrNull() ?: return
                loadPlaylistsForTagInternal(tag, source, epoch)
                return
            }
        }
        when (module) {
            "board" -> {
                val boards = repository.getBoards(source)
                if (loadEpoch != epoch) return
                _browseState.value = _browseState.value.copy(
                    moduleSource = source,
                    boards = boards,
                    selectedBoard = null,
                    boardSongs = emptyList(),
                    loadingBoard = false,
                    error = null,
                )
                val first = boards.firstOrNull() ?: run {
                    _browseState.value = _browseState.value.copy(error = context.getString(R.string.lx_error_board_unsupported))
                    return
                }
                loadBoardSongsInternal(first, source, epoch)
            }
            "playlist" -> {
                _browseState.value = _browseState.value.copy(
                    moduleSource = source,
                    playlistTags = emptyList(),
                    selectedTag = null,
                    playlists = emptyList(),
                    selectedPlaylist = null,
                    playlistSongs = emptyList(),
                    loadingPlaylists = true,
                    error = null,
                    userPlaylists = emptyList(),
                    loadingUserPlaylists = false,
                    userPlaylistUid = null,
                    userPlaylistName = null,
                )
                val tags = runCatching { repository.getPlaylistTags(source) }.getOrElse {
                    Timber.w(it, "ensureModuleData: getPlaylistTags $source failed")
                    emptyList()
                }
                if (loadEpoch != epoch || _selectedSource.value != source || _browseState.value.module != "playlist") return
                _browseState.value = _browseState.value.copy(playlistTags = tags, loadingPlaylists = false)
                val first = tags.firstOrNull() ?: run {
                    _browseState.value = _browseState.value.copy(error = context.getString(R.string.lx_error_playlist_unsupported))
                    return
                }
                loadPlaylistsForTagInternal(first, source, epoch)
            }
        }
    }

    fun selectBoard(board: LxBoard) {
        val st = _browseState.value
        if (st.selectedBoard == board && st.boardSongs.isNotEmpty()) return
        currentLoadJob?.cancel()
        val epoch = ++loadEpoch
        currentLoadJob = viewModelScope.launch {
            loadBoardSongsInternal(board, _selectedSource.value, epoch)
        }
    }

    private suspend fun loadBoardSongsInternal(board: LxBoard, source: String, epoch: Long) {
        _browseState.value = _browseState.value.copy(
            selectedBoard = board, loadingBoard = true, error = null, boardSongs = emptyList(),
        )
        val songs = runCatching { repository.fetchBoardSongs(source, board.bangid) }
            .getOrElse { Timber.w(it, "loadBoardSongs failed ${board.id}"); emptyList() }
        if (loadEpoch != epoch || _selectedSource.value != source) return
        val cur = _browseState.value
        if (cur.selectedBoard != board) return
        _browseState.value = cur.copy(
            boardSongs = songs,
            loadingBoard = false,
            error = if (songs.isEmpty()) context.getString(R.string.lx_error_board_load_failed) else null,
        )
        loadBrowsePics(songs)
    }

    fun selectPlaylistTag(tag: LxPlaylistTag) {
        currentLoadJob?.cancel()
        val epoch = ++loadEpoch
        currentLoadJob = viewModelScope.launch {
            loadPlaylistsForTagInternal(tag, _selectedSource.value, epoch)
        }
    }

    private suspend fun loadPlaylistsForTagInternal(tag: LxPlaylistTag, source: String, epoch: Long) {
        _browseState.value = _browseState.value.copy(
            selectedTag = tag, loadingPlaylists = true, error = null,
            selectedPlaylist = null, playlistSongs = emptyList(),
        )
        val playlists = runCatching { repository.fetchPlaylists(source, tag.id, 1) }
            .getOrElse { Timber.w(it, "loadPlaylistsForTag failed ${tag.id}"); emptyList() }
        if (loadEpoch != epoch || _selectedSource.value != source) return
        val cur = _browseState.value
        if (cur.selectedTag != tag) return
        _browseState.value = cur.copy(
            playlists = playlists,
            loadingPlaylists = false,
            error = if (playlists.isEmpty()) context.getString(R.string.lx_error_playlist_load_failed) else null,
        )
    }

    fun selectPlaylist(playlist: LxPlaylist) {
        currentLoadJob?.cancel()
        val epoch = ++loadEpoch
        currentLoadJob = viewModelScope.launch {
            loadPlaylistSongsInternal(playlist, _selectedSource.value, epoch)
        }
    }

    fun backToPlaylistList() {
        _browseState.value = _browseState.value.copy(
            selectedPlaylist = null, playlistSongs = emptyList(), error = null,
        )
    }

    fun back(): Boolean {
        val st = _browseState.value
        val mineHomeAvailable = isSourceLoggedIn(_selectedSource.value)
        val rootModule = if (mineHomeAvailable) "mine" else "board"
        if (st.module == "mine") {
            if (mineBackStack.size > 1) {
                backMinePage()
                return true
            }
            return false
        }
        if (st.selectedPlaylist != null) {
            if (st.playlistTags.isEmpty() && st.playlists.isEmpty() && !st.isPlaylistSearchMode) {
                _browseState.value = st.copy(
                    selectedPlaylist = null, playlistSongs = emptyList(),
                    loadingPlaylistSongs = false, error = null,
                )
                selectModule(rootModule)
            } else {
                backToPlaylistList()
            }
            return true
        }
        if (st.userPlaylistUid != null || st.loadingUserPlaylists) {
            exitUserPlaylists()
            return true
        }
        if (st.isPlaylistSearchMode) {
            exitPlaylistSearch()
            return true
        }
        if (st.module == "playlist") {
            selectModule(rootModule)
            return true
        }
        if (mineHomeAvailable) {
            selectModule("mine")
            return true
        }
        return false
    }


    fun updatePlaylistSearchQuery(query: String) {
        _browseState.value = _browseState.value.copy(playlistSearchKeyword = query)
    }

    fun searchPlaylists(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        currentLoadJob?.cancel()
        val epoch = ++loadEpoch
        val source = _selectedSource.value
        _browseState.value = _browseState.value.copy(
            playlistSearchKeyword = trimmed,
            isPlaylistSearchMode = true,
            searchingPlaylists = true,
            error = null,
            playlistSearchResults = emptyList(),
        )
        currentLoadJob = viewModelScope.launch {
            val results = runCatching { repository.searchPlaylists(source, trimmed, 1) }
                .onFailure { Timber.w(it, "searchPlaylists $source failed") }
                .getOrElse { emptyList() }
            if (loadEpoch != epoch) return@launch
            val cur = _browseState.value
            if (_selectedSource.value != source || !cur.isPlaylistSearchMode) return@launch
            _browseState.value = cur.copy(
                playlistSearchResults = results,
                searchingPlaylists = false,
                error = if (results.isEmpty()) context.getString(R.string.lx_error_no_playlist_found) else null,
            )
        }
    }

    fun exitPlaylistSearch() {
        _browseState.value = _browseState.value.copy(
            isPlaylistSearchMode = false,
            playlistSearchKeyword = "",
            playlistSearchResults = emptyList(),
            error = null,
        )
    }


    fun loadNeteaseUserPlaylists(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        currentLoadJob?.cancel()
        val epoch = ++loadEpoch
        val source = LxSources.NETEASE
        if (_selectedSource.value != source) {
            _selectedSource.value = source
            persistSelectedSource()
        }
        _browseState.value = _browseState.value.copy(
            module = "playlist",
            moduleSource = source,
            selectedPlaylist = null,
            playlistSongs = emptyList(),
            loadingPlaylistSongs = false,
            isPlaylistSearchMode = false,
            playlistSearchKeyword = "",
            playlistSearchResults = emptyList(),
            searchingPlaylists = false,
            userPlaylists = emptyList(),
            loadingUserPlaylists = true,
            userPlaylistUid = null,
            userPlaylistName = null,
            error = null,
        )
        currentLoadJob = viewModelScope.launch {
            val uid = runCatching { repository.resolveNeteaseUserId(trimmed) }.getOrNull()
            if (uid == null) {
                if (loadEpoch != epoch) return@launch
                _browseState.value = _browseState.value.copy(
                    loadingUserPlaylists = false,
                    error = context.getString(R.string.lx_error_invalid_uid),
                )
                return@launch
            }
            val list = runCatching { repository.fetchNeteaseUserPlaylists(uid) }
                .onFailure { Timber.w(it, "loadNeteaseUserPlaylists uid=$uid failed") }
                .getOrElse { emptyList() }
            if (loadEpoch != epoch || _selectedSource.value != source) return@launch
            val name = list.firstOrNull { it.specialType == 5 }?.creator
                ?: list.firstOrNull()?.creator
            _browseState.value = _browseState.value.copy(
                userPlaylists = list,
                loadingUserPlaylists = false,
                userPlaylistUid = uid,
                userPlaylistName = name,
                error = if (list.isEmpty()) context.getString(R.string.lx_error_user_playlists_not_found) else null,
            )
        }
    }

    fun exitUserPlaylists() {
        _browseState.value = _browseState.value.copy(
            userPlaylists = emptyList(),
            loadingUserPlaylists = false,
            userPlaylistUid = null,
            userPlaylistName = null,
            error = null,
        )
    }


    sealed interface MinePage {
        data object Home : MinePage
        data object RecommendPlaylists : MinePage
        data object SubArtists : MinePage
        data object SubAlbums : MinePage
        data class Artist(val id: String, val name: String) : MinePage

        data class SongList(
            val title: String,
            val kind: String,
            val playlistId: String? = null,
            val albumId: String? = null,
            val artistId: String? = null,
        ) : MinePage
    }

    data class MineState(
        val loadingHome: Boolean = false,
        val homeLoaded: Boolean = false,
        val recommendPlaylists: List<LxPlaylist> = emptyList(),
        val myPlaylists: List<LxPlaylist> = emptyList(),
        val subArtists: List<LxArtist> = emptyList(),
        val subAlbums: List<LxAlbum> = emptyList(),
        val page: MinePage = MinePage.Home,
        val pageTitle: String = "",
        val pageSongs: List<LxSong> = emptyList(),
        val pageLoading: Boolean = false,
        val pagePlaylists: List<LxPlaylist> = emptyList(),
        val pageArtists: List<LxArtist> = emptyList(),
        val pageAlbums: List<LxAlbum> = emptyList(),
        val pageSongsHasMore: Boolean = false,
        val pageLoadingMore: Boolean = false,
        val artistTab: String = "songs", // songs | albums
        val artistInfo: LxArtist? = null,
        val artistFollowed: Boolean? = null,
        val artistFollowBusy: Boolean = false,
        val artistSongsTotal: Int = 0,
        val artistSongsHasMore: Boolean = false,
        val artistLoadingMore: Boolean = false,
        val navSession: Long = 0,
        val error: String? = null,
    )

    private data class MinePageSnapshot(
        val page: MinePage,
        val pageTitle: String,
        val pageSongs: List<LxSong>,
        val pagePlaylists: List<LxPlaylist>,
        val pageArtists: List<LxArtist>,
        val pageAlbums: List<LxAlbum>,
        val pageSongsHasMore: Boolean,
        val pageLoadingMore: Boolean,
        val artistTab: String,
        val artistInfo: LxArtist?,
        val artistFollowed: Boolean?,
        val artistFollowBusy: Boolean,
        val artistSongsTotal: Int,
        val artistSongsHasMore: Boolean,
        val artistLoadingMore: Boolean,
        val navSession: Long,
        val pageLoading: Boolean,
        val error: String?,
    )

    private fun MineState.pageSnapshot() = MinePageSnapshot(
        page = page,
        pageTitle = pageTitle,
        pageSongs = pageSongs,
        pagePlaylists = pagePlaylists,
        pageArtists = pageArtists,
        pageAlbums = pageAlbums,
        pageSongsHasMore = pageSongsHasMore,
        pageLoadingMore = pageLoadingMore,
        artistTab = artistTab,
        artistInfo = artistInfo,
        artistFollowed = artistFollowed,
        artistFollowBusy = artistFollowBusy,
        artistSongsTotal = artistSongsTotal,
        artistSongsHasMore = artistSongsHasMore,
        artistLoadingMore = artistLoadingMore,
        navSession = navSession,
        pageLoading = pageLoading,
        error = error,
    )

    private fun MineState.restorePageSnapshot(s: MinePageSnapshot): MineState = copy(
        page = s.page,
        pageTitle = s.pageTitle,
        pageSongs = s.pageSongs,
        pagePlaylists = s.pagePlaylists,
        pageArtists = s.pageArtists,
        pageAlbums = s.pageAlbums,
        pageSongsHasMore = s.pageSongsHasMore,
        pageLoadingMore = s.pageLoadingMore,
        artistTab = s.artistTab,
        artistInfo = s.artistInfo,
        artistFollowed = s.artistFollowed,
        artistFollowBusy = s.artistFollowBusy,
        artistSongsTotal = s.artistSongsTotal,
        artistSongsHasMore = s.artistSongsHasMore,
        artistLoadingMore = s.artistLoadingMore,
        navSession = s.navSession,
        pageLoading = s.pageLoading,
        error = s.error,
    )

    private inline fun navigateForward(build: MineState.() -> MineState) {
        mineBackStack[mineBackStack.lastIndex] = _mineState.value.pageSnapshot()
        val session = ++mineNavSession
        val target = _mineState.value.build().copy(navSession = session)
        _mineState.value = target
        mineBackStack.addLast(target.pageSnapshot())
    }

    fun resetMineNavigation() {
        mineJob?.cancel()
        mineNavSession = 0L
        mineBackStack.clear()
        mineBackStack.addLast(MineState().pageSnapshot())
        _mineState.value = MineState()
    }

    fun validateAndLogin(rawCookie: String, onResult: (ok: Boolean, error: String?) -> Unit) {
        val cookie = rawCookie.trim()
        if (!cookie.contains("S_INFO=")) {
            onResult(false, context.getString(R.string.lx_msg_no_login_info))
            return
        }
        viewModelScope.launch {
            neteaseCookieStore.setCookie(cookie)
            when (val res = runCatching { neteaseOfficialApi.fetchLoginProfile() }.getOrNull()) {
                is NeteaseOfficialApi.ProfileResult.Ok -> {
                    val profile = res.profile
                    neteaseCookieStore.setProfile(
                        profile.nickname.ifBlank { context.getString(R.string.lx_default_nickname) },
                        profile.avatarUrl,
                    )
                    refreshNeteaseAccountInfo()
                    reloadScriptsForCookieChange()
                    resetMineNavigation()
                    if (_selectedSource.value != LxSources.NETEASE) {
                        _selectedSource.value = LxSources.NETEASE
                        persistSelectedSource()
                    }
                    _browseState.value = _browseState.value.copy(module = "mine", error = null)
                    loadMineHome(force = true)
                    onResult(true, null)
                }
                is NeteaseOfficialApi.ProfileResult.CookieInvalid -> {
                    neteaseCookieStore.clear()
                    onResult(false, context.getString(R.string.lx_msg_login_invalid))
                }
                else -> {
                    neteaseCookieStore.clear()
                    onResult(false, context.getString(R.string.lx_msg_network_retrying))
                }
            }
        }
    }

    fun logoutNetease() {
        clearNeteaseWebViewSession()
        clearNeteaseCookie()
        resetMineNavigation()
        _mineState.value = MineState()
        _searchState.value = SearchState()
        repository.clearNeteaseUid()
        if (_browseState.value.module == "mine") selectModule("board")
    }


    private fun validateAndLoginPlatform(
        rawCookie: String,
        source: String,
        hasMarker: (String) -> Boolean,
        saveCookie: (String) -> Unit,
        saveProfile: (String?, String?) -> Unit,
        fetchProfile: suspend () -> Pair<String?, String?>,
        fetchLikedIds: suspend () -> Set<String>,
        updateLikedIds: (Set<String>) -> Unit,
        onResult: (ok: Boolean, error: String?) -> Unit,
    ) {
        val cookie = rawCookie.trim()
        if (!hasMarker(cookie)) {
            onResult(false, context.getString(R.string.lx_msg_no_login_info))
            return
        }
        viewModelScope.launch {
            saveCookie(cookie)
            runCatching { fetchProfile() }.getOrNull()?.let { (nick, avatar) ->
                if (!nick.isNullOrBlank() || !avatar.isNullOrBlank()) saveProfile(nick, avatar)
            }
            updateLikedIds(runCatching { fetchLikedIds() }.getOrDefault(emptySet()))
            if (_selectedSource.value != source) {
                _selectedSource.value = source
                persistSelectedSource()
            }
            resetMineNavigation()
            _browseState.value = _browseState.value.copy(module = "mine", error = null)
            loadMineHome(force = true)
            onResult(true, null)
        }
    }

    fun validateAndLoginTencent(rawCookie: String, onResult: (ok: Boolean, error: String?) -> Unit) =
        validateAndLoginPlatform(
            rawCookie = rawCookie,
            source = LxSources.TENCENT,
            hasMarker = { it.contains("qm_keyst=") || it.contains("qqmusic_key=") },
            saveCookie = tencentCookieStore::setCookie,
            saveProfile = tencentCookieStore::setProfile,
            fetchProfile = { repository.fetchTxProfile() },
            fetchLikedIds = { repository.fetchTxLikedSongIds() },
            updateLikedIds = repository::setTxLikedSongIds,
            onResult = onResult,
        )

    fun txQrCreate(type: LxTxQrType, onResult: (ByteArray?, String?) -> Unit) {
        viewModelScope.launch {
            val r = runCatching { repository.txQrCreate(type) }.getOrNull()
            onResult(r?.first, r?.second)
        }
    }

    fun txQrPoll(type: LxTxQrType, session: String, onResult: (LxTxQrPoll) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.txQrPoll(type, session) }
                .getOrElse { LxTxQrPoll(LxTxQrPoll.Status.ERROR) })
        }
    }

    fun loginTencentByQr(rawCookie: String, onResult: (ok: Boolean, error: String?) -> Unit) =
        validateAndLoginPlatform(
            rawCookie = rawCookie,
            source = LxSources.TENCENT,
            hasMarker = { it.contains("qqmusic_key=") || it.contains("p_skey=") || it.contains("skey=") },
            saveCookie = tencentCookieStore::setCookie,
            saveProfile = tencentCookieStore::setProfile,
            fetchProfile = { repository.fetchTxProfile() },
            fetchLikedIds = { repository.fetchTxLikedSongIds() },
            updateLikedIds = repository::setTxLikedSongIds,
            onResult = onResult,
        )

    fun sendKugouSmsCode(mobile: String, onResult: (ok: Boolean, error: String?) -> Unit) {
        if (!Regex("^1\\d{10}$").matches(mobile)) {
            onResult(false, context.getString(R.string.lx_msg_invalid_mobile)); return
        }
        viewModelScope.launch {
            val ok = runCatching { repository.kgSendSmsCode(mobile) }.getOrDefault(false)
            onResult(ok, if (ok) null else context.getString(R.string.lx_msg_sms_send_failed))
        }
    }

    fun loginKugouBySms(mobile: String, code: String, onResult: (ok: Boolean,  error: String?) -> Unit) {
        if (!Regex("^1\\d{10}$").matches(mobile)) {
            onResult(false, context.getString(R.string.lx_msg_invalid_mobile)); return
        }
        if (code.isBlank()) {
            onResult(false, context.getString(R.string.lx_msg_invalid_code)); return
        }
        viewModelScope.launch {
            val ok = runCatching { repository.kgLoginBySmsCode(mobile, code.trim()) }.getOrDefault(false)
            if (!ok) {
                onResult(false, context.getString(R.string.lx_msg_login_invalid)); return@launch
            }
            repository.clearKgCloudCache()
            runCatching { repository.setKgLikedSongIds(repository.fetchKgLikedSongIds()) }
            if (_selectedSource.value != LxSources.KUGOU) {
                _selectedSource.value = LxSources.KUGOU
                persistSelectedSource()
            }
            resetMineNavigation()
            _browseState.value = _browseState.value.copy(module = "mine", error = null)
            loadMineHome(force = true)
            onResult(true, null)
        }
    }

    fun validateAndLoginKuwo(rawCookie: String, onResult: (ok: Boolean, error: String?) -> Unit) =
        validateAndLoginPlatform(
            rawCookie = rawCookie,
            source = LxSources.KUWO,
            hasMarker = { Regex("userid=\\d+").containsMatchIn(it) && it.contains("sid=") },
            saveCookie = kuwoCookieStore::setCookie,
            saveProfile = kuwoCookieStore::setProfile,
            fetchProfile = { repository.fetchKwProfile() },
            fetchLikedIds = { repository.fetchKwLikedSongIds() },
            updateLikedIds = repository::setKwLikedSongIds,
            onResult = onResult,
        )

    fun logoutTencent() {
        clearAllWebViewSession()
        tencentCookieStore.clear()
        repository.setTxLikedSongIds(emptySet())
        if (_browseState.value.module == "mine" && _selectedSource.value == LxSources.TENCENT) {
            selectModule("board")
        }
    }

    fun logoutKugou() {
        clearAllWebViewSession()
        kugouCookieStore.clear()
        repository.clearKgCloudCache()
        repository.setKgLikedSongIds(emptySet())
        if (_browseState.value.module == "mine" && _selectedSource.value == LxSources.KUGOU) {
            selectModule("board")
        }
    }

    fun logoutKuwo() {
        clearAllWebViewSession()
        kuwoCookieStore.clear()
        repository.clearKwLikedListCache()
        repository.setKwLikedSongIds(emptySet())
        if (_browseState.value.module == "mine" && _selectedSource.value == LxSources.KUWO) {
            selectModule("board")
        }
    }

    private fun MineState.likedPlaylist(): LxPlaylist? =
        myPlaylists.firstOrNull { it.specialType == 5 }

    private fun ensureMineHome() {
        val st = _mineState.value
        if (!st.loadingHome && !st.homeLoaded) loadMineHome(force = false)
        viewModelScope.launch {
            if (neteaseCookieStore.hasCookie() &&
                System.currentTimeMillis() - neteaseCookieStore.getAccountUpdatedAt() > MINE_COOKIE_CHECK_TTL_MS
            ) {
                refreshNeteaseAccountInfo()
            }
        }
    }

    private fun ensurePlatformLikedIds(source: String) {
        viewModelScope.launch {
            runCatching {
                when (source) {
                    LxSources.NETEASE -> repository.ensureNeteaseLikedListLoaded()
                    LxSources.TENCENT ->
                        if (repository.txLikedSongIds.value.isEmpty()) {
                            repository.setTxLikedSongIds(repository.fetchTxLikedSongIds())
                        }
                    LxSources.KUGOU ->
                        if (repository.kgLikedSongIds.value.isEmpty()) {
                            repository.setKgLikedSongIds(repository.fetchKgLikedSongIds())
                        }
                    LxSources.KUWO ->
                        if (repository.kwLikedSongIds.value.isEmpty()) {
                            repository.setKwLikedSongIds(repository.fetchKwLikedSongIds())
                        }
                }
            }.onFailure { Timber.w(it, "ensurePlatformLikedIds failed: %s", source) }
        }
    }

    fun isSourceLoggedIn(source: String): Boolean = when (source) {
        LxSources.NETEASE -> neteaseCookieStore.hasCookie()
        LxSources.TENCENT -> tencentCookieStore.hasCookie()
        LxSources.KUGOU -> kugouCookieStore.hasCookie() || kugouCookieStore.hasAppLogin()
        LxSources.KUWO -> kuwoCookieStore.hasCookie()
        else -> false
    }

    fun loadMineHome(force: Boolean) {
        val source = _selectedSource.value
        if (!isSourceLoggedIn(source)) return
        if (force) {
            if (source == LxSources.NETEASE) {
                viewModelScope.launch { runCatching { refreshNeteaseAccountInfo() } }
            } else if (source == LxSources.KUGOU) {
                runCatching { repository.invalidateKgCloudListsCache() }
            }
        } else {
            val st = _mineState.value
            if (st.loadingHome || st.homeLoaded) return
        }
        mineJob?.cancel()
        mineJob = viewModelScope.launch {
            _mineState.value = _mineState.value.copy(loadingHome = true, error = null)
            when (source) {
                LxSources.TENCENT -> loadPlatformMineHome(LxSources.TENCENT)
                LxSources.KUGOU -> loadPlatformMineHome(LxSources.KUGOU)
                LxSources.KUWO -> loadPlatformMineHome(LxSources.KUWO)
                else -> loadNeteaseMineHome()
            }
        }
    }

    private suspend fun loadNeteaseMineHome() {
        var uid = neteaseCookieStore.getAccountId().takeIf { it >= 0 }
        if (uid == null) {
            runCatching { repository.fetchNeteaseLoginProfile() }.getOrNull()?.let { p ->
                uid = p.uid
                neteaseCookieStore.setProfile(p.nickname, p.avatarUrl)
            }
        }
        coroutineScope {
            val recommendDeferred = async {
                runCatching { repository.fetchNeteaseRecommendPlaylists() }.getOrDefault(emptyList())
            }
            val playlistsDeferred = async {
                val u = uid ?: return@async emptyList()
                runCatching { repository.fetchNeteaseMyPlaylists(u.toString()) }.getOrDefault(emptyList())
            }
            val artistsDeferred = async {
                runCatching { repository.fetchNeteaseSubArtists() }.getOrDefault(emptyList())
            }
            val albumsDeferred = async {
                runCatching { repository.fetchNeteaseSubAlbums() }.getOrDefault(emptyList())
            }
            val recommend = recommendDeferred.await()
            val playlists = playlistsDeferred.await()
            val artists = artistsDeferred.await()
            val albums = albumsDeferred.await()
            var newState = _mineState.value.copy(
                loadingHome = false,
                homeLoaded = true,
                recommendPlaylists = recommend,
                myPlaylists = playlists,
                subArtists = artists,
                subAlbums = albums,
                error = null,
            )
            if (newState.page is MinePage.RecommendPlaylists) {
                newState = newState.copy(pagePlaylists = recommend)
            }
            _mineState.value = newState
        }
        _collectDataVersion.update { it + 1 }
    }

    private suspend fun loadPlatformMineHome(source: String) {
        val (nick, avatar) = runCatching {
            when (source) {
                LxSources.TENCENT -> repository.fetchTxProfile()
                LxSources.KUGOU -> repository.fetchKgProfile()
                LxSources.KUWO -> repository.fetchKwProfile()
                else -> null to null
            }
        }.getOrNull() ?: (null to null)
        if (!nick.isNullOrBlank() || !avatar.isNullOrBlank()) {
            when (source) {
                LxSources.TENCENT -> tencentCookieStore.setProfile(nick, avatar)
                LxSources.KUGOU -> kugouCookieStore.setProfile(nick, avatar)
                LxSources.KUWO -> kuwoCookieStore.setProfile(nick, avatar)
            }
        }
        if (source == LxSources.KUGOU && kugouCookieStore.hasAppLogin()) {
            runCatching { repository.setKgLikedSongIds(repository.fetchKgLikedSongIds()) }
        }
        if (source == LxSources.KUWO) {
            runCatching { repository.setKwLikedSongIds(repository.fetchKwLikedSongIds()) }
        }
        if (source == LxSources.TENCENT && tencentCookieStore.hasCookie()) {
            runCatching { repository.setTxLikedSongIds(repository.fetchTxLikedSongIds()) }
        }
        val likedIds = when (source) {
            LxSources.TENCENT -> repository.txLikedSongIds.value
            LxSources.KUGOU -> repository.kgLikedSongIds.value
            LxSources.KUWO -> repository.kwLikedSongIds.value
            else -> emptySet()
        }
        val likedCount = if (source == LxSources.KUGOU) {
            likedIds.count { it.length == 32 && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' } }
        } else {
            likedIds.size
        }
        val cloudPlaylists = when (source) {
            LxSources.KUGOU -> runCatching { repository.fetchKgUserPlaylists() }.getOrDefault(emptyList())
            LxSources.KUWO -> runCatching { repository.fetchKwUserPlaylists() }.getOrDefault(emptyList())
            LxSources.TENCENT -> runCatching { repository.fetchTxUserPlaylists() }.getOrDefault(emptyList())
            else -> emptyList()
        }
        val likedPlaylist = LxPlaylist(
            id = "${source}_liked",
            name = context.getString(R.string.lx_subtitle_liked_music_default),
            creator = null,
            pic = null,
            trackCount = likedCount,
            specialType = 5,
        )
        val kwArtists = if (source == LxSources.KUWO) {
            (runCatching { repository.fetchKwFollowedArtists() }.getOrNull()
                ?: repository.kwFollowedArtists.value)
        } else emptyList()
        val kwAlbums = if (source == LxSources.KUWO) {
            (runCatching { repository.fetchKwLikedAlbums() }.getOrNull()
                ?: repository.kwLikedAlbums.value)
        } else emptyList()
        val txAlbums = if (source == LxSources.TENCENT) {
            runCatching { repository.fetchTxLikedAlbums() }.getOrDefault(emptyList())
        } else emptyList()
        _mineState.value = _mineState.value.copy(
            loadingHome = false,
            homeLoaded = true,
            recommendPlaylists = emptyList(),
            myPlaylists = listOf(likedPlaylist) + cloudPlaylists,
            subArtists = kwArtists,
            subAlbums = kwAlbums + txAlbums,
            error = null,
        )
        _collectDataVersion.update { it + 1 }
    }


    fun openDailySongs() = openMineSongs(MinePage.SongList(context.getString(R.string.lx_title_daily_recommend), "daily"))

    fun openHeartbeatMode() = openMineSongs(MinePage.SongList(context.getString(R.string.lx_title_heartbeat_mode), "heartbeat"))

    fun openLikedRoaming() = openMineSongs(MinePage.SongList(context.getString(R.string.lx_title_liked_roaming), "roaming"))

    fun openPersonalFm() = openMineSongs(MinePage.SongList(context.getString(R.string.lx_title_personal_fm), "personal_fm"))

    fun openRecommendPlaylistsPage() {
        navigateForward {
            copy(
                page = MinePage.RecommendPlaylists,
                pageTitle = context.getString(R.string.lx_title_recommend_playlists),
                pagePlaylists = recommendPlaylists,
                pageSongs = emptyList(),
                pageArtists = emptyList(),
                pageAlbums = emptyList(),
                artistInfo = null,
                pageLoading = false,
                error = null,
            )
        }
    }


    fun openLikedPlaylist() {
        val liked = _mineState.value.likedPlaylist() ?: return
        openMineSongs(MinePage.SongList(liked.name, "playlist", playlistId = liked.id))
    }

    fun openMinePlaylist(playlist: LxPlaylist) =
        openMineSongs(MinePage.SongList(playlist.name, "playlist", playlistId = playlist.id))

    fun openSubArtistsPage() {
        navigateForward {
            copy(
                page = MinePage.SubArtists,
                pageTitle = context.getString(R.string.lx_title_sub_artists),
                pageArtists = subArtists,
                pageSongs = emptyList(),
                pagePlaylists = emptyList(),
                pageAlbums = emptyList(),
                artistInfo = null,
                pageLoading = false,
                error = null,
            )
        }
    }

    fun openSubAlbumsPage() {
        navigateForward {
            copy(
                page = MinePage.SubAlbums,
                pageTitle = context.getString(R.string.lx_title_sub_albums),
                pageAlbums = subAlbums,
                pageSongs = emptyList(),
                pagePlaylists = emptyList(),
                pageArtists = emptyList(),
                pageSongsHasMore = false,
                pageLoadingMore = false,
                artistInfo = null,
                pageLoading = false,
                error = null,
            )
        }
    }

    fun openArtistPage(artist: LxArtist) {
        val page = MinePage.Artist(artist.id, artist.name.ifBlank { context.getString(R.string.lx_default_artist_name) })
        navigateForward {
            copy(
                page = page,
                pageTitle = page.name,
                artistTab = "songs",
                artistInfo = artist,
                artistFollowed = null,
                artistFollowBusy = false,
                pageSongs = emptyList(),
                pageAlbums = emptyList(),
                pageArtists = emptyList(),
                pagePlaylists = emptyList(),
                artistSongsTotal = 0,
                artistSongsHasMore = false,
                artistLoadingMore = false,
                pageLoading = true,
                error = null,
            )
        }
        loadArtistSongs(artist.id)
    }

    fun openArtistFromSearch(artist: LxArtist, source: String? = null) {
        _searchAlbumOverlay.value = false
        _searchAlbumPageState.value = SearchAlbumPageState()
        _searchArtistPageState.value = SearchArtistPageState(
            artist = artist,
            loading = true,
            source = source ?: _selectedSource.value,
        )
        _searchArtistOverlay.value = true
        loadSearchArtistPage(artist.id)
    }

    private val _searchArtistOverlay = MutableStateFlow(false)
    val searchArtistOverlay: StateFlow<Boolean> = _searchArtistOverlay.asStateFlow()

    fun dismissSearchArtistOverlay() {
        _searchArtistOverlay.value = false
        _searchArtistPageState.value = SearchArtistPageState()
    }

    fun openAlbumFromSearch(album: LxAlbum, source: String? = null) {
        _searchArtistOverlay.value = false
        _searchArtistPageState.value = SearchArtistPageState()
        _searchAlbumPageState.value = SearchAlbumPageState(
            album = album,
            loading = true,
            source = source ?: _selectedSource.value,
        )
        _searchAlbumOverlay.value = true
        loadSearchAlbumPage(album.id)
    }

    fun dismissSearchAlbumOverlay() {
        _searchAlbumOverlay.value = false
        _searchAlbumPageState.value = SearchAlbumPageState()
    }

    private fun loadSearchAlbumPage(albumId: String) {
        viewModelScope.launch {
            val source = _searchAlbumPageState.value.source
            val (detail, songs) = when (source) {
                LxSources.TENCENT -> repository.fetchTxAlbumDetail(albumId, _searchAlbumPageState.value.album?.name.orEmpty())
                LxSources.KUGOU -> repository.fetchKgAlbumDetail(albumId)
                LxSources.KUWO -> repository.fetchKwAlbumDetail(albumId, _searchAlbumPageState.value.album?.name.orEmpty())
                else -> repository.fetchNeteaseAlbumDetail(albumId)
            }
            val cur = _searchAlbumPageState.value
            if (cur.album?.id != albumId) return@launch
            _searchAlbumPageState.value = cur.copy(
                album = detail ?: cur.album,
                loading = false,
                songs = songs,
                error = if (songs.isEmpty()) context.getString(R.string.lx_error_album_no_songs) else null,
            )
            if (songs.isNotEmpty()) loadPicsForResults(songs)
        }
    }

    private fun loadSearchArtistPage(artistId: String) {
        viewModelScope.launch {
            val source = _searchArtistPageState.value.source
            val detailDeferred = async {
                when (source) {
                    LxSources.TENCENT -> repository.fetchTxArtistDetail(artistId)
                    LxSources.KUGOU -> repository.fetchKgArtistDetail(artistId)
                    LxSources.KUWO -> repository.fetchKwArtistDetail(artistId, _searchArtistPageState.value.artist?.name.orEmpty())
                    else -> repository.fetchNeteaseArtistInfo(artistId)
                }
            }
            val songsDeferred = async {
                when (source) {
                    LxSources.TENCENT -> repository.fetchTxArtistSongs(
                        artistId, _searchArtistPageState.value.artist?.name.orEmpty(),
                        offset = 0, limit = artistSongsPageSize)
                    LxSources.KUGOU -> repository.fetchKgArtistSongs(artistId, offset = 0, limit = artistSongsPageSize)
                    LxSources.KUWO -> repository.fetchKwArtistSongs(artistId, _searchArtistPageState.value.artist?.name.orEmpty(), offset = 0, limit = artistSongsPageSize)
                    else -> repository.fetchNeteaseArtistSongs(artistId, offset = 0, limit = artistSongsPageSize)
                }
            }
            val (songs, total) = songsDeferred.await()
            val cur = _searchArtistPageState.value
            if (cur.artist?.id != artistId) return@launch
            _searchArtistPageState.value = cur.copy(
                loading = false,
                songs = songs,
                songsPage = 1,
                songsTotal = total,
                songsHasMore = songs.isNotEmpty() &&
                    (if (total > 0) songs.size < total else songs.size >= artistSongsPageSize),
                error = if (songs.isEmpty()) context.getString(R.string.lx_error_artist_no_hot_songs) else null,
            )
            if (songs.isNotEmpty()) loadPicsForResults(songs)
            val detail = detailDeferred.await()
            val cur2 = _searchArtistPageState.value
            if (cur2.artist?.id != artistId) return@launch
            val detailArtist = detail?.artist
            val artistForFill = when {
                detailArtist == null -> cur2.artist
                !detailArtist.pic.isNullOrBlank() -> detailArtist
                else -> detailArtist.copy(pic = cur2.artist?.pic)
            }
            when (source) {
                LxSources.KUGOU -> {
                    val kgFollowed = if (kugouCookieStore.hasAppLogin() && accountWritesEnabled)
                        runCatching { repository.kgGetSingerFollowInfo(artistId) }.getOrNull()
                    else null
                    _searchArtistPageState.value = cur2.copy(
                        artist = artistForFill, followed = kgFollowed)
                }
                else -> {
                    if (source == LxSources.KUWO) {
                        val kwFollowed = if (kuwoCookieStore.hasCookie() && accountWritesEnabled)
                            repository.kwFollowedArtists.value.any { it.id == artistId } else null
                        _searchArtistPageState.value = cur2.copy(
                            artist = artistForFill, followed = kwFollowed)
                        return@launch
                    }
                    val loggedIn = source == LxSources.NETEASE && neteaseCookieStore.hasCookie()
                    val followed = if (source != LxSources.NETEASE) null else detail?.followed
                        ?: if (loggedIn && _mineState.value.homeLoaded)
                            _mineState.value.subArtists.any { it.id == artistId } else null
                    if (followed == null && loggedIn && !_mineState.value.homeLoaded) {
                        launch {
                            val subs = runCatching { repository.fetchNeteaseSubArtists() }
                                .getOrDefault(emptyList())
                            _mineState.value = _mineState.value.copy(subArtists = subs, homeLoaded = true)
                            val s = _searchArtistPageState.value
                            if (s.artist?.id == artistId) {
                                _searchArtistPageState.value = s.copy(
                                    followed = subs.any { it.id == artistId })
                            }
                        }
                    }
                    _searchArtistPageState.value = cur2.copy(
                        artist = artistForFill,
                        followed = followed,
                    )
                }
            }
        }
    }

    fun loadMoreSearchArtistSongs() {
        val before = _searchArtistPageState.value
        if (before.songsLoadingMore || !before.songsHasMore || before.artist == null) return
        val offset = before.songs.size
        viewModelScope.launch {
            _searchArtistPageState.value = before.copy(songsLoadingMore = true)
            val (batch, total) = runCatching {
                when (before.source) {
                    LxSources.TENCENT -> repository.fetchTxArtistSongs(before.artist.id, before.artist.name, offset = offset, limit = artistSongsPageSize)
                    LxSources.KUGOU -> repository.fetchKgArtistSongs(before.artist.id, offset = offset, limit = artistSongsPageSize)
                    LxSources.KUWO -> repository.fetchKwArtistSongs(before.artist.id, before.artist.name, offset = offset, limit = artistSongsPageSize)
                    else -> repository.fetchNeteaseArtistSongs(before.artist.id, offset = offset, limit = artistSongsPageSize)
                }
            }.getOrDefault(emptyList<LxSong>() to before.songsTotal)
            if (_searchArtistPageState.value.artist?.id != before.artist?.id) return@launch
            val merged = (before.songs + batch).distinctBy { it.songmid }
            val hasMore = batch.isNotEmpty() && when {
                total > 0 -> merged.size < total
                else -> batch.size >= artistSongsPageSize
            }
            _searchArtistPageState.value = _searchArtistPageState.value.copy(
                songs = merged,
                songsPage = before.songsPage + 1,
                songsTotal = if (total > 0) total else merged.size,
                songsHasMore = hasMore,
                songsLoadingMore = false,
            )
            if (batch.isNotEmpty()) loadPicsForResults(batch)
        }
    }

    fun selectSearchArtistTab(tab: String) {
        val cur = _searchArtistPageState.value
        if (cur.tab == tab) return
        _searchArtistPageState.value = cur.copy(tab = tab)
        if (tab == "albums" && cur.albums.isEmpty() && cur.artist != null) {
            _searchArtistPageState.value = _searchArtistPageState.value.copy(albumsLoading = true)
            viewModelScope.launch {
                val albums = when (cur.source) {
                    LxSources.TENCENT -> repository.fetchTxArtistAlbums(cur.artist.id, cur.artist.name)
                    LxSources.KUGOU -> repository.fetchKgArtistAlbums(cur.artist.id)
                    LxSources.KUWO -> repository.fetchKwArtistAlbums(cur.artist.id, cur.artist.name)
                    else -> repository.fetchNeteaseArtistAlbums(cur.artist.id)
                }
                val now = _searchArtistPageState.value
                if (now.artist?.id == cur.artist.id) {
                    _searchArtistPageState.value = now.copy(albums = albums, albumsLoading = false)
                }
            }
        }
    }

    fun openSearchArtistAlbum(album: LxAlbum) {
        val cur = _searchArtistPageState.value
        if (cur.selectedAlbum?.id == album.id) return
        _searchArtistPageState.value = cur.copy(selectedAlbum = album, albumSongs = emptyList(), albumLoading = true)
        viewModelScope.launch {
            val (_, songs) = when (cur.source) {
                LxSources.TENCENT -> repository.fetchTxAlbumDetail(album.id, album.name)
                LxSources.KUGOU -> repository.fetchKgAlbumDetail(album.id)
                LxSources.KUWO -> repository.fetchKwAlbumDetail(album.id, album.name)
                else -> repository.fetchNeteaseAlbumDetail(album.id)
            }
            val now = _searchArtistPageState.value
            if (now.selectedAlbum?.id == album.id) {
                _searchArtistPageState.value = now.copy(albumSongs = songs, albumLoading = false)
                if (songs.isNotEmpty()) loadPicsForResults(songs)
            }
        }
    }

    fun backFromSearchArtistAlbum() {
        val cur = _searchArtistPageState.value
        _searchArtistPageState.value = cur.copy(selectedAlbum = null, albumSongs = emptyList(), albumLoading = false)
    }

    fun toggleSearchArtistFollow() {
        val cur = _searchArtistPageState.value
        if (cur.source != LxSources.NETEASE && cur.source != LxSources.KUGOU && cur.source != LxSources.KUWO) return
        if ((cur.source == LxSources.KUGOU || cur.source == LxSources.KUWO) && !accountWritesEnabled) return
        val artist = cur.artist ?: return
        val followed = cur.followed ?: return
        if (cur.followBusy) return
        _searchArtistPageState.value = cur.copy(followBusy = true)
        val wantFollow = !followed
        viewModelScope.launch {
            val ok = runCatching {
                when (cur.source) {
                    LxSources.KUGOU -> repository.kgFollowSinger(artist.id, wantFollow)
                    LxSources.KUWO -> repository.kwFollowArtist(artist.id, wantFollow)
                    else -> repository.followNeteaseArtist(artist.id, wantFollow)
                }
            }.getOrDefault(false)
            val now = _searchArtistPageState.value
            if (now.artist?.id != artist.id) return@launch
            _searchArtistPageState.value = now.copy(followBusy = false, followed = if (ok) wantFollow else followed)
            if (ok && cur.source == LxSources.KUWO) {
                if (wantFollow) repository.addKwFollowedArtist(artist)
                else repository.removeKwFollowedArtist(artist.id)
                _mineState.value = _mineState.value.copy(
                    subArtists = repository.kwFollowedArtists.value)
            }
            if (ok && cur.source == LxSources.NETEASE) {
                if (wantFollow) {
                    _mineState.value = _mineState.value.copy(
                        subArtists = listOf(artist) + _mineState.value.subArtists)
                } else {
                    _mineState.value = _mineState.value.copy(
                        subArtists = _mineState.value.subArtists.filter { it.id != artist.id })
                }
            }
        }
    }

    fun toggleArtistFollow() {
        if ((_selectedSource.value == LxSources.KUGOU || _selectedSource.value == LxSources.KUWO) &&
            !accountWritesEnabled
        ) return
        val cur = _mineState.value
        val page = cur.page as? MinePage.Artist ?: return
        val followed = cur.artistFollowed ?: return
        if (cur.artistFollowBusy) return
        _mineState.value = cur.copy(artistFollowBusy = true)
        val wantFollow = !followed
        viewModelScope.launch {
            val ok = runCatching {
                when (_selectedSource.value) {
                    LxSources.KUGOU -> repository.kgFollowSinger(page.id, wantFollow)
                    LxSources.KUWO -> repository.kwFollowArtist(page.id, wantFollow)
                    else -> repository.followNeteaseArtist(page.id, wantFollow)
                }
            }.getOrDefault(false)
            val now = _mineState.value
            if (now.page !is MinePage.Artist || (now.page as MinePage.Artist).id != page.id) return@launch
            if (ok) {
                _mineState.value = now.copy(artistFollowBusy = false, artistFollowed = wantFollow)
                val info = now.artistInfo ?: LxArtist(page.id, page.name)
                if (_selectedSource.value == LxSources.KUWO) {
                    if (wantFollow) repository.addKwFollowedArtist(info)
                    else repository.removeKwFollowedArtist(page.id)
                }
                val subs = _mineState.value.subArtists
                val newSubs = if (wantFollow) {
                    if (subs.any { it.id == page.id }) subs else listOf(info) + subs
                } else {
                    subs.filterNot { it.id == page.id }
                }
                _mineState.value = _mineState.value.copy(subArtists = newSubs)
            } else {
                _mineState.value = now.copy(artistFollowBusy = false)
                _toastMessage.tryEmit(context.getString(R.string.lx_msg_operation_failed))
            }
        }
    }

    fun selectArtistTab(tab: String) {
        val cur = _mineState.value
        val page = cur.page as? MinePage.Artist ?: return
        if (cur.artistTab == tab) return
        _mineState.value = cur.copy(artistTab = tab, error = null)
        when (tab) {
            "albums" -> if (cur.pageAlbums.isEmpty()) loadArtistAlbums(page.id)
            else -> if (cur.pageSongs.isEmpty()) loadArtistSongs(page.id)
        }
    }

    fun openAlbumPage(album: LxAlbum) =
        openMineSongs(MinePage.SongList(album.name, "album", albumId = album.id))


    enum class CollectKind { PLAYLIST, ALBUM }

    enum class CollectState { UNCOLLECTED, COLLECTED, COLLECTED_LOCKED }

    private val _collectBusyKeys = MutableStateFlow<Set<String>>(emptySet())
    val collectBusyKeys: StateFlow<Set<String>> = _collectBusyKeys.asStateFlow()

    private val _collectDataVersion = MutableStateFlow(0)
    val collectDataVersion: StateFlow<Int> = _collectDataVersion.asStateFlow()

    private val _uncollectConfirm = MutableStateFlow<Pair<CollectKind, String>?>(null)
    val uncollectConfirm: StateFlow<Pair<CollectKind, String>?> = _uncollectConfirm.asStateFlow()

    private var pendingUncollectExtra: Pair<String?, String?> = null to null

    fun collectKey(kind: CollectKind, id: String) = "${kind.name}_$id"

    fun ensureCollectDataLoaded() {
        val loggedIn = when (_selectedSource.value) {
            LxSources.KUGOU -> kugouCookieStore.hasCookie() || kugouCookieStore.hasAppLogin()
            LxSources.KUWO -> kuwoCookieStore.hasCookie()
            LxSources.TENCENT -> tencentCookieStore.hasCookie()
            else -> neteaseCookieStore.hasCookie()
        }
        if (loggedIn) ensureMineHome()
    }

    private fun kwRawPlaylistId(id: String): String =
        id.removePrefix("kw_pl_").removePrefix("kw_opt_").replace(Regex("^kw:\\d+:"), "")

    fun collectStateOf(kind: CollectKind, id: String?, gid: String? = null, name: String? = null): CollectState? {
        if (id.isNullOrBlank()) return null
        val st = _mineState.value
        if (_selectedSource.value == LxSources.TENCENT) return null
        if (_selectedSource.value == LxSources.KUGOU) {
            if (!accountWritesEnabled) return null
            val own = st.myPlaylists.filter { it.specialType != 5 && it.subscribed != true }
            return when (kind) {
                CollectKind.PLAYLIST -> {
                    val mineEntry = st.myPlaylists.firstOrNull { it.id == id }
                    when {
                        mineEntry != null &&
                            (mineEntry.specialType == 5 || mineEntry.subscribed != true) ->
                            CollectState.COLLECTED_LOCKED
                        mineEntry != null -> CollectState.COLLECTED
                        !gid.isNullOrBlank() && st.myPlaylists.any { it.originGid == gid } ->
                            CollectState.COLLECTED
                        !name.isNullOrBlank() && own.any { it.name == name } -> CollectState.COLLECTED
                        else -> CollectState.UNCOLLECTED
                    }
                }
                CollectKind.ALBUM ->
                    if (!name.isNullOrBlank() && own.any { it.name == name }) CollectState.COLLECTED
                    else CollectState.UNCOLLECTED
            }
        }
        return when (kind) {
            CollectKind.PLAYLIST -> {
                val rawId = kwRawPlaylistId(id)
                val p = st.myPlaylists.firstOrNull {
                    kwRawPlaylistId(it.id) == rawId
                }
                when {
                    p == null -> CollectState.UNCOLLECTED
                    p.specialType == 5 || p.subscribed == false -> CollectState.COLLECTED_LOCKED
                    else -> CollectState.COLLECTED
                }
            }
            CollectKind.ALBUM ->
                if (_selectedSource.value == LxSources.KUWO) {
                    if (repository.kwLikedAlbums.value.any { it.id == id }) CollectState.COLLECTED
                    else CollectState.UNCOLLECTED
                } else if (st.subAlbums.any { it.id == id }) CollectState.COLLECTED
                else CollectState.UNCOLLECTED
        }
    }

    fun onCollectButtonClick(kind: CollectKind, id: String, gid: String? = null, name: String? = null) {
        if (!accountWritesEnabled &&
            (_selectedSource.value == LxSources.KUGOU || _selectedSource.value == LxSources.KUWO)
        ) return
        if (_selectedSource.value == LxSources.TENCENT) return
        val loggedIn = when (_selectedSource.value) {
            LxSources.KUGOU -> kugouCookieStore.hasAppLogin()
            LxSources.KUWO -> kuwoCookieStore.hasCookie()
            else -> neteaseCookieStore.hasCookie()
        }
        if (!loggedIn) {
            _toastMessage.tryEmit(context.getString(R.string.lx_msg_user_not_logged_in))
            return
        }
        when (collectStateOf(kind, id, gid, name)) {
            CollectState.COLLECTED -> {
                pendingUncollectExtra = gid to name
                _uncollectConfirm.value = kind to id
            }
            CollectState.UNCOLLECTED -> viewModelScope.launch {
                applyCollect(kind, id, gid, name, subscribe = true)
            }
            CollectState.COLLECTED_LOCKED, null -> Unit
        }
    }

    fun dismissUncollectConfirm() { _uncollectConfirm.value = null }


    data class OnlineAddSheetState(
        val song: LxSong,
        val songTitle: String,
        val playlists: List<LxPlaylist> = emptyList(),
        val loading: Boolean = false,
        val adding: Boolean = false,
        val extraSongs: List<LxSong> = emptyList(),
    ) {
        val songs: List<LxSong> get() = listOf(song) + extraSongs
    }

    private val _onlineAddSheet = MutableStateFlow<OnlineAddSheetState?>(null)
    val onlineAddSheet: StateFlow<OnlineAddSheetState?> = _onlineAddSheet.asStateFlow()

    fun openOnlineAddToPlaylist(lxSong: LxSong) {
        val allowed = when (lxSong.source) {
            LxSources.NETEASE -> neteaseCookieStore.hasCookie()
            LxSources.KUGOU -> accountWritesEnabled && kugouCookieStore.hasAppLogin()
            LxSources.KUWO -> accountWritesEnabled && kuwoCookieStore.hasCookie()
            else -> false
        }
        if (!allowed) {
            _toastMessage.tryEmit(context.getString(R.string.lx_msg_user_not_logged_in))
            return
        }
        _onlineAddSheet.value = OnlineAddSheetState(song = lxSong, songTitle = lxSong.name, loading = true)
        loadOwnPlaylistsIntoAddSheet(lxSong.source)
    }

    fun openOnlineAddToPlaylistBatch(songs: List<LxSong>) {
        if (songs.isEmpty()) return
        val distinctSongs = songs.distinctBy { it.source to it.songmid }
        val sources = distinctSongs.map { it.source }.distinct()
        if (sources.size > 1) {
            _toastMessage.tryEmit(context.getString(R.string.lx_msg_batch_add_mixed_source))
            return
        }
        val source = sources.first()
        val allowed = when (source) {
            LxSources.NETEASE -> neteaseCookieStore.hasCookie()
            LxSources.KUGOU -> accountWritesEnabled && kugouCookieStore.hasAppLogin()
            LxSources.KUWO -> accountWritesEnabled && kuwoCookieStore.hasCookie()
            else -> false
        }
        if (!allowed) {
            _toastMessage.tryEmit(context.getString(R.string.lx_msg_user_not_logged_in))
            return
        }
        val first = distinctSongs.first()
        _onlineAddSheet.value = OnlineAddSheetState(
            song = first,
            songTitle = first.name,
            loading = true,
            extraSongs = distinctSongs.drop(1),
        )
        loadOwnPlaylistsIntoAddSheet(source)
    }

    private fun loadOwnPlaylistsIntoAddSheet(source: String) {
        viewModelScope.launch {
            val own = when (source) {
                LxSources.NETEASE -> {
                    val uid = neteaseCookieStore.getAccountId().takeIf { it >= 0 }
                    val playlists = uid?.let {
                        runCatching { repository.fetchNeteaseMyPlaylists(it.toString()) }.getOrNull()
                    } ?: emptyList()
                    val uidStr = uid?.toString()
                    playlists.filter { p ->
                        p.specialType != 5 && (uidStr == null || p.creatorUserId == null || p.creatorUserId == uidStr)
                    }
                }
                LxSources.KUGOU -> runCatching { repository.fetchKgUserPlaylists() }.getOrDefault(emptyList())
                    .filter { it.subscribed != true }
                LxSources.KUWO -> runCatching { repository.fetchKwUserPlaylists() }.getOrDefault(emptyList())
                    .filter { it.subscribed != true }
                else -> emptyList()
            }
            _onlineAddSheet.update { it?.copy(playlists = own, loading = false) }
        }
    }

    fun dismissOnlineAddSheet() { _onlineAddSheet.value = null }

    fun onlineAddSongToPlaylists(selectedIds: List<String>) {
        val st = _onlineAddSheet.value ?: return
        if (st.adding || selectedIds.isEmpty()) return
        _onlineAddSheet.update { it?.copy(adding = true) }
        viewModelScope.launch {
            val batchSongs = st.songs
            val source = batchSongs.first().source
            val songmids = batchSongs.map { it.songmid }.distinct()
            val results = mutableMapOf<String, LxAddToPlaylistResult>()
            var anyFailed = false
            for (pid in selectedIds) {
                val result = runCatching {
                    when (source) {
                        LxSources.NETEASE -> repository.neteaseAddSongsToPlaylist(pid, songmids)
                        LxSources.KUGOU -> repository.kgAddSongsToPlaylist(
                            pid.removePrefix("kg_cloud_"), batchSongs
                        )
                        LxSources.KUWO -> repository.kwAddSongsToPlaylist(
                            pid.removePrefix("kw_pl_"), songmids
                        )
                        else -> LxAddToPlaylistResult.FAILED
                    }
                }.getOrDefault(LxAddToPlaylistResult.FAILED)
                results[pid] = result
                if (result == LxAddToPlaylistResult.FAILED) {
                    anyFailed = true
                    break
                }
            }
            _onlineAddSheet.value = null
            val addedCount = results.count { it.value == LxAddToPlaylistResult.ADDED }
            val existsCount = results.count { it.value == LxAddToPlaylistResult.ALREADY_EXISTS }
            val msgRes = when {
                anyFailed -> R.string.lx_msg_add_to_playlist_failed
                addedCount > 0 && existsCount > 0 -> R.string.lx_msg_add_to_playlist_partial_exists
                existsCount > 0 -> R.string.lx_msg_add_to_playlist_already_exists
                else -> R.string.lx_msg_add_to_playlist_success
            }
            _toastMessage.tryEmit(context.getString(msgRes))
            if (addedCount > 0) {
                _mineState.update { s ->
                    s.copy(myPlaylists = s.myPlaylists.map { pl ->
                        if (pl.id in selectedIds && results[pl.id] == LxAddToPlaylistResult.ADDED) {
                            val newCount = (pl.trackCount ?: 0) + batchSongs.size
                            pl.copy(
                                trackCount = newCount,
                                pic = if (pl.trackCount == 0 || pl.pic.isNullOrBlank())
                                    batchSongs.first().pic ?: pl.pic else pl.pic,
                            )
                        } else pl
                    })
                }
                if (source != LxSources.KUGOU) silentRefreshMyPlaylists(source)
            }
        }
    }

    private fun silentRefreshMyPlaylists(source: String) {
        if (_selectedSource.value != source || !_mineState.value.homeLoaded) return
        viewModelScope.launch {
            val playlists: List<LxPlaylist> = when (source) {
                LxSources.NETEASE -> {
                    val uid = neteaseCookieStore.getAccountId().takeIf { it >= 0 } ?: return@launch
                    runCatching { repository.fetchNeteaseMyPlaylists(uid.toString()) }.getOrNull()
                }
                LxSources.KUGOU -> {
                    runCatching { repository.invalidateKgCloudListsCache() }
                    runCatching { repository.fetchKgUserPlaylists() }.getOrNull()
                }
                LxSources.KUWO -> runCatching { repository.fetchKwUserPlaylists() }.getOrNull()
                else -> null
            } ?: return@launch
            _mineState.update { st ->
                if (source == LxSources.NETEASE) {
                    st.copy(myPlaylists = playlists)
                } else {
                    val liked = st.myPlaylists.firstOrNull { it.specialType == 5 }
                    st.copy(myPlaylists = listOfNotNull(liked) + playlists.filter { it.specialType != 5 })
                }
            }
        }
    }

    fun confirmUncollect(kind: CollectKind, id: String) {
        _uncollectConfirm.value = null
        val extra = pendingUncollectExtra
        pendingUncollectExtra = null to null
        viewModelScope.launch { applyCollect(kind, id, extra.first, extra.second, subscribe = false) }
    }


    data class OnlineRemoveConfirmState(
        val song: LxSong,
        val songTitle: String,
        val playlistId: String,
        val playlistName: String,
        val removing: Boolean = false,
    )

    private val _onlineRemoveConfirm = MutableStateFlow<OnlineRemoveConfirmState?>(null)
    val onlineRemoveConfirm: StateFlow<OnlineRemoveConfirmState?> = _onlineRemoveConfirm.asStateFlow()

    fun currentOwnPlaylistContext(source: String): Pair<String, String>? {
        val mineContext = run {
            val st = _mineState.value
            val page = st.page as? MinePage.SongList ?: return@run null
            if (page.kind != "playlist") return@run null
            val pid = page.playlistId ?: return@run null
            val pl = st.myPlaylists.firstOrNull { it.id == pid } ?: return@run null
            if (pl.specialType == 5) return@run null
            when (source) {
                LxSources.NETEASE -> {
                    val uid = neteaseCookieStore.getAccountId().takeIf { it >= 0 }?.toString() ?: return@run null
                    if (pl.creatorUserId == null || pl.creatorUserId != uid) return@run null
                    pid to pl.name
                }
                LxSources.KUGOU, LxSources.KUWO ->
                    if (pl.subscribed == true) null else pid to pl.name
                else -> null
            }
        }
        if (mineContext != null) return mineContext
        val browsePlaylist = _browseState.value.selectedPlaylist ?: return null
        if (browsePlaylist.specialType == 5) return null
        return when (source) {
            LxSources.NETEASE -> {
                val uid = neteaseCookieStore.getAccountId().takeIf { it >= 0 }?.toString() ?: return null
                if (browsePlaylist.creatorUserId == null || browsePlaylist.creatorUserId != uid) return null
                browsePlaylist.id to browsePlaylist.name
            }
            LxSources.KUGOU, LxSources.KUWO ->
                if (browsePlaylist.subscribed == true) null
                else browsePlaylist.id to browsePlaylist.name
            else -> null
        }
    }

    fun requestRemoveSongFromOwnPlaylist(lxSong: LxSong) {
        val allowed = when (lxSong.source) {
            LxSources.NETEASE -> accountWritesEnabled && neteaseCookieStore.hasCookie()
            LxSources.KUGOU -> accountWritesEnabled && kugouCookieStore.hasAppLogin()
            LxSources.KUWO -> accountWritesEnabled && kuwoCookieStore.hasCookie()
            else -> false
        }
        if (!allowed) {
            _toastMessage.tryEmit(context.getString(R.string.lx_msg_user_not_logged_in))
            return
        }
        val (pid, pname) = currentOwnPlaylistContext(lxSong.source) ?: return
        _onlineRemoveConfirm.value = OnlineRemoveConfirmState(
            song = lxSong,
            songTitle = lxSong.name,
            playlistId = pid,
            playlistName = pname,
        )
    }

    fun dismissOnlineRemoveConfirm() { _onlineRemoveConfirm.value = null }

    fun confirmOnlineRemoveFromPlaylist() {
        val st = _onlineRemoveConfirm.value ?: return
        if (st.removing) return
        _onlineRemoveConfirm.update { it?.copy(removing = true) }
        viewModelScope.launch {
            val ok = runCatching {
                when (st.song.source) {
                    LxSources.NETEASE -> repository.neteaseRemoveSongsFromPlaylist(
                        st.playlistId, listOf(st.song.songmid)
                    )
                    LxSources.KUGOU -> repository.kgRemoveSongsFromPlaylist(
                        st.playlistId.removePrefix("kg_cloud_"), listOf(st.song)
                    )
                    LxSources.KUWO -> repository.kwRemoveSongsFromPlaylist(
                        st.playlistId.removePrefix("kw_pl_"), listOf(st.song.songmid)
                    )
                    else -> false
                }
            }.getOrDefault(false)
            _onlineRemoveConfirm.value = null
            if (ok) {
                _mineState.update { s ->
                    s.copy(pageSongs = s.pageSongs.filterNot {
                        it.source == st.song.source && it.songmid == st.song.songmid
                    })
                }
                _toastMessage.tryEmit(context.getString(R.string.lx_msg_remove_from_playlist_success))
                _mineState.update { s ->
                    s.copy(myPlaylists = s.myPlaylists.map { pl ->
                        if (pl.id == st.playlistId) {
                            pl.copy(trackCount = ((pl.trackCount ?: 0) - 1).coerceAtLeast(0))
                        } else pl
                    })
                }
                if (st.song.source != LxSources.KUGOU) silentRefreshMyPlaylists(st.song.source)
            } else {
                _toastMessage.tryEmit(context.getString(R.string.lx_msg_remove_from_playlist_failed))
            }
        }
    }


    data class BatchOnlineRemoveConfirmState(
        val playlistId: String,
        val playlistName: String,
        val songs: List<LxSong>,
        val removing: Boolean = false,
    )

    private val _batchOnlineRemoveConfirm = MutableStateFlow<BatchOnlineRemoveConfirmState?>(null)
    val batchOnlineRemoveConfirm: StateFlow<BatchOnlineRemoveConfirmState?> =
        _batchOnlineRemoveConfirm.asStateFlow()

    private val _batchRemovedSelectionIds = MutableSharedFlow<Set<String>>(extraBufferCapacity = 1)
    val batchRemovedSelectionIds = _batchRemovedSelectionIds.asSharedFlow()

    fun requestRemoveSongsFromOwnPlaylist(songs: List<LxSong>) {
        if (songs.isEmpty()) return
        val distinctSongs = songs.distinctBy { it.source to it.songmid }
        val sources = distinctSongs.map { it.source }.distinct()
        if (sources.size > 1) return
        val source = sources.first()
        val allowed = when (source) {
            LxSources.NETEASE -> accountWritesEnabled && neteaseCookieStore.hasCookie()
            LxSources.KUGOU -> accountWritesEnabled && kugouCookieStore.hasAppLogin()
            LxSources.KUWO -> accountWritesEnabled && kuwoCookieStore.hasCookie()
            else -> false
        }
        if (!allowed) {
            _toastMessage.tryEmit(context.getString(R.string.lx_msg_user_not_logged_in))
            return
        }
        val (pid, pname) = currentOwnPlaylistContext(source) ?: return
        _batchOnlineRemoveConfirm.value = BatchOnlineRemoveConfirmState(
            playlistId = pid,
            playlistName = pname,
            songs = distinctSongs,
        )
    }

    fun dismissBatchOnlineRemoveConfirm() {
        _batchOnlineRemoveConfirm.value = null
    }

    fun confirmBatchOnlineRemoveFromPlaylist() {
        val st = _batchOnlineRemoveConfirm.value ?: return
        if (st.removing) return
        _batchOnlineRemoveConfirm.update { it?.copy(removing = true) }
        viewModelScope.launch {
            val source = st.songs.first().source
            val songmids = st.songs.map { it.songmid }.distinct()
            val ok = runCatching {
                when (source) {
                    LxSources.NETEASE -> repository.neteaseRemoveSongsFromPlaylist(st.playlistId, songmids)
                    LxSources.KUGOU -> repository.kgRemoveSongsFromPlaylist(
                        st.playlistId.removePrefix("kg_cloud_"), st.songs
                    )
                    LxSources.KUWO -> repository.kwRemoveSongsFromPlaylist(
                        st.playlistId.removePrefix("kw_pl_"), songmids
                    )
                    else -> false
                }
            }.getOrDefault(false)
            _batchOnlineRemoveConfirm.value = null
            if (ok) {
                val removedCount = st.songs.size
                val matchPairs = st.songs.map { it.source to it.songmid }.toSet()
                _mineState.update { s ->
                    s.copy(
                        pageSongs = s.pageSongs.filterNot { (it.source to it.songmid) in matchPairs },
                        myPlaylists = s.myPlaylists.map { pl ->
                            if (pl.id == st.playlistId) {
                                pl.copy(trackCount = ((pl.trackCount ?: 0) - removedCount).coerceAtLeast(0))
                            } else pl
                        },
                    )
                }
                _browseState.update { b ->
                    b.copy(
                        playlistSongs = b.playlistSongs.filterNot { (it.source to it.songmid) in matchPairs },
                        selectedPlaylist = b.selectedPlaylist?.let { pl ->
                            if (pl.id == st.playlistId) {
                                pl.copy(trackCount = ((pl.trackCount ?: 0) - removedCount).coerceAtLeast(0))
                            } else pl
                        },
                    )
                }
                val removedUiIds = st.songs
                    .map { LxSongMapper.unifiedSongId(it.source, it.songmid).toString() }
                    .toSet()
                _batchRemovedSelectionIds.tryEmit(removedUiIds)
                _toastMessage.tryEmit(context.getString(R.string.lx_msg_remove_from_playlist_success))
                if (source != LxSources.KUGOU) silentRefreshMyPlaylists(source)
            } else {
                _toastMessage.tryEmit(context.getString(R.string.lx_msg_remove_from_playlist_failed))
            }
        }
    }

    private suspend fun applyCollect(
        kind: CollectKind,
        id: String,
        gid: String? = null,
        name: String? = null,
        subscribe: Boolean,
    ) {
        val key = collectKey(kind, id)
        _collectBusyKeys.update { it + key }
        try {
            val ok = if (_selectedSource.value == LxSources.KUGOU) {
                when (kind) {
                    CollectKind.PLAYLIST -> runCatching {
                        if (subscribe) {
                            if (!gid.isNullOrBlank()) {
                                repository.kgSubscribeSpecial(name.orEmpty().ifBlank { id }, gid)
                            } else {
                                val songs = repository.fetchPlaylistSongsAll(LxSources.KUGOU, id)
                                repository.kgCollectByCopy(name.orEmpty().ifBlank { id }, songs)
                            }
                        } else {
                            repository.kgUnsubscribeCloudList(
                                gid = gid?.takeIf { it.isNotBlank() },
                                name = name?.takeIf { it.isNotBlank() },
                            )
                        }
                    }.getOrDefault(false)
                    CollectKind.ALBUM -> runCatching {
                        if (subscribe) {
                            val songs = repository.fetchKgAlbumDetail(id).second
                            repository.kgCollectByCopy(name.orEmpty().ifBlank { id }, songs)
                        } else {
                            repository.kgUnsubscribeCloudList(name = name?.takeIf { it.isNotBlank() })
                        }
                    }.getOrDefault(false)
                }
            } else if (_selectedSource.value == LxSources.KUWO) {
                when (kind) {
                    CollectKind.PLAYLIST -> runCatching {
                        repository.kwSetPlaylistCollect(kwRawPlaylistId(id), subscribe)
                    }.getOrDefault(false)
                    CollectKind.ALBUM -> runCatching {
                        repository.kwSetAlbumLike(id, subscribe)
                    }.onSuccess { ok ->
                        if (ok) {
                            if (subscribe) {
                                repository.addKwLikedAlbum(
                                    LxAlbum(id = id, name = name?.takeIf { it.isNotBlank() } ?: id))
                            } else {
                                repository.removeKwLikedAlbum(id)
                            }
                        }
                    }.getOrDefault(false)
                }
            } else {
                when (kind) {
                    CollectKind.PLAYLIST -> runCatching {
                        repository.setNeteasePlaylistSubscribed(id, subscribe)
                    }.getOrDefault(false)
                    CollectKind.ALBUM -> runCatching {
                        repository.setNeteaseAlbumSubscribed(id, subscribe)
                    }.getOrDefault(false)
                }
            }
            if (ok) {
                _toastMessage.tryEmit(if (subscribe) context.getString(R.string.lx_msg_subscribe_success) else context.getString(R.string.lx_msg_unsubscribe_success))
                refreshCollectLists(kind, id, gid, name, subscribe)
            } else {
                Timber.w("collect failed kind=%s id=%s subscribe=%s", kind, id, subscribe)
                _toastMessage.tryEmit(if (subscribe) context.getString(R.string.lx_msg_subscribe_failed) else context.getString(R.string.lx_msg_unsubscribe_failed))
            }
        } finally {
            _collectBusyKeys.update { it - key }
        }
    }

    private fun refreshCollectLists(
        kind: CollectKind,
        id: String,
        gid: String?,
        name: String?,
        subscribe: Boolean,
    ) {
        viewModelScope.launch {
            if (_selectedSource.value == LxSources.KUGOU) {
                runCatching { repository.invalidateKgCloudListsCache() }
                val serverLists = runCatching { repository.fetchKgUserPlaylists() }.getOrDefault(emptyList())
                val playlists = if (!subscribe) {
                    serverLists.filterNot {
                        it.id == id ||
                            (!gid.isNullOrBlank() && it.originGid == gid) ||
                            (!name.isNullOrBlank() && it.name == name)
                    }
                } else {
                    val hit = serverLists.any {
                        (!gid.isNullOrBlank() && it.originGid == gid) ||
                            (!name.isNullOrBlank() && it.name == name)
                    }
                    if (hit) serverLists
                    else serverLists + LxPlaylist(
                        id = "kg_opt_$id",
                        name = name?.takeIf { it.isNotBlank() } ?: id,
                        subscribed = true,
                        originGid = gid,
                    )
                }
                _mineState.update { st ->
                    val liked = st.myPlaylists.firstOrNull { it.specialType == 5 }
                    st.copy(myPlaylists = listOfNotNull(liked) + playlists)
                }
                _collectDataVersion.update { it + 1 }
                return@launch
            }
            if (_selectedSource.value == LxSources.KUWO) {
                if (kind == CollectKind.ALBUM) {
                    val albums = runCatching { repository.fetchKwLikedAlbums() }.getOrNull()
                        ?: repository.kwLikedAlbums.value
                    _mineState.update { st -> st.copy(subAlbums = albums) }
                    _collectDataVersion.update { it + 1 }
                    return@launch
                }
                val serverLists =
                    runCatching { repository.fetchKwUserPlaylists() }.getOrDefault(emptyList())
                val playlists = if (!subscribe) {
                    val rawId = kwRawPlaylistId(id)
                    serverLists.filterNot { kwRawPlaylistId(it.id) == rawId }
                } else {
                    val optName = name?.takeIf { it.isNotBlank() } ?: id
                    val result = serverLists.toMutableList()
                    if (result.none { it.subscribed == true && it.name == optName }) {
                        result.add(LxPlaylist(id = "kw_opt_${kwRawPlaylistId(id)}", name = optName, subscribed = true))
                    }
                    result
                }
                _mineState.update { st ->
                    val liked = st.myPlaylists.firstOrNull { it.specialType == 5 }
                    st.copy(myPlaylists = listOfNotNull(liked) + playlists)
                }
                _collectDataVersion.update { it + 1 }
                return@launch
            }
            val uid = neteaseCookieStore.getAccountId().takeIf { it >= 0 }
            val playlists = uid?.let {
                runCatching { repository.fetchNeteaseMyPlaylists(it.toString()) }.getOrNull()
            }
            val albums = runCatching { repository.fetchNeteaseSubAlbums() }.getOrNull()
            _mineState.update { st ->
                st.copy(
                    myPlaylists = playlists ?: st.myPlaylists,
                    subAlbums = albums ?: st.subAlbums,
                )
            }
            _collectDataVersion.update { it + 1 }
        }
    }


    private fun openMineSongs(page: MinePage.SongList, forward: Boolean = true) {
        mineJob?.cancel()
        val newState: MineState.() -> MineState = {
            copy(
                page = page,
                pageTitle = page.title,
                pageSongs = emptyList(),
                pageLoading = true,
                pagePlaylists = emptyList(),
                pageArtists = emptyList(),
                pageAlbums = emptyList(),
                pageSongsHasMore = false,
                pageLoadingMore = false,
                artistInfo = null,
                error = null,
            )
        }
        if (forward) navigateForward(newState) else _mineState.value = _mineState.value.newState()
        mineJob = viewModelScope.launch {
            if (page.kind == "personal_fm") {
                val songs = runCatching { repository.fetchNeteasePersonalFm() }
                    .onFailure { Timber.w(it, "personal fm load failed") }
                    .getOrDefault(emptyList())
                val cur = _mineState.value
                if (cur.page != page) return@launch
                _mineState.value = cur.copy(
                    pageLoading = false,
                    pageSongs = songs,
                    pageSongsHasMore = songs.isNotEmpty(),
                    error = if (songs.isEmpty()) context.getString(R.string.lx_error_no_content) else null,
                )
                return@launch
            }
            val loaded: Pair<List<LxSong>, String?> = if (page.kind == "daily") {
                when (val r = runCatching { repository.fetchNeteaseDailyRecommendWithReason() }
                    .getOrElse {
                        Timber.w(it, "daily recommend load failed")
                        null
                    }) {
                    is LxMusicRepository.DailyRecommendOutcome.Ok -> r.songs to null
                    is LxMusicRepository.DailyRecommendOutcome.CookieInvalid ->
                        emptyList<LxSong>() to context.getString(R.string.lx_msg_login_expired)
                    is LxMusicRepository.DailyRecommendOutcome.Failed -> emptyList<LxSong>() to r.reason
                    null -> emptyList<LxSong>() to context.getString(R.string.lx_error_load_failed_retry)
                }
            } else {
                runCatching { mineSongsLoader(page).invoke() }
                    .onFailure { Timber.w(it, "mine songs load failed: ${page.kind}/${page.title}") }
                    .getOrDefault(emptyList()) to null
            }
            val songs = loaded.first
            val loadError = loaded.second
            val cur = _mineState.value
            if (cur.page != page) return@launch
            _mineState.value = cur.copy(
                pageLoading = false,
                pageSongs = songs,
                error = loadError ?: if (songs.isEmpty()) context.getString(R.string.lx_error_no_content) else null,
            )
            if (songs.isNotEmpty()) loadPicsForResults(songs)
        }
    }

    fun loadMoreMineSongs() {
        val cur = _mineState.value
        val page = cur.page as? MinePage.SongList ?: return
        if (cur.pageLoadingMore || !cur.pageSongsHasMore || page.kind != "personal_fm") return
        _mineState.value = cur.copy(pageLoadingMore = true)
        viewModelScope.launch {
            val batch = runCatching { repository.fetchNeteasePersonalFm() }
                .getOrDefault(emptyList())
            val now = _mineState.value
            if (now.page != page) return@launch
            val merged = (now.pageSongs + batch).distinctBy { it.songmid }
            _mineState.value = now.copy(
                pageLoadingMore = false,
                pageSongs = merged,
                pageSongsHasMore = batch.isNotEmpty(),
            )
        }
    }

    private fun mineSongsLoader(page: MinePage.SongList): suspend () -> List<LxSong> = when (page.kind) {
        "daily" -> ({ repository.fetchNeteaseDailyRecommend() })
        "heartbeat" -> ({
            val liked = _mineState.value.likedPlaylist()
            val seed = liked?.let {
                runCatching { repository.fetchNeteasePlaylistSongs(it.id).first }
                    .getOrDefault(emptyList()).firstOrNull()?.songmid
            }
            if (liked != null && seed != null) {
                repository.fetchNeteaseHeartbeatSongs(liked.id, seed)
            } else {
                emptyList()
            }
        })
        "roaming" -> ({
            val liked = _mineState.value.likedPlaylist()
            val seed = liked?.let {
                runCatching { repository.fetchNeteasePlaylistSongs(it.id).first }
                    .getOrDefault(emptyList()).randomOrNull()?.songmid
            }
            if (seed != null) repository.fetchNeteaseSimilarSongs(seed) else emptyList()
        })
        "playlist" -> ({
            val pid = page.playlistId.orEmpty()
            when {
                pid.startsWith("kg_cloud_") -> repository.fetchKgCloudPlaylistSongs(pid.removePrefix("kg_cloud_"))
                pid.startsWith("kw_pl_") -> repository.fetchKwUserPlaylistSongs(pid.removePrefix("kw_pl_"))
                pid.startsWith("tx_pl_") -> repository.fetchTxUserPlaylistSongs(pid.removePrefix("tx_pl_"))
                _selectedSource.value == LxSources.TENCENT -> repository.fetchTxLikedSongs()
                _selectedSource.value == LxSources.KUGOU -> repository.fetchKgLikedSongs()
                _selectedSource.value == LxSources.KUWO -> repository.fetchKwLikedSongs()
                else -> repository.fetchNeteasePlaylistSongs(pid).first
            }
        })
        "album" -> ({
            val aid = page.albumId.orEmpty()
            when (_selectedSource.value) {
                LxSources.TENCENT -> repository.fetchTxAlbumDetail(aid).second
                LxSources.KUGOU -> repository.fetchKgAlbumDetail(aid).second
                LxSources.KUWO -> repository.fetchKwAlbumDetail(aid, page.title).second
                else -> repository.fetchNeteaseAlbumDetail(aid).second
            }
        })
        "artist_songs" -> ({
            val aid = page.artistId.orEmpty()
            when (_selectedSource.value) {
                LxSources.TENCENT -> repository.fetchTxArtistSongs(aid, page.title).first
                LxSources.KUGOU -> repository.fetchKgArtistSongs(aid).first
                LxSources.KUWO -> repository.fetchKwArtistSongs(aid, page.title).first
                else -> repository.fetchNeteaseArtistSongs(aid).first
            }
        })
        else -> ({ emptyList() })
    }

    private val artistSongsPageSize = 30

    private val playlistSongsPageSize = 100

    private fun loadArtistSongs(artistId: String) {
        mineJob?.cancel()
        val source = _selectedSource.value
        val artistName = (_mineState.value.page as? MinePage.Artist)?.name.orEmpty()
        _mineState.value = _mineState.value.copy(
            pageLoading = true,
            error = null,
            artistSongsTotal = 0,
            artistSongsHasMore = false,
            artistLoadingMore = false,
        )
        mineJob = viewModelScope.launch {
            val infoDeferred = async {
                runCatching {
                    when (source) {
                        LxSources.TENCENT -> repository.fetchTxArtistDetail(artistId)
                        LxSources.KUGOU -> repository.fetchKgArtistDetail(artistId)
                        LxSources.KUWO -> repository.fetchKwArtistDetail(artistId, artistName)
                        else -> repository.fetchNeteaseArtistInfo(artistId)
                    }
                }.getOrNull()
            }
            val (songs, total) = runCatching {
                when (source) {
                    LxSources.TENCENT -> repository.fetchTxArtistSongs(artistId, artistName, offset = 0, limit = artistSongsPageSize)
                    LxSources.KUGOU -> repository.fetchKgArtistSongs(artistId, offset = 0, limit = artistSongsPageSize)
                    LxSources.KUWO -> repository.fetchKwArtistSongs(artistId, artistName, offset = 0, limit = artistSongsPageSize)
                    else -> repository.fetchNeteaseArtistSongs(artistId, offset = 0, limit = artistSongsPageSize)
                }
            }.getOrDefault(emptyList<LxSong>() to 0)
            val detail = infoDeferred.await()
            val cur = _mineState.value
            val page = cur.page as? MinePage.Artist ?: return@launch
            if (page.id != artistId) return@launch
            val loggedIn = when (source) {
                LxSources.TENCENT -> tencentCookieStore.hasCookie()
                LxSources.KUGOU -> kugouCookieStore.hasCookie()
                LxSources.KUWO -> kuwoCookieStore.hasCookie()
                else -> neteaseCookieStore.hasCookie()
            }
            val followed = detail?.followed
                ?: if (loggedIn) cur.subArtists.any { it.id == artistId } else null
            if (songs.isNotEmpty()) loadPicsForResults(songs)
            _mineState.value = cur.copy(
                artistInfo = detail?.artist ?: cur.artistInfo,
                artistFollowed = followed,
                pageLoading = false,
                pageSongs = songs,
                artistSongsTotal = total,
                artistSongsHasMore = songs.isNotEmpty() &&
                    (if (total > 0) songs.size < total else songs.size >= artistSongsPageSize),
                error = if (songs.isEmpty()) context.getString(R.string.lx_error_artist_no_hot_songs) else null,
            )
        }
    }

    fun loadMoreArtistSongs() {
        val before = _mineState.value
        val page = before.page as? MinePage.Artist ?: return
        if (before.pageLoading || before.artistLoadingMore || !before.artistSongsHasMore) return
        val source = _selectedSource.value
        _mineState.value = before.copy(artistLoadingMore = true)
        viewModelScope.launch {
            val offset = before.pageSongs.size
            val (batch, total) = runCatching {
                when (source) {
                    LxSources.TENCENT -> repository.fetchTxArtistSongs(page.id, page.name, offset = offset, limit = artistSongsPageSize)
                    LxSources.KUGOU -> repository.fetchKgArtistSongs(page.id, offset = offset, limit = artistSongsPageSize)
                    LxSources.KUWO -> repository.fetchKwArtistSongs(page.id, page.name, offset = offset, limit = artistSongsPageSize)
                    else -> repository.fetchNeteaseArtistSongs(page.id, offset = offset, limit = artistSongsPageSize)
                }
            }.getOrDefault(emptyList<LxSong>() to before.artistSongsTotal)
            val cur = _mineState.value
            val nowPage = cur.page as? MinePage.Artist ?: return@launch
            if (nowPage.id != page.id) return@launch
            val merged = (cur.pageSongs + batch).distinctBy { it.songmid }
            if (batch.isNotEmpty()) loadPicsForResults(batch)
            val hasMore = batch.isNotEmpty() && when {
                total > 0 -> merged.size < total
                else -> batch.size >= artistSongsPageSize
            }
            _mineState.value = cur.copy(
                artistLoadingMore = false,
                pageSongs = merged,
                artistSongsTotal = if (total > 0) total else merged.size,
                artistSongsHasMore = hasMore,
            )
        }
    }

    private fun loadArtistAlbums(artistId: String) {
        mineJob?.cancel()
        val source = _selectedSource.value
        val artistName = (_mineState.value.page as? MinePage.Artist)?.name.orEmpty()
        _mineState.value = _mineState.value.copy(pageLoading = true, error = null)
        mineJob = viewModelScope.launch {
            val albums = runCatching {
                when (source) {
                    LxSources.TENCENT -> repository.fetchTxArtistAlbums(artistId, artistName)
                    LxSources.KUGOU -> repository.fetchKgArtistAlbums(artistId)
                    LxSources.KUWO -> repository.fetchKwArtistAlbums(artistId, artistName)
                    else -> repository.fetchNeteaseArtistAlbums(artistId)
                }
            }.getOrDefault(emptyList())
            val cur = _mineState.value
            val page = cur.page as? MinePage.Artist ?: return@launch
            if (page.id != artistId) return@launch
            _mineState.value = cur.copy(
                pageLoading = false,
                pageAlbums = albums,
                error = if (albums.isEmpty()) context.getString(R.string.lx_error_artist_no_albums) else null,
            )
        }
    }

    private var lastMineBackAtMs = 0L

    fun backMinePage() {
        val now = System.currentTimeMillis()
        if (now - lastMineBackAtMs < 400L) return
        lastMineBackAtMs = now
        mineJob?.cancel()
        if (mineBackStack.size <= 1) return
        mineBackStack.removeLastOrNull()
        val snapshot = mineBackStack.last()
        _mineState.value = _mineState.value.restorePageSnapshot(snapshot)
    }

    private fun refreshMineCurrent() {
        repository.invalidateNeteasePlaylistDetailCache()
        when (val page = _mineState.value.page) {
            MinePage.Home, MinePage.RecommendPlaylists -> loadMineHome(force = true)
            MinePage.SubArtists, MinePage.SubAlbums -> {
                mineJob?.cancel()
                mineJob = viewModelScope.launch {
                    _mineState.value = _mineState.value.copy(pageLoading = true)
                    val a = when (_selectedSource.value) {
                        LxSources.KUWO -> runCatching { repository.fetchKwFollowedArtists() }.getOrNull()
                            ?: repository.kwFollowedArtists.value
                        else -> runCatching { repository.fetchNeteaseSubArtists() }.getOrDefault(emptyList())
                    }
                    val al = when (_selectedSource.value) {
                        LxSources.KUWO -> runCatching { repository.fetchKwLikedAlbums() }.getOrNull()
                            ?: repository.kwLikedAlbums.value
                        LxSources.TENCENT -> runCatching { repository.fetchTxLikedAlbums() }.getOrDefault(emptyList())
                        else -> runCatching { repository.fetchNeteaseSubAlbums() }.getOrDefault(emptyList())
                    }
                    var s = _mineState.value.copy(
                        loadingHome = false,
                        homeLoaded = true,
                        subArtists = a,
                        subAlbums = al,
                        pageLoading = false,
                    )
                    s = when (s.page) {
                        MinePage.SubArtists -> s.copy(pageArtists = a)
                        MinePage.SubAlbums -> s.copy(pageAlbums = al)
                        else -> s
                    }
                    _mineState.value = s
                }
            }
            is MinePage.Artist -> {
                if (_mineState.value.artistTab == "albums") loadArtistAlbums(page.id)
                else loadArtistSongs(page.id)
            }
            is MinePage.SongList -> openMineSongs(page, forward = false)
        }
    }

    fun refreshBrowse() {
        repository.invalidateNeteasePlaylistDetailCache()
        val st = _browseState.value
        val source = _selectedSource.value
        currentLoadJob?.cancel()
        val epoch = ++loadEpoch
        when {
            st.module == "mine" -> refreshMineCurrent()
            st.selectedPlaylist != null ->
                currentLoadJob = viewModelScope.launch {
                    loadPlaylistSongsInternal(st.selectedPlaylist, source, epoch)
                }
            st.userPlaylistUid != null ->
                currentLoadJob = viewModelScope.launch {
                    val uid = st.userPlaylistUid
                    val list = runCatching { repository.fetchNeteaseUserPlaylists(uid) }
                        .onFailure { Timber.w(it, "refresh user playlists uid=$uid failed") }
                        .getOrElse { emptyList() }
                    if (loadEpoch != epoch || _selectedSource.value != LxSources.NETEASE) return@launch
                    val cur = _browseState.value
                    if (cur.userPlaylistUid != uid) return@launch
                    _browseState.value = cur.copy(
                        userPlaylists = list,
                        error = if (list.isEmpty()) context.getString(R.string.lx_error_user_playlists_not_found) else null,
                    )
                }
            st.isPlaylistSearchMode -> searchPlaylists(st.playlistSearchKeyword)
            st.module == "playlist" && st.selectedTag != null ->
                currentLoadJob = viewModelScope.launch {
                    loadPlaylistsForTagInternal(st.selectedTag, source, epoch)
                }
            st.module == "playlist" ->
                startModuleLoad("playlist", source, force = true)
            st.module == "board" && st.selectedBoard != null ->
                currentLoadJob = viewModelScope.launch {
                    loadBoardSongsInternal(st.selectedBoard, source, epoch)
                }
            st.module == "board" ->
                startModuleLoad("board", source, force = true)
            else -> {
                val kw = _searchState.value.keyword
                if (kw.isNotBlank()) search(kw)
            }
        }
    }

    fun submitPlaylistInput(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val ref = runCatching {
                repository.resolvePlaylistRef(trimmed, _selectedSource.value)
            }.getOrNull()
            if (ref == null) {
                searchPlaylists(trimmed)
            } else {
                openPlaylistDirect(ref.first, ref.second)
            }
        }
    }

    fun openPlaylistDirect(source: String, playlistId: String) {
        if (source !in LxSources.ALL) return
        if (_selectedSource.value != source) {
            _selectedSource.value = source
            persistSelectedSource()
        }
        currentLoadJob?.cancel()
        val epoch = ++loadEpoch
        val placeholder = LxPlaylist(
            id = playlistId,
            name = context.getString(R.string.lx_msg_playlist_loading),
            pic = null,
            playCount = null,
            trackCount = null,
            creator = null,
        )
        _browseState.value = _browseState.value.copy(
            module = "playlist",
            moduleSource = source,
            isPlaylistSearchMode = false,
            playlistSearchKeyword = "",
            playlistSearchResults = emptyList(),
            searchingPlaylists = false,
            selectedPlaylist = placeholder,
            playlistSongs = emptyList(),
            loadingPlaylistSongs = true,
            playlistSongsPage = 1,
            playlistSongsHasMore = false,
            playlistSongsLoadingMore = false,
            loadingPlaylists = false,
            selectedTag = null,
            playlists = emptyList(),
            playlistTags = emptyList(),
            error = null,
        )
        currentLoadJob = viewModelScope.launch {
            val (songs, hasMore) = runCatching {
                repository.fetchPlaylistSongs(source, playlistId, page = 1, pageSize = playlistSongsPageSize)
            }
                .onFailure { Timber.w(it, "openPlaylistDirect failed $source/$playlistId") }
                .getOrElse { emptyList<LxSong>() to false }
            if (loadEpoch != epoch) return@launch
            val cur = _browseState.value
            if (_selectedSource.value != source || cur.selectedPlaylist?.id != playlistId) return@launch
            val title = songs.firstOrNull()?.albumName?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.lx_default_playlist_name, playlistId)
            _browseState.value = cur.copy(
                playlistSongs = songs,
                loadingPlaylistSongs = false,
                playlistSongsPage = 1,
                playlistSongsHasMore = hasMore,
                selectedPlaylist = placeholder.copy(name = title),
                error = if (songs.isEmpty()) context.getString(R.string.lx_error_playlist_not_found) else null,
            )
        }
    }

    private suspend fun loadPlaylistSongsInternal(playlist: LxPlaylist, source: String, epoch: Long) {
        _browseState.value = _browseState.value.copy(
            selectedPlaylist = playlist, loadingPlaylistSongs = true, error = null,
            playlistSongsPage = 1, playlistSongsHasMore = false, playlistSongsLoadingMore = false,
        )
        val (songs, hasMore) = runCatching {
            repository.fetchPlaylistSongs(source, playlist.id, page = 1, pageSize = playlistSongsPageSize)
        }
            .getOrElse { Timber.w(it, "loadPlaylistSongs failed ${playlist.id}"); emptyList<LxSong>() to false }
        if (loadEpoch != epoch || _selectedSource.value != source) return
        val cur = _browseState.value
        if (cur.selectedPlaylist != playlist) return
        _browseState.value = cur.copy(
            playlistSongs = songs,
            loadingPlaylistSongs = false,
            playlistSongsPage = 1,
            playlistSongsHasMore = hasMore,
            error = if (songs.isEmpty()) context.getString(R.string.lx_error_playlist_songs_load_failed) else null,
        )
    }

    fun loadMorePlaylistSongs() {
        val before = _browseState.value
        if (before.loadingPlaylistSongs || before.playlistSongsLoadingMore || !before.playlistSongsHasMore) return
        val playlist = before.selectedPlaylist ?: return
        val source = _selectedSource.value
        val nextPage = before.playlistSongsPage + 1
        _browseState.value = before.copy(playlistSongsLoadingMore = true)
        viewModelScope.launch {
            val (batch, hasMore) = runCatching {
                repository.fetchPlaylistSongs(source, playlist.id, page = nextPage, pageSize = playlistSongsPageSize)
            }.getOrElse { Timber.w(it, "loadMorePlaylistSongs failed ${playlist.id} page=$nextPage"); emptyList<LxSong>() to false }
            val cur = _browseState.value
            if (cur.selectedPlaylist?.id != playlist.id || _selectedSource.value != source) return@launch
            val merged = (cur.playlistSongs + batch).distinctBy { it.source to it.songmid }
            _browseState.value = cur.copy(
                playlistSongs = merged,
                playlistSongsPage = nextPage,
                playlistSongsHasMore = hasMore && batch.isNotEmpty(),
                playlistSongsLoadingMore = false,
            )
        }
    }

    private suspend fun loadBrowsePics(songs: List<LxSong>) {
        val needPic = songs.filter { it.pic.isNullOrBlank() && it.source in setOf("kw", "kg") }
        if (needPic.isEmpty()) return
        val semaphore = kotlinx.coroutines.sync.Semaphore(6)
        coroutineScope {
            needPic.map { song ->
                async {
                    semaphore.acquire()
                    try {
                        val pic = runCatching { repository.fetchPic(song) }
                            .onFailure { Timber.w(it, "loadBrowsePics: fetchPic failed ${song.source}/${song.songmid}") }
                            .getOrNull() ?: return@async
                        _browseState.value = _browseState.value.let { cur ->
                            cur.copy(
                                boardSongs = cur.boardSongs.map {
                                    if (it.source == song.source && it.songmid == song.songmid) it.copy(pic = pic) else it
                                },
                                playlistSongs = cur.playlistSongs.map {
                                    if (it.source == song.source && it.songmid == song.songmid) it.copy(pic = pic) else it
                                },
                            )
                        }
                        streamProxy.cacheMetadata(
                            source = song.source,
                            songmid = song.songmid,
                            name = song.name,
                            singer = song.singer,
                            albumName = song.albumName,
                            pic = pic,
                            hash = song.hash,
                            copyrightId = song.copyrightId,
                            extraFields = song.extraFields,
                        )
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchState.value = _searchState.value.copy(keyword = query, error = null)
    }

    fun clearSearchResults() {
        _searchState.value = _searchState.value.copy(
            keyword = "",
            error = null,
            results = emptyList(),
            searchedSource = null,
            page = 1,
            canLoadMore = false,
            artistResults = emptyList(),
            artistTotal = 0,
            artistPage = 1,
            loadingMoreArtists = false,
            canLoadMoreArtists = false,
            artistUnsupported = false,
            albumResults = emptyList(),
            albumTotal = 0,
            albumPage = 1,
            loadingMoreAlbums = false,
            canLoadMoreAlbums = false,
            albumUnsupported = false,
        )
        _searchArtistOverlay.value = false
        resetMineNavigation()
    }

    private fun persistActiveScript(scriptPath: String?) {
        viewModelScope.launch {
            userPreferencesRepository.setLxMusicActiveScript(scriptPath)
        }
    }

    fun setSearchType(type: SearchType) {
        val cur = _searchState.value
        if (cur.type == type) return
        _searchState.value = cur.copy(type = type, error = null)
        if (cur.keyword.isBlank()) return
        when (type) {
            SearchType.ARTIST -> if (!cur.searchingArtists) searchArtists(cur.keyword)
            SearchType.ALBUM -> if (!cur.searchingAlbums) searchAlbums(cur.keyword)
            else -> if (!cur.searching) searchSongs(cur.keyword)
        }
    }

    fun search(keyword: String) {
        when (_searchState.value.type) {
            SearchType.ARTIST -> searchArtists(keyword)
            SearchType.ALBUM -> searchAlbums(keyword)
            else -> searchSongs(keyword)
        }
    }

    fun loadMore() {
        when (_searchState.value.type) {
            SearchType.ARTIST -> loadMoreArtists()
            SearchType.ALBUM -> loadMoreAlbums()
            else -> loadMoreSongs()
        }
    }

    fun searchSongs(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        val source = _selectedSource.value
        val epoch = _searchState.value.searchEpoch + 1
        viewModelScope.launch {
            _searchState.value = _searchState.value.copy(
                keyword = trimmed,
                searching = true,
                error = null,
                results = emptyList(),
                searchedSource = null,
                searchEpoch = epoch,
            )
            val result = runCatching { repository.searchMusic("", source, trimmed) }
                .onFailure { Timber.w(it, "search $source failed") }
            val results = result.getOrElse { emptyList() }
                .distinctBy { "${it.source}:${it.songmid}" }
            val cur = _searchState.value
            if (cur.searchEpoch != epoch) return@launch
            _searchState.value = cur.copy(
                searching = false,
                results = results,
                searchedSource = source,
                page = 1,
                canLoadMore = results.size >= 20,
                error = if (results.isEmpty()) {
                    result.exceptionOrNull()?.let { context.getString(R.string.lx_error_search_failed, it.message ?: "") }
                } else null,
            )
            if (results.isNotEmpty() && source in setOf("wy", "kw", "kg")) {
                loadPicsForResults(results)
            }
        }
    }

    fun searchArtists(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        val source = _selectedSource.value
        val epoch = _searchState.value.searchEpoch + 1
        viewModelScope.launch {
            _searchState.value = _searchState.value.copy(
                keyword = trimmed,
                searchingArtists = true,
                error = null,
                artistResults = emptyList(),
                artistUnsupported = false,
                loadingMoreArtists = false,
                canLoadMoreArtists = false,
                artistPage = 1,
                searchEpoch = epoch,
            )
            val result = runCatching {
                when (source) {
                    LxSources.TENCENT -> repository.searchTxArtists(trimmed, page = 1)
                    LxSources.KUGOU -> repository.searchKgArtists(trimmed, page = 1)
                    LxSources.KUWO -> repository.searchKwArtists(trimmed, page = 1)
                    else -> repository.searchNeteaseArtists(trimmed, page = 1)
                }
            }.onFailure { Timber.w(it, "searchArtists failed") }
            val (artists, total) = result.getOrElse { emptyList<LxArtist>() to 0 }
            val cur = _searchState.value
            if (cur.searchEpoch != epoch || _selectedSource.value != source) return@launch
            _searchState.value = cur.copy(
                searchingArtists = false,
                artistResults = artists,
                artistTotal = total,
                artistPage = 1,
                canLoadMoreArtists = artists.isNotEmpty() && artists.size < total,
                error = result.exceptionOrNull()?.let {
                    context.getString(R.string.lx_error_search_failed, it.message ?: "")
                },
            )
            if (source == LxSources.KUGOU && artists.isNotEmpty()) {
                launch {
                    val picById = coroutineScope {
                        artists.map { a ->
                            async {
                                a.id to runCatching { repository.fetchKgArtistPic(a.id) }.getOrNull()
                            }
                        }.awaitAll().filter { it.second != null }.toMap()
                    }
                    if (picById.isEmpty()) return@launch
                    val s = _searchState.value
                    if (s.searchEpoch == epoch && _selectedSource.value == source && s.artistResults.isNotEmpty()) {
                        _searchState.value = s.copy(
                            artistResults = s.artistResults.map { it.copy(pic = picById[it.id] ?: it.pic) }
                        )
                    }
                }
            }
        }
    }

    fun searchAlbums(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        val source = _selectedSource.value
        val epoch = _searchState.value.searchEpoch + 1
        viewModelScope.launch {
            _searchState.value = _searchState.value.copy(
                keyword = trimmed,
                searchingAlbums = true,
                error = null,
                albumResults = emptyList(),
                albumUnsupported = false,
                loadingMoreAlbums = false,
                canLoadMoreAlbums = false,
                albumPage = 1,
                searchEpoch = epoch,
            )
            val result = runCatching {
                when (source) {
                    LxSources.TENCENT -> repository.searchTxAlbums(trimmed, page = 1)
                    LxSources.KUGOU -> repository.searchKgAlbums(trimmed, page = 1)
                    LxSources.KUWO -> repository.searchKwAlbums(trimmed, page = 1)
                    else -> repository.searchNeteaseAlbums(trimmed, page = 1)
                }
            }.onFailure { Timber.w(it, "searchAlbums failed") }
            val (albums, total) = result.getOrElse { emptyList<LxAlbum>() to 0 }
            val cur = _searchState.value
            if (cur.searchEpoch != epoch || _selectedSource.value != source) return@launch
            _searchState.value = cur.copy(
                searchingAlbums = false,
                albumResults = albums,
                albumTotal = total,
                albumPage = 1,
                canLoadMoreAlbums = albums.isNotEmpty() && albums.size < total,
                error = result.exceptionOrNull()?.let {
                    context.getString(R.string.lx_error_search_failed, it.message ?: "")
                },
            )
        }
    }

    fun loadMoreAlbums() {
        val state = _searchState.value
        if (state.searchingAlbums || state.loadingMoreAlbums || !state.canLoadMoreAlbums) return
        val source = _selectedSource.value
        val keyword = state.keyword.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            _searchState.value = state.copy(loadingMoreAlbums = true)
            val nextPage = state.albumPage + 1
            val (batch, total) = runCatching {
                when (source) {
                    LxSources.TENCENT -> repository.searchTxAlbums(keyword, page = nextPage)
                    LxSources.KUGOU -> repository.searchKgAlbums(keyword, page = nextPage)
                    LxSources.KUWO -> repository.searchKwAlbums(keyword, page = nextPage)
                    else -> repository.searchNeteaseAlbums(keyword, page = nextPage)
                }
            }.getOrDefault(emptyList<LxAlbum>() to state.albumTotal)
            val cur = _searchState.value
            if (cur.keyword != keyword || _selectedSource.value != source) return@launch
            val merged = (cur.albumResults + batch).distinctBy { it.id }
            _searchState.value = cur.copy(
                loadingMoreAlbums = false,
                albumResults = merged,
                albumTotal = total,
                albumPage = nextPage,
                canLoadMoreAlbums = merged.size < total,
            )
        }
    }

    fun loadMoreArtists() {
        val state = _searchState.value
        if (state.searchingArtists || state.loadingMoreArtists || !state.canLoadMoreArtists) return
        val source = _selectedSource.value
        val keyword = state.keyword.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            _searchState.value = state.copy(loadingMoreArtists = true)
            val nextPage = state.artistPage + 1
            val (batch, total) = runCatching {
                when (source) {
                    LxSources.TENCENT -> repository.searchTxArtists(keyword, page = nextPage)
                    LxSources.KUGOU -> repository.searchKgArtists(keyword, page = nextPage)
                    LxSources.KUWO -> repository.searchKwArtists(keyword, page = nextPage)
                    else -> repository.searchNeteaseArtists(keyword, page = nextPage)
                }
            }.getOrDefault(emptyList<LxArtist>() to state.artistTotal)
            val cur = _searchState.value
            if (cur.keyword != keyword || _selectedSource.value != source) return@launch
            val merged = (cur.artistResults + batch).distinctBy { it.id }
            _searchState.value = cur.copy(
                loadingMoreArtists = false,
                artistResults = merged,
                artistPage = nextPage,
                artistTotal = if (total > 0) total else merged.size,
                canLoadMoreArtists = batch.isNotEmpty() && merged.size < total,
            )
        }
    }

    private val picsLoadSemaphore = kotlinx.coroutines.sync.Semaphore(6)

    private fun loadPicsForResults(songs: List<LxSong>) {
        val needPic = songs.filter { it.pic.isNullOrBlank() }
        if (needPic.isEmpty()) return
        viewModelScope.launch {
            coroutineScope {
                needPic.map { song ->
                    async {
                        picsLoadSemaphore.acquire()
                        try {
                            runCatching { repository.fetchPic(song) }
                                .onFailure { Timber.w(it, "loadPics: fetchPic failed for ${song.source}/${song.songmid}") }
                                .getOrNull()?.takeIf { it.isNotBlank() }?.let { pic ->
                                    val updated = song.copy(pic = pic)
                                    applySongPicEverywhere(updated)
                                    streamProxy.cacheMetadata(
                                        source = updated.source,
                                        songmid = updated.songmid,
                                        name = updated.name,
                                        singer = updated.singer,
                                        albumName = updated.albumName,
                                        pic = pic,
                                        hash = updated.hash,
                                        copyrightId = updated.copyrightId,
                                        extraFields = updated.extraFields,
                                    )
                                }
                        } finally {
                            picsLoadSemaphore.release()
                        }
                    }
                }.awaitAll()
            }
        }
    }

    fun loadPlaylistSongPicIfMissing(song: LxSong) {
        if (!song.pic.isNullOrBlank()) return
        loadPicsForResults(listOf(song))
    }

    private fun applySongPicEverywhere(updated: LxSong) {
        fun List<LxSong>.withPic() = map { old ->
            if (old.source == updated.source && old.songmid == updated.songmid) updated else old
        }
        _searchState.value = _searchState.value.copy(results = _searchState.value.results.withPic())
        val artistPage = _searchArtistPageState.value
        if (artistPage.songs.any { it.source == updated.source && it.songmid == updated.songmid }) {
            _searchArtistPageState.value = artistPage.copy(songs = artistPage.songs.withPic())
        }
        val artistPageNow = _searchArtistPageState.value
        if (artistPageNow.albumSongs.any { it.source == updated.source && it.songmid == updated.songmid }) {
            _searchArtistPageState.value = artistPageNow.copy(albumSongs = artistPageNow.albumSongs.withPic())
        }
        val albumPage = _searchAlbumPageState.value
        if (albumPage.songs.any { it.source == updated.source && it.songmid == updated.songmid }) {
            _searchAlbumPageState.value = albumPage.copy(songs = albumPage.songs.withPic())
        }
        val mine = _mineState.value
        if (mine.pageSongs.any { it.source == updated.source && it.songmid == updated.songmid }) {
            _mineState.value = mine.copy(pageSongs = mine.pageSongs.withPic())
        }
        val browse = _browseState.value
        if (browse.playlistSongs.any { it.source == updated.source && it.songmid == updated.songmid }) {
            _browseState.value = browse.copy(playlistSongs = browse.playlistSongs.withPic())
        }
    }

    private data class LikedSets(
        val wy: Set<String>,
        val kg: Set<String>,
        val tx: Set<String>,
        val kw: Set<String>,
    )

    private fun LxSong.isUnlikedIn(source: String, likedSet: Set<String>): Boolean {
        if (this.source != source) return false
        return when (source) {
            LxSources.KUGOU -> (hash == null || hash !in likedSet) && songmid !in likedSet
            else -> songmid !in likedSet
        }
    }

    fun loadMoreSongs() {
        val state = _searchState.value
        val keyword = state.keyword.takeIf { it.isNotBlank() } ?: return
        val source = state.searchedSource ?: return
        if (state.searching || state.loadingMore || !state.canLoadMore) return
        viewModelScope.launch {
            _searchState.value = state.copy(loadingMore = true)
            val nextPage = state.page + 1
            val batch = runCatching { repository.searchMusic("", source, keyword, nextPage) }
                .onFailure { Timber.w(it, "loadMore $source failed (page=$nextPage)") }
                .getOrElse { emptyList() }
            val current = _searchState.value
            if (current.keyword != keyword || current.searchedSource != source) return@launch
            val before = current.results.size
            val merged = (current.results + batch).distinctBy { "${it.source}:${it.songmid}" }
            _searchState.value = current.copy(
                loadingMore = false,
                results = merged,
                page = nextPage,
                canLoadMore = batch.size >= 20 && merged.size > before,
            )
            if (batch.isNotEmpty() && source in setOf("wy", "kw", "kg")) {
                loadPicsForResults(batch)
            }
        }
    }

    fun clearSearch() {
        _searchState.value = SearchState()
    }


    fun importFromUrl(url: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.importFromUrl(url)
            result.onSuccess { meta ->
                _loadState.value = LoadState(loadedSources = emptyList())
                onResult(null)
            }.onFailure { e ->
                _loadState.value = LoadState(error = context.getString(R.string.lx_error_import_failed, e.message ?: ""))
                onResult(e.message)
            }
        }
    }

    fun importFromFile(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.importFromFile(uri)
            result.onSuccess { meta ->
                _loadState.value = LoadState(loadedSources = emptyList())
                onResult(null)
            }.onFailure { e ->
                _loadState.value = LoadState(error = context.getString(R.string.lx_error_import_failed, e.message ?: ""))
                onResult(e.message)
            }
        }
    }


    fun batchDownloadSongs(lxSongs: List<LxSong>, quality: String) {
        viewModelScope.launch {
            val activeScriptPath = repository.activeScriptPath()
            if (activeScriptPath == null) {
                _toastMessage.tryEmit(context.getString(R.string.lx_msg_download_needs_script))
                return@launch
            }

            var successCount = 0
            var failCount = 0

            lxSongs.forEach { lxSong ->
                runCatching {
                    val song = LxSongMapper.toSong(lxSong)
                    repository.persistLxSong(lxSong)
                    val scripts = repository.activeRuntimeScriptPathsOrdered(lxSong.source)
                    val assetPath = scripts.firstOrNull()
                    if (assetPath != null) {
                        cloudOfflineRepository.enqueue(song, quality, assetPath, scripts)
                        successCount++
                    } else {
                        failCount++
                    }
                }.onFailure { e ->
                    Timber.w(e, "batchDownloadSongs: failed to enqueue ${lxSong.source}/${lxSong.songmid}")
                    failCount++
                }
            }

            val msg = when {
                failCount == 0 -> context.getString(R.string.lx_msg_batch_download_started, successCount)
                successCount == 0 -> context.getString(R.string.lx_msg_batch_download_failed)
                else -> context.getString(R.string.lx_msg_batch_download_partial, successCount, failCount)
            }
            _toastMessage.tryEmit(msg)
        }
    }
}

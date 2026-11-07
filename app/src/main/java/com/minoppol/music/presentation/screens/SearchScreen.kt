package com.minoppol.music.presentation.screens

import com.minoppol.music.presentation.navigation.navigateSafely
import com.minoppol.music.presentation.navigation.navigateSafelyReplacing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import com.minoppol.music.ui.theme.ShapeCache
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import coil.compose.AsyncImage
import com.minoppol.music.data.lxmusic.LxSongMapper
import com.minoppol.music.presentation.viewmodel.MultiSelectionStateHolder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.minoppol.music.data.model.Album
import com.minoppol.music.data.model.Artist
import com.minoppol.music.data.model.Playlist
import com.minoppol.music.data.model.SearchFilterType
import com.minoppol.music.data.model.SearchHistoryItem
import com.minoppol.music.data.model.SearchResultItem
import com.minoppol.music.data.model.Song
import com.minoppol.music.presentation.components.SmartImage
import com.minoppol.music.presentation.components.SmartImageListTargetSize
import com.minoppol.music.presentation.components.SongInfoBottomSheet
import com.minoppol.music.presentation.viewmodel.PlayerViewModel
import com.minoppol.music.ui.theme.LocalPixelPlayerDarkTheme
import com.minoppol.music.ui.theme.RoundedSans
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.minoppol.music.R
import com.minoppol.music.data.diagnostics.AdvancedPerformanceDiagnostics
import com.minoppol.music.presentation.components.MiniPlayerHeight
import com.minoppol.music.presentation.components.PlaylistBottomSheet
import com.minoppol.music.presentation.components.PlaylistCover
import com.minoppol.music.presentation.components.resolveMainScreenBottomGradientHeight
import com.minoppol.music.data.preferences.NavBarStyle
import com.minoppol.music.presentation.components.resolveNavBarOccupiedHeight
import com.minoppol.music.presentation.navigation.Screen
import com.minoppol.music.presentation.screens.search.components.GenreCategoriesGrid
import com.minoppol.music.presentation.viewmodel.PlaylistViewModel
import com.minoppol.music.utils.formatSongCount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import kotlin.math.roundToInt
import com.minoppol.music.presentation.components.subcomps.EnhancedSongListItem
import com.minoppol.music.presentation.components.subcomps.SelectionActionRow
import com.minoppol.music.presentation.components.subcomps.SelectionCountPill
import com.minoppol.music.presentation.components.ExpressiveScrollBar
import com.minoppol.music.presentation.screens.lxmusic.rememberLxHeaderMetrics
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.OutlinedButton
import com.minoppol.music.data.lxmusic.LxSong
import com.minoppol.music.data.lxmusic.LxSources
import com.minoppol.music.presentation.viewmodel.LxOnlineHotItem
import com.minoppol.music.presentation.viewmodel.LxOnlinePlaylistHit
import com.minoppol.music.presentation.viewmodel.LxOnlineSearchTab
import com.minoppol.music.presentation.viewmodel.LxOnlineSearchUiState
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.minoppol.music.presentation.screens.lxmusic.LxFolderActionCapsule
import com.minoppol.music.presentation.screens.lxmusic.LxFolderTitleText
import com.minoppol.music.presentation.screens.lxmusic.LxUncollectConfirmDialog
import com.minoppol.music.presentation.screens.lxmusic.rememberCollectState
import com.minoppol.music.presentation.screens.lxmusic.rememberFolderShare
import com.minoppol.music.presentation.screens.lxmusic.LxShareUrls
import com.minoppol.music.presentation.screens.lxmusic.rememberLxHeaderMetrics
import com.minoppol.music.presentation.screens.lxmusic.LxBottomPlayActions
import com.minoppol.music.presentation.screens.lxmusic.LxEmptyState
import com.minoppol.music.presentation.screens.lxmusic.LxDiscoverSortOptions
import com.minoppol.music.presentation.screens.lxmusic.LxScrollToTopFab
import com.minoppol.music.presentation.screens.lxmusic.LxLocateCurrentSongFab
import com.minoppol.music.presentation.screens.lxmusic.sortLxSongs
import com.minoppol.music.presentation.components.LibrarySortBottomSheet
import com.minoppol.music.data.model.SortOption

private data class SearchUiSlice(
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL,
    val searchResults: ImmutableList<SearchResultItem> = persistentListOf()
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavHostController,
    onSearchBarActiveChange: (Boolean) -> Unit = {}
) {
    val searchScreenEnteredAt = remember { android.os.SystemClock.elapsedRealtime() }
    var searchQuery by rememberSaveable { mutableStateOf(playerViewModel.searchQuery) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val onlineSearchMode by playerViewModel.onlineSearchMode.collectAsStateWithLifecycle()
    var onlineInputFocused by rememberSaveable { mutableStateOf(false) }
    val lxViewModel: com.minoppol.music.presentation.screens.lxmusic.LxMusicViewModel = hiltViewModel()
    val onlineSearchViewModel: com.minoppol.music.presentation.viewmodel.LxOnlineSearchViewModel = hiltViewModel()
    val onlineState by onlineSearchViewModel.uiState.collectAsStateWithLifecycle()
    val onlineSuggestions by onlineSearchViewModel.suggestions.collectAsStateWithLifecycle()
    val onlineHotItems by onlineSearchViewModel.hotItems.collectAsStateWithLifecycle()
    val onlineHotLoading by onlineSearchViewModel.hotLoading.collectAsStateWithLifecycle()
    val onlinePlaylistDetail by onlineSearchViewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val onlineMultiSelection = remember { MultiSelectionStateHolder() }
    val onlineSelectedSongs by onlineMultiSelection.selectedSongs.collectAsStateWithLifecycle()
    val onlineSelectedSongIds by onlineMultiSelection.selectedSongIds.collectAsStateWithLifecycle()
    val onlineSelectionMode by onlineMultiSelection.isSelectionMode.collectAsStateWithLifecycle()
    var showOnlineMultiSelectionSheet by remember { mutableStateOf(false) }
    var onlineInfoSong by remember { mutableStateOf<com.minoppol.music.data.lxmusic.LxSong?>(null) }
    var showOnlineArtistPicker by remember { mutableStateOf(false) }
    var onlineArtistPickerCandidates by remember { mutableStateOf<List<com.minoppol.music.data.lxmusic.LxArtist>>(emptyList()) }
    var onlineArtistPickerSource by remember { mutableStateOf("") }
    var showOnlineClearHistoryConfirm by remember { mutableStateOf(false) }
    val onlineScope = rememberCoroutineScope()
    val lxNeteaseLoggedIn by lxViewModel.neteaseLoggedIn.collectAsStateWithLifecycle()
    val lxKugouLoggedIn by lxViewModel.kugouLoggedIn.collectAsStateWithLifecycle()
    val lxKuwoLoggedIn by lxViewModel.kuwoLoggedIn.collectAsStateWithLifecycle()
    val lxTencentLoggedIn by lxViewModel.tencentLoggedIn.collectAsStateWithLifecycle()
    val lxBoardIncludeQueue by playerViewModel.lxBoardIncludeQueue.collectAsStateWithLifecycle()
    val searchHistory = playerViewModel.playerUiState.collectAsStateWithLifecycle().value.searchHistory

    suspend fun ensureOnlineScriptOrToast(song: com.minoppol.music.data.lxmusic.LxSong): Boolean {
        if (lxViewModel.activeScript.value != null) return true
        val resolved = lxViewModel.ensureActiveScriptForPlayback()
        if (resolved != null) return true
        if (song.source in com.minoppol.music.data.lxmusic.LxSources.ALL) return true
        val hasDisabled = lxViewModel.hasUserDisabledScripts()
        android.widget.Toast.makeText(
            context,
            if (hasDisabled) context.getString(R.string.lx_msg_source_disabled)
            else context.getString(com.minoppol.music.R.string.lxmusic_need_script_or_cookie),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
        return false
    }

    val onlinePlayLxSong: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = { song ->
        onlineScope.launch {
            if (!ensureOnlineScriptOrToast(song)) return@launch
            lxViewModel.recordPlay(song)
            lxViewModel.cacheSongsMetadata(listOf(song))
            val singleSong = LxSongMapper.toSong(song)
            playerViewModel.playSongs(
                songsToPlay = listOf(singleSong),
                startSong = singleSong,
                queueName = context.getString(R.string.lx_queue_name_playlist),
                origin = com.minoppol.music.presentation.viewmodel.PlaybackOrigin.ONLINE,
            )
        }
    }

    val onlinePlayPageSong: (com.minoppol.music.data.lxmusic.LxSong, List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit = { song, pageSongs, queueName ->
        if (lxBoardIncludeQueue && pageSongs.isNotEmpty()) {
            onlineScope.launch {
                if (!ensureOnlineScriptOrToast(song)) return@launch
                lxViewModel.recordPlay(song)
                lxViewModel.cacheSongsMetadata(pageSongs)
                lxViewModel.setPlaybackQueueLxSongs(pageSongs)
                playerViewModel.playSongs(
                    songsToPlay = pageSongs.map { LxSongMapper.toSong(it) },
                    startSong = LxSongMapper.toSong(song),
                    queueName = queueName,
                    origin = com.minoppol.music.presentation.viewmodel.PlaybackOrigin.ONLINE,
                )
            }
        } else {
            onlinePlayLxSong(song)
        }
    }

    val onlinePlayAll: (List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit = { songs, queueName ->
        if (songs.isEmpty()) {
            android.widget.Toast.makeText(context, context.getString(R.string.lx_msg_playlist_no_songs), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            onlineScope.launch {
                if (!ensureOnlineScriptOrToast(songs.first())) return@launch
                lxViewModel.recordPlay(songs.first())
                lxViewModel.cacheSongsMetadata(songs)
                lxViewModel.setPlaybackQueueLxSongs(songs)
                playerViewModel.playSongs(
                    songsToPlay = songs.map { LxSongMapper.toSong(it) },
                    startSong = LxSongMapper.toSong(songs.first()),
                    queueName = queueName,
                    origin = com.minoppol.music.presentation.viewmodel.PlaybackOrigin.ONLINE,
                )
            }
        }
    }

    val onlineShuffleAll: (List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit = { songs, queueName ->
        if (songs.isEmpty()) {
            android.widget.Toast.makeText(context, context.getString(R.string.lx_msg_playlist_no_songs), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            onlineScope.launch {
                if (!ensureOnlineScriptOrToast(songs.first())) return@launch
                lxViewModel.cacheSongsMetadata(songs)
                lxViewModel.setPlaybackQueueLxSongs(songs)
                val songsById = songs.associateBy { LxSongMapper.toSong(it).id }
                playerViewModel.playSongsShuffled(
                    songsToPlay = songs.map { LxSongMapper.toSong(it) },
                    queueName = queueName,
                    startAtZero = true,
                    onStartSongResolved = { startSong ->
                        songsById[startSong.id]?.let { lxViewModel.recordPlay(it) }
                    },
                    origin = com.minoppol.music.presentation.viewmodel.PlaybackOrigin.ONLINE,
                )
            }
        }
    }

    val onlineDiscoverSortOption by lxViewModel.discoverSortOption.collectAsStateWithLifecycle()
    var showOnlineSortSheet by remember { mutableStateOf(false) }

    val toggleOnlineSongSelection: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = { song ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onlineMultiSelection.toggleSelection(LxSongMapper.toSong(song))
    }

    val submitOnlineSearch: (String) -> Unit = { query ->
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            playerViewModel.onSearchQuerySubmitted(trimmed)
            onlineSearchViewModel.searchOnline(trimmed)
        }
        focusManager.clearFocus()
    }

    LaunchedEffect(onlineSearchMode) {
        if (onlineSearchMode) {
            playerViewModel.loadSearchHistory()
            onlineSearchViewModel.refreshHotSearches()
        }
    }

    androidx.activity.compose.BackHandler(enabled = onlineSearchMode && onlineSelectionMode) {
        onlineMultiSelection.clearSelection()
    }

    androidx.activity.compose.BackHandler(
        enabled = onlineSearchMode && !onlineSelectionMode && !onlineInputFocused && onlineState.submittedQuery.isNotBlank()
    ) {
        searchQuery = ""
        playerViewModel.updateSearchQuery("")
        onlineSearchViewModel.resetSearch()
    }

    val statusBarTopInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val navBarStyle by playerViewModel.navBarStyle.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    val bottomGradientHeight = if (navBarStyle == NavBarStyle.FLOATING) {
        0.dp
    } else {
        resolveMainScreenBottomGradientHeight(navBarCompactMode)
    }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val searchUiState by remember(playerViewModel) {
        playerViewModel.playerUiState
            .map { uiState ->
                SearchUiSlice(
                    selectedSearchFilter = uiState.selectedSearchFilter,
                    searchResults = uiState.searchResults
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = SearchUiSlice())
    val currentFilter = searchUiState.selectedSearchFilter
    val genres by playerViewModel.genres.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchInputFocusRequester = remember { FocusRequester() }
    val searchLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(searchLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        }
        searchLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { searchLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
            type = AdvancedPerformanceDiagnostics.EventTypes.UI,
            name = "search_screen_entered",
        )
        onSearchBarActiveChange(false)
        withFrameNanos { }
        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
            type = AdvancedPerformanceDiagnostics.EventTypes.UI,
            name = "search_screen_first_frame",
        ) {
            mapOf(
                "durationMs" to
                    (android.os.SystemClock.elapsedRealtime() - searchScreenEnteredAt).toString(),
                "genreCount" to genres.size.toString(),
            )
        }
    }

    LaunchedEffect(playerViewModel, keyboardController) {
        playerViewModel.searchNavDoubleTapEvents.collect {
            delay(40L)
            searchInputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(searchQuery, currentFilter) {
        if (!onlineSearchMode) {
            playerViewModel.performSearch(searchQuery)
        }
    }
    val searchResults = searchUiState.searchResults
    val handleSongMoreOptionsClick: (Song) -> Unit = { song ->
        playerViewModel.selectSongForInfo(song)
        showSongInfoBottomSheet = true
    }

    val searchbarCornerRadius = 28.dp

    val dm = LocalPixelPlayerDarkTheme.current

    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val gradientColorsDark = remember(primaryContainer) {
        listOf(primaryContainer.copy(alpha = 0.5f), Color.Transparent).toImmutableList()
    }
    val gradientColorsLight = remember(onPrimaryContainer) {
        listOf(onPrimaryContainer.copy(alpha = 0.2f), Color.Transparent).toImmutableList()
    }

    val gradientColors = if (dm) gradientColorsDark else gradientColorsLight

    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }
    val colorScheme = MaterialTheme.colorScheme
    val bottomGradientBrush = remember(colorScheme.surfaceContainerLowest) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.2f to Color.Transparent,
                0.8f to colorScheme.surfaceContainerLowest,
                1.0f to colorScheme.surfaceContainerLowest
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            onSearchBarActiveChange(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = statusBarTopInset + 12.dp, end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val searchBarInputFieldColors = SearchBarDefaults.inputFieldColors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )

                Box(
                    Modifier
                        .weight(1f)
                        .background(color = Color.Transparent)
                ) {
                    DockedSearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                modifier = Modifier
                                    .focusRequester(searchInputFocusRequester)
                                    .onFocusChanged { onlineInputFocused = it.isFocused },
                                query = searchQuery,
                                onQueryChange = {
                                    searchQuery = it
                                    playerViewModel.updateSearchQuery(it)
                                    onlineSearchViewModel.onQueryInput(it)
                                },
                                onSearch = { query ->
                                    if (onlineSearchMode) {
                                        submitOnlineSearch(query)
                                    } else if (query.isNotBlank()) {
                                        playerViewModel.onSearchQuerySubmitted(query)
                                    }
                                    keyboardController?.hide()
                                },
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = {
                                    Text(
                                        stringResource(R.string.search_placeholder),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                leadingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    val nextMode = !onlineSearchMode
                                                    playerViewModel.setOnlineSearchMode(nextMode)
                                                    if (!nextMode) {
                                                        onlineSearchViewModel.resetSearch()
                                                    } else {
                                                        onlineSearchViewModel.refreshHotSearches()
                                                    }
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Public,
                                                contentDescription = stringResource(R.string.search_online_mode_cd),
                                                tint = if (onlineSearchMode) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                },
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = stringResource(R.string.cd_search_icon),
                                            tint = if (onlineSearchMode) {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                searchQuery = ""
                                                playerViewModel.updateSearchQuery("")
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.cd_clear_search_query),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                colors = searchBarInputFieldColors
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier = Modifier
                            .clip(RoundedCornerShape(searchbarCornerRadius)),
                        colors = SearchBarDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            inputFieldColors = searchBarInputFieldColors
                        ),
                        content = {}
                    )
                }

                FilledIconButton(
                    modifier = Modifier.padding(bottom = 2.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    onClick = { navController.navigateSafely(Screen.Settings.route) }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_settings_24),
                        contentDescription = stringResource(R.string.presentation_batch_d_open_settings_cd)
                    )
                }
            }

            val showGenreBrowse by remember(searchQuery) { derivedStateOf { searchQuery.isBlank() } }
            LaunchedEffect(onlineInputFocused, searchQuery) {
                if (onlineSearchMode && onlineInputFocused && searchQuery.isNotBlank() && onlineSuggestions.isEmpty()) {
                    onlineSearchViewModel.refreshSuggestions(searchQuery)
                }
            }
            if (onlineSearchMode) {
                OnlineSearchSection(
                    state = onlineState,
                    suggestions = onlineSuggestions,
                    hotItems = onlineHotItems,
                    hotLoading = onlineHotLoading,
                    history = searchHistory,
                    query = searchQuery,
                    inputFocused = onlineInputFocused,
                    playerViewModel = playerViewModel,
                    onSubmitQuery = { query ->
                        searchQuery = query
                        playerViewModel.updateSearchQuery(query)
                        submitOnlineSearch(query)
                        keyboardController?.hide()
                    },
                    onDeleteHistoryItem = { item -> playerViewModel.deleteSearchHistoryItem(item.query) },
                    onClearHistory = { showOnlineClearHistoryConfirm = true },
                    onSetTab = { onlineSearchViewModel.setTab(it) },
                    onOpenPlaylist = { onlineSearchViewModel.openPlaylist(it) },
                    currentPlayingSongId = stablePlayerState.currentSong?.id,
                    isPlaying = stablePlayerState.isPlaying,
                    isSelectionMode = onlineSelectionMode,
                    selectedSongIds = onlineSelectedSongIds,
                    onSongLongPress = { song -> toggleOnlineSongSelection(song) },
                    onSongSelectionToggle = { song -> toggleOnlineSongSelection(song) },
                    getSelectionIndex = { songId -> onlineMultiSelection.getSelectionIndex(songId) },
                    onSelectionOptionsClick = { showOnlineMultiSelectionSheet = true },
                    onSelectAll = { onlineMultiSelection.selectAll(onlineState.songs.map { LxSongMapper.toSong(it) }) },
                    onDeselect = { onlineMultiSelection.clearSelection() },
                    onSongMoreOptions = { song -> onlineInfoSong = song },
                    playLxSong = onlinePlayLxSong,
                    playPageSong = onlinePlayPageSong,
                    playAll = onlinePlayAll,
                    shuffleAll = onlineShuffleAll,
                    sortOption = onlineDiscoverSortOption,
                    onSortClick = { showOnlineSortSheet = true },
                    onLoadMoreSongs = { onlineSearchViewModel.loadMoreSongs() },
                    onLoadMorePlaylists = { onlineSearchViewModel.loadMorePlaylists() },
                    bottomBarHeightDp = bottomBarHeightDp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else AnimatedContent(
                targetState = showGenreBrowse,
                transitionSpec = {
                    val switchingToGenre = targetState
                    val enter = fadeIn(animationSpec = tween(durationMillis = 320, delayMillis = 70)) +
                        slideInVertically(animationSpec = tween(durationMillis = 320)) { fullHeight ->
                            if (switchingToGenre) -fullHeight / 10 else fullHeight / 10
                        }
                    val exit = fadeOut(animationSpec = tween(durationMillis = 220)) +
                        slideOutVertically(animationSpec = tween(durationMillis = 220)) { fullHeight ->
                            if (switchingToGenre) fullHeight / 12 else -fullHeight / 12
                        }
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "search_mode_transition"
            ) { isGenreMode ->
                if (isGenreMode) {
                    GenreCategoriesGrid(
                        genres = genres,
                        onGenreClick = { genre ->
                            Timber.tag("SearchScreen")
                                .d("Genre clicked: ${genre.name} (ID: ${genre.id})")
                            val encodedGenreId = java.net.URLEncoder.encode(genre.id, "UTF-8")
                            navController.navigateSafely(Screen.GenreDetail.createRoute(encodedGenreId))
                        },
                        playerViewModel = playerViewModel,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            SearchFilterChip(SearchFilterType.ALL, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.SONGS, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.ALBUMS, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.ARTISTS, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.PLAYLISTS, currentFilter, playerViewModel)
                        }
                        Crossfade(
                            targetState = searchResults.isEmpty(),
                            animationSpec = tween(durationMillis = 190),
                            label = "search_results_fade"
                        ) { isEmpty ->
                            if (isEmpty) {
                                EmptySearchResults(
                                    searchQuery = searchQuery,
                                    colorScheme = colorScheme
                                )
                            } else {
                                SearchResultsList(
                                    results = searchResults,
                                    searchQuery = searchQuery,
                                    playerViewModel = playerViewModel,
                                    onItemSelected = {
                                        if (searchQuery.isNotBlank()) {
                                            playerViewModel.onSearchQuerySubmitted(searchQuery)
                                        }
                                    },
                                    currentPlayingSongId = stablePlayerState.currentSong?.id,
                                    isPlaying = stablePlayerState.isPlaying,
                                    onSongMoreOptionsClick = handleSongMoreOptionsClick,
                                    navController = navController
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(bottomGradientHeight)
                .background(brush = bottomGradientBrush)
        )
    }


    if (onlineSearchMode) {
        onlinePlaylistDetail?.let { playlistDetail ->
            androidx.activity.compose.BackHandler { onlineSearchViewModel.closePlaylist() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                OnlinePlaylistDetailPage(
                    detail = playlistDetail,
                    onBack = { onlineSearchViewModel.closePlaylist() },
                    currentPlayingSongId = stablePlayerState.currentSong?.id,
                    isPlaying = stablePlayerState.isPlaying,
                    isSelectionMode = onlineSelectionMode,
                    selectedSongIds = onlineSelectedSongIds,
                    onSongLongPress = toggleOnlineSongSelection,
                    onSongSelectionToggle = toggleOnlineSongSelection,
                    getSelectionIndex = { songId -> onlineMultiSelection.getSelectionIndex(songId) },
                    onSongMoreOptions = { onlineInfoSong = it },
                    playLxSong = onlinePlayLxSong,
                    playPageSong = onlinePlayPageSong,
                    playAll = onlinePlayAll,
                    shuffleAll = onlineShuffleAll,
                    sortOption = onlineDiscoverSortOption,
                    onSortClick = { showOnlineSortSheet = true },
                    bottomBarHeightDp = bottomBarHeightDp,
                    lxViewModel = lxViewModel,
                    playerViewModel = playerViewModel,
                )
            }
        }
        val showOnlineAlbumOverlay by lxViewModel.searchAlbumOverlay.collectAsStateWithLifecycle()
        if (showOnlineAlbumOverlay) {
            val albumPageState by lxViewModel.searchAlbumPageState.collectAsStateWithLifecycle()
            androidx.activity.compose.BackHandler {
                if (onlineSelectionMode) {
                    onlineMultiSelection.clearSelection()
                } else {
                    lxViewModel.dismissSearchAlbumOverlay()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                com.minoppol.music.presentation.screens.lxmusic.LxSearchAlbumPage(
                    state = albumPageState,
                    viewModel = lxViewModel,
                    playerViewModel = playerViewModel,
                    playLxSong = onlinePlayLxSong,
                    playAllPlaylist = onlinePlayAll,
                    shuffleAllPlaylist = onlineShuffleAll,
                    playPageSong = onlinePlayPageSong,
                    bottomBarHeightDp = bottomBarHeightDp,
                    onBack = lxViewModel::dismissSearchAlbumOverlay,
                    onScrollingChange = {},
                    onSongMoreOptions = { onlineInfoSong = it },
                    isSelectionMode = onlineSelectionMode,
                    selectedSongIds = onlineSelectedSongIds,
                    onSongLongPress = toggleOnlineSongSelection,
                    onSongSelectionToggle = toggleOnlineSongSelection,
                    getSelectionIndex = { songId -> onlineMultiSelection.getSelectionIndex(songId) },
                    multiSelectionState = onlineMultiSelection,
                    onSelectionOptionsClick = { showOnlineMultiSelectionSheet = true },
                )
            }
        }
        val showOnlineArtistOverlay by lxViewModel.searchArtistOverlay.collectAsStateWithLifecycle()
        if (showOnlineArtistOverlay) {
            val artistPageState by lxViewModel.searchArtistPageState.collectAsStateWithLifecycle()
            androidx.activity.compose.BackHandler {
                if (onlineSelectionMode) {
                    onlineMultiSelection.clearSelection()
                } else if (artistPageState.selectedAlbum != null) {
                    lxViewModel.backFromSearchArtistAlbum()
                } else {
                    lxViewModel.dismissSearchArtistOverlay()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                com.minoppol.music.presentation.screens.lxmusic.LxSearchArtistPage(
                    state = artistPageState,
                    viewModel = lxViewModel,
                    playerViewModel = playerViewModel,
                    playLxSong = onlinePlayLxSong,
                    playAllPlaylist = onlinePlayAll,
                    shuffleAllPlaylist = onlineShuffleAll,
                    playPageSong = onlinePlayPageSong,
                    bottomBarHeightDp = bottomBarHeightDp,
                    showFollow = when (artistPageState.source) {
                        LxSources.TENCENT -> lxTencentLoggedIn && lxViewModel.accountWritesEnabled
                        LxSources.KUGOU -> lxKugouLoggedIn && lxViewModel.accountWritesEnabled
                        LxSources.KUWO -> lxKuwoLoggedIn && lxViewModel.accountWritesEnabled
                        else -> lxNeteaseLoggedIn
                    },
                    onBack = lxViewModel::dismissSearchArtistOverlay,
                    onScrollingChange = {},
                    onSongMoreOptions = { onlineInfoSong = it },
                    isSelectionMode = onlineSelectionMode,
                    selectedSongIds = onlineSelectedSongIds,
                    onSongLongPress = toggleOnlineSongSelection,
                    onSongSelectionToggle = toggleOnlineSongSelection,
                    getSelectionIndex = { songId -> onlineMultiSelection.getSelectionIndex(songId) },
                    multiSelectionState = onlineMultiSelection,
                    onSelectionOptionsClick = { showOnlineMultiSelectionSheet = true },
                )
            }
        }
        if (showOnlineSortSheet) {
            LibrarySortBottomSheet(
                title = stringResource(R.string.presentation_batch_d_sort_by),
                options = LxDiscoverSortOptions,
                selectedOption = onlineDiscoverSortOption,
                onDismiss = { showOnlineSortSheet = false },
                onOptionSelected = { lxViewModel.setDiscoverSortOption(it) },
            )
        }
        val onlineUncollectConfirm by lxViewModel.uncollectConfirm.collectAsStateWithLifecycle()
        onlineUncollectConfirm?.let { (kind, id) ->
            LxUncollectConfirmDialog(
                kind = kind,
                onConfirm = { lxViewModel.confirmUncollect(kind, id) },
                onDismiss = lxViewModel::dismissUncollectConfirm,
            )
        }
        onlineInfoSong?.let { infoLxSong ->
            val infoUiSong = remember(infoLxSong.source, infoLxSong.songmid, infoLxSong.pic) {
                LxSongMapper.toSong(infoLxSong)
            }
            val onlineLikeSnapshot by playerViewModel.platformLikeSnapshot.collectAsStateWithLifecycle()
            val (infoIsFavorite, infoFavoriteEnabled) = onlineLikeSnapshot.let {
                playerViewModel.platformLikeStateOf(infoUiSong)
            }
            SongInfoBottomSheet(
                song = infoUiSong,
                isFavorite = infoIsFavorite,
                favoriteEnabled = infoFavoriteEnabled,
                onToggleFavorite = { playerViewModel.togglePlatformFavorite(infoUiSong) },
                onDismiss = { onlineInfoSong = null },
                onPlaySong = {
                    onlinePlayLxSong(infoLxSong)
                    onlineInfoSong = null
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(infoUiSong)
                    onlineInfoSong = null
                    android.widget.Toast.makeText(
                        context, context.getString(R.string.toast_added_to_queue),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(infoUiSong)
                    onlineInfoSong = null
                    android.widget.Toast.makeText(
                        context, context.getString(R.string.toast_playing_next),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                onAddToPlayList = {
                    lxViewModel.openOnlineAddToPlaylist(infoLxSong)
                    onlineInfoSong = null
                },
                onAddToLibrary = {
                    lxViewModel.addLxSongsToLibrary(listOf(infoLxSong))
                    onlineInfoSong = null
                },
                addToPlaylistEnabled = when (infoLxSong.source) {
                    LxSources.NETEASE -> lxNeteaseLoggedIn
                    LxSources.KUGOU -> lxKugouLoggedIn && lxViewModel.accountWritesEnabled
                    LxSources.KUWO -> lxKuwoLoggedIn && lxViewModel.accountWritesEnabled
                    else -> false
                },
                removeFromPlaylist = run {
                    val loggedIn = when (infoLxSong.source) {
                        LxSources.NETEASE -> lxNeteaseLoggedIn && lxViewModel.accountWritesEnabled
                        LxSources.KUGOU -> lxKugouLoggedIn && lxViewModel.accountWritesEnabled
                        LxSources.KUWO -> lxKuwoLoggedIn && lxViewModel.accountWritesEnabled
                        else -> false
                    }
                    val ownCtx = if (loggedIn) lxViewModel.currentOwnPlaylistContext(infoLxSong.source) else null
                    if (ownCtx != null) {
                        {
                            lxViewModel.requestRemoveSongFromOwnPlaylist(infoLxSong)
                            onlineInfoSong = null
                        }
                    } else null
                },
                cloudDownloadEnabled = false,
                onDeleteFromDevice = { activity, songToDelete, onResult ->
                    playerViewModel.deleteFromDevice(activity, songToDelete, onResult)
                    onlineInfoSong = null
                },
                onNavigateToAlbum = {
                    val albumId = when (infoLxSong.source) {
                        LxSources.TENCENT -> infoUiSong.lxMusicExtraFields["albumMid"]
                        else -> infoUiSong.lxMusicExtraFields["albumId"]
                    }
                    if (!albumId.isNullOrBlank()) {
                        lxViewModel.openAlbumFromSearch(
                            com.minoppol.music.data.lxmusic.LxAlbum(
                                id = albumId,
                                name = infoUiSong.album.orEmpty(),
                                pic = infoLxSong.pic,
                            ),
                            source = infoLxSong.source
                        )
                        onlineInfoSong = null
                    }
                },
                onNavigateToArtist = {
                    onlineScope.launch {
                        val artists = playerViewModel.resolveLxSongArtists(infoUiSong)
                        when {
                            artists.size == 1 -> {
                                lxViewModel.openArtistFromSearch(artists.first(), infoLxSong.source)
                                onlineInfoSong = null
                            }
                            artists.size > 1 -> {
                                onlineArtistPickerCandidates = artists
                                onlineArtistPickerSource = infoLxSong.source
                                showOnlineArtistPicker = true
                                onlineInfoSong = null
                            }
                        }
                    }
                },
                onNavigateToGenre = {},
                onEditSong = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                removeFromListTrigger = {},
            )
        }
        if (showOnlineArtistPicker && onlineArtistPickerCandidates.isNotEmpty()) {
            com.minoppol.music.presentation.components.LxArtistPickerBottomSheet(
                artists = onlineArtistPickerCandidates,
                source = onlineArtistPickerSource,
                sheetState = com.minoppol.music.presentation.components.rememberModalSheetState(skipPartiallyExpanded = true),
                onDismiss = { showOnlineArtistPicker = false },
                onArtistClick = { artist ->
                    lxViewModel.openArtistFromSearch(artist, onlineArtistPickerSource)
                    showOnlineArtistPicker = false
                },
            )
        }
        if (showOnlineMultiSelectionSheet && onlineSelectedSongs.isNotEmpty()) {
            val selectedLxSongs = remember(onlineSelectedSongs) {
                onlineSelectedSongs.mapNotNull { LxSongMapper.toLxSong(it) }
            }
            val batchSameSource = selectedLxSongs.size == onlineSelectedSongs.size &&
                selectedLxSongs.map { it.source }.distinct().size == 1
            val batchSource = selectedLxSongs.firstOrNull()?.source
            val canBatchAddToPlaylist = batchSameSource && when (batchSource) {
                LxSources.NETEASE -> lxNeteaseLoggedIn
                LxSources.KUGOU -> lxKugouLoggedIn && lxViewModel.accountWritesEnabled
                LxSources.KUWO -> lxKuwoLoggedIn && lxViewModel.accountWritesEnabled
                else -> false
            }
            val canBatchDelete = batchSameSource && batchSource != null &&
                lxViewModel.currentOwnPlaylistContext(batchSource) != null
            com.minoppol.music.presentation.components.MultiSelectionBottomSheet(
                selectedSongs = onlineSelectedSongs,
                onDismiss = { showOnlineMultiSelectionSheet = false },
                showAddToPlaylist = canBatchAddToPlaylist,
                showDelete = canBatchDelete,
                showBatchEdit = false,
                onPlayAll = {
                    if (selectedLxSongs.isNotEmpty()) {
                        onlinePlayAll(selectedLxSongs, "多选播放")
                    }
                    onlineMultiSelection.clearSelection()
                    showOnlineMultiSelectionSheet = false
                },
                onAddToQueue = {
                    onlineSelectedSongs.forEach { song -> playerViewModel.addSongToQueue(song) }
                    onlineMultiSelection.clearSelection()
                    showOnlineMultiSelectionSheet = false
                    android.widget.Toast.makeText(
                        context, context.getString(R.string.toast_added_to_queue),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                onPlayNext = {
                    onlineSelectedSongs.forEach { song -> playerViewModel.addSongNextToQueue(song) }
                    onlineMultiSelection.clearSelection()
                    showOnlineMultiSelectionSheet = false
                    android.widget.Toast.makeText(
                        context, context.getString(R.string.toast_added_to_queue),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                onAddToPlaylist = {
                    lxViewModel.openOnlineAddToPlaylistBatch(selectedLxSongs)
                    onlineMultiSelection.clearSelection()
                    showOnlineMultiSelectionSheet = false
                },
                onToggleLikeAll = { shouldLike: Boolean ->
                    onlineSelectedSongs.forEach { song ->
                        playerViewModel.togglePlatformFavorite(song)
                    }
                    onlineMultiSelection.clearSelection()
                    showOnlineMultiSelectionSheet = false
                },
                onShareAll = {
                    playerViewModel.shareSelectedSongs(onlineSelectedSongs)
                    onlineMultiSelection.clearSelection()
                    showOnlineMultiSelectionSheet = false
                },
                onDownloadAll = {},
                onAddToLibrary = {
                    lxViewModel.addLxSongsToLibrary(selectedLxSongs)
                    onlineMultiSelection.clearSelection()
                    showOnlineMultiSelectionSheet = false
                },
                onDeleteAll = { _: android.app.Activity, onResult: (Boolean) -> Unit ->
                    lxViewModel.requestRemoveSongsFromOwnPlaylist(selectedLxSongs)
                    onResult(true)
                    showOnlineMultiSelectionSheet = false
                },
            )
        }
        val onlineAddSheetState by lxViewModel.onlineAddSheet.collectAsStateWithLifecycle()
        onlineAddSheetState?.let { addState ->
            com.minoppol.music.presentation.components.NeteaseAddToPlaylistSheet(
                state = addState,
                onDismiss = { lxViewModel.dismissOnlineAddSheet() },
                onConfirm = { ids -> lxViewModel.onlineAddSongToPlaylists(ids) },
            )
        }
        val onlineRemoveConfirmState by lxViewModel.onlineRemoveConfirm.collectAsStateWithLifecycle()
        onlineRemoveConfirmState?.let { removeState ->
            com.minoppol.music.presentation.screens.lxmusic.LxRemoveFromPlaylistConfirmDialog(
                songTitle = removeState.songTitle,
                playlistName = removeState.playlistName,
                removing = removeState.removing,
                onConfirm = { lxViewModel.confirmOnlineRemoveFromPlaylist() },
                onDismiss = { lxViewModel.dismissOnlineRemoveConfirm() },
            )
        }
        val onlineBatchRemoveConfirmState by lxViewModel.batchOnlineRemoveConfirm.collectAsStateWithLifecycle()
        onlineBatchRemoveConfirmState?.let { batchRemoveState ->
            com.minoppol.music.presentation.screens.lxmusic.LxBatchRemoveFromPlaylistConfirmDialog(
                count = batchRemoveState.songs.size,
                playlistName = batchRemoveState.playlistName,
                removing = batchRemoveState.removing,
                onConfirm = { lxViewModel.confirmBatchOnlineRemoveFromPlaylist() },
                onDismiss = { lxViewModel.dismissBatchOnlineRemoveConfirm() },
            )
        }
        if (showOnlineClearHistoryConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showOnlineClearHistoryConfirm = false },
                title = { Text(stringResource(R.string.search_online_clear_history)) },
                text = { Text(stringResource(R.string.search_online_clear_history_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        playerViewModel.clearSearchHistory()
                        showOnlineClearHistoryConfirm = false
                    }) { Text(stringResource(R.string.search_online_clear_history)) }
                },
                dismissButton = {
                    TextButton(onClick = { showOnlineClearHistoryConfirm = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteSongIds) {
            derivedStateOf {
                currentSong?.let { favoriteSongIds.contains(it.id) }
            }
        }.value ?: false
        val removeFromListTrigger = remember(currentSong) {
            {
                searchQuery = "$searchQuery "
            }
        }

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                removeFromListTrigger = removeFromListTrigger,
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(currentSong)
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true;
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafelyReplacing(
                        route = Screen.AlbumDetail.createRoute(currentSong.albumId),
                        patternToPop = Screen.AlbumDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(currentSong.artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistById = { artistId ->
                    navController.navigateSafelyReplacing(
                        route = Screen.ArtistDetail.createRoute(artistId),
                        patternToPop = Screen.ArtistDetail.route
                    )
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    currentSong.genre?.let {
                        navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate, customMetadataChanges ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newAlbumArtist,
                        newComposer,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        newDiscNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate,
                        customMetadataChanges
                    )
                },
            )
            if (showPlaylistBottomSheet) {
                val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = persistentListOf(currentSong),
                    onDismiss = { showPlaylistBottomSheet = false },
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }
}

@Composable
fun SearchResultSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
fun SearchHistoryList(
    historyItems: ImmutableList<SearchHistoryItem>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onClearAllHistory: () -> Unit
) {
    val localDensity = LocalDensity.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.recent_searches),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (historyItems.isNotEmpty()) {
                TextButton(onClick = onClearAllHistory) {
                    Text(stringResource(R.string.clear_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = 8.dp,
            )
        ) {
            items(historyItems, key = { "history_${it.id ?: it.query}" }, contentType = { "search_history" }) { item ->
                SearchHistoryListItem(
                    item = item,
                    onHistoryClick = onHistoryClick,
                    onHistoryDelete = onHistoryDelete
                )
            }
        }
    }
}

@Composable
fun SearchHistoryListItem(
    item: SearchHistoryItem,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { onHistoryClick(item.query) }) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = stringResource(R.string.cd_search_history_icon),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.query,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { onHistoryDelete(item.query) }) {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = stringResource(R.string.cd_delete_search_history_item),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
fun EmptySearchResults(searchQuery: String, colorScheme: ColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.cd_no_search_results),
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
            tint = colorScheme.primary.copy(alpha = 0.6f)
        )

        Text(
            text = if (searchQuery.isNotBlank()) {
                stringResource(R.string.search_no_results_for_query, searchQuery)
            } else {
                stringResource(R.string.search_nothing_found)
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.search_try_different_or_filters),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchResultsList(
    results: ImmutableList<SearchResultItem>,
    searchQuery: String,
    playerViewModel: PlayerViewModel,
    onItemSelected: () -> Unit,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongMoreOptionsClick: (Song) -> Unit,
    navController: NavHostController
) {
    val localDensity = LocalDensity.current
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.search_no_results_found), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val groupedResults = remember(results) {
        results.groupBy { item ->
            when (item) {
                is SearchResultItem.SongItem -> SearchFilterType.SONGS
                is SearchResultItem.AlbumItem -> SearchFilterType.ALBUMS
                is SearchResultItem.ArtistItem -> SearchFilterType.ARTISTS
                is SearchResultItem.PlaylistItem -> SearchFilterType.PLAYLISTS
            }
        }
    }
    val songResultsQueue = remember(groupedResults) {
        buildList {
            groupedResults[SearchFilterType.SONGS]
                ?.forEach { item ->
                    val song = (item as? SearchResultItem.SongItem)?.song ?: return@forEach
                    add(song)
                }
        }
    }
    val searchQueueName = remember(searchQuery) {
        searchQuery.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { "Search: $it" }
            ?: "Search Results"
    }
    val onSongResultClick = remember(playerViewModel, onItemSelected, songResultsQueue, searchQueueName) {
        { song: Song ->
            val playbackQueue = if (songResultsQueue.any { it.id == song.id }) {
                songResultsQueue
            } else {
                listOf(song)
            }
            playerViewModel.showAndPlaySong(song, playbackQueue, searchQueueName)
            onItemSelected()
        }
    }

    val sectionOrder = listOf(
        SearchFilterType.SONGS,
        SearchFilterType.ALBUMS,
        SearchFilterType.ARTISTS,
        SearchFilterType.PLAYLISTS
    )

    val imePadding = WindowInsets.ime.getBottom(localDensity).dp
    val systemBarPaddingBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .clip(
                RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp
                )
            ),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = if (imePadding <= 8.dp) (MiniPlayerHeight + systemBarPaddingBottom) else imePadding
        )
    ) {
        sectionOrder.forEach { filterType ->
            val itemsForSection = groupedResults[filterType] ?: emptyList()

            if (itemsForSection.isNotEmpty()) {
                item(key = "header_${filterType.name}") {
                    SearchResultSectionHeader(
                        title = when (filterType) {
                            SearchFilterType.SONGS -> "Songs"
                            SearchFilterType.ALBUMS -> "Albums"
                            SearchFilterType.ARTISTS -> "Artists"
                            SearchFilterType.PLAYLISTS -> "Playlists"
                            else -> "Results"
                        }
                    )
                }

                items(
                    count = itemsForSection.size,
                    key = { index ->
                        val item = itemsForSection[index]
                        when (item) {
                            is SearchResultItem.SongItem -> "song_${item.song.id}"
                            is SearchResultItem.AlbumItem -> "album_${item.album.id}"
                            is SearchResultItem.ArtistItem -> "artist_${item.artist.id}"
                            is SearchResultItem.PlaylistItem -> "playlist_${item.playlist.id}_${index}"
                        }
                    },
                    contentType = { index ->
                        when (itemsForSection[index]) {
                            is SearchResultItem.SongItem -> "search_song"
                            is SearchResultItem.AlbumItem -> "search_album"
                            is SearchResultItem.ArtistItem -> "search_artist"
                            is SearchResultItem.PlaylistItem -> "search_playlist"
                        }
                    }
                ) { index ->
                    val item = itemsForSection[index]
                    Box(modifier = Modifier.padding(bottom = 12.dp)) {
                        when (item) {
                            is SearchResultItem.SongItem -> {
                                EnhancedSongListItem(
                                    song = item.song,
                                    isPlaying = isPlaying,
                                    isCurrentSong = currentPlayingSongId == item.song.id,
                                    onMoreOptionsClick = onSongMoreOptionsClick,
                                    onClick = { onSongResultClick(item.song) }
                                )
                            }

                            is SearchResultItem.AlbumItem -> {
                                val onPlayClick = remember(item.album, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Album clicked: ${item.album.title}")
                                        playerViewModel.playAlbum(item.album)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.album,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafelyReplacing(
                                            route = Screen.AlbumDetail.createRoute(item.album.id),
                                            patternToPop = Screen.AlbumDetail.route
                                        )
                                        onItemSelected()
                                    }
                                }
                                SearchResultAlbumItem(
                                    album = item.album,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }

                            is SearchResultItem.ArtistItem -> {
                                val onPlayClick = remember(item.artist, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Artist clicked: ${item.artist.name}")
                                        playerViewModel.playArtist(item.artist)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.artist,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafelyReplacing(
                                            route = Screen.ArtistDetail.createRoute(item.artist.id),
                                            patternToPop = Screen.ArtistDetail.route
                                        )
                                        onItemSelected()
                                    }
                                }
                                SearchResultArtistItem(
                                    artist = item.artist,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }

                            is SearchResultItem.PlaylistItem -> {
                                val playlistSongs by remember(item.playlist.songIds, playerViewModel) {
                                    playerViewModel.observeSongs(item.playlist.songIds)
                                        .map { it.toImmutableList() }
                                }.collectAsStateWithLifecycle(initialValue = persistentListOf())
                                val coroutineScope = rememberCoroutineScope()
                                val onPlayClick: () -> Unit = {
                                    coroutineScope.launch {
                                        val songs = playerViewModel.getSongs(item.playlist.songIds)
                                        if (songs.isNotEmpty()) {
                                            playerViewModel.playSongs(
                                                songs,
                                                songs.first(),
                                                item.playlist.name
                                            )
                                            if (playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                                        } else {
                                            playerViewModel.sendToast("Empty playlist")
                                        }
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember(
                                    item.playlist,
                                    playerViewModel, onItemSelected
                                ) {
                                    {
                                        navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.playlist.id))
                                        onItemSelected()
                                    }
                                }
                                SearchResultPlaylistItem(
                                    playlist = item.playlist,
                                    playlistSongs = playlistSongs,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultAlbumItem(
    album: Album,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartImage(
                model = album.albumArtUriString,
                contentDescription = stringResource(R.string.cd_album_art_of, album.title),
                targetSize = SmartImageListTargetSize,
                modifier = Modifier
                    .size(56.dp)
                    .clip(itemShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_play_album), modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultArtistItem(
    artist: Artist,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!artist.effectiveImageUrl.isNullOrBlank()) {
                SmartImage(
                    model = artist.effectiveImageUrl,
                    contentDescription = stringResource(R.string.cd_artist_format, artist.name),
                    targetSize = SmartImageListTargetSize,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(ShapeCache.expressiveAvatar)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_artist_24),
                    contentDescription = stringResource(R.string.cd_artist_icon),
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, ShapeCache.expressiveAvatar)
                        .padding(12.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSongCount(artist.songCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_play_artist), modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultPlaylistItem(
    playlist: Playlist,
    playlistSongs: ImmutableList<Song>,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistCover(
                playlist = playlist,
                playlistSongs = playlistSongs,
                size = 56.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSongCount(playlist.songIds.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_play_playlist), modifier = Modifier.size(24.dp))
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchFilterChip(
    filterType: SearchFilterType,
    currentFilter: SearchFilterType,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val selected = filterType == currentFilter

    ToggleButton(
        checked = selected,
        onCheckedChange = { playerViewModel.updateSearchFilter(filterType) },
        modifier = modifier,
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.rounded_check_circle_24),
                contentDescription = stringResource(R.string.presentation_batch_g_cd_selected),
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(filterType.name.lowercase().replaceFirstChar { it.titlecase() })
    }
}

@Composable
internal fun OnlineSearchSection(
    state: com.minoppol.music.presentation.viewmodel.LxOnlineSearchUiState,
    suggestions: List<String>,
    hotItems: List<com.minoppol.music.presentation.viewmodel.LxOnlineHotItem>,
    hotLoading: Boolean,
    history: List<SearchHistoryItem>,
    query: String,
    inputFocused: Boolean,
    onSubmitQuery: (String) -> Unit,
    onDeleteHistoryItem: (SearchHistoryItem) -> Unit,
    onClearHistory: () -> Unit,
    onSetTab: (com.minoppol.music.presentation.viewmodel.LxOnlineSearchTab) -> Unit,
    onOpenPlaylist: (com.minoppol.music.presentation.viewmodel.LxOnlinePlaylistHit) -> Unit,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    isSelectionMode: Boolean,
    selectedSongIds: Set<String>,
    onSongLongPress: (LxSong) -> Unit,
    onSongSelectionToggle: (LxSong) -> Unit,
    getSelectionIndex: (String) -> Int?,
    onSongMoreOptions: (LxSong) -> Unit,
    onSelectionOptionsClick: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselect: () -> Unit,
    playLxSong: (LxSong) -> Unit,
    playPageSong: (LxSong, List<LxSong>, String) -> Unit,
    playAll: (List<LxSong>, String) -> Unit,
    shuffleAll: (List<LxSong>, String) -> Unit,
    sortOption: SortOption,
    onSortClick: () -> Unit,
    onLoadMoreSongs: () -> Unit,
    onLoadMorePlaylists: () -> Unit,
    playerViewModel: PlayerViewModel,
    bottomBarHeightDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (query.isBlank()) {
            OnlineSearchLanding(
                history = history,
                hotItems = hotItems,
                hotLoading = hotLoading,
                bottomBarHeightDp = bottomBarHeightDp,
                onSubmitQuery = onSubmitQuery,
                onDeleteHistoryItem = onDeleteHistoryItem,
                onClearHistory = onClearHistory,
            )
        } else {
            OnlineSearchResults(
                state = state,
                suggestions = suggestions,
                query = query,
                inputFocused = inputFocused,
                onSubmitQuery = onSubmitQuery,
                onSetTab = onSetTab,
                onOpenPlaylist = onOpenPlaylist,
                currentPlayingSongId = currentPlayingSongId,
                isPlaying = isPlaying,
                isSelectionMode = isSelectionMode,
                selectedSongIds = selectedSongIds,
                onSongLongPress = onSongLongPress,
                onSongSelectionToggle = onSongSelectionToggle,
                getSelectionIndex = getSelectionIndex,
                onSongMoreOptions = onSongMoreOptions,
                onSelectionOptionsClick = onSelectionOptionsClick,
                onSelectAll = onSelectAll,
                onDeselect = onDeselect,
                playLxSong = playLxSong,
                playPageSong = playPageSong,
                playAll = playAll,
                shuffleAll = shuffleAll,
                sortOption = sortOption,
                onSortClick = onSortClick,
                onLoadMoreSongs = onLoadMoreSongs,
                onLoadMorePlaylists = onLoadMorePlaylists,
                bottomBarHeightDp = bottomBarHeightDp,
                playerViewModel = playerViewModel,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnlineSearchLanding(
    history: List<SearchHistoryItem>,
    hotItems: List<com.minoppol.music.presentation.viewmodel.LxOnlineHotItem>,
    hotLoading: Boolean,
    bottomBarHeightDp: androidx.compose.ui.unit.Dp,
    onSubmitQuery: (String) -> Unit,
    onDeleteHistoryItem: (SearchHistoryItem) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (history.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.search_online_history_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClearHistory) {
                Text(stringResource(R.string.search_online_clear_history))
            }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            history.forEach { item ->
                Text(
                    text = item.query,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                        .pointerInput(item.query) {
                            detectTapGestures(
                                onTap = { onSubmitQuery(item.query) },
                                onLongPress = { onDeleteHistoryItem(item) },
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 4.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.search_online_hot_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (hotLoading) {
            androidx.compose.material3.LoadingIndicator(
                modifier = Modifier.size(16.dp)
            )
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = MiniPlayerHeight + bottomBarHeightDp + 16.dp)
    ) {
        if (hotItems.isEmpty() && !hotLoading) {
            item(key = "hot_empty") {
                LxEmptyState(
                    title = stringResource(R.string.lx_empty_load_failed_title),
                    subtitle = stringResource(R.string.lx_error_board_load_failed),
                    modifier = Modifier.padding(top = 48.dp, bottom = 24.dp),
                )
            }
        }
        items(hotItems, key = { "${it.source}_${it.keyword}" }) { hot ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSubmitQuery(hot.keyword) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = LxSources.shortLabel(hot.source),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = LxSources.onBrandColor(hot.source),
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(LxSources.brandColor(hot.source))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = hot.keyword,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnlineSearchResults(
    state: com.minoppol.music.presentation.viewmodel.LxOnlineSearchUiState,
    suggestions: List<String>,
    query: String,
    inputFocused: Boolean,
    onSubmitQuery: (String) -> Unit,
    onSetTab: (com.minoppol.music.presentation.viewmodel.LxOnlineSearchTab) -> Unit,
    onOpenPlaylist: (com.minoppol.music.presentation.viewmodel.LxOnlinePlaylistHit) -> Unit,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    isSelectionMode: Boolean,
    selectedSongIds: Set<String>,
    onSongLongPress: (LxSong) -> Unit,
    onSongSelectionToggle: (LxSong) -> Unit,
    getSelectionIndex: (String) -> Int?,
    onSongMoreOptions: (LxSong) -> Unit,
    onSelectionOptionsClick: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselect: () -> Unit,
    playLxSong: (LxSong) -> Unit,
    playPageSong: (LxSong, List<LxSong>, String) -> Unit,
    playAll: (List<LxSong>, String) -> Unit,
    shuffleAll: (List<LxSong>, String) -> Unit,
    sortOption: SortOption,
    onSortClick: () -> Unit,
    onLoadMoreSongs: () -> Unit,
    onLoadMorePlaylists: () -> Unit,
    bottomBarHeightDp: androidx.compose.ui.unit.Dp,
    playerViewModel: PlayerViewModel,
) {
    val submitted = state.submittedQuery.isNotBlank()
    if (inputFocused && query.isNotBlank()) {
        if (suggestions.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                items(suggestions, key = { it }) { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSubmitQuery(suggestion) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_online_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }
    if (!submitted) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.search_online_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        return
    }
    AnimatedContent(
        targetState = isSelectionMode,
        transitionSpec = {
            (slideInHorizontally { -it } + fadeIn()) togetherWith
                (slideOutHorizontally { it } + fadeOut())
        },
        label = "onlineSelectionSwitch"
    ) { selectionMode ->
        if (selectionMode) {
            SelectionActionRow(
                selectedCount = selectedSongIds.size,
                onSelectAll = onSelectAll,
                onDeselect = onDeselect,
                onOptionsClick = onSelectionOptionsClick,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                trailingContentBeforeOptions = {
                    SelectionCountPill(selectedCount = selectedSongIds.size)
                }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { onSetTab(LxOnlineSearchTab.SONGS) },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (state.tab == LxOnlineSearchTab.SONGS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (state.tab == LxOnlineSearchTab.SONGS) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 6.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.search_online_tab_songs),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                FilledTonalButton(
                    onClick = { onSetTab(LxOnlineSearchTab.PLAYLISTS) },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (state.tab == LxOnlineSearchTab.PLAYLISTS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (state.tab == LxOnlineSearchTab.PLAYLISTS) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 6.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.search_online_tab_playlists),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    Crossfade(
        targetState = state.tab,
        animationSpec = tween(durationMillis = 190),
        label = "onlineSearchTab"
    ) { tab ->
        Box(modifier = Modifier.fillMaxWidth()) {
        when (tab) {
            com.minoppol.music.presentation.viewmodel.LxOnlineSearchTab.SONGS -> {
                if (state.searching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.ContainedLoadingIndicator()
                    }
                } else if (state.songs.isEmpty()) {
                    EmptySearchResults(searchQuery = state.submittedQuery, colorScheme = MaterialTheme.colorScheme)
                } else {
                    val songsListState = rememberLazyListState()
                    val listBottomCornerRadius = rememberListBottomCornerRadius(playerViewModel)
                    val sortedSongs = remember(state.songs, sortOption) { sortLxSongs(state.songs, sortOption) }
                    val showShuffleFab by playerViewModel.lxBoardShowShuffleFab.collectAsStateWithLifecycle()
                    val showPlayAllFab by playerViewModel.lxBoardShowPlayAllFab.collectAsStateWithLifecycle()
                    val showSortFab by playerViewModel.lxDiscoverShowSortFab.collectAsStateWithLifecycle()
                    val songsFabSongIds = remember(sortedSongs) {
                        sortedSongs.map { LxSongMapper.unifiedSongId(it.source, it.songmid).toString() }
                    }
                    LaunchedEffect(songsListState, state.songs.size, state.songsHasMore, state.songsLoadingMore) {
                        snapshotFlow {
                            val layoutInfo = songsListState.layoutInfo
                            (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layoutInfo.totalItemsCount - 4
                        }.distinctUntilChanged().collect { nearEnd ->
                            if (nearEnd && state.songsHasMore && !state.songsLoadingMore) onLoadMoreSongs()
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AbsoluteSmoothCornerShape(
                                cornerRadiusTL = 34.dp,
                                smoothnessAsPercentTL = 60,
                                cornerRadiusTR = 34.dp,
                                smoothnessAsPercentTR = 60,
                                cornerRadiusBL = listBottomCornerRadius,
                                smoothnessAsPercentBL = 60,
                                cornerRadiusBR = listBottomCornerRadius,
                                smoothnessAsPercentBR = 60
                            )),
                        state = songsListState,
                        contentPadding = PaddingValues(start = 8.dp, end = 20.dp, bottom = MiniPlayerHeight + bottomBarHeightDp + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sortedSongs, key = { "${it.source}_${it.songmid}" }) { song ->
                            val uiSong = remember(song.source, song.songmid, song.pic) { LxSongMapper.toSong(song) }
                            val isCurrent = currentPlayingSongId == uiSong.id
                            val isSelected = selectedSongIds.contains(uiSong.id)
                            EnhancedSongListItem(
                                song = uiSong,
                                isPlaying = isCurrent && isPlaying,
                                isCurrentSong = isCurrent,
                                showAlbumArt = true,
                                showSourceTag = true,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                selectionIndex = getSelectionIndex(uiSong.id),
                                onLongPress = { onSongLongPress(song) },
                                onMoreOptionsClick = { onSongMoreOptions(song) },
                                onClick = {
                                    if (isSelectionMode) {
                                        onSongSelectionToggle(song)
                                    } else {
                                        playPageSong(song, sortedSongs, "Search: ${state.submittedQuery}")
                                    }
                                }
                            )
                        }
                        if (state.songsLoadingMore) {
                            item(key = "online_songs_loading_more") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.LoadingIndicator(
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        item(key = "online_count_footer") {
                            Text(
                                text = stringResource(R.string.lx_text_total_songs_count, state.songs.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    ExpressiveScrollBar(
                        listState = songsListState,
                        paddingEnd = 0.dp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = 8.dp, bottom = MiniPlayerHeight + bottomBarHeightDp + 16.dp)
                    )
                    LxLocateCurrentSongFab(
                        listState = songsListState,
                        currentSongId = currentPlayingSongId,
                        songIds = songsFabSongIds,
                        leadingItems = 0,
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = MiniPlayerHeight + bottomBarHeightDp + ListExtraBottomGap + 74.dp),
                    )
                    LxScrollToTopFab(
                        listState = songsListState,
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = MiniPlayerHeight + bottomBarHeightDp + ListExtraBottomGap + 14.dp),
                    )
                    if (!isSelectionMode && (showShuffleFab || showPlayAllFab || showSortFab)) {
                        LxBottomPlayActions(
                            showShuffle = showShuffleFab,
                            showPlayAll = showPlayAllFab,
                            showSort = showSortFab,
                            onSort = onSortClick,
                            onShuffle = { shuffleAll(sortedSongs, "Search: ${state.submittedQuery}") },
                            onPlayAll = { playAll(sortedSongs, "Search: ${state.submittedQuery}") },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 20.dp, bottom = MiniPlayerHeight + bottomBarHeightDp + ListExtraBottomGap + 14.dp),
                        )
                    }
                    }
                }
            }
            com.minoppol.music.presentation.viewmodel.LxOnlineSearchTab.PLAYLISTS -> {
                        if (state.playlistsLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.ContainedLoadingIndicator()
                    }
                } else if (state.playlists.isEmpty()) {
                    EmptySearchResults(searchQuery = state.submittedQuery, colorScheme = MaterialTheme.colorScheme)
                } else {
                    val playlistsListState = rememberLazyListState()
                    val listBottomCornerRadius = rememberListBottomCornerRadius(playerViewModel)
                    LaunchedEffect(playlistsListState, state.playlists.size, state.playlistsHasMore, state.playlistsLoadingMore) {
                        snapshotFlow {
                            val layoutInfo = playlistsListState.layoutInfo
                            (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layoutInfo.totalItemsCount - 4
                        }.distinctUntilChanged().collect { nearEnd ->
                            if (nearEnd && state.playlistsHasMore && !state.playlistsLoadingMore) onLoadMorePlaylists()
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AbsoluteSmoothCornerShape(
                                cornerRadiusTL = 34.dp,
                                smoothnessAsPercentTL = 60,
                                cornerRadiusTR = 34.dp,
                                smoothnessAsPercentTR = 60,
                                cornerRadiusBL = listBottomCornerRadius,
                                smoothnessAsPercentBL = 60,
                                cornerRadiusBR = listBottomCornerRadius,
                                smoothnessAsPercentBR = 60
                            )),
                        state = playlistsListState,
                        contentPadding = PaddingValues(start = 8.dp, end = 20.dp, bottom = MiniPlayerHeight + bottomBarHeightDp + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.playlists, key = { "${it.source}_${it.playlist.id}" }) { hit ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenPlaylist(hit) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(50.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = hit.playlist.pic,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hit.playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitle = listOfNotNull(
                                        hit.playlist.creator?.takeIf { it.isNotBlank() },
                                        hit.playlist.trackCount?.let { "${it}+ Tracks" },
                                    ).joinToString(" \u00b7 ")
                                    if (subtitle.isNotBlank()) {
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = LxSources.shortLabel(hit.source),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = LxSources.onBrandColor(hit.source),
                                    maxLines = 1,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(LxSources.brandColor(hit.source))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (state.playlistsLoadingMore) {
                            item(key = "online_playlists_loading_more") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.LoadingIndicator(
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    ExpressiveScrollBar(
                        listState = playlistsListState,
                        paddingEnd = 0.dp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = 8.dp, bottom = MiniPlayerHeight + bottomBarHeightDp + 16.dp)
                    )
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OnlinePlaylistDetailPage(
    detail: com.minoppol.music.presentation.viewmodel.LxOnlinePlaylistDetail,
    onBack: () -> Unit,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    isSelectionMode: Boolean,
    selectedSongIds: Set<String>,
    onSongLongPress: (LxSong) -> Unit,
    onSongSelectionToggle: (LxSong) -> Unit,
    getSelectionIndex: (String) -> Int?,
    onSongMoreOptions: (LxSong) -> Unit,
    playLxSong: (LxSong) -> Unit,
    playPageSong: (LxSong, List<LxSong>, String) -> Unit,
    playAll: (List<LxSong>, String) -> Unit,
    shuffleAll: (List<LxSong>, String) -> Unit,
    sortOption: SortOption,
    onSortClick: () -> Unit,
    bottomBarHeightDp: androidx.compose.ui.unit.Dp,
    lxViewModel: com.minoppol.music.presentation.screens.lxmusic.LxMusicViewModel,
    playerViewModel: PlayerViewModel,
) {
    val hm = rememberLxHeaderMetrics()
    val statusBarTopInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    var headerHeight by remember { mutableStateOf(hm.searchBarHeight + 8.dp + statusBarTopInset) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val detailListState = rememberLazyListState()
    val listBottomCornerRadius = rememberListBottomCornerRadius(playerViewModel)
    val collectState = if (detail.source != LxSources.NETEASE && !lxViewModel.accountWritesEnabled) null
        else rememberCollectState(lxViewModel, com.minoppol.music.presentation.screens.lxmusic.LxMusicViewModel.CollectKind.PLAYLIST, detail.playlist.id, gid = detail.playlist.originGid, name = detail.playlist.name, source = detail.source)
    val collectBusyKeys by lxViewModel.collectBusyKeys.collectAsStateWithLifecycle()
    val collectBusy = collectBusyKeys.contains(lxViewModel.collectKey(com.minoppol.music.presentation.screens.lxmusic.LxMusicViewModel.CollectKind.PLAYLIST, detail.playlist.id))
    val onFolderShare = rememberFolderShare(lxViewModel)
    val sortedSongs = remember(detail.songs, sortOption) { sortLxSongs(detail.songs, sortOption) }
    val showShuffleFab by playerViewModel.lxBoardShowShuffleFab.collectAsStateWithLifecycle()
    val showPlayAllFab by playerViewModel.lxBoardShowPlayAllFab.collectAsStateWithLifecycle()
    val showSortFab by playerViewModel.lxDiscoverShowSortFab.collectAsStateWithLifecycle()
    val detailFabSongIds = remember(sortedSongs) {
        sortedSongs.map { LxSongMapper.unifiedSongId(it.source, it.songmid).toString() }
    }
    val context = LocalContext.current
    val playState = when {
        currentPlayingSongId != null && detailFabSongIds.contains(currentPlayingSongId) && isPlaying -> HeroPlayState.Playing
        currentPlayingSongId != null && detailFabSongIds.contains(currentPlayingSongId) -> HeroPlayState.Paused
        else -> HeroPlayState.Idle
    }
    val playNextLabel = stringResource(R.string.cd_play_next_in_queue)
    val addToQueueLabel = stringResource(R.string.action_add_to_queue)
    val heroMenuActions = remember(sortedSongs, playNextLabel, addToQueueLabel) {
        listOf(
            OnlineHeroMenuAction(playNextLabel, Icons.Rounded.SkipNext) {
                val uiSongs = sortedSongs.map { LxSongMapper.toSong(it) }
                uiSongs.asReversed().forEach { playerViewModel.addSongNextToQueue(it) }
                val count = uiSongs.size
                android.widget.Toast.makeText(
                    context,
                    context.resources.getQuantityString(R.plurals.n_songs_will_play_next, count, count),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            OnlineHeroMenuAction(addToQueueLabel, Icons.Rounded.QueueMusic) {
                val uiSongs = sortedSongs.map { LxSongMapper.toSong(it) }
                uiSongs.forEach { playerViewModel.addSongToQueue(it) }
                val count = uiSongs.size
                android.widget.Toast.makeText(
                    context,
                    context.resources.getQuantityString(R.plurals.n_songs_added_to_queue, count, count),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (detail.loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.ContainedLoadingIndicator()
            }
        } else if (detail.songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_online_hot_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = headerHeight, bottom = 6.dp)
                    .clip(AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 34.dp,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusTR = 34.dp,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBL = listBottomCornerRadius,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBR = listBottomCornerRadius,
                        smoothnessAsPercentBR = 60
                    )),
                state = detailListState,
                contentPadding = PaddingValues(start = 8.dp, end = 20.dp, bottom = MiniPlayerHeight + bottomBarHeightDp + 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "playlist_header_info") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = detail.playlist.pic,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            var nameExpanded by remember(detail.playlist.id) { mutableStateOf(false) }
                            var nameOverflow by remember(detail.playlist.id) { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { nameExpanded = !nameExpanded },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = detail.playlist.name,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = RoundedSans,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = if (nameExpanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    onTextLayout = { if (!nameExpanded) nameOverflow = it.hasVisualOverflow },
                                    modifier = Modifier.weight(1f)
                                )
                                if (nameOverflow) {
                                    Icon(
                                        imageVector = if (nameExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            val headerSubtitle = listOfNotNull(
                                detail.playlist.creator?.takeIf { it.isNotBlank() },
                                detail.playlist.trackCount?.let { formatSongCount(it) }
                            ).joinToString(" \u00b7 ")
                            if (headerSubtitle.isNotBlank()) {
                                Text(
                                    text = headerSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                item(key = "play_controls") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OnlineHeroActionGroup(
                            onShuffle = { shuffleAll(sortedSongs, detail.playlist.name) },
                            onPlay = {
                                if (playState == HeroPlayState.Playing) {
                                    playerViewModel.playPause()
                                } else {
                                    playAll(sortedSongs, detail.playlist.name)
                                }
                            },
                            playState = playState,
                            menuActions = heroMenuActions,
                            enabled = sortedSongs.isNotEmpty()
                        )
                    }
                }
                items(sortedSongs, key = { "${it.source}_${it.songmid}" }) { song ->
                    val uiSong = remember(song.source, song.songmid, song.pic) { LxSongMapper.toSong(song) }
                    val isCurrent = currentPlayingSongId == uiSong.id
                    val isSelected = selectedSongIds.contains(uiSong.id)
                    EnhancedSongListItem(
                        song = uiSong,
                        isPlaying = isCurrent && isPlaying,
                        isCurrentSong = isCurrent,
                        showAlbumArt = true,
                        showSourceTag = true,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        selectionIndex = getSelectionIndex(uiSong.id),
                        onLongPress = { onSongLongPress(song) },
                        onMoreOptionsClick = { onSongMoreOptions(song) },
                        onClick = {
                            if (isSelectionMode) {
                                onSongSelectionToggle(song)
                            } else {
                                playPageSong(song, sortedSongs, detail.playlist.name)
                            }
                        }
                    )
                }
                item(key = "online_playlist_count_footer") {
                    Text(
                        text = stringResource(R.string.lx_text_total_songs_count, sortedSongs.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            ExpressiveScrollBar(
                listState = detailListState,
                paddingEnd = 0.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = headerHeight + 8.dp, bottom = MiniPlayerHeight + bottomBarHeightDp + 16.dp)
            )
            LxLocateCurrentSongFab(
                listState = detailListState,
                currentSongId = currentPlayingSongId,
                songIds = detailFabSongIds,
                leadingItems = 2,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = MiniPlayerHeight + bottomBarHeightDp + ListExtraBottomGap + 74.dp),
            )
            LxScrollToTopFab(
                listState = detailListState,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = MiniPlayerHeight + bottomBarHeightDp + ListExtraBottomGap + 14.dp),
            )
            if (!isSelectionMode && sortedSongs.isNotEmpty() && (showShuffleFab || showPlayAllFab || showSortFab)) {
                LxBottomPlayActions(
                    showShuffle = showShuffleFab,
                    showPlayAll = showPlayAllFab,
                    showSort = showSortFab,
                    onSort = onSortClick,
                    onShuffle = { shuffleAll(sortedSongs, detail.playlist.name) },
                    onPlayAll = { playAll(sortedSongs, detail.playlist.name) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, bottom = MiniPlayerHeight + bottomBarHeightDp + ListExtraBottomGap + 14.dp),
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onGloballyPositioned { headerHeight = with(density) { it.size.height.toDp() } }
                .padding(top = statusBarTopInset),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.lx_text_playlist_list),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                LxFolderActionCapsule(
                    showLocate = false,
                    onLocate = {},
                    collectState = collectState,
                    collectBusy = collectBusy,
                    onCollectClick = {
                        detail.playlist.id.let {
                            lxViewModel.onCollectButtonClick(
                                com.minoppol.music.presentation.screens.lxmusic.LxMusicViewModel.CollectKind.PLAYLIST,
                                it,
                                gid = detail.playlist.originGid,
                                name = detail.playlist.name,
                                source = detail.source
                            )
                        }
                    },
                    onShare = { onFolderShare(com.minoppol.music.presentation.screens.lxmusic.LxMusicViewModel.CollectKind.PLAYLIST, detail.playlist.id) }
                )
            }
        }
    }
}

private enum class HeroPlayState { Idle, Playing, Paused }

private data class OnlineHeroMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

private val OnlineHeroSegmentHeight = 60.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnlineHeroActionGroup(
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    playState: HeroPlayState,
    menuActions: List<OnlineHeroMenuAction>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motionScheme = remember { MotionScheme.expressive() }
    var showMenu by remember { mutableStateOf(false) }
    val shuffleInteraction = remember { MutableInteractionSource() }
    val playInteraction = remember { MutableInteractionSource() }
    val addInteraction = remember { MutableInteractionSource() }
    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        modifier = modifier
            .fillMaxWidth(0.86f)
            .widthIn(max = 380.dp)
            .height(OnlineHeroSegmentHeight),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        customItem(
            buttonGroupContent = {
                OnlineHeroSegment(
                    onClick = onShuffle,
                    enabled = enabled,
                    position = OnlineSegmentPosition.Leading,
                    container = colors.surfaceContainerHighest,
                    content = colors.onSurface,
                    interactionSource = shuffleInteraction,
                    motionScheme = motionScheme,
                    contentDescription = null,
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(shuffleInteraction)
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                }
            },
            menuContent = {}
        )
        customItem(
            buttonGroupContent = {
                val isActive = playState == HeroPlayState.Playing
                val container by animateColorAsState(
                    targetValue = if (isActive) colors.primary else colors.inverseSurface,
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    label = "onlineHeroPlayContainer"
                )
                val content by animateColorAsState(
                    targetValue = if (isActive) colors.onPrimary else colors.inverseOnSurface,
                    animationSpec = motionScheme.defaultEffectsSpec(),
                    label = "onlineHeroPlayContent"
                )
                OnlineHeroSegment(
                    onClick = onPlay,
                    enabled = enabled,
                    position = OnlineSegmentPosition.Middle,
                    container = container,
                    content = content,
                    interactionSource = playInteraction,
                    motionScheme = motionScheme,
                    restingInnerCorner = if (isActive) OnlineHeroSegmentHeight / 2 else 8.dp,
                    contentDescription = null,
                    modifier = Modifier
                        .weight(2f)
                        .animateWidth(playInteraction)
                ) {
                    AnimatedContent(
                        targetState = playState,
                        contentAlignment = Alignment.Center,
                        transitionSpec = {
                            ((fadeIn(motionScheme.fastEffectsSpec()) +
                                scaleIn(motionScheme.fastSpatialSpec(), initialScale = 0.7f)) togetherWith
                                (fadeOut(motionScheme.fastEffectsSpec()) +
                                    scaleOut(motionScheme.fastSpatialSpec(), targetScale = 0.7f)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "onlineHeroPlayLabel"
                    ) { state ->
                        val isPause = state == HeroPlayState.Playing
                        OnlineHeroPlayLabel(
                            icon = if (isPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            glyphInsets = if (isPause) OnlinePauseGlyphInsets else OnlinePlayGlyphInsets,
                            text = stringResource(
                                when (state) {
                                    HeroPlayState.Idle -> R.string.play_playback
                                    HeroPlayState.Playing -> R.string.cd_pause
                                    HeroPlayState.Paused -> R.string.play_playback
                                }
                            )
                        )
                    }
                }
            },
            menuContent = {}
        )
        customItem(
            buttonGroupContent = {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(addInteraction)
                ) {
                    OnlineHeroSegment(
                        onClick = { showMenu = true },
                        enabled = enabled && menuActions.isNotEmpty(),
                        position = OnlineSegmentPosition.Trailing,
                        container = colors.surfaceContainerHighest,
                        content = colors.onSurface,
                        interactionSource = addInteraction,
                        motionScheme = motionScheme,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val rotation by animateFloatAsState(
                            targetValue = if (showMenu) 45f else 0f,
                            animationSpec = motionScheme.fastSpatialSpec(),
                            label = "onlineHeroAddRotation"
                        )
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.graphicsLayer { rotationZ = rotation }
                        )
                    }
                    DropdownMenuPopup(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(
                            MenuAnchorPosition.Custom(
                                xCandidates = {
                                    androidx.collection.mutableIntListOf(
                                        anchorBounds.right - menuSize.width,
                                        anchorBounds.left
                                    )
                                },
                                yCandidates = {
                                    androidx.collection.mutableIntListOf(
                                        anchorBounds.bottom,
                                        anchorBounds.top - menuSize.height
                                    )
                                }
                            )
                        )
                    ) {
                        DropdownMenuGroup(
                            shapes = MenuDefaults.groupShape(index = 0, count = 1),
                            containerColor = MenuDefaults.groupVibrantContainerColor
                        ) {
                            menuActions.forEachIndexed { index, action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                                    shape = MenuDefaults.itemShape(index = index, count = menuActions.size).shape,
                                    colors = MenuDefaults.itemColors(),
                                    onClick = {
                                        showMenu = false
                                        action.onClick()
                                    }
                                )
                            }
                        }
                    }
                }
            },
            menuContent = {}
        )
    }
}

private class OnlineGlyphInsets(val start: Float, val end: Float)

private val OnlinePlayGlyphInsets = OnlineGlyphInsets(start = 8f, end = 5.9f)

private val OnlinePauseGlyphInsets = OnlineGlyphInsets(start = 6f, end = 6f)

private const val OnlineIconViewport = 24f

private const val OnlineCapHeightRatio = 0.7f

@Composable
private fun OnlineHeroPlayLabel(
    icon: ImageVector,
    glyphInsets: OnlineGlyphInsets,
    text: String,
    iconSize: Dp = 24.dp,
    gap: Dp = 8.dp,
) {
    val style = MaterialTheme.typography.titleMedium
    Layout(
        content = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
            Text(
                text = text,
                style = style,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    ) { measurables, constraints ->
        val iconPlaceable = measurables[0].measure(Constraints())
        val iconSizePx = iconPlaceable.width.toFloat()
        val insetStart = (iconSizePx * glyphInsets.start / OnlineIconViewport).roundToInt()
        val insetEnd = (iconSizePx * glyphInsets.end / OnlineIconViewport).roundToInt()
        val gapPx = gap.roundToPx()
        val visibleIconWidth = iconPlaceable.width - insetStart - insetEnd
        val textPlaceable = measurables[1].measure(
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = (constraints.maxWidth - visibleIconWidth - gapPx).coerceAtLeast(0)
            )
        )
        val baseline = textPlaceable[FirstBaseline].takeIf { it != AlignmentLine.Unspecified }
            ?: textPlaceable.height
        val capCenter = baseline - (style.fontSize.toPx() * OnlineCapHeightRatio / 2f).roundToInt()
        val halfHeight = maxOf(
            iconPlaceable.height / 2,
            capCenter,
            textPlaceable.height - capCenter
        )
        val width = visibleIconWidth + gapPx + textPlaceable.width
        val height = halfHeight * 2
        layout(width, height) {
            iconPlaceable.placeRelative(-insetStart, halfHeight - iconPlaceable.height / 2)
            textPlaceable.placeRelative(visibleIconWidth + gapPx, halfHeight - capCenter)
        }
    }
}

private enum class OnlineSegmentPosition { Leading, Middle, Trailing }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnlineHeroSegment(
    onClick: () -> Unit,
    enabled: Boolean,
    position: OnlineSegmentPosition,
    container: Color,
    content: Color,
    interactionSource: MutableInteractionSource,
    motionScheme: MotionScheme,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    restingInnerCorner: Dp = 8.dp,
    body: @Composable RowScope.() -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val spatial = motionScheme.fastSpatialSpec<Dp>()
    val outer by animateDpAsState(if (pressed) 18.dp else 30.dp, spatial, label = "onlineHeroOuterCorner")
    val inner by animateDpAsState(if (pressed) 18.dp else restingInnerCorner, spatial, label = "onlineHeroInnerCorner")
    val start = if (position == OnlineSegmentPosition.Leading) outer else inner
    val end = if (position == OnlineSegmentPosition.Trailing) outer else inner
    val shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = container,
        contentColor = content,
        interactionSource = interactionSource,
        modifier = modifier
            .height(OnlineHeroSegmentHeight)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = body
        )
    }
}

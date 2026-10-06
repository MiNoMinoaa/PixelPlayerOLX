package com.minoppol.music.presentation.screens.lxmusic

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.minoppol.music.presentation.viewmodel.MultiSelectionStateHolder
import com.minoppol.music.presentation.components.CollapsibleCommonTopBar
import com.minoppol.music.presentation.components.MultiSelectionBottomSheet
import com.minoppol.music.presentation.components.ReorderPlatformsSheet
import com.minoppol.music.presentation.components.subcomps.SelectionActionRow
import com.minoppol.music.presentation.components.subcomps.SelectionCountPill
import com.minoppol.music.data.lxmusic.LxQualities
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.abs
import com.minoppol.music.R
import com.minoppol.music.data.lxmusic.LxScriptMeta
import com.minoppol.music.data.lxmusic.LxSongMapper
import com.minoppol.music.data.lxmusic.LxSources
import com.minoppol.music.presentation.components.MiniPlayerHeight
import com.minoppol.music.presentation.components.ExpressiveScrollBar
import com.minoppol.music.presentation.components.SmartImage
import com.minoppol.music.presentation.components.SmartImageListTargetSize
import com.minoppol.music.presentation.components.resolveNavBarOccupiedHeight
import com.minoppol.music.presentation.components.rememberModalSheetState
import com.minoppol.music.presentation.components.SongInfoBottomSheet
import com.minoppol.music.presentation.components.subcomps.EnhancedSongListItem
import com.minoppol.music.presentation.screens.ListExtraBottomGap
import com.minoppol.music.presentation.screens.MusicIconPattern
import com.minoppol.music.presentation.viewmodel.PlayerViewModel
import com.minoppol.music.ui.theme.LocalPixelPlayerDarkTheme
import com.minoppol.music.ui.theme.PixelPlayerStatusBarStyle
import com.minoppol.music.ui.theme.RoundedSans
import com.minoppol.music.utils.formatSongCount
import com.minoppol.music.utils.shapes.RoundedStarShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private object LxMoodHolder {
    var word: String? = null
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun LxMusicScreen(
    context: android.content.Context = LocalContext.current,
    playerViewModel: PlayerViewModel,
    onSettingsClick: () -> Unit = {},
    viewModel: LxMusicViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
    ),
) {
    val scripts by viewModel.scripts.collectAsStateWithLifecycle()
    val activeScript by viewModel.activeScript.collectAsStateWithLifecycle()
    val runtimePaths by viewModel.runtimePaths.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedSource.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()

    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    LaunchedEffect(stablePlayerState.currentSong?.id) {
        viewModel.onPlaybackSongChanged(stablePlayerState.currentSong?.id)
    }
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val browseState by viewModel.browseState.collectAsStateWithLifecycle()
    val playerSheetState by playerViewModel.sheetState.collectAsStateWithLifecycle()
    val queueSheetVisible by playerViewModel.isQueueSheetVisible.collectAsStateWithLifecycle()

    var showScriptSheet by remember { mutableStateOf(false) }
    var lxInfoSong by remember { mutableStateOf<com.minoppol.music.data.lxmusic.LxSong?>(null) }
    var showLxArtistPicker by remember { mutableStateOf(false) }
    var lxArtistPickerCandidates by remember { mutableStateOf<List<com.minoppol.music.data.lxmusic.LxArtist>>(emptyList()) }
    var lxArtistPickerSource by remember { mutableStateOf("") }
    var showUrlImport by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var urlImporting by remember { mutableStateOf(false) }
    var scriptToDelete by remember { mutableStateOf<LxScriptMeta?>(null) }
    var showPlatformNameDialog by remember { mutableStateOf(false) }
    var showPlatformOrderSheet by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showTxLoginDialog by remember { mutableStateOf(false) }
    var showKgLoginDialog by remember { mutableStateOf(false) }
    var showKwLoginDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val neteaseCookie by viewModel.neteaseCookie.collectAsStateWithLifecycle()
    val neteaseVipType by viewModel.neteaseVipType.collectAsStateWithLifecycle()
    val neteaseCookieValid by viewModel.neteaseCookieValid.collectAsStateWithLifecycle()
    val neteaseLoggedIn by viewModel.neteaseLoggedIn.collectAsStateWithLifecycle()
    val neteaseNickname by viewModel.neteaseNickname.collectAsStateWithLifecycle()
    val neteaseAvatar by viewModel.neteaseAvatar.collectAsStateWithLifecycle()
    val tencentCookie by viewModel.tencentCookie.collectAsStateWithLifecycle()
    val tencentCookieValid by viewModel.tencentCookieValid.collectAsStateWithLifecycle()
    val tencentNickname by viewModel.tencentNickname.collectAsStateWithLifecycle()
    val tencentAvatar by viewModel.tencentAvatar.collectAsStateWithLifecycle()
    val tencentLoggedIn by viewModel.tencentLoggedIn.collectAsStateWithLifecycle()
    val kugouCookie by viewModel.kugouCookie.collectAsStateWithLifecycle()
    val kugouCookieValid by viewModel.kugouCookieValid.collectAsStateWithLifecycle()
    val kugouNickname by viewModel.kugouNickname.collectAsStateWithLifecycle()
    val kugouAvatar by viewModel.kugouAvatar.collectAsStateWithLifecycle()
    val kugouLoggedIn by viewModel.kugouLoggedIn.collectAsStateWithLifecycle()
    val kuwoCookie by viewModel.kuwoCookie.collectAsStateWithLifecycle()
    val kuwoCookieValid by viewModel.kuwoCookieValid.collectAsStateWithLifecycle()
    val kuwoNickname by viewModel.kuwoNickname.collectAsStateWithLifecycle()
    val kuwoAvatar by viewModel.kuwoAvatar.collectAsStateWithLifecycle()
    val kuwoLoggedIn by viewModel.kuwoLoggedIn.collectAsStateWithLifecycle()
    val tencentVipType by viewModel.tencentVipType.collectAsStateWithLifecycle()
    val kugouVipType by viewModel.kugouVipType.collectAsStateWithLifecycle()
    val kuwoVipType by viewModel.kuwoVipType.collectAsStateWithLifecycle()
    val currentSourceLoggedIn by viewModel.currentSourceLoggedIn.collectAsStateWithLifecycle()

    val platformNameStyle by viewModel.platformNameStyle.collectAsStateWithLifecycle()
    val platformOrder by viewModel.platformOrder.collectAsStateWithLifecycle()

    @Composable
    fun platformName(source: String): String {
        val aliasResId = when (source) {
            "wy" -> R.string.lx_source_alias_netease
            "kw" -> R.string.lx_source_alias_kuwo
            "tx" -> R.string.lx_source_alias_qq
            "kg" -> R.string.lx_source_alias_kugou
            else -> null
        }
        val originalResId = when (source) {
            "wy" -> R.string.lx_source_netease
            "kw" -> R.string.lx_source_kuwo
            "tx" -> R.string.lx_source_qq
            "kg" -> R.string.lx_source_kugou
            else -> null
        }
        return if (platformNameStyle == "alias" && aliasResId != null) {
            stringResource(aliasResId)
        } else if (originalResId != null) {
            stringResource(originalResId)
        } else {
            source
        }
    }

    val orderedSearchTabs = remember(platformOrder) {
        val orderMap = platformOrder.withIndex().associate { it.value to it.index }
        searchTabs.sortedBy { (src, _) -> orderMap[src] ?: Int.MAX_VALUE }
    }
    val mineState by viewModel.mineState.collectAsStateWithLifecycle()
    val preferredQuality by viewModel.preferredQuality.collectAsStateWithLifecycle()
    var showQualityDialog by remember { mutableStateOf(false) }

    val multiSelectionState = remember { MultiSelectionStateHolder() }
    val selectedSongs by multiSelectionState.selectedSongs.collectAsStateWithLifecycle()
    val selectedSongIds by multiSelectionState.selectedSongIds.collectAsStateWithLifecycle()
    val isSelectionMode by multiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    var showMultiSelectionSheet by remember { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    val toggleLxSongSelection: (com.minoppol.music.data.lxmusic.LxSong) -> Unit =
        remember(haptics, multiSelectionState) {
            { song ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val uiSong = LxSongMapper.toSong(song)
                multiSelectionState.toggleSelection(uiSong)
            }
        }

    var showBatchDownloadDialog by remember { mutableStateOf(false) }
    var batchDownloadQuality by remember { mutableStateOf(LxQualities.Q320) }

   
    suspend fun ensureScriptOrToast(song: com.minoppol.music.data.lxmusic.LxSong): Boolean {
        if (viewModel.activeScript.value != null) return true
        val resolved = viewModel.ensureActiveScriptForPlayback()
        if (resolved != null) return true
        if (song.source in com.minoppol.music.data.lxmusic.LxSources.ALL) return true
        val hasDisabled = viewModel.hasUserDisabledScripts()
        android.widget.Toast.makeText(
            context,
            if (hasDisabled) context.getString(R.string.lx_msg_source_disabled)
            else context.getString(com.minoppol.music.R.string.lxmusic_need_script_or_cookie),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
        return false
    }

    val playLxSong: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = { song ->
        scope.launch {
            if (!ensureScriptOrToast(song)) return@launch
            viewModel.recordPlay(song)
            viewModel.cacheSongsMetadata(listOf(song))
            val singleSong = LxSongMapper.toSong(song)
            playerViewModel.playSongs(
                songsToPlay = listOf(singleSong),
                startSong = singleSong,
                queueName = context.getString(R.string.lx_queue_name_playlist),
                origin = com.minoppol.music.presentation.viewmodel.PlaybackOrigin.ONLINE,
            )
        }
    }

    val playAllPlaylist: (List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit = { songs, queueName ->
        if (songs.isEmpty()) {
            android.widget.Toast.makeText(context, context.getString(R.string.lx_msg_playlist_no_songs), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                if (!ensureScriptOrToast(songs.first())) return@launch
            viewModel.recordPlay(songs.first())
                viewModel.cacheSongsMetadata(songs)
                viewModel.setPlaybackQueueLxSongs(songs)
                playerViewModel.playSongs(
                    songsToPlay = songs.map { LxSongMapper.toSong(it) },
                    startSong = LxSongMapper.toSong(songs.first()),
                    queueName = queueName,
                    origin = com.minoppol.music.presentation.viewmodel.PlaybackOrigin.ONLINE,
                )
            }
        }
    }

    val shuffleAllPlaylist: (List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit = { songs, queueName ->
        if (songs.isEmpty()) {
            android.widget.Toast.makeText(context, context.getString(R.string.lx_msg_playlist_no_songs), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                if (!ensureScriptOrToast(songs.first())) return@launch
                viewModel.cacheSongsMetadata(songs)
                viewModel.setPlaybackQueueLxSongs(songs)
                val songsById = songs.associateBy { LxSongMapper.toSong(it).id }
                playerViewModel.playSongsShuffled(
                    songsToPlay = songs.map { LxSongMapper.toSong(it) },
                    queueName = queueName,
                    startAtZero = true,
                    onStartSongResolved = { startSong ->
                        songsById[startSong.id]?.let { viewModel.recordPlay(it) }
                    },
                    origin = com.minoppol.music.presentation.viewmodel.PlaybackOrigin.ONLINE,
                )
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importFromFile(it) { } }
    }

    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)

    val mineAvailable = currentSourceLoggedIn

    val browseBackActive = (browseState.module == "mine" &&
        mineState.page !is LxMusicViewModel.MinePage.Home) ||
        browseState.module == "playlist" ||
        browseState.selectedPlaylist != null ||
        browseState.isPlaylistSearchMode ||
        (mineAvailable && (browseState.module == "board" || browseState.module == "search"))
    val topLayerVisible = playerSheetState == com.minoppol.music.presentation.viewmodel.PlayerSheetState.EXPANDED ||
        queueSheetVisible || showScriptSheet || showUrlImport || showLoginDialog
    BackHandler(enabled = isSelectionMode && !topLayerVisible) {
        multiSelectionState.clearSelection()
    }
    BackHandler(enabled = browseBackActive && !topLayerVisible && !isSelectionMode) {
        viewModel.back()
    }

    val headerContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    val activeScriptName = remember(activeScript, scripts) {
        scripts.firstOrNull { it.assetPath == activeScript }?.name
    }

    val brandTitle = stringResource(R.string.lx_brand_title)
    val moodWord = remember {
        LxMoodHolder.word ?: run {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val arrayRes = when (hour) {
                in 0..5 -> R.array.lx_mood_0_6
                in 6..8 -> R.array.lx_mood_6_9
                in 9..10 -> R.array.lx_mood_9_11
                in 11..14 -> R.array.lx_mood_11_15
                in 15..19 -> R.array.lx_mood_15_20
                else -> R.array.lx_mood_20_24
            }
            context.resources.getStringArray(arrayRes).random().also { LxMoodHolder.word = it }
        }
    }

    Scaffold(
        containerColor = headerContainerColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 40.sp)) {
                                append(brandTitle)
                                append(' ')
                            }
                            withStyle(SpanStyle(fontSize = 18.sp)) {
                                append(moodWord)
                            }
                        },
                        fontFamily = RoundedSans,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                    )
                },
                actions = {
                    FilledIconButton(
                        modifier = Modifier.padding(end = 4.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        onClick = { showScriptSheet = true },
                    ) {
                        Icon(Icons.Rounded.Build, contentDescription = stringResource(R.string.lx_desc_script_management))
                    }
                    FilledIconButton(
                        modifier = Modifier.padding(end = 14.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        onClick = onSettingsClick,
                    ) {
                        Icon(
                            painter = painterResource(com.minoppol.music.R.drawable.rounded_settings_24),
                            contentDescription = stringResource(R.string.lx_desc_settings),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        fun modulePageIndex(module: String): Int = when (module) {
            "mine" -> if (mineAvailable) 0 else 1
            "board" -> if (mineAvailable) 1 else 0
            "playlist" -> if (mineAvailable) 2 else 1
            else -> if (mineAvailable) 3 else 2
        }
        fun pageIndexToModule(page: Int): String = if (mineAvailable) {
            when (page) { 0 -> "mine"; 1 -> "board"; 2 -> "playlist"; else -> "search" }
        } else {
            when (page) { 0 -> "board"; 1 -> "playlist"; else -> "search" }
        }

        val pagerState = rememberPagerState(
            initialPage = modulePageIndex(browseState.module),
            pageCount = { if (mineAvailable) 4 else 3 },
        )
        val isListScrolling = remember { mutableStateOf(false) }
        LaunchedEffect(browseState.module, mineAvailable) {
            isListScrolling.value = false
            val target = modulePageIndex(browseState.module)
            if (pagerState.currentPage != target) {
                pagerState.animateScrollToPage(target)
            }
        }
        LaunchedEffect(pagerState, mineAvailable) {
            snapshotFlow {
                Triple(pagerState.currentPage, pagerState.targetPage, pagerState.isScrollInProgress)
            }
                .filter { !it.third }
                .map { it.first }
                .distinctUntilChanged()
                .collect { page ->
                    val target = pageIndexToModule(page)
                    if (browseState.module != target) viewModel.selectModule(target)
                }
        }
        val indicatorModule by remember(mineAvailable) {
            derivedStateOf { pageIndexToModule(pagerState.targetPage) }
        }
        val focusManager = LocalFocusManager.current
        LaunchedEffect(pagerState.currentPage, selectedSource) {
            focusManager.clearFocus()
        }
        Box(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
        ) {
            val sourceSelectedIndex = orderedSearchTabs.indexOfFirst { it.first == selectedSource }
            val sourceTabsListState = rememberLazyListState()
            LaunchedEffect(sourceSelectedIndex) {
                if (sourceSelectedIndex >= 0) {
                    sourceTabsListState.animateScrollToItem(sourceSelectedIndex)
                }
            }
            androidx.compose.foundation.lazy.LazyRow(
                state = sourceTabsListState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(orderedSearchTabs, key = { _, it -> it.first }) { index, (src, _) ->
                    LxSourceTab(
                        label = platformName(src),
                        selected = src == selectedSource,
                        index = index,
                        selectedIndex = sourceSelectedIndex,
                        onClick = { viewModel.selectSource(src) },
                    )
                }
                item(key = "__reorder__") {
                    LxSourceTab(
                        label = "",
                        icon = Icons.Rounded.Edit,
                        selected = false,
                        index = orderedSearchTabs.size,
                        selectedIndex = sourceSelectedIndex,
                        onClick = { showPlatformOrderSheet = true },
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxSize()
                    .padding(top = 68.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val mineBubbleProgress by animateFloatAsState(
                        targetValue = if (mineAvailable) 1f else 0f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                        ),
                        label = "mineBubble",
                    )
                    val mineLabel = stringResource(R.string.lx_tab_mine)
                    val boardLabel = stringResource(R.string.lx_tab_board)
                    val playlistLabel = stringResource(R.string.lx_tab_playlist)
                    val searchLabel = stringResource(R.string.lx_tab_search)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LxModuleButton(
                            label = mineLabel,
                            icon = Icons.Rounded.Person,
                            selected = indicatorModule == "mine",
                            onClick = { if (mineBubbleProgress > 0.5f) viewModel.selectModule("mine") },
                            modifier = Modifier
                                .weight(mineBubbleProgress.coerceIn(0.0001f, 1f))
                                .clip(RoundedCornerShape(16.dp))
                                .graphicsLayer {
                                    alpha = mineBubbleProgress
                                    scaleX = mineBubbleProgress
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                },
                        )
                        LxModuleButton(boardLabel, Icons.Rounded.Leaderboard,
                            indicatorModule == "board", { viewModel.selectModule("board") }, Modifier.weight(1f))
                        LxModuleButton(playlistLabel, Icons.Rounded.QueueMusic,
                            indicatorModule == "playlist", { viewModel.selectModule("playlist") }, Modifier.weight(1f))
                        LxModuleButton(searchLabel, Icons.Rounded.Search,
                            indicatorModule == "search", {
                                viewModel.updateSearchQuery("")
                                viewModel.selectModule("search")
                            }, Modifier.weight(1f))
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = pagerState.isScrollInProgress || !isListScrolling.value,
                        beyondViewportPageCount = 1,
                        key = { it },
                        pageSpacing = 0.dp,
                    ) { page ->
                        if (mineAvailable) {
                            when (page) {
                                0 -> {
                                    val mineNickname: String?
                                    val mineAvatar: String?
                                    val mineVipType: Int
                                    val mineShowFollow: Boolean
                                    val mineLogout: () -> Unit
                                    when (selectedSource) {
                                        LxSources.TENCENT -> {
                                            mineNickname = tencentNickname
                                            mineAvatar = tencentAvatar
                                            mineVipType = tencentVipType
                                            mineShowFollow = false
                                            mineLogout = { viewModel.logoutTencent() }
                                        }
                                        LxSources.KUGOU -> {
                                            mineNickname = kugouNickname
                                            mineAvatar = kugouAvatar
                                            mineVipType = kugouVipType
                                            mineShowFollow = kugouLoggedIn && viewModel.accountWritesEnabled
                                            mineLogout = { viewModel.logoutKugou() }
                                        }
                                        LxSources.KUWO -> {
                                            mineNickname = kuwoNickname
                                            mineAvatar = kuwoAvatar
                                            mineVipType = kuwoVipType
                                            mineShowFollow = kuwoLoggedIn && viewModel.accountWritesEnabled
                                            mineLogout = { viewModel.logoutKuwo() }
                                        }
                                        else -> {
                                            mineNickname = neteaseNickname
                                            mineAvatar = neteaseAvatar
                                            mineVipType = neteaseVipType
                                            mineShowFollow = neteaseLoggedIn
                                            mineLogout = {
                                                clearNeteaseWebViewSession()
                                                viewModel.logoutNetease()
                                            }
                                        }
                                    }
                                    LxMinePage(
                                        mineState, mineNickname, mineAvatar, mineVipType,
                                        viewModel, playerViewModel, playLxSong,
                                        playAllPlaylist, shuffleAllPlaylist, bottomBarHeightDp,
                                        onScrollingChange = { if (pagerState.currentPage == 0) isListScrolling.value = it },
                                        showArtistFollow = mineShowFollow,
                                        onLogout = mineLogout,
                                        onSongMoreOptions = { lxInfoSong = it },
                                        isSelectionMode = isSelectionMode,
                                        selectedSongIds = selectedSongIds,
                                        onSongLongPress = toggleLxSongSelection,
                                        onSongSelectionToggle = toggleLxSongSelection,
                                        getSelectionIndex = { songId ->
                                            multiSelectionState.getSelectionIndex(songId)
                                        },
                                        multiSelectionState = multiSelectionState,
                                        onSelectionOptionsClick = { showMultiSelectionSheet = true },
                                    )
                                }
                                1 -> LxBoardPage(browseState, viewModel, playerViewModel, playLxSong,
                                    playAllPlaylist, shuffleAllPlaylist, bottomBarHeightDp,
                                    onScrollingChange = { if (pagerState.currentPage == 1) isListScrolling.value = it },
                                    onSongMoreOptions = { lxInfoSong = it },
                                    isSelectionMode = isSelectionMode,
                                    selectedSongIds = selectedSongIds,
                                    onSongLongPress = toggleLxSongSelection,
                                    onSongSelectionToggle = toggleLxSongSelection,
                                    getSelectionIndex = { songId ->
                                        multiSelectionState.getSelectionIndex(songId)
                                    },
                                    multiSelectionState = multiSelectionState,
                                    onSelectionOptionsClick = { showMultiSelectionSheet = true })
                                2 -> LxPlaylistPage(browseState, viewModel, playerViewModel, playLxSong,
                                    playAllPlaylist, shuffleAllPlaylist, bottomBarHeightDp,
                                    onScrollingChange = { if (pagerState.currentPage == 2) isListScrolling.value = it },
                                    onSongMoreOptions = { lxInfoSong = it },
                                    isSelectionMode = isSelectionMode,
                                    selectedSongIds = selectedSongIds,
                                    onSongLongPress = toggleLxSongSelection,
                                    onSongSelectionToggle = toggleLxSongSelection,
                                    getSelectionIndex = { songId ->
                                        multiSelectionState.getSelectionIndex(songId)
                                    },
                                    multiSelectionState = multiSelectionState,
                                    onSelectionOptionsClick = { showMultiSelectionSheet = true })
                                else -> LxSearchPage(browseState, searchState, viewModel, playerViewModel, playLxSong,
                                    playAllPlaylist, shuffleAllPlaylist, bottomBarHeightDp,
                                    onScrollingChange = { if (pagerState.currentPage == 3) isListScrolling.value = it },
                                    onSongMoreOptions = { lxInfoSong = it },
                                    isSelectionMode = isSelectionMode,
                                    selectedSongIds = selectedSongIds,
                                    onSongLongPress = toggleLxSongSelection,
                                    onSongSelectionToggle = toggleLxSongSelection,
                                    getSelectionIndex = { songId ->
                                        multiSelectionState.getSelectionIndex(songId)
                                    },
                                    multiSelectionState = multiSelectionState,
                                    onSelectionOptionsClick = { showMultiSelectionSheet = true })
                            }
                        } else {
                            when (page) {
                                0 -> LxBoardPage(browseState, viewModel, playerViewModel, playLxSong,
                                    playAllPlaylist, shuffleAllPlaylist, bottomBarHeightDp,
                                    onScrollingChange = { if (pagerState.currentPage == 0) isListScrolling.value = it },
                                    onSongMoreOptions = { lxInfoSong = it },
                                    isSelectionMode = isSelectionMode,
                                    selectedSongIds = selectedSongIds,
                                    onSongLongPress = toggleLxSongSelection,
                                    onSongSelectionToggle = toggleLxSongSelection,
                                    getSelectionIndex = { songId ->
                                        multiSelectionState.getSelectionIndex(songId)
                                    },
                                    multiSelectionState = multiSelectionState,
                                    onSelectionOptionsClick = { showMultiSelectionSheet = true })
                                1 -> LxPlaylistPage(browseState, viewModel, playerViewModel, playLxSong,
                                    playAllPlaylist, shuffleAllPlaylist, bottomBarHeightDp,
                                    onScrollingChange = { if (pagerState.currentPage == 1) isListScrolling.value = it },
                                    onSongMoreOptions = { lxInfoSong = it },
                                    isSelectionMode = isSelectionMode,
                                    selectedSongIds = selectedSongIds,
                                    onSongLongPress = toggleLxSongSelection,
                                    onSongSelectionToggle = toggleLxSongSelection,
                                    getSelectionIndex = { songId ->
                                        multiSelectionState.getSelectionIndex(songId)
                                    },
                                    multiSelectionState = multiSelectionState,
                                    onSelectionOptionsClick = { showMultiSelectionSheet = true })
                                else -> LxSearchPage(browseState, searchState, viewModel, playerViewModel, playLxSong,
                                    playAllPlaylist, shuffleAllPlaylist, bottomBarHeightDp,
                                    onScrollingChange = { if (pagerState.currentPage == 2) isListScrolling.value = it },
                                    onSongMoreOptions = { lxInfoSong = it },
                                    isSelectionMode = isSelectionMode,
                                    selectedSongIds = selectedSongIds,
                                    onSongLongPress = toggleLxSongSelection,
                                    onSongSelectionToggle = toggleLxSongSelection,
                                    getSelectionIndex = { songId ->
                                        multiSelectionState.getSelectionIndex(songId)
                                    },
                                    multiSelectionState = multiSelectionState,
                                    onSelectionOptionsClick = { showMultiSelectionSheet = true })
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    if (showMultiSelectionSheet && selectedSongs.isNotEmpty()) {
        val selectedLxSongs = remember(selectedSongs) {
            selectedSongs.mapNotNull { LxSongMapper.toLxSong(it) }
        }
        val batchSameSource = selectedLxSongs.size == selectedSongs.size &&
            selectedLxSongs.map { it.source }.distinct().size == 1
        val batchSource = selectedLxSongs.firstOrNull()?.source
        val canBatchAddToPlaylist = batchSameSource && when (batchSource) {
            LxSources.NETEASE -> neteaseLoggedIn
            LxSources.KUGOU -> kugouLoggedIn && viewModel.accountWritesEnabled
            LxSources.KUWO -> kuwoLoggedIn && viewModel.accountWritesEnabled
            else -> false
        }
        val canBatchDelete = batchSameSource && batchSource != null &&
            viewModel.currentOwnPlaylistContext(batchSource) != null
        MultiSelectionBottomSheet(
            selectedSongs = selectedSongs,
            onDismiss = { showMultiSelectionSheet = false },
            showAddToPlaylist = canBatchAddToPlaylist,
            showDelete = canBatchDelete,
            showBatchEdit = false,
            onPlayAll = {
                val lxSongs = selectedSongs.mapNotNull { LxSongMapper.toLxSong(it) }
                if (lxSongs.isNotEmpty()) {
                    playAllPlaylist(lxSongs, "多选播放")
                }
                multiSelectionState.clearSelection()
                showMultiSelectionSheet = false
            },
            onAddToQueue = {
                selectedSongs.forEach { song ->
                    playerViewModel.addSongToQueue(song)
                }
                multiSelectionState.clearSelection()
                showMultiSelectionSheet = false
                android.widget.Toast.makeText(
                    context, context.getString(R.string.toast_added_to_queue),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onPlayNext = {
                selectedSongs.forEach { song ->
                    playerViewModel.addSongNextToQueue(song)
                }
                multiSelectionState.clearSelection()
                showMultiSelectionSheet = false
                android.widget.Toast.makeText(
                    context, context.getString(R.string.toast_added_to_queue),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onAddToPlaylist = {
                viewModel.openOnlineAddToPlaylistBatch(selectedLxSongs)
                multiSelectionState.clearSelection()
                showMultiSelectionSheet = false
            },
            onToggleLikeAll = { shouldLike: Boolean ->
                selectedSongs.forEach { song ->
                    playerViewModel.togglePlatformFavorite(song)
                }
                multiSelectionState.clearSelection()
                showMultiSelectionSheet = false
            },
            onShareAll = {
                playerViewModel.shareSelectedSongs(selectedSongs)
                multiSelectionState.clearSelection()
                showMultiSelectionSheet = false
            },
            onDownloadAll = {
                showBatchDownloadDialog = true
                showMultiSelectionSheet = false
            },
            onDeleteAll = { _: android.app.Activity, onResult: (Boolean) -> Unit ->
                viewModel.requestRemoveSongsFromOwnPlaylist(selectedLxSongs)
                onResult(true)
                showMultiSelectionSheet = false
            },
        )
    }

    if (showBatchDownloadDialog && selectedSongs.isNotEmpty()) {
        LxBatchDownloadQualityDialog(
            songCount = selectedSongs.size,
            selectedQuality = batchDownloadQuality,
            onSelect = { batchDownloadQuality = it },
            onConfirm = {
                val lxSongs = selectedSongs.mapNotNull { LxSongMapper.toLxSong(it) }
                if (lxSongs.isNotEmpty()) {
                    viewModel.batchDownloadSongs(lxSongs, batchDownloadQuality)
                }
                showBatchDownloadDialog = false
                multiSelectionState.clearSelection()
            },
            onDismiss = { showBatchDownloadDialog = false }
        )
    }

    lxInfoSong?.let { infoLxSong ->
        val infoUiSong = remember(infoLxSong.source, infoLxSong.songmid, infoLxSong.pic) {
            LxSongMapper.toSong(infoLxSong)
        }
        val likeSnapshot by playerViewModel.platformLikeSnapshot.collectAsStateWithLifecycle()
        val (infoIsFavorite, infoFavoriteEnabled) = likeSnapshot.let {
            playerViewModel.platformLikeStateOf(infoUiSong)
        }
        SongInfoBottomSheet(
            song = infoUiSong,
            isFavorite = infoIsFavorite,
            favoriteEnabled = infoFavoriteEnabled,
            onToggleFavorite = { playerViewModel.togglePlatformFavorite(infoUiSong) },
            onDismiss = { lxInfoSong = null },
            onPlaySong = {
                playLxSong(infoLxSong)
                lxInfoSong = null
            },
            onAddToQueue = {
                playerViewModel.addSongToQueue(infoUiSong)
                lxInfoSong = null
                android.widget.Toast.makeText(
                    context, context.getString(R.string.toast_added_to_queue),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onAddNextToQueue = {
                playerViewModel.addSongNextToQueue(infoUiSong)
                lxInfoSong = null
                android.widget.Toast.makeText(
                    context, context.getString(R.string.toast_playing_next),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onAddToPlayList = {
                viewModel.openOnlineAddToPlaylist(infoLxSong)
                lxInfoSong = null
            },
            addToPlaylistEnabled = when (infoLxSong.source) {
                LxSources.NETEASE -> neteaseLoggedIn
                LxSources.KUGOU -> kugouLoggedIn && viewModel.accountWritesEnabled
                LxSources.KUWO -> kuwoLoggedIn && viewModel.accountWritesEnabled
                else -> false
            },
            removeFromPlaylist = run {
                val loggedIn = when (infoLxSong.source) {
                    LxSources.NETEASE -> neteaseLoggedIn && viewModel.accountWritesEnabled
                    LxSources.KUGOU -> kugouLoggedIn && viewModel.accountWritesEnabled
                    LxSources.KUWO -> kuwoLoggedIn && viewModel.accountWritesEnabled
                    else -> false
                }
                val ownCtx = if (loggedIn) viewModel.currentOwnPlaylistContext(infoLxSong.source) else null
                if (ownCtx != null) {
                    {
                        viewModel.requestRemoveSongFromOwnPlaylist(infoLxSong)
                        lxInfoSong = null
                    }
                } else null
            },
            cloudDownloadEnabled = false,
            onDeleteFromDevice = { activity, songToDelete, onResult ->
                playerViewModel.deleteFromDevice(activity, songToDelete, onResult)
                lxInfoSong = null
            },
            onNavigateToAlbum = {
                val albumId = when (infoLxSong.source) {
                    com.minoppol.music.data.lxmusic.LxSources.TENCENT -> infoUiSong.lxMusicExtraFields["albumMid"]
                    else -> infoUiSong.lxMusicExtraFields["albumId"]
                }
                if (!albumId.isNullOrBlank()) {
                    viewModel.openAlbumFromSearch(
                        com.minoppol.music.data.lxmusic.LxAlbum(
                            id = albumId,
                            name = infoUiSong.album.orEmpty(),
                        )
                    )
                    lxInfoSong = null
                }
            },
            onNavigateToArtist = {
                scope.launch {
                    val artists = playerViewModel.resolveLxSongArtists(infoUiSong)
                    when {
                        artists.size == 1 -> {
                            viewModel.openArtistFromSearch(artists.first(), infoLxSong.source)
                            lxInfoSong = null
                        }
                        artists.size > 1 -> {
                            lxArtistPickerCandidates = artists
                            lxArtistPickerSource = infoLxSong.source
                            showLxArtistPicker = true
                            lxInfoSong = null
                        }
                    }
                }
            },
            onNavigateToGenre = {},
            onEditSong = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
            removeFromListTrigger = {},
        )
    }

    if (showLxArtistPicker && lxArtistPickerCandidates.isNotEmpty()) {
        com.minoppol.music.presentation.components.LxArtistPickerBottomSheet(
            artists = lxArtistPickerCandidates,
            source = lxArtistPickerSource,
            sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
            onDismiss = { showLxArtistPicker = false },
            onArtistClick = { artist ->
                viewModel.openArtistFromSearch(artist, lxArtistPickerSource)
                showLxArtistPicker = false
            },
        )
    }

    val showSearchAlbumOverlay by viewModel.searchAlbumOverlay.collectAsStateWithLifecycle()
    if (showSearchAlbumOverlay) {
        val albumPageState by viewModel.searchAlbumPageState.collectAsStateWithLifecycle()
        BackHandler {
            if (isSelectionMode) multiSelectionState.clearSelection()
            else viewModel.dismissSearchAlbumOverlay()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            LxSearchAlbumPage(
                state = albumPageState,
                viewModel = viewModel,
                playerViewModel = playerViewModel,
                playLxSong = playLxSong,
                playAllPlaylist = playAllPlaylist,
                shuffleAllPlaylist = shuffleAllPlaylist,
                bottomBarHeightDp = bottomBarHeightDp,
                onBack = viewModel::dismissSearchAlbumOverlay,
                onScrollingChange = {},
                onSongMoreOptions = { lxInfoSong = it },
                isSelectionMode = isSelectionMode,
                selectedSongIds = selectedSongIds,
                onSongLongPress = toggleLxSongSelection,
                onSongSelectionToggle = toggleLxSongSelection,
                getSelectionIndex = { songId -> multiSelectionState.getSelectionIndex(songId) },
                multiSelectionState = multiSelectionState,
                onSelectionOptionsClick = { showMultiSelectionSheet = true },
            )
        }
    }
    val showSearchArtistOverlay by viewModel.searchArtistOverlay.collectAsStateWithLifecycle()
    if (showSearchArtistOverlay) {
        val artistPageState by viewModel.searchArtistPageState.collectAsStateWithLifecycle()
        BackHandler {
            if (isSelectionMode) {
                multiSelectionState.clearSelection()
            } else if (artistPageState.selectedAlbum != null) {
                viewModel.backFromSearchArtistAlbum()
            } else {
                viewModel.dismissSearchArtistOverlay()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            LxSearchArtistPage(
                state = artistPageState,
                viewModel = viewModel,
                playerViewModel = playerViewModel,
                playLxSong = playLxSong,
                playAllPlaylist = playAllPlaylist,
                shuffleAllPlaylist = shuffleAllPlaylist,
                bottomBarHeightDp = bottomBarHeightDp,
                showFollow = when (artistPageState.source) {
                    LxSources.TENCENT -> tencentLoggedIn && viewModel.accountWritesEnabled
                    LxSources.KUGOU -> kugouLoggedIn && viewModel.accountWritesEnabled
                    LxSources.KUWO -> kuwoLoggedIn && viewModel.accountWritesEnabled
                    else -> neteaseLoggedIn
                },
                onBack = viewModel::dismissSearchArtistOverlay,
                onScrollingChange = {},
                onSongMoreOptions = { lxInfoSong = it },
                isSelectionMode = isSelectionMode,
                selectedSongIds = selectedSongIds,
                onSongLongPress = toggleLxSongSelection,
                onSongSelectionToggle = toggleLxSongSelection,
                getSelectionIndex = { songId -> multiSelectionState.getSelectionIndex(songId) },
                multiSelectionState = multiSelectionState,
                onSelectionOptionsClick = { showMultiSelectionSheet = true },
            )
        }
    }

    val onlineAddSheetState by viewModel.onlineAddSheet.collectAsStateWithLifecycle()
    onlineAddSheetState?.let { addState ->
        com.minoppol.music.presentation.components.NeteaseAddToPlaylistSheet(
            state = addState,
            onDismiss = { viewModel.dismissOnlineAddSheet() },
            onConfirm = { ids -> viewModel.onlineAddSongToPlaylists(ids) },
        )
    }

    val onlineRemoveConfirmState by viewModel.onlineRemoveConfirm.collectAsStateWithLifecycle()
    onlineRemoveConfirmState?.let { removeState ->
        LxRemoveFromPlaylistConfirmDialog(
            songTitle = removeState.songTitle,
            playlistName = removeState.playlistName,
            removing = removeState.removing,
            onConfirm = { viewModel.confirmOnlineRemoveFromPlaylist() },
            onDismiss = { viewModel.dismissOnlineRemoveConfirm() },
        )
    }

    val batchRemoveConfirmState by viewModel.batchOnlineRemoveConfirm.collectAsStateWithLifecycle()
    batchRemoveConfirmState?.let { batchRemoveState ->
        LxBatchRemoveFromPlaylistConfirmDialog(
            count = batchRemoveState.songs.size,
            playlistName = batchRemoveState.playlistName,
            removing = batchRemoveState.removing,
            onConfirm = { viewModel.confirmBatchOnlineRemoveFromPlaylist() },
            onDismiss = { viewModel.dismissBatchOnlineRemoveConfirm() },
        )
    }
    LaunchedEffect(Unit) {
        viewModel.batchRemovedSelectionIds.collect { removedIds ->
            multiSelectionState.removeSongs(removedIds)
        }
    }

    if (showScriptSheet) {
        ModalBottomSheet(
            onDismissRequest = { showScriptSheet = false },
            sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())) {
                var scriptIntroExpanded by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.lx_title_source_scripts),
                        fontFamily = RoundedSans,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(
                        onClick = { scriptIntroExpanded = !scriptIntroExpanded },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (scriptIntroExpanded) {
                                Icons.Rounded.ExpandLess
                            } else {
                                Icons.Rounded.ExpandMore
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(visible = scriptIntroExpanded) {
                    Text(
                        text = stringResource(R.string.lx_msg_script_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }

                if (activeScriptName != null) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.lx_title_current_source),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                )
                                Text(
                                    text = activeScriptName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                if (loadState.loading) {
                    Text(
                        text = stringResource(R.string.lx_msg_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                loadState.error?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                if (loadState.loadedSources.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.lx_msg_declared_sources, loadState.loadedSources.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { showUrlImport = true },
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.lx_btn_url_import), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    }
                    FilledTonalButton(
                        onClick = { filePicker.launch(arrayOf("application/javascript", "text/plain", "*/*")) },
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.lx_btn_file_import), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(Modifier.height(12.dp))

                val loginStatusText = when {
                    neteaseCookie.isNullOrBlank() -> stringResource(R.string.lx_msg_not_logged_in)
                    !neteaseCookieValid -> stringResource(R.string.lx_msg_login_expired_status)
                    neteaseVipType == 13 ->
                        neteaseNickname?.let { stringResource(R.string.lx_status_nickname_svip, it) } ?: stringResource(R.string.lx_status_logged_in_svip)
                    neteaseVipType == 11 ->
                        neteaseNickname?.let { stringResource(R.string.lx_status_nickname_vip, it) } ?: stringResource(R.string.lx_status_logged_in_vip)
                    neteaseVipType == 0 ->
                        neteaseNickname?.let { stringResource(R.string.lx_status_nickname_logged_in, it) } ?: stringResource(R.string.lx_status_logged_in)
                    else -> neteaseNickname ?: stringResource(R.string.lx_status_logged_in)
                }
                @Composable
                fun platformStatusText(
                    cookie: String?, valid: Boolean, nickname: String?, appLoggedIn: Boolean = false,
                ): String = when {
                    appLoggedIn || (!cookie.isNullOrBlank() && valid) ->
                        nickname?.let { stringResource(R.string.lx_status_nickname_logged_in, it) }
                            ?: stringResource(R.string.lx_status_logged_in)
                    cookie.isNullOrBlank() -> stringResource(R.string.lx_msg_not_logged_in_platform)
                    else -> stringResource(R.string.lx_msg_login_expired_status)
                }
                @Composable
                fun PlatformLoginRow(
                    title: String,
                    statusText: String,
                    avatarUrl: String?,
                    trailing: @Composable () -> Unit,
                    onClick: () -> Unit,
                ) {
                    Surface(
                        onClick = onClick,
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!avatarUrl.isNullOrBlank()) {
                                SmartImage(
                                    model = avatarUrl,
                                    contentDescription = null,
                                    targetSize = SmartImageListTargetSize,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            trailing()
                        }
                    }
                }
                var loginExpanded by remember { mutableStateOf(false) }
                PlatformLoginRow(
                    title = platformName("wy"),
                    statusText = loginStatusText,
                    avatarUrl = if (!neteaseCookie.isNullOrBlank() && neteaseCookieValid) neteaseAvatar else null,
                    trailing = {
                        IconButton(onClick = { loginExpanded = !loginExpanded }) {
                            Icon(
                                if (loginExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    onClick = {
                        clearNeteaseWebViewSession { showLoginDialog = true }
                    },
                )
                AnimatedVisibility(visible = loginExpanded) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        if (viewModel.accountWritesEnabled) {
                            PlatformLoginRow(
                                title = platformName("tx"),
                                statusText = platformStatusText(tencentCookie, tencentCookieValid, tencentNickname),
                                avatarUrl = if (!tencentCookie.isNullOrBlank() && tencentCookieValid) tencentAvatar else null,
                                trailing = {
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                },
                                onClick = { clearAllWebViewSession { showTxLoginDialog = true } },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        if (viewModel.accountWritesEnabled) {
                            PlatformLoginRow(
                                title = platformName("kg"),
                                statusText = platformStatusText(kugouCookie, kugouCookieValid, kugouNickname, appLoggedIn = kugouLoggedIn),
                                avatarUrl = if (kugouLoggedIn) kugouAvatar else null,
                                trailing = {
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                },
                                onClick = { clearAllWebViewSession { showKgLoginDialog = true } },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        if (viewModel.accountWritesEnabled) {
                            PlatformLoginRow(
                                title = platformName("kw"),
                                statusText = platformStatusText(kuwoCookie, kuwoCookieValid, kuwoNickname),
                                avatarUrl = if (!kuwoCookie.isNullOrBlank() && kuwoCookieValid) kuwoAvatar else null,
                                trailing = {
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                },
                                onClick = { clearAllWebViewSession { showKwLoginDialog = true } },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val qualityLabel = when (preferredQuality) {
                    "128k" -> stringResource(R.string.lxmusic_quality_128k)
                    "320k" -> stringResource(R.string.lxmusic_quality_320k)
                    "flac" -> stringResource(R.string.lxmusic_quality_flac)
                    "flac24bit" -> stringResource(R.string.lxmusic_quality_flac24bit)
                    "atmos" -> stringResource(R.string.lxmusic_quality_atmos)
                    "master" -> stringResource(R.string.lxmusic_quality_master)
                    else -> stringResource(R.string.lxmusic_quality_320k)
                }
                Surface(
                    onClick = { showQualityDialog = true },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.lxmusic_quality_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = qualityLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                scripts.forEachIndexed { index, meta ->
                    ScriptRow(
                        meta = meta,
                        isPreferred = activeScript == meta.assetPath,
                        isEnabled = meta.assetPath in runtimePaths,
                        shape = when {
                            scripts.size == 1 -> RoundedCornerShape(24.dp)
                            index == 0 -> RoundedCornerShape(
                                topStart = 24.dp, topEnd = 24.dp,
                                bottomStart = 4.dp, bottomEnd = 4.dp,
                            )
                            index == scripts.size - 1 -> RoundedCornerShape(
                                topStart = 4.dp, topEnd = 4.dp,
                                bottomStart = 24.dp, bottomEnd = 24.dp,
                            )
                            else -> RoundedCornerShape(4.dp)
                        },
                        onToggle = { checked ->
                            if (checked) viewModel.selectScript(meta.assetPath)
                            else viewModel.unloadScript(meta.assetPath)
                        },
                        onSetPreferred = { viewModel.makePreferred(meta.assetPath) },
                        onDelete = {
                            scriptToDelete = meta
                        },
                    )
                    if (index < scripts.size - 1) Spacer(Modifier.height(2.dp))
                }

                Spacer(Modifier.height(12.dp))

                val nameStyleLabel = if (platformNameStyle == "alias") {
                    stringResource(R.string.lx_platform_name_style_alias)
                } else {
                    stringResource(R.string.lx_platform_name_style_original)
                }
                Surface(
                    onClick = { showPlatformNameDialog = true },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.lx_platform_name_style_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = nameStyleLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showUrlImport) {
        val urlFieldColor = MaterialTheme.colorScheme.secondaryContainer
            .compositeOver(MaterialTheme.colorScheme.surface)
        val onUrlFieldColor = MaterialTheme.colorScheme.onSecondaryContainer
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            onDismissRequest = { if (!urlImporting) showUrlImport = false },
            title = { Text(stringResource(R.string.lx_title_import_from_url)) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(urlFieldColor)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !urlImporting,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = onUrlFieldColor),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (urlInput.isNotBlank() && !urlImporting) {
                                urlImporting = true
                                viewModel.importFromUrl(urlInput) {
                                    urlImporting = false
                                    showUrlImport = false
                                    urlInput = ""
                                }
                            }
                        }),
                    ) { innerTextField ->
                        if (urlInput.isEmpty()) {
                            Text("https://example.com/source.js",
                                style = MaterialTheme.typography.bodyLarge,
                                color = onUrlFieldColor.copy(alpha = 0.6f))
                        }
                        innerTextField()
                    }
                }
            },
            confirmButton = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilledTonalIconButton(
                        enabled = !urlImporting,
                        onClick = { showUrlImport = false },
                        shape = lxDialogCancelShape,
                        modifier = Modifier
                            .shadow(2.dp, lxDialogCancelShape)
                            .size(42.dp),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lx_desc_cancel))
                    }
                    FilledTonalIconButton(
                        enabled = urlInput.isNotBlank() && !urlImporting,
                        onClick = {
                            urlImporting = true
                            viewModel.importFromUrl(urlInput) {
                                urlImporting = false
                                showUrlImport = false
                                urlInput = ""
                            }
                        },
                        shape = lxDialogConfirmShape,
                        modifier = Modifier
                            .then(
                                if (urlInput.isNotBlank()) Modifier.shadow(2.dp, lxDialogConfirmShape)
                                else Modifier
                            )
                            .size(42.dp),
                    ) {
                        if (urlImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.lx_desc_import))
                        }
                    }
                }
            },
        )
    }

    if (scriptToDelete != null) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            onDismissRequest = { scriptToDelete = null },
            title = { Text(stringResource(R.string.lx_title_delete_script)) },
            text = { Text(stringResource(R.string.lx_msg_delete_script_confirm, scriptToDelete?.name ?: "")) },
            confirmButton = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = { scriptToDelete = null },
                        shape = lxDialogCancelShape,
                        modifier = Modifier
                            .shadow(2.dp, lxDialogCancelShape)
                            .size(42.dp),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lx_desc_cancel))
                    }
                    FilledTonalIconButton(
                        onClick = {
                            scriptToDelete?.let { viewModel.deleteScript(it.assetPath) }
                            scriptToDelete = null
                        },
                        shape = lxDialogConfirmShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier
                            .shadow(2.dp, lxDialogConfirmShape)
                            .size(42.dp),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.lx_desc_delete))
                    }
                }
            },
        )
    }

    if (showPlatformNameDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            onDismissRequest = { showPlatformNameDialog = false },
            title = { Text(stringResource(R.string.lx_platform_name_style_title)) },
            text = {
                Column {
                    listOf("original", "alias").forEach { style ->
                        val selected = platformNameStyle == style
                        Surface(
                            onClick = {
                                viewModel.setPlatformNameStyle(style)
                                showPlatformNameDialog = false
                            },
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = selected,
                                    onClick = null,
                                    colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (style == "original") {
                                        stringResource(R.string.lx_platform_name_style_original)
                                    } else {
                                        stringResource(R.string.lx_platform_name_style_alias)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalIconButton(
                    onClick = { showPlatformNameDialog = false },
                    shape = lxDialogCancelShape,
                    modifier = Modifier
                        .shadow(2.dp, lxDialogCancelShape)
                        .size(42.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lx_desc_cancel))
                }
            },
        )
    }

    if (showPlatformOrderSheet) {
        ReorderPlatformsSheet(
            platforms = platformOrder.toImmutableList(),
            platformName = { platformName(it) },
            onReorder = { viewModel.savePlatformOrder(it) },
            onReset = { viewModel.resetPlatformOrder() },
            onDismiss = { showPlatformOrderSheet = false },
        )
    }

    val uncollectConfirm by viewModel.uncollectConfirm.collectAsStateWithLifecycle()
    uncollectConfirm?.let { (kind, id) ->
        LxUncollectConfirmDialog(
            kind = kind,
            onConfirm = { viewModel.confirmUncollect(kind, id) },
            onDismiss = viewModel::dismissUncollectConfirm,
        )
    }
    if (showLoginDialog) {
        NeteaseLoginDialog(
            onDismiss = { showLoginDialog = false },
            onValidate = { cookie, callback ->
                viewModel.validateAndLogin(cookie) { ok, error ->
                    callback(ok, error)
                    if (ok) showLoginDialog = false
                }
            },
        )
    }
    if (showTxLoginDialog) {
        TencentQrLoginDialog(
            onDismiss = { showTxLoginDialog = false },
            onCreateQr = { type, callback -> viewModel.txQrCreate(type, callback) },
            onPoll = { type, session, callback -> viewModel.txQrPoll(type, session, callback) },
            onLogin = { cookie, callback ->
                viewModel.loginTencentByQr(cookie) { ok, error ->
                    callback(ok, error)
                    if (ok) showTxLoginDialog = false
                }
            },
        )
    }
    if (showKgLoginDialog) {
        KugouSmsLoginDialog(
            onDismiss = { showKgLoginDialog = false },
            onSendCode = { mobile, callback ->
                viewModel.sendKugouSmsCode(mobile) { ok, error -> callback(ok, error) }
            },
            onLogin = { mobile, code, callback ->
                viewModel.loginKugouBySms(mobile, code) { ok, error ->
                    callback(ok, error)
                    if (ok) showKgLoginDialog = false
                }
            },
        )
    }
    if (showKwLoginDialog) {
        KuwoLoginDialog(
            onDismiss = { showKwLoginDialog = false },
            onValidate = { cookie, callback ->
                viewModel.validateAndLoginKuwo(cookie) { ok, error ->
                    callback(ok, error)
                    if (ok) showKwLoginDialog = false
                }
            },
        )
    }
    if (showQualityDialog) {
        val options = listOf(
            "master" to (R.string.lxmusic_quality_master to R.string.lxmusic_quality_master_desc),
            "atmos" to (R.string.lxmusic_quality_atmos to R.string.lxmusic_quality_atmos_desc),
            "flac24bit" to (R.string.lxmusic_quality_flac24bit to R.string.lxmusic_quality_flac24bit_desc),
            "flac" to (R.string.lxmusic_quality_flac to R.string.lxmusic_quality_flac_desc),
            "320k" to (R.string.lxmusic_quality_320k to R.string.lxmusic_quality_320k_desc),
            "128k" to (R.string.lxmusic_quality_128k to R.string.lxmusic_quality_128k_desc),
        )
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            onDismissRequest = { showQualityDialog = false },
            title = { Text(stringResource(R.string.lxmusic_quality_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { (key, labelDesc) ->
                        val (labelRes, descRes) = labelDesc
                        val isSelected = preferredQuality == key
                        Surface(
                            onClick = {
                                viewModel.setPreferredQuality(key)
                                showQualityDialog = false
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource(descRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalIconButton(
                    onClick = { showQualityDialog = false },
                    shape = lxDialogCancelShape,
                    modifier = Modifier
                        .shadow(2.dp, lxDialogCancelShape)
                        .size(42.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lx_desc_cancel))
                }
            },
        )
    }
}

@Composable
private fun ScriptRow(
    meta: LxScriptMeta,
    isPreferred: Boolean,
    isEnabled: Boolean,
    shape: RoundedCornerShape,
    onToggle: (Boolean) -> Unit,
    onSetPreferred: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val containerColor = if (isPreferred) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainer
    val titleColor = when {
        isPreferred -> MaterialTheme.colorScheme.onPrimaryContainer
        isEnabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = containerColor,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = { if (isEnabled && !isPreferred) onSetPreferred() },
                onLongClick = onDelete,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = meta.name + if (isPreferred) stringResource(R.string.lx_msg_preferred_suffix) else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isPreferred) FontWeight.Bold else FontWeight.Medium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = buildList {
                    if (meta.author.isNotBlank()) add(meta.author)
                    if (meta.version.isNotBlank()) add("v" + meta.version)
                }.joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isPreferred) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { checked ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(checked)
                },
                thumbContent = {
                    AnimatedContent(
                        targetState = isEnabled,
                        transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                        label = "script_switch_thumb_icon",
                    ) { isChecked ->
                        Icon(
                            imageVector = if (isChecked) Icons.Rounded.Check else Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedIconColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}

private val searchTabs = listOf(
    "wy" to R.string.lx_source_netease,
    "kw" to R.string.lx_source_kuwo,
    "tx" to R.string.lx_source_qq,
    "kg" to R.string.lx_source_kugou,
)

private val lxDialogCancelShape = RoundedCornerShape(
    topStart = 26.dp, bottomStart = 26.dp,
    topEnd = 8.dp, bottomEnd = 8.dp,
)
private val lxDialogConfirmShape = RoundedCornerShape(
    topStart = 8.dp, bottomStart = 8.dp,
    topEnd = 26.dp, bottomEnd = 26.dp,
)

@Composable
private fun LxSourceTab(
    label: String,
    selected: Boolean,
    index: Int,
    selectedIndex: Int,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    var hasAnimatedSelectionChange by remember { mutableStateOf(false) }
    val animationSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 200),
        label = "LxSourceTabBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 200),
        label = "LxSourceTabContent"
    )

    LaunchedEffect(selectedIndex) {
        if (!hasAnimatedSelectionChange) {
            hasAnimatedSelectionChange = true
            scale.snapTo(1f)
            offsetX.snapTo(0f)
            return@LaunchedEffect
        }
        if (selected) {
            scale.animateTo(1.05f, animationSpec)
            scale.animateTo(1f, animationSpec)
        } else {
            scale.snapTo(1f)
        }
        if (!selected) {
            val distance = index - selectedIndex
            if (abs(distance) == 1) {
                val direction = if (distance > 0) 1 else -1
                val offsetValue = 12f * direction
                offsetX.animateTo(offsetValue, animationSpec)
                offsetX.animateTo(0f, animationSpec)
            } else {
                offsetX.snapTo(0f)
            }
        } else {
            offsetX.snapTo(0f)
        }
    }

    Surface(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(22.dp),
        color = backgroundColor,
        modifier = Modifier
            .widthIn(min = 64.dp)
            .graphicsLayer {
            scaleX = scale.value
            translationX = offsetX.value
            transformOrigin = TransformOrigin.Center
        },
    ) {
        if (icon != null && label.isEmpty()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .size(20.dp),
            )
        } else {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun LxModuleButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun LxDockedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    solidBackground: Boolean = false,
    enableInnerBox: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val fieldColor = MaterialTheme.colorScheme.secondaryContainer
        .compositeOver(MaterialTheme.colorScheme.surface)
    val onFieldColor = MaterialTheme.colorScheme.onSecondaryContainer
    val doSearch: () -> Unit = {
        if (query.isNotBlank()) {
            onSearch(query)
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .shadow(4.dp, shape)
            .clip(shape)
            .background(fieldColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(16.dp))
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = onFieldColor,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(10.dp))
        if (enableInnerBox) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = onFieldColor),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text, imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                ) { innerTextField ->
                    if (query.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge,
                            color = onFieldColor.copy(alpha = 0.6f))
                    }
                    innerTextField()
                }
            }
        } else {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = onFieldColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            ) { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge,
                            color = onFieldColor.copy(alpha = 0.6f))
                    }
                    innerTextField()
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (query.isNotBlank()) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.lx_desc_clear),
                tint = onFieldColor,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable { onClear() }
                    .padding(2.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        FilledIconButton(
            onClick = doSearch,
            shape = RoundedCornerShape(
                topStart = 8.dp,
                bottomStart = 8.dp,
                topEnd = 24.dp,
                bottomEnd = 24.dp,
            ),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .padding(end = 5.dp)
                .size(42.dp),
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = stringResource(R.string.lx_desc_search),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun LxPlaylistItem(
    playlist: com.minoppol.music.data.lxmusic.LxPlaylist,
    onClick: () -> Unit,
) {
    val trackCountFormat = stringResource(R.string.lx_text_track_count_format)
    val playCountHundredMillionFormat = stringResource(R.string.lx_text_play_count_hundred_million_plus)
    val playCountTenThousandFormat = stringResource(R.string.lx_text_play_count_ten_thousand_plus)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pic = playlist.pic
        if (!pic.isNullOrBlank()) {
            SmartImage(
                model = pic,
                contentDescription = null,
                targetSize = SmartImageListTargetSize,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    playlist.creator?.let { append(it) }
                    if (!playlist.creator.isNullOrBlank()) append(" · ")
                    playlist.trackCount?.let { append(trackCountFormat.format(it)) }
                    playlist.playCount?.let {
                        if (it > 100000000) append(playCountHundredMillionFormat.format(it / 100000000))
                        else if (it > 10000) append(playCountTenThousandFormat.format(it / 10000))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun LxScrollToTopFab(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val isScrolled = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 120

    AnimatedVisibility(
        visible = isScrolled,
        enter = scaleIn(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
        exit = scaleOut(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180)),
        modifier = modifier,
    ) {
        FilledIconButton(
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowUp,
                contentDescription = stringResource(R.string.lx_desc_back_to_top),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
internal fun LxLocateCurrentSongFab(
    listState: LazyListState,
    currentSongId: String?,
    songIds: List<String>,
    leadingItems: Int = 0,
    modifier: Modifier = Modifier,
    perSongMatchIds: List<Set<String>>? = null,
    currentMatchIds: Set<String>? = null,
) {
    val scope = rememberCoroutineScope()
    val targetIndex = remember(currentSongId, songIds, leadingItems, perSongMatchIds, currentMatchIds) {
        val i = if (perSongMatchIds != null && currentMatchIds != null) {
            perSongMatchIds.indexOfFirst { ids -> ids.any { it in currentMatchIds } }
        } else {
            currentSongId?.let { songIds.indexOf(it) } ?: -1
        }
        if (i >= 0) i + leadingItems else -1
    }
    val visible by remember(targetIndex, listState) {
        derivedStateOf {
            if (targetIndex < 0) {
                false
            } else {
                val visibleIndices = listState.layoutInfo.visibleItemsInfo
                visibleIndices.none { it.index == targetIndex }
            }
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
        exit = scaleOut(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180)),
        modifier = modifier,
    ) {
        FilledIconButton(
            onClick = { scope.launch { listState.animateScrollToItem(targetIndex) } },
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                Icons.Rounded.MyLocation,
                contentDescription = stringResource(R.string.lx_desc_locate_current),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun Modifier.lxVerticalDragGate(onVerticalDrag: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var triggered = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    if (!triggered) {
                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (abs(dy) > 2f && abs(dy) > abs(dx)) {
                            triggered = true
                            onVerticalDrag(true)
                        }
                    }
                    if (event.changes.none { it.pressed }) break
                }
            } finally {
                if (triggered) onVerticalDrag(false)
            }
        }
    }

@Composable
internal fun LxFloatingSelectionRow(
    visible: Boolean,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeselect: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        SelectionActionRow(
            selectedCount = selectedCount,
            onSelectAll = onSelectAll,
            onDeselect = onDeselect,
            onOptionsClick = onOptionsClick,
            modifier = Modifier.padding(end = 19.dp),
            trailingContentBeforeOptions = { SelectionCountPill(selectedCount) },
        )
    }
}

@Composable
internal fun LxSelectionSwitch(
    inSelectionMode: Boolean,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeselect: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
    normalContent: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = inSelectionMode,
        label = "LxSelectionSwitch",
        transitionSpec = {
            (slideInHorizontally { -it } + fadeIn()) togetherWith
                (slideOutHorizontally { it } + fadeOut())
        },
        modifier = modifier,
    ) { selection ->
        if (selection) {
            SelectionActionRow(
                selectedCount = selectedCount,
                onSelectAll = onSelectAll,
                onDeselect = onDeselect,
                onOptionsClick = onOptionsClick,
                trailingContentBeforeOptions = { SelectionCountPill(selectedCount) },
            )
        } else {
            normalContent()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LxBoardPage(
    browseState: LxMusicViewModel.BrowseState,
    viewModel: LxMusicViewModel,
    playerViewModel: PlayerViewModel,
    playLxSong: (com.minoppol.music.data.lxmusic.LxSong) -> Unit,
    playAllPlaylist: (List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit,
    shuffleAllPlaylist: (List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit,
    bottomBarHeightDp: Dp,
    onScrollingChange: (Boolean) -> Unit = {},
    onSongMoreOptions: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    onSongSelectionToggle: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    multiSelectionState: MultiSelectionStateHolder = MultiSelectionStateHolder(),
    onSelectionOptionsClick: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val boardQueueFallback = stringResource(R.string.lx_tab_board)

    val pullToRefreshState = rememberPullToRefreshState()
    val boardHeaderHeight = 66.dp
    val consumeHorizontalScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource
            ): Offset = Offset(available.x, 0f)
            override suspend fun onPreFling(available: Velocity): Velocity = Velocity(available.x, 0f)
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val boardSongIds = remember(browseState.boardSongs) {
        browseState.boardSongs.map { LxSongMapper.unifiedSongId(it.source, it.songmid).toString() }
    }
    val boardScope = rememberCoroutineScope()
    val boardPlaylistId = browseState.selectedBoard?.bangid
    LaunchedEffect(boardPlaylistId) {
        if (boardPlaylistId != null) viewModel.ensureCollectDataLoaded()
    }
    val (boardLocateVisible, boardLocateIndex) = rememberLocateButtonState(
        listState = listState,
        currentSongId = stablePlayerState.currentSong?.id,
        songIds = boardSongIds,
    )
    val collectBusyKeys by viewModel.collectBusyKeys.collectAsStateWithLifecycle()
    val onFolderShare = rememberFolderShare(viewModel)
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = browseState.loadingBoard,
            onRefresh = { viewModel.refreshBrowse() },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = browseState.loadingBoard,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = boardHeaderHeight),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = boardHeaderHeight)
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 34.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTR = 34.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBL = 0.dp,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = 0.dp,
                            smoothnessAsPercentBR = 60,
                        )
                    )
                    .lxVerticalDragGate(onScrollingChange),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 14.dp,
                    bottom = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(browseState.boardSongs, key = { "board_${it.source}_${it.songmid}" }) { song ->
                    val uiSong = remember(song.source, song.songmid, song.pic) { LxSongMapper.toSong(song) }
                    val isCurrent = stablePlayerState.currentSong?.id == uiSong.id
                    val isSelected = selectedSongIds.contains(uiSong.id)
                    val selectionIndex = getSelectionIndex(uiSong.id)
                    EnhancedSongListItem(
                        song = uiSong,
                        isPlaying = isCurrent && stablePlayerState.isPlaying,
                        isCurrentSong = isCurrent,
                        showAlbumArt = true,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        selectionIndex = selectionIndex,
                        onLongPress = { onSongLongPress(song) },
                        onMoreOptionsClick = { onSongMoreOptions(song) },
                        onClick = {
                            if (isSelectionMode) {
                                onSongSelectionToggle(song)
                            } else {
                                playLxSong(song)
                            }
                        },
                    )
                }
                if (browseState.boardSongs.isNotEmpty()) {
                    item(key = "board_count_footer") {
                        Text(stringResource(R.string.lx_text_total_songs_count, browseState.boardSongs.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .nestedScroll(consumeHorizontalScroll),
            contentAlignment = Alignment.CenterEnd,
        ) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 10.dp, end = 162.dp),
            ) {
                    items(browseState.boards, key = { it.id }) { board ->
                        Surface(
                            onClick = { viewModel.selectBoard(board) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (browseState.selectedBoard?.id == board.id)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = board.name,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (browseState.selectedBoard?.id == board.id)
                                    MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                val boardCollectState = if (browseState.moduleSource == LxSources.KUGOU ||
                    browseState.moduleSource == LxSources.KUWO ||
                    (browseState.moduleSource != LxSources.NETEASE &&
                        !viewModel.accountWritesEnabled)
                ) null else
                    rememberCollectState(
                        viewModel, LxMusicViewModel.CollectKind.PLAYLIST, boardPlaylistId,
                        name = browseState.selectedBoard?.name,
                    )
                val boardCapsule = @Composable {
                    LxFolderActionCapsule(
                        modifier = Modifier.padding(start = 7.dp, end = 17.dp, top = 4.dp, bottom = 4.dp),
                        showLocate = boardLocateVisible,
                        onLocate = {
                            if (boardLocateIndex >= 0) boardScope.launch {
                                listState.animateScrollToItem(boardLocateIndex)
                            }
                        },
                        collectState = boardCollectState,
                        collectBusy = boardPlaylistId != null && collectBusyKeys.contains(
                            viewModel.collectKey(LxMusicViewModel.CollectKind.PLAYLIST, boardPlaylistId)
                        ),
                        onCollectClick = {
                            boardPlaylistId?.let {
                                viewModel.onCollectButtonClick(
                                    LxMusicViewModel.CollectKind.PLAYLIST, it,
                                    name = browseState.selectedBoard?.name,
                                )
                            }
                        },
                        onShare = {
                            boardPlaylistId?.let {
                                onFolderShare(LxMusicViewModel.CollectKind.PLAYLIST, it)
                            }
                        },
                        buttonElevation = if (boardCollectState == null) 4.dp else 0.dp,
                    )
                }
                if (boardCollectState == null) {
                    boardCapsule()
                } else {
                    Surface(
                        shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        boardCapsule()
                    }
                }
            }
        ExpressiveScrollBar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = boardHeaderHeight + 4.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 16.dp),
            listState = listState,
        )
        LxScrollToTopFab(
            listState = listState,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
        )
        if (browseState.boardSongs.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalIconButton(
                    onClick = { shuffleAllPlaylist(browseState.boardSongs, browseState.selectedBoard?.name ?: boardQueueFallback) },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.lx_desc_shuffle), modifier = Modifier.size(22.dp))
                }
                FilledIconButton(
                    onClick = { playAllPlaylist(browseState.boardSongs, browseState.selectedBoard?.name ?: boardQueueFallback) },
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.lx_desc_play_all), modifier = Modifier.size(26.dp))
                }
            }
        }
        LxFloatingSelectionRow(
            visible = isSelectionMode,
            selectedCount = selectedSongIds.size,
            onSelectAll = {
                multiSelectionState.selectAll(browseState.boardSongs.map { LxSongMapper.toSong(it) })
            },
            onDeselect = { multiSelectionState.clearSelection() },
            onOptionsClick = onSelectionOptionsClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = boardHeaderHeight + 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LxPlaylistPage(
    browseState: LxMusicViewModel.BrowseState,
    viewModel: LxMusicViewModel,
    playerViewModel: PlayerViewModel,
    playLxSong: (com.minoppol.music.data.lxmusic.LxSong) -> Unit,
    playAllPlaylist: (List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit,
    shuffleAllPlaylist: (List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit,
    bottomBarHeightDp: Dp,
    onScrollingChange: (Boolean) -> Unit = {},
    onSongMoreOptions: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    onSongSelectionToggle: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    multiSelectionState: MultiSelectionStateHolder = MultiSelectionStateHolder(),
    onSelectionOptionsClick: () -> Unit = {},
) {
    val playlistListState = rememberLazyListState()
    val detailListState = rememberLazyListState()
    val listState = if (browseState.selectedPlaylist != null) detailListState else playlistListState

    val pullToRefreshState = rememberPullToRefreshState()
    val isUserPlaylistMode = browseState.userPlaylistUid != null || browseState.loadingUserPlaylists
    val isRefreshing = if (browseState.selectedPlaylist != null) browseState.loadingPlaylistSongs
    else if (isUserPlaylistMode) browseState.loadingUserPlaylists
    else if (browseState.isPlaylistSearchMode) browseState.searchingPlaylists
    else browseState.loadingPlaylists
    val density = LocalDensity.current
    val mode = when {
        browseState.selectedPlaylist != null -> "detail"
        isUserPlaylistMode -> "user"
        browseState.isPlaylistSearchMode -> "search"
        else -> "category"
    }
    var headerHeight by remember(mode) {
        mutableStateOf(
            when (mode) {
                "detail" -> 50.dp
                "user", "search" -> 110.dp
                else -> 122.dp
            }
        )
    }
    var showNeteaseUidDialog by remember { mutableStateOf(false) }
    val consumeHorizontalScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource
            ): Offset = Offset(available.x, 0f)
            override suspend fun onPreFling(available: Velocity): Velocity = Velocity(available.x, 0f)
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val detailSongIds = remember(browseState.playlistSongs) {
        browseState.playlistSongs.map { LxSongMapper.unifiedSongId(it.source, it.songmid).toString() }
    }
    val scope = rememberCoroutineScope()
    val detailPlaylistId = browseState.selectedPlaylist?.id
    LaunchedEffect(detailPlaylistId) {
        if (detailPlaylistId != null) viewModel.ensureCollectDataLoaded()
    }
    val (detailLocateVisible, detailLocateIndex) = rememberLocateButtonState(
        listState = detailListState,
        currentSongId = stablePlayerState.currentSong?.id,
        songIds = detailSongIds,
    )
    val collectBusyKeys by viewModel.collectBusyKeys.collectAsStateWithLifecycle()
    val onFolderShare = rememberFolderShare(viewModel)
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshBrowse() },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState, isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = headerHeight),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = headerHeight)
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 34.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTR = 34.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBL = 0.dp,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = 0.dp,
                            smoothnessAsPercentBR = 60,
                        )
                    )
                    .lxVerticalDragGate(onScrollingChange),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 14.dp,
                    bottom = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (browseState.selectedPlaylist == null) {
                    val listPlaylists = when {
                        isUserPlaylistMode -> browseState.userPlaylists
                        browseState.isPlaylistSearchMode -> browseState.playlistSearchResults
                        else -> browseState.playlists
                    }
                    items(listPlaylists, key = { "pl_${it.id}" }) { playlist ->
                        LxPlaylistItem(playlist = playlist, onClick = { viewModel.selectPlaylist(playlist) })
                    }
                    val playlistCount = listPlaylists.size
                    if (playlistCount > 0) {
                        item(key = "playlist_count_footer") {
                            Text(stringResource(R.string.lx_text_total_playlists_count, playlistCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                } else {
                    items(browseState.playlistSongs, key = { "pls_${it.source}_${it.songmid}" }) { song ->
                    val uiSong = remember(song.source, song.songmid, song.pic) { LxSongMapper.toSong(song) }
                        LaunchedEffect(song) { viewModel.loadPlaylistSongPicIfMissing(song) }
                        val isCurrent = stablePlayerState.currentSong?.id == uiSong.id
                        val isSelected = selectedSongIds.contains(uiSong.id)
                        val selectionIndex = getSelectionIndex(uiSong.id)
                        EnhancedSongListItem(
                            song = uiSong,
                            isPlaying = isCurrent && stablePlayerState.isPlaying,
                            isCurrentSong = isCurrent,
                            showAlbumArt = true,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            selectionIndex = selectionIndex,
                            onLongPress = { onSongLongPress(song) },
                            onMoreOptionsClick = { onSongMoreOptions(song) },
                            onClick = {
                                if (isSelectionMode) {
                                    onSongSelectionToggle(song)
                                } else {
                                    playLxSong(song)
                                }
                            },
                        )
                    }
                    if (browseState.playlistSongsHasMore) {
                        item(key = "playlist_songs_load_more") {
                            LaunchedEffect(Unit) { viewModel.loadMorePlaylistSongs() }
                            LxLoadMoreButton(
                                loading = browseState.playlistSongsLoadingMore,
                                onClick = viewModel::loadMorePlaylistSongs,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                    if (browseState.playlistSongs.isNotEmpty()) {
                        item(key = "playlist_song_count_footer") {
                            Text(stringResource(R.string.lx_text_total_songs_count, browseState.playlistSongs.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
                if (browseState.error != null) {
                    item {
                        Text(browseState.error, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .onGloballyPositioned { coords ->
                    val h = with(density) { coords.size.height.toDp() }
                    if (h > 0.dp && h != headerHeight) headerHeight = h
                },
        ) {
            if (browseState.selectedPlaylist != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = { viewModel.backToPlaylistList() },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.lx_text_playlist_list), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    browseState.selectedPlaylist?.name?.takeIf { it.isNotBlank() }?.let { pName ->
                        LxFolderTitleText(text = pName, modifier = Modifier.weight(1f))
                    }
                    LxFolderActionCapsule(
                        showLocate = detailLocateVisible,
                        onLocate = {
                            if (detailLocateIndex >= 0) scope.launch {
                                detailListState.animateScrollToItem(detailLocateIndex)
                            }
                        },
                        collectState = if (browseState.moduleSource != LxSources.NETEASE &&
                                !viewModel.accountWritesEnabled
                        ) null else
                            rememberCollectState(
                                viewModel, LxMusicViewModel.CollectKind.PLAYLIST, detailPlaylistId,
                                gid = browseState.selectedPlaylist?.originGid,
                                name = browseState.selectedPlaylist?.name,
                            ),
                        collectBusy = detailPlaylistId != null && collectBusyKeys.contains(
                            viewModel.collectKey(LxMusicViewModel.CollectKind.PLAYLIST, detailPlaylistId)
                        ),
                        onCollectClick = {
                            detailPlaylistId?.let {
                                viewModel.onCollectButtonClick(
                                    LxMusicViewModel.CollectKind.PLAYLIST, it,
                                    gid = browseState.selectedPlaylist?.originGid,
                                    name = browseState.selectedPlaylist?.name,
                                )
                            }
                        },
                        onShare = {
                            detailPlaylistId?.let {
                                onFolderShare(LxMusicViewModel.CollectKind.PLAYLIST, it)
                            }
                        },
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            LxDockedSearchBar(
                                query = browseState.playlistSearchKeyword,
                                onQueryChange = viewModel::updatePlaylistSearchQuery,
                                onSearch = { if (browseState.playlistSearchKeyword.isNotBlank())
                                    viewModel.submitPlaylistInput(browseState.playlistSearchKeyword) },
                                onClear = { viewModel.updatePlaylistSearchQuery("") },
                                placeholder = stringResource(R.string.lx_placeholder_search_playlist),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                enableInnerBox = false,
                            )
                        }
                        if (browseState.moduleSource == LxSources.NETEASE
                            && !browseState.isPlaylistSearchMode && !isUserPlaylistMode
                        ) {
                            FilledIconButton(
                                onClick = { showNeteaseUidDialog = true },
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .shadow(4.dp, CircleShape)
                                    .size(40.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = stringResource(R.string.lx_desc_fetch_user_playlists),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    if (isUserPlaylistMode) {
                        Surface(
                            onClick = { viewModel.exitUserPlaylists() },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                                .compositeOver(MaterialTheme.colorScheme.surface),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    browseState.userPlaylistName?.takeIf { it.isNotBlank() }
                                        ?.let { stringResource(R.string.lx_text_netease_user_with_name, it) } ?: stringResource(R.string.lx_text_back_to_categories),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else if (browseState.isPlaylistSearchMode) {
                        Surface(
                            onClick = { viewModel.exitPlaylistSearch() },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                                .compositeOver(MaterialTheme.colorScheme.surface),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.lx_text_back_to_categories), style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .nestedScroll(consumeHorizontalScroll),
                        ) {
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                items(browseState.playlistTags, key = { it.id }) { tag ->
                                    Surface(
                                        onClick = { viewModel.selectPlaylistTag(tag) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (browseState.selectedTag?.id == tag.id)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(tag.name,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (browseState.selectedTag?.id == tag.id)
                                                MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ExpressiveScrollBar(
            modifier = Modifier.align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = headerHeight + 4.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 16.dp),
            listState = listState,
        )
        LxScrollToTopFab(
            listState = listState,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
        )
        if (browseState.selectedPlaylist != null && browseState.playlistSongs.isNotEmpty()) {
            LxBottomPlayActions(
                onShuffle = {
                    shuffleAllPlaylist(
                        browseState.playlistSongs, browseState.selectedPlaylist.name
                    )
                },
                onPlayAll = {
                    playAllPlaylist(
                        browseState.playlistSongs, browseState.selectedPlaylist.name
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
            )
        }
        if (showNeteaseUidDialog) {
            NeteaseUserPlaylistDialog(
                onDismiss = { showNeteaseUidDialog = false },
                onConfirm = { input ->
                    showNeteaseUidDialog = false
                    viewModel.loadNeteaseUserPlaylists(input)
                },
            )
        }
        LxFloatingSelectionRow(
            visible = isSelectionMode,
            selectedCount = selectedSongIds.size,
            onSelectAll = {
                multiSelectionState.selectAll(browseState.playlistSongs.map { LxSongMapper.toSong(it) })
            },
            onDeselect = { multiSelectionState.clearSelection() },
            onOptionsClick = onSelectionOptionsClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = headerHeight + 4.dp),
        )
    }
}

@Composable
private fun NeteaseUserPlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val fieldColor = MaterialTheme.colorScheme.secondaryContainer
        .compositeOver(MaterialTheme.colorScheme.surface)
    val onFieldColor = MaterialTheme.colorScheme.onSecondaryContainer
    val dialogColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val doConfirm: () -> Unit = {
        if (text.isNotBlank()) {
            focusManager.clearFocus()
            keyboardController?.hide()
            onConfirm(text)
        }
    }
    AlertDialog(
        containerColor = dialogColor,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lx_title_netease_user_playlists)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.lx_msg_netease_user_playlists_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(fieldColor)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = onFieldColor),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text, imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { doConfirm() }),
                    ) { innerTextField ->
                        if (text.isEmpty()) {
                            Text(stringResource(R.string.lx_placeholder_uid_or_link),
                                style = MaterialTheme.typography.bodyLarge,
                                color = onFieldColor.copy(alpha = 0.6f))
                        }
                        innerTextField()
                    }
                }
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onDismiss,
                    shape = lxDialogCancelShape,
                    modifier = Modifier
                        .shadow(2.dp, lxDialogCancelShape)
                        .size(42.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lx_desc_cancel))
                }
                FilledTonalIconButton(
                    enabled = text.isNotBlank(),
                    onClick = { doConfirm() },
                    shape = lxDialogConfirmShape,
                    modifier = Modifier
                        .then(
                            if (text.isNotBlank()) Modifier.shadow(2.dp, lxDialogConfirmShape)
                            else Modifier
                        )
                        .size(42.dp),
                ) {
                    Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.lx_desc_view_playlists))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LxSearchPage(
    browseState: LxMusicViewModel.BrowseState,
    searchState: LxMusicViewModel.SearchState,
    viewModel: LxMusicViewModel,
    playerViewModel: PlayerViewModel,
    playLxSong: (com.minoppol.music.data.lxmusic.LxSong) -> Unit,
    playAllPlaylist: (List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit,
    shuffleAllPlaylist: (List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit,
    bottomBarHeightDp: Dp,
    onScrollingChange: (Boolean) -> Unit = {},
    onSongMoreOptions: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    onSongSelectionToggle: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    multiSelectionState: MultiSelectionStateHolder = MultiSelectionStateHolder(),
    onSelectionOptionsClick: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val searchResultsQueueName = stringResource(R.string.lx_text_search_results)

    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(searchState.searchEpoch) {
        if (searchState.searchEpoch <= 0L) return@LaunchedEffect
        viewModel.searchState.first {
            if (it.searchEpoch != searchState.searchEpoch) false
            else when (it.type) {
                LxMusicViewModel.SearchType.SONG -> it.results.isNotEmpty()
                LxMusicViewModel.SearchType.ARTIST -> it.artistResults.isNotEmpty()
                LxMusicViewModel.SearchType.ALBUM -> it.albumResults.isNotEmpty()
            }
        }
        withFrameNanos { }
        listState.requestScrollToItem(0, 0)
    }

    LaunchedEffect(searchState.type) {
        listState.requestScrollToItem(0, 0)
    }

    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val searchSongIds = remember(searchState.results) {
        searchState.results.map { LxSongMapper.unifiedSongId(it.source, it.songmid).toString() }
    }
    val searchLeadingItems = if (searchState.error != null) 1 else 0

    Box(modifier = Modifier.fillMaxSize()) {
        val isSearching = when (searchState.type) {
            LxMusicViewModel.SearchType.ARTIST -> searchState.searchingArtists
            LxMusicViewModel.SearchType.ALBUM -> searchState.searchingAlbums
            else -> searchState.searching
        }
        PullToRefreshBox(
            isRefreshing = isSearching,
            onRefresh = { viewModel.refreshBrowse() },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState, isRefreshing = isSearching,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 34.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTR = 34.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBL = 0.dp,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = 0.dp,
                            smoothnessAsPercentBR = 60,
                        )
                    )
                    .lxVerticalDragGate(onScrollingChange),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 14.dp,
                    top = 102.dp,
                    bottom = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (searchState.type == LxMusicViewModel.SearchType.ARTIST) {
                    if (searchState.searchingArtists && searchState.artistResults.isEmpty()) {
                        item(key = "artist_loading") {
                            LxLoadingSkeletonRows(3)
                        }
                    } else {
                        if (searchState.artistResults.isNotEmpty()) {
                            mineArtistItems(
                                artists = searchState.artistResults,
                                onClick = viewModel::openArtistFromSearch,
                            )
                        } else if (searchState.error != null) {
                            item(key = "artist_error") {
                                Text(
                                    searchState.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        if (searchState.canLoadMoreArtists) {
                            item(key = "load_more_artist") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LxLoadMoreButton(
                                        loading = searchState.loadingMoreArtists,
                                        onClick = { viewModel.loadMore() },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                } else if (searchState.type == LxMusicViewModel.SearchType.ALBUM) {
                    if (searchState.searchingAlbums && searchState.albumResults.isEmpty()) {
                        item(key = "album_loading") {
                            LxLoadingSkeletonRows(3)
                        }
                    } else {
                        if (searchState.albumResults.isNotEmpty()) {
                            mineAlbumItems(
                                albums = searchState.albumResults,
                                onClick = viewModel::openAlbumFromSearch,
                            )
                        } else if (searchState.error != null) {
                            item(key = "album_error") {
                                Text(
                                    searchState.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        if (searchState.canLoadMoreAlbums) {
                            item(key = "load_more_album") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LxLoadMoreButton(
                                        loading = searchState.loadingMoreAlbums,
                                        onClick = { viewModel.loadMore() },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                } else {
                if (searchState.error != null) {
                    item {
                        Text(searchState.error, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                if (searchState.error == null && !searchState.searching &&
                    searchState.searchedSource != null && searchState.results.isEmpty()
                ) {
                    item(key = "search_empty") {
                        Text(
                            stringResource(R.string.lx_search_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                        )
                    }
                }
                items(searchState.results, key = { "${it.source}_${it.songmid}" }) { song ->
                    val uiSong = remember(song.source, song.songmid, song.pic) { LxSongMapper.toSong(song) }
                    val isCurrent = stablePlayerState.currentSong?.id == uiSong.id
                    val isSelected = selectedSongIds.contains(uiSong.id)
                    val selectionIndex = getSelectionIndex(uiSong.id)
                    EnhancedSongListItem(
                        song = uiSong,
                        isPlaying = isCurrent && stablePlayerState.isPlaying,
                        isCurrentSong = isCurrent,
                        showAlbumArt = true,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        selectionIndex = selectionIndex,
                        onLongPress = { onSongLongPress(song) },
                        onMoreOptionsClick = { onSongMoreOptions(song) },
                        onClick = {
                            if (isSelectionMode) {
                                onSongSelectionToggle(song)
                            } else {
                                playLxSong(song)
                            }
                        },
                    )
                }
                if (searchState.results.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.lx_text_total_songs_count, searchState.results.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                if (searchState.canLoadMore) {
                    item(key = "load_more") {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center) {
                            LxLoadMoreButton(
                                loading = searchState.loadingMore,
                                onClick = { viewModel.loadMore() },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
                }
            }
        }
        ExpressiveScrollBar(
            modifier = Modifier.align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = 102.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 16.dp),
            listState = listState,
        )
        LxLocateCurrentSongFab(
            listState = listState,
            currentSongId = stablePlayerState.currentSong?.id,
            songIds = searchSongIds,
            leadingItems = searchLeadingItems,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 74.dp),
        )
        LxScrollToTopFab(
            listState = listState,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
        )
        if (searchState.type == LxMusicViewModel.SearchType.SONG && searchState.results.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalIconButton(
                    onClick = { shuffleAllPlaylist(searchState.results, searchResultsQueueName) },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.lx_desc_shuffle), modifier = Modifier.size(22.dp))
                }
                FilledIconButton(
                    onClick = { playAllPlaylist(searchState.results, searchResultsQueueName) },
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.lx_desc_play_all), modifier = Modifier.size(26.dp))
                }
            }
        }
        LxDockedSearchBar(
            query = searchState.keyword,
            onQueryChange = viewModel::updateSearchQuery,
            onSearch = { if (searchState.keyword.isNotBlank()) viewModel.search(searchState.keyword) },
            onClear = { viewModel.updateSearchQuery("") },
            placeholder = stringResource(R.string.lx_placeholder_search_songs),
            modifier = Modifier.align(Alignment.TopCenter)
                .padding(horizontal = 20.dp)
                .offset(y = 8.dp),
            enableInnerBox = false,
        )
        LxSearchTypeSwitch(
            selected = searchState.type,
            onSelect = viewModel::setSearchType,
            modifier = Modifier.align(Alignment.TopCenter)
                .offset(y = 64.5.dp),
        )
        LxFloatingSelectionRow(
            visible = isSelectionMode,
            selectedCount = selectedSongIds.size,
            onSelectAll = {
                multiSelectionState.selectAll(searchState.results.map { LxSongMapper.toSong(it) })
            },
            onDeselect = { multiSelectionState.clearSelection() },
            onOptionsClick = onSelectionOptionsClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 102.dp + 4.dp),
        )
    }
}

@Composable
private fun LxSearchTypeSwitch(
    selected: LxMusicViewModel.SearchType,
    onSelect: (LxMusicViewModel.SearchType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldColor = MaterialTheme.colorScheme.secondaryContainer
        .compositeOver(MaterialTheme.colorScheme.surface)
    val fraction by animateFloatAsState(
        targetValue = when (selected) {
            LxMusicViewModel.SearchType.SONG -> 0f
            LxMusicViewModel.SearchType.ALBUM -> 1f
            LxMusicViewModel.SearchType.ARTIST -> 2f
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "searchTypeThumb",
    )
    val songColor by animateColorAsState(
        targetValue = if (selected == LxMusicViewModel.SearchType.SONG)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220), label = "songColor",
    )
    val artistColor by animateColorAsState(
        targetValue = if (selected == LxMusicViewModel.SearchType.ARTIST)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220), label = "artistColor",
    )
    val albumColor by animateColorAsState(
        targetValue = if (selected == LxMusicViewModel.SearchType.ALBUM)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220), label = "albumColor",
    )
    BoxWithConstraints(
        modifier = modifier
            .width(162.dp)
            .height(30.dp)
            .shadow(4.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(fieldColor),
    ) {
        val thirdWidthPx = with(LocalDensity.current) { (maxWidth / 3).toPx() }
        Box(
            Modifier
                .offset { IntOffset((fraction * thirdWidthPx).roundToInt(), 0) }
                .width(maxWidth / 3)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
        Row(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(LxMusicViewModel.SearchType.SONG) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.lx_text_song_type),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = songColor,
                )
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(LxMusicViewModel.SearchType.ALBUM) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.lx_text_album_type),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = albumColor,
                )
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(LxMusicViewModel.SearchType.ARTIST) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.lx_text_artist_type),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = artistColor,
                )
            }
        }
    }
}

private class LxDetailHeaderState(
    val minHeightPx: Float,
    val maxHeightPx: Float,
    val height: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    val connection: NestedScrollConnection,
)

@Composable
private fun rememberLxDetailHeaderState(listState: LazyListState): LxDetailHeaderState {
    val density = LocalDensity.current
    val statusBarHeightPx = with(density) { WindowInsets.statusBars.getTop(this).toFloat() }
    val minHeightPx = with(density) { 64.dp.toPx() } + statusBarHeightPx
    val maxHeightPx = with(density) { 300.dp.toPx() }
    val height = remember(minHeightPx, maxHeightPx) { Animatable(maxHeightPx) }
    val scope = rememberCoroutineScope()
    val connection = remember(minHeightPx, maxHeightPx, listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0
                if (!isScrollingDown &&
                    (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }
                val previousHeight = height.value
                val newHeight = (previousHeight + delta).coerceIn(minHeightPx, maxHeightPx)
                val consumed = newHeight - previousHeight
                if (consumed.roundToInt() != 0) scope.launch { height.snapTo(newHeight) }
                val canConsumeScroll = !(isScrollingDown && newHeight == minHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }
    LaunchedEffect(listState.isScrollInProgress, minHeightPx, maxHeightPx) {
        if (!listState.isScrollInProgress) {
            val shouldExpand = height.value > (minHeightPx + maxHeightPx) / 2
            val canExpand = listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
            val target = if (shouldExpand && canExpand) maxHeightPx else minHeightPx
            if (height.value != target) {
                height.animateTo(target, spring(stiffness = Spring.StiffnessMedium))
            }
        }
    }
    return remember(minHeightPx, maxHeightPx) {
        LxDetailHeaderState(minHeightPx, maxHeightPx, height, connection)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LxCollapsingDetailHeader(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    collapseFraction: Float,
    headerHeightDp: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onShuffle: (() -> Unit)? = null,
    showShuffleFab: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    expandedBottomContent: (@Composable () -> Unit)? = null,
    shuffleContentDescription: String? = null,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val statusBarColor =
        if (LocalPixelPlayerDarkTheme.current) Color.Black.copy(alpha = 0.6f)
        else Color.White.copy(alpha = 0.4f)
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val expandedContentAlpha = 1f - solidAlpha
    val displayUrl = artworkUrl?.takeIf { it.isNotBlank() }
    val headerOverlayBrush = remember(surfaceColor, expandedContentAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                surfaceColor.copy(alpha = 0.22f * expandedContentAlpha),
                surfaceColor.copy(alpha = 0.82f * expandedContentAlpha),
                surfaceColor,
            )
        )
    }
    val statusBarBrush = remember(statusBarColor) {
        Brush.verticalGradient(colors = listOf(statusBarColor, Color.Transparent))
    }
    val expandedStatusBarFallback = remember(statusBarColor, surfaceColor) {
        statusBarColor.compositeOver(surfaceColor)
    }
    val fallbackStatusBarColor = remember(expandedStatusBarFallback, surfaceColor, solidAlpha) {
        lerpColor(expandedStatusBarFallback, surfaceColor, solidAlpha)
    }
    val titleVerticalBias = lerp(1f, -1f, collapseFraction)
    val shuffleAlignment = BiasAlignment(horizontalBias = 1f, verticalBias = titleVerticalBias)

    PixelPlayerStatusBarStyle(color = fallbackStatusBarColor)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeightDp)
            .clipToBounds()
    ) {
        if (expandedContentAlpha > 0.01f) {
            if (displayUrl != null) {
                SmartImage(
                    model = displayUrl,
                    contentDescription = title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    allowHardware = true,
                    crossfadeDurationMillis = 0,
                    alpha = expandedContentAlpha,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MusicIconPattern(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = expandedContentAlpha },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(headerOverlayBrush)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(statusBarBrush)
                .align(Alignment.TopCenter)
        )

        CollapsibleCommonTopBar(
            title = title,
            subtitle = subtitle,
            collapseFraction = collapseFraction,
            headerHeight = headerHeightDp,
            onBackClick = onBack,
            containerColor = surfaceColor.copy(alpha = solidAlpha),
            collapsedTitleStartPadding = 68.dp,
            expandedTitleStartPadding = 24.dp,
            collapsedTitleEndPadding = 24.dp,
            expandedTitleEndPadding = 136.dp,
            containerHeightRange = 112.dp to 56.dp,
            titleStyle = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = RoundedSans,
                fontWeight = FontWeight.SemiBold,
                textGeometricTransform = TextGeometricTransform(scaleX = 1.08f)
            ),
            titleScaleRange = 1f to 1f,
            titleFontSizeRange = 30.sp to 18.sp,
            maxLines = if (collapseFraction < 0.5f) 2 else 1,
            collapsedSubtitleMaxLines = 1,
            expandedSubtitleMaxLines = 2,
            contentColor = MaterialTheme.colorScheme.onSurface,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            fadeSubtitleOnCollapse = false,
            syncStatusBarWithContainer = false,
            actions = actions,
        )

        expandedBottomContent?.let { content ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 136.dp, bottom = 14.dp)
                    .graphicsLayer { alpha = expandedContentAlpha },
            ) {
                content()
            }
        }

        if (showShuffleFab && onShuffle != null) {
            LargeExtendedFloatingActionButton(
                onClick = onShuffle,
                shape = RoundedStarShape(sides = 8, curve = 0.05, rotation = 0f),
                modifier = Modifier
                    .align(shuffleAlignment)
                    .statusBarsPadding()
                    .padding(end = 16.dp)
                    .graphicsLayer {
                        scaleX = expandedContentAlpha
                        scaleY = expandedContentAlpha
                        alpha = expandedContentAlpha
                    }
            ) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = shuffleContentDescription,
                )
            }
        }
    }
}

@Composable
private fun LxSearchArtistPage(
    state: LxMusicViewModel.SearchArtistPageState,
    viewModel: LxMusicViewModel,
    playerViewModel: PlayerViewModel,
    playLxSong: (com.minoppol.music.data.lxmusic.LxSong) -> Unit,
    playAllPlaylist: (List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit,
    shuffleAllPlaylist: (List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit,
    bottomBarHeightDp: Dp,
    showFollow: Boolean,
    onBack: () -> Unit,
    onScrollingChange: (Boolean) -> Unit = {},
    onSongMoreOptions: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    onSongSelectionToggle: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    multiSelectionState: MultiSelectionStateHolder = MultiSelectionStateHolder(),
    onSelectionOptionsClick: () -> Unit = {},
) {
    val artist = state.artist ?: return
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    state.selectedAlbum?.let { album ->
        BackHandler {
            if (isSelectionMode) multiSelectionState.clearSelection()
            else viewModel.backFromSearchArtistAlbum()
        }
        val albumListState = rememberLazyListState()
        LaunchedEffect(album.id) { runCatching { albumListState.scrollToItem(0) } }
        val albumSongIds = remember(state.albumSongs) {
            state.albumSongs.map {
                com.minoppol.music.data.lxmusic.LxSongMapper.unifiedSongId(it.source, it.songmid).toString()
            }
        }
        LaunchedEffect(album.id) { viewModel.ensureCollectDataLoaded() }
        val albumKind = LxMusicViewModel.CollectKind.ALBUM
        val albumCollectBusyKeys by viewModel.collectBusyKeys.collectAsStateWithLifecycle()
        val albumOnFolderShare = rememberFolderShare(viewModel)
        val albumHeaderState = rememberLxDetailHeaderState(albumListState)
        val albumDensity = LocalDensity.current
        val albumHeaderHeightDp = with(albumDensity) { albumHeaderState.height.value.toDp() }
        val albumMinHeaderDp = with(albumDensity) { albumHeaderState.minHeightPx.toDp() }
        val albumCollapseFraction by remember(albumHeaderState) {
            derivedStateOf {
                1f - (
                    (albumHeaderState.height.value - albumHeaderState.minHeightPx) /
                        (albumHeaderState.maxHeightPx - albumHeaderState.minHeightPx)
                    ).coerceIn(0f, 1f)
            }
        }
        val albumBottomPadding = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap
        val showAlbumScrollBar by remember {
            derivedStateOf {
                albumCollapseFraction > 0.95f &&
                    (albumListState.canScrollForward || albumListState.canScrollBackward)
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(albumHeaderState.connection),
            ) {
                LazyColumn(
                    state = albumListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(
                                0,
                                (albumHeaderState.height.value - albumHeaderState.minHeightPx).roundToInt(),
                            )
                        }
                        .lxVerticalDragGate(onScrollingChange),
                    contentPadding = PaddingValues(
                        top = albumMinHeaderDp + 8.dp,
                        start = 16.dp,
                        end = if (showAlbumScrollBar) 24.dp else 16.dp,
                        bottom = albumBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (state.albumSongs.isNotEmpty()) {
                        item(key = "artist_album_play_all") {
                            LxSelectionSwitch(
                                inSelectionMode = isSelectionMode,
                                selectedCount = selectedSongIds.size,
                                onSelectAll = {
                                    multiSelectionState.selectAll(state.albumSongs.map { LxSongMapper.toSong(it) })
                                },
                                onDeselect = { multiSelectionState.clearSelection() },
                                onOptionsClick = onSelectionOptionsClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FilledIconButton(
                                        onClick = { playAllPlaylist(state.albumSongs, album.name) },
                                        shape = CircleShape,
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = stringResource(R.string.lx_desc_play_all),
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    mineSongItems(
                        songs = state.albumSongs,
                        loading = state.albumLoading,
                        error = null,
                        stablePlayerState = stablePlayerState,
                        playLxSong = playLxSong,
                        onSongMoreOptions = onSongMoreOptions,
                        isSelectionMode = isSelectionMode,
                        selectedSongIds = selectedSongIds,
                        onSongLongPress = onSongLongPress,
                        onSongSelectionToggle = onSongSelectionToggle,
                        getSelectionIndex = getSelectionIndex,
                    )
                }
                if (showAlbumScrollBar) {
                    ExpressiveScrollBar(
                        listState = albumListState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(
                                top = albumMinHeaderDp + 12.dp,
                                end = 4.dp,
                                bottom = albumBottomPadding + 8.dp,
                            ),
                    )
                }
                LxCollapsingDetailHeader(
                    title = album.name,
                    subtitle = album.artistName?.takeIf { it.isNotBlank() }
                        ?.let { "$it · ${formatSongCount(state.albumSongs.size)}" }
                        ?: formatSongCount(state.albumSongs.size),
                    artworkUrl = album.pic,
                    collapseFraction = albumCollapseFraction,
                    headerHeightDp = albumHeaderHeightDp,
                    onBack = viewModel::backFromSearchArtistAlbum,
                    onShuffle = if (state.albumSongs.isNotEmpty()) {
                        { shuffleAllPlaylist(state.albumSongs, album.name) }
                    } else null,
                    shuffleContentDescription = stringResource(R.string.lx_desc_shuffle),
                    actions = {
                        Box(modifier = Modifier.padding(end = 12.dp, top = 4.dp)) {
                            LxFolderActionCapsule(
                                showLocate = false,
                                onLocate = {},
                                collectState = if (state.source == LxSources.KUGOU ||
                                    (state.source != LxSources.NETEASE && !viewModel.accountWritesEnabled)
                                ) null else
                                    rememberCollectState(viewModel, albumKind, album.id, name = album.name),
                                collectBusy = albumCollectBusyKeys.contains(
                                    viewModel.collectKey(albumKind, album.id),
                                ),
                                onCollectClick = {
                                    viewModel.onCollectButtonClick(albumKind, album.id, name = album.name)
                                },
                                onShare = { albumOnFolderShare(albumKind, album.id) },
                            )
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                if (state.albumSongs.isNotEmpty()) {
                    LxLocateCurrentSongFab(
                        listState = albumListState,
                        currentSongId = stablePlayerState.currentSong?.id,
                        songIds = albumSongIds,
                        leadingItems = 1,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 74.dp),
                    )
                    LxScrollToTopFab(
                        listState = albumListState,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
                    )
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(artist.id) { runCatching { listState.scrollToItem(0) } }
    val songIds = remember(state.songs) {
        state.songs.map { com.minoppol.music.data.lxmusic.LxSongMapper.unifiedSongId(it.source, it.songmid).toString() }
    }
    val artistHeaderState = rememberLxDetailHeaderState(listState)
    val artistDensity = LocalDensity.current
    val artistHeaderHeightDp = with(artistDensity) { artistHeaderState.height.value.toDp() }
    val artistMinHeaderDp = with(artistDensity) { artistHeaderState.minHeightPx.toDp() }
    val artistCollapseFraction by remember(artistHeaderState) {
        derivedStateOf {
            1f - (
                (artistHeaderState.height.value - artistHeaderState.minHeightPx) /
                    (artistHeaderState.maxHeightPx - artistHeaderState.minHeightPx)
                ).coerceIn(0f, 1f)
        }
    }
    val artistBottomPadding = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap
    val showArtistScrollBar by remember {
        derivedStateOf {
            artistCollapseFraction > 0.95f &&
                (listState.canScrollForward || listState.canScrollBackward)
        }
    }
    val artistSubtitle = if (state.tab == "songs") {
        formatSongCount(state.songsTotal.takeIf { it > 0 } ?: state.songs.size)
    } else null
    val artistHasBrief = !artist.briefDesc.isNullOrBlank()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(artistHeaderState.connection),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            0,
                            (artistHeaderState.height.value - artistHeaderState.minHeightPx).roundToInt(),
                        )
                    }
                    .lxVerticalDragGate(onScrollingChange),
                contentPadding = PaddingValues(
                    top = artistMinHeaderDp + 8.dp,
                    start = 16.dp,
                    end = if (showArtistScrollBar) 24.dp else 16.dp,
                    bottom = artistBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "artist_tabs") {
                    LxSelectionSwitch(
                        inSelectionMode = isSelectionMode,
                        selectedCount = selectedSongIds.size,
                        onSelectAll = {
                            multiSelectionState.selectAll(state.songs.map { LxSongMapper.toSong(it) })
                        },
                        onDeselect = { multiSelectionState.clearSelection() },
                        onOptionsClick = onSelectionOptionsClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtistTabChip(stringResource(R.string.lx_tab_hot_songs), state.tab == "songs") {
                                viewModel.selectSearchArtistTab("songs")
                            }
                            Spacer(Modifier.width(8.dp))
                            ArtistTabChip(stringResource(R.string.lx_tab_all_albums), state.tab == "albums") {
                                viewModel.selectSearchArtistTab("albums")
                            }
                            if (state.tab == "songs" && state.songs.isNotEmpty()) {
                                Spacer(Modifier.weight(1f))
                                FilledIconButton(
                                    onClick = { playAllPlaylist(state.songs, artist.name) },
                                    shape = CircleShape,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = stringResource(R.string.lx_desc_play_all),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (artistHasBrief) {
                    item(key = "artist_brief") {
                        var briefExpanded by remember(artist.id) { mutableStateOf(false) }
                        Text(
                            text = artist.briefDesc.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (briefExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { briefExpanded = !briefExpanded },
                        )
                    }
                }
                if (state.tab == "songs") {
                    mineSongItems(
                        songs = state.songs,
                        loading = state.loading,
                        error = state.error,
                        stablePlayerState = stablePlayerState,
                        playLxSong = playLxSong,
                        onSongMoreOptions = onSongMoreOptions,
                        isSelectionMode = isSelectionMode,
                        selectedSongIds = selectedSongIds,
                        onSongLongPress = onSongLongPress,
                        onSongSelectionToggle = onSongSelectionToggle,
                        getSelectionIndex = getSelectionIndex,
                    )
                    if (!state.loading && state.songs.isNotEmpty() &&
                        (state.songsHasMore || state.songsLoadingMore)
                    ) {
                        item(key = "search_artist_load_more") {
                            LxLoadMoreButton(
                                loading = state.songsLoadingMore,
                                onClick = viewModel::loadMoreSearchArtistSongs,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                } else {
                    mineAlbumItems(
                        albums = state.albums,
                        onClick = viewModel::openSearchArtistAlbum,
                        loading = state.albumsLoading,
                    )
                    if (state.albumsLoading) item(key = "search_artist_albums_loading") { MineLoadingRow() }
                }
            }

            LxCollapsingDetailHeader(
                title = artist.name,
                subtitle = artistSubtitle,
                artworkUrl = artist.pic,
                collapseFraction = artistCollapseFraction,
                headerHeightDp = artistHeaderHeightDp,
                onBack = onBack,
                onShuffle = if (state.tab == "songs" && state.songs.isNotEmpty()) {
                    { shuffleAllPlaylist(state.songs, artist.name) }
                } else null,
                shuffleContentDescription = stringResource(R.string.lx_desc_shuffle),
                actions = {
                    if (showFollow && state.followed != null) {
                        Box(modifier = Modifier.padding(end = 12.dp, top = 4.dp)) {
                            ArtistFollowButton(
                                followed = state.followed == true,
                                busy = state.followBusy,
                                onClick = viewModel::toggleSearchArtistFollow,
                            )
                        }
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )

            if (showArtistScrollBar) {
                ExpressiveScrollBar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            top = artistMinHeaderDp + 12.dp,
                            end = 4.dp,
                            bottom = artistBottomPadding + 8.dp,
                        ),
                )
            }

            if (state.tab == "songs" && state.songs.isNotEmpty()) {
                LxLocateCurrentSongFab(
                    listState = listState,
                    currentSongId = stablePlayerState.currentSong?.id,
                    songIds = songIds,
                    leadingItems = if (artistHasBrief) 2 else 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 74.dp),
                )
                LxScrollToTopFab(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
                )
            }
        }
    }
}

@Composable
private fun LxSearchAlbumPage(
    state: LxMusicViewModel.SearchAlbumPageState,
    viewModel: LxMusicViewModel,
    playerViewModel: PlayerViewModel,
    playLxSong: (com.minoppol.music.data.lxmusic.LxSong) -> Unit,
    playAllPlaylist: (List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit,
    shuffleAllPlaylist: (List<com.minoppol.music.data.lxmusic.LxSong>, String) -> Unit,
    bottomBarHeightDp: Dp,
    onBack: () -> Unit,
    onScrollingChange: (Boolean) -> Unit = {},
    onSongMoreOptions: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    onSongSelectionToggle: (com.minoppol.music.data.lxmusic.LxSong) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    multiSelectionState: MultiSelectionStateHolder = MultiSelectionStateHolder(),
    onSelectionOptionsClick: () -> Unit = {},
) {
    val album = state.album ?: return
    val listState = rememberLazyListState()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val songIds = remember(state.songs) {
        state.songs.map { com.minoppol.music.data.lxmusic.LxSongMapper.unifiedSongId(it.source, it.songmid).toString() }
    }
    val collectKind = LxMusicViewModel.CollectKind.ALBUM
    LaunchedEffect(album.id) { viewModel.ensureCollectDataLoaded() }
    val collectBusyKeys by viewModel.collectBusyKeys.collectAsStateWithLifecycle()
    val onFolderShare = rememberFolderShare(viewModel)

    val albumHeaderState = rememberLxDetailHeaderState(listState)
    val albumDensity = LocalDensity.current
    val albumHeaderHeightDp = with(albumDensity) { albumHeaderState.height.value.toDp() }
    val albumMinHeaderDp = with(albumDensity) { albumHeaderState.minHeightPx.toDp() }
    val albumCollapseFraction by remember(albumHeaderState) {
        derivedStateOf {
            1f - (
                (albumHeaderState.height.value - albumHeaderState.minHeightPx) /
                    (albumHeaderState.maxHeightPx - albumHeaderState.minHeightPx)
                ).coerceIn(0f, 1f)
        }
    }
    val albumBottomPadding = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap
    val showAlbumScrollBar by remember {
        derivedStateOf {
            albumCollapseFraction > 0.95f &&
                (listState.canScrollForward || listState.canScrollBackward)
        }
    }
    val albumSubtitle = album.artistName?.takeIf { it.isNotBlank() }
        ?.let { "$it · ${formatSongCount(state.songs.size)}" }
        ?: formatSongCount(state.songs.size)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(albumHeaderState.connection),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            0,
                            (albumHeaderState.height.value - albumHeaderState.minHeightPx).roundToInt(),
                        )
                    }
                    .lxVerticalDragGate(onScrollingChange),
                contentPadding = PaddingValues(
                    top = albumMinHeaderDp + 8.dp,
                    start = 16.dp,
                    end = if (showAlbumScrollBar) 24.dp else 16.dp,
                    bottom = albumBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.songs.isNotEmpty()) {
                    item(key = "search_album_play_all") {
                        LxSelectionSwitch(
                            inSelectionMode = isSelectionMode,
                            selectedCount = selectedSongIds.size,
                            onSelectAll = {
                                multiSelectionState.selectAll(state.songs.map { LxSongMapper.toSong(it) })
                            },
                            onDeselect = { multiSelectionState.clearSelection() },
                            onOptionsClick = onSelectionOptionsClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilledIconButton(
                                    onClick = { playAllPlaylist(state.songs, album.name) },
                                    shape = CircleShape,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = stringResource(R.string.lx_desc_play_all),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                mineSongItems(
                    songs = state.songs,
                    loading = state.loading,
                    error = state.error,
                    stablePlayerState = stablePlayerState,
                    playLxSong = playLxSong,
                    onSongMoreOptions = onSongMoreOptions,
                    isSelectionMode = isSelectionMode,
                    selectedSongIds = selectedSongIds,
                    onSongLongPress = onSongLongPress,
                    onSongSelectionToggle = onSongSelectionToggle,
                    getSelectionIndex = getSelectionIndex,
                )
            }
            LxCollapsingDetailHeader(
                title = album.name,
                subtitle = albumSubtitle,
                artworkUrl = album.pic,
                collapseFraction = albumCollapseFraction,
                headerHeightDp = albumHeaderHeightDp,
                onBack = onBack,
                onShuffle = if (state.songs.isNotEmpty()) {
                    { shuffleAllPlaylist(state.songs, album.name) }
                } else null,
                shuffleContentDescription = stringResource(R.string.lx_desc_shuffle),
                actions = {
                    Box(modifier = Modifier.padding(end = 12.dp, top = 4.dp)) {
                        LxFolderActionCapsule(
                            showLocate = false,
                            onLocate = {},
                            collectState = if (state.source == LxSources.KUGOU ||
                                (state.source != LxSources.NETEASE && !viewModel.accountWritesEnabled)
                            ) null else
                                rememberCollectState(viewModel, collectKind, album.id, name = album.name),
                            collectBusy = collectBusyKeys.contains(viewModel.collectKey(collectKind, album.id)),
                            onCollectClick = {
                                viewModel.onCollectButtonClick(collectKind, album.id, name = album.name)
                            },
                            onShare = { onFolderShare(collectKind, album.id) },
                        )
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            if (showAlbumScrollBar) {
                ExpressiveScrollBar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            top = albumMinHeaderDp + 12.dp,
                            end = 4.dp,
                            bottom = albumBottomPadding + 8.dp,
                        ),
                )
            }
            if (state.songs.isNotEmpty()) {
                LxLocateCurrentSongFab(
                    listState = listState,
                    currentSongId = stablePlayerState.currentSong?.id,
                    songIds = songIds,
                    leadingItems = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 74.dp),
                )
                LxScrollToTopFab(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = bottomBarHeightDp + MiniPlayerHeight + 14.dp),
                )
            }
        }
    }
}



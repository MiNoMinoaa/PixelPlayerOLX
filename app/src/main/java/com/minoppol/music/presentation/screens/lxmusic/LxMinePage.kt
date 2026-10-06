package com.minoppol.music.presentation.screens.lxmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.minoppol.music.presentation.components.ShimmerBox
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minoppol.music.data.lxmusic.LxAlbum
import com.minoppol.music.data.lxmusic.LxArtist
import com.minoppol.music.data.lxmusic.LxPlaylist
import com.minoppol.music.data.lxmusic.LxSong
import com.minoppol.music.data.lxmusic.LxSongMapper
import com.minoppol.music.data.lxmusic.LxSources
import com.minoppol.music.R
import com.minoppol.music.presentation.components.ExpressiveScrollBar
import com.minoppol.music.presentation.components.MiniPlayerHeight
import com.minoppol.music.presentation.components.SmartImage
import com.minoppol.music.presentation.components.SmartImageListTargetSize
import com.minoppol.music.presentation.components.subcomps.EnhancedSongListItem
import com.minoppol.music.presentation.screens.ListExtraBottomGap
import com.minoppol.music.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LxMinePage(
    mineState: LxMusicViewModel.MineState,
    nickname: String?,
    avatarUrl: String?,
    vipType: Int,
    viewModel: LxMusicViewModel,
    playerViewModel: PlayerViewModel,
    playLxSong: (LxSong) -> Unit,
    playAllPlaylist: (List<LxSong>, String) -> Unit,
    shuffleAllPlaylist: (List<LxSong>, String) -> Unit,
    bottomBarHeightDp: Dp,
    onScrollingChange: (Boolean) -> Unit = {},
    onLogout: () -> Unit = {},
    showArtistFollow: Boolean = false,
    subPageBackOverride: (() -> Unit)? = null,
    onSongMoreOptions: (LxSong) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (LxSong) -> Unit = {},
    onSongSelectionToggle: (LxSong) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    multiSelectionState: com.minoppol.music.presentation.viewmodel.MultiSelectionStateHolder =
        com.minoppol.music.presentation.viewmodel.MultiSelectionStateHolder(),
    onSelectionOptionsClick: () -> Unit = {},
) {
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeight = with(density) { headerHeightPx.toDp() }.takeIf { it > 0.dp } ?: 72.dp
    val pullToRefreshState = rememberPullToRefreshState()
    val currentSource by viewModel.selectedSource.collectAsStateWithLifecycle()
    val isNeteaseSource = currentSource == LxSources.NETEASE
    var homeTab by remember(currentSource) {
        mutableStateOf(if (isNeteaseSource) MINE_TAB_RECOMMEND else MINE_TAB_FAVORITE)
    }
    val scope = rememberCoroutineScope()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    val page = mineState.page
    val isHome = page is LxMusicViewModel.MinePage.Home
    val isArtistPage = page is LxMusicViewModel.MinePage.Artist
    val isSongPage = page is LxMusicViewModel.MinePage.SongList
    val showSongActions =
        (isSongPage || (isArtistPage && mineState.artistTab == "songs")) &&
            mineState.pageSongs.isNotEmpty()
    val refreshing = if (isHome || page is LxMusicViewModel.MinePage.RecommendPlaylists)
        mineState.loadingHome else mineState.pageLoading

    val listStateStore = remember { mutableStateMapOf<Any, androidx.compose.foundation.lazy.LazyListState>() }
    val subScrollKey: Any = if (isArtistPage) {
        mineState.navSession to "artist_${mineState.artistTab}"
    } else {
        mineState.navSession to page
    }
    val subListState = listStateStore.getOrPut(subScrollKey) { androidx.compose.foundation.lazy.LazyListState() }
    val homeScrollKey: Any = mineState.navSession to "home_$homeTab"
    val homeListState = listStateStore.getOrPut(homeScrollKey) { androidx.compose.foundation.lazy.LazyListState() }
    val homeContentEmpty = if (homeTab == MINE_TAB_FAVORITE) {
        mineState.myPlaylists.isEmpty()
    } else {
        mineState.recommendPlaylists.isEmpty()
    }
    var homeContentWasEmpty by remember(homeScrollKey) { mutableStateOf(homeContentEmpty) }
    LaunchedEffect(homeScrollKey, homeContentEmpty) {
        if (homeContentWasEmpty && !homeContentEmpty) homeListState.scrollToItem(0)
        homeContentWasEmpty = homeContentEmpty
    }
    LaunchedEffect(mineState.navSession) {
        val current = mineState.navSession
        listStateStore.keys.filterIsInstance<Pair<*, *>>()
            .filter { (it.first as? Long ?: 0L) > current }
            .forEach { listStateStore.remove(it) }
    }

    val folderPage = (page as? LxMusicViewModel.MinePage.SongList)
        ?.takeIf { it.kind == "playlist" || it.kind == "album" }
    if (folderPage != null) {
        LaunchedEffect(folderPage.playlistId, folderPage.albumId) {
            viewModel.ensureCollectDataLoaded()
        }
    }
    val perSongIds = remember(mineState.pageSongs) {
        mineState.pageSongs.map {
            com.minoppol.music.data.lxmusic.LxSongMapper
                .matchIds(it.source, it.songmid, it.hash)
        }
    }
    val songIdIndex = perSongIds.map { it.first() }
    val currentMatchIds = stablePlayerState.currentSong
        ?.let { com.minoppol.music.data.lxmusic.LxSongMapper.matchIds(it) }
        ?: emptySet()
    val (folderLocateVisible, folderLocateIndex) = rememberLocateButtonState(
        listState = subListState,
        currentSongId = null,
        songIds = songIdIndex,
        perSongMatchIds = perSongIds,
        currentMatchIds = currentMatchIds,
    )
    val collectBusyKeys by viewModel.collectBusyKeys.collectAsStateWithLifecycle()
    val onFolderShare = rememberFolderShare(viewModel)
    val folderActions: (@Composable () -> Unit)? = if (folderPage != null) {
        @Composable {
            val kind = if (folderPage.kind == "playlist")
                LxMusicViewModel.CollectKind.PLAYLIST else LxMusicViewModel.CollectKind.ALBUM
            val targetId = if (kind == LxMusicViewModel.CollectKind.PLAYLIST)
                folderPage.playlistId else folderPage.albumId
            val entryGid = remember(targetId) {
                targetId?.let { tid ->
                    mineState.myPlaylists.firstOrNull { it.id == tid }?.originGid
                }
            }
            LxFolderActionCapsule(
                showLocate = folderLocateVisible,
                onLocate = { if (folderLocateIndex >= 0) scope.launch { subListState.animateScrollToItem(folderLocateIndex) } },
                collectState = rememberCollectState(viewModel, kind, targetId, gid = entryGid),
                collectBusy = targetId != null &&
                    collectBusyKeys.contains(viewModel.collectKey(kind, targetId)),
                onCollectClick = {
                    targetId?.let { viewModel.onCollectButtonClick(kind, it, gid = entryGid) }
                },
                onShare = { targetId?.let { onFolderShare(kind, it) } },
            )
        }
    } else null

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

    val bottomPadding = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap

    Box(modifier = Modifier.fillMaxSize().nestedScroll(consumeHorizontalScroll)) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refreshBrowse() },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = headerHeight),
                )
            },
        ) {
            val clipShape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = 34.dp,
                smoothnessAsPercentTL = 60,
                cornerRadiusTR = 34.dp,
                smoothnessAsPercentTR = 60,
                cornerRadiusBL = 0.dp,
                smoothnessAsPercentBL = 60,
                cornerRadiusBR = 0.dp,
                smoothnessAsPercentBR = 60,
            )
            if (isHome) {
                val homeAwaiting =
                    !isNeteaseSource && !mineState.homeLoaded && mineState.error == null
                if (homeAwaiting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = headerHeight),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(top = 44.dp)
                                .size(30.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                } else {
                    LazyColumn(
                        state = homeListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = headerHeight)
                            .clip(clipShape)
                            .mineVerticalDragGate(onScrollingChange),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 14.dp,
                            bottom = bottomPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (isNeteaseSource && homeTab == MINE_TAB_RECOMMEND) {
                            mineRecommendSection(mineState, viewModel)
                        } else {
                            mineFavoriteSection(mineState, viewModel, currentSource)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = subListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = headerHeight)
                        .clip(clipShape)
                        .mineVerticalDragGate(onScrollingChange),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 14.dp,
                        bottom = bottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (page) {
                        is LxMusicViewModel.MinePage.SongList -> {
                            mineSongItems(
                                songs = mineState.pageSongs,
                                loading = mineState.pageLoading,
                                error = mineState.error,
                                stablePlayerState = stablePlayerState,
                                playLxSong = playLxSong,
                                onSongMoreOptions = onSongMoreOptions,
                                isSelectionMode = isSelectionMode,
                                selectedSongIds = selectedSongIds,
                                onSongLongPress = onSongLongPress,
                                onSongSelectionToggle = onSongSelectionToggle,
                                getSelectionIndex = getSelectionIndex,
                            )
                            if (!mineState.pageLoading && mineState.pageSongs.isNotEmpty() &&
                                (mineState.pageSongsHasMore || mineState.pageLoadingMore)
                            ) {
                                val isPersonalFm = page.kind == "personal_fm"
                                item(key = "mine_songs_load_more") {
                                    LxLoadMoreButton(
                                        loading = mineState.pageLoadingMore,
                                        onClick = viewModel::loadMoreMineSongs,
                                        label = if (isPersonalFm) stringResource(R.string.lx_btn_refresh_batch) else stringResource(R.string.lx_btn_load_more_default),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }
                        is LxMusicViewModel.MinePage.RecommendPlaylists -> {
                            minePlaylistItems(mineState.pagePlaylists, viewModel::openMinePlaylist)
                        }
                        is LxMusicViewModel.MinePage.SubArtists -> {
                            mineArtistItems(mineState.pageArtists, viewModel::openArtistPage)
                        }
                        is LxMusicViewModel.MinePage.SubAlbums -> {
                            mineAlbumItems(mineState.pageAlbums, viewModel::openAlbumPage)
                        }
                        is LxMusicViewModel.MinePage.Artist -> {
                            if (mineState.artistTab == "albums") {
                                mineAlbumItems(mineState.pageAlbums, viewModel::openAlbumPage)
                                if (mineState.pageLoading) item(key = "mine_artist_loading") { MineLoadingRow() }
                            } else {
                                mineSongItems(
                                    songs = mineState.pageSongs,
                                    loading = mineState.pageLoading,
                                    error = mineState.error,
                                    stablePlayerState = stablePlayerState,
                                    playLxSong = playLxSong,
                                    onSongMoreOptions = onSongMoreOptions,
                                    isSelectionMode = isSelectionMode,
                                    selectedSongIds = selectedSongIds,
                                    onSongLongPress = onSongLongPress,
                                    onSongSelectionToggle = onSongSelectionToggle,
                                    getSelectionIndex = getSelectionIndex,
                                )
                                if (!mineState.pageLoading && mineState.pageSongs.isNotEmpty() &&
                                    (mineState.artistSongsHasMore || mineState.artistLoadingMore)
                                ) {
                                    item(key = "mine_artist_load_more") {
                                        LxLoadMoreButton(
                                            loading = mineState.artistLoadingMore,
                                            onClick = viewModel::loadMoreArtistSongs,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                        )
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onGloballyPositioned { headerHeightPx = it.size.height }
                .then(if (isArtistPage) Modifier.nestedScroll(consumeHorizontalScroll) else Modifier),
        ) {
            if (isHome) {
                Column {
                    MineProfileHeader(
                        nickname = nickname,
                        avatarUrl = avatarUrl,
                        vipType = vipType,
                        onLogout = onLogout,
                    )
                    if (isNeteaseSource) {
                        MineHomeTabSwitcher(
                            selected = homeTab,
                            onSelect = { homeTab = it },
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                    }
                }
            } else {
                Column {
                    if (isArtistPage) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                onClick = { (subPageBackOverride ?: viewModel::backMinePage)() },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(R.string.lx_desc_back),
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = stringResource(R.string.lx_text_back),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = mineState.pageTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (showArtistFollow && mineState.artistFollowed != null) {
                                        Spacer(Modifier.width(8.dp))
                                        ArtistFollowButton(
                                            followed = mineState.artistFollowed == true,
                                            busy = mineState.artistFollowBusy,
                                            onClick = viewModel::toggleArtistFollow,
                                        )
                                    }
                                }
                                mineState.artistInfo?.briefDesc?.takeIf { it.isNotBlank() }?.let { desc ->
                                    Spacer(Modifier.height(2.dp))
                                    var expanded by remember { mutableStateOf(false) }
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { expanded = !expanded },
                                    )
                                }
                            }
                            mineState.artistInfo?.pic?.takeIf { it.isNotBlank() }?.let { pic ->
                                Spacer(Modifier.width(8.dp))
                                SmartImage(
                                    model = pic,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(48.dp)
                                        .clip(CircleShape),
                                )
                            }
                        }
                    } else {
                        MineSubHeader(
                            title = mineState.pageTitle,
                            showPlayAll = false,
                            onBack = { (subPageBackOverride ?: viewModel::backMinePage)() },
                            onPlayAll = {
                                if (showSongActions) {
                                    playAllPlaylist(mineState.pageSongs, mineState.pageTitle)
                                }
                            },
                            titleExpand = true,
                            followButton = null,
                            actions = folderActions,
                        )
                    }
                    if (isArtistPage) {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            item {
                                ArtistTabChip(stringResource(R.string.lx_tab_hot_songs), mineState.artistTab == "songs") {
                                    viewModel.selectArtistTab("songs")
                                }
                            }
                            item {
                                ArtistTabChip(stringResource(R.string.lx_tab_all_albums), mineState.artistTab == "albums") {
                                    viewModel.selectArtistTab("albums")
                                }
                            }
                        }
                    }
                }
            }
        }

        ExpressiveScrollBar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = headerHeight + 4.dp, bottom = bottomPadding + 16.dp),
            listState = if (isHome) homeListState else subListState,
        )

        MineScrollToTopFab(
            listState = if (isHome) homeListState else subListState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomPadding + 14.dp),
        )

        if (isArtistPage && mineState.artistTab == "songs" && mineState.pageSongs.isNotEmpty()) {
            val perIds = remember(mineState.pageSongs) {
                mineState.pageSongs.map {
                    com.minoppol.music.data.lxmusic.LxSongMapper
                        .matchIds(it.source, it.songmid, it.hash)
                }
            }
            val curIds = stablePlayerState.currentSong
                ?.let { com.minoppol.music.data.lxmusic.LxSongMapper.matchIds(it) }
                ?: emptySet()
            LxLocateCurrentSongFab(
                listState = subListState,
                currentSongId = null,
                songIds = perIds.map { it.first() },
                leadingItems = 0,
                perSongMatchIds = perIds,
                currentMatchIds = curIds,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = bottomPadding + 74.dp),
            )
        }

        if (showSongActions) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = bottomPadding + 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalIconButton(
                    onClick = { shuffleAllPlaylist(mineState.pageSongs, mineState.pageTitle) },
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
                    onClick = { playAllPlaylist(mineState.pageSongs, mineState.pageTitle) },
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
                multiSelectionState.selectAll(
                    mineState.pageSongs.map {
                        com.minoppol.music.data.lxmusic.LxSongMapper.toSong(it)
                    }
                )
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
private fun MineProfileHeader(
    nickname: String?,
    avatarUrl: String?,
    vipType: Int,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            SmartImage(
                model = avatarUrl,
                contentDescription = null,
                targetSize = SmartImageListTargetSize,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nickname ?: stringResource(R.string.lx_default_nickname),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val vipLabel = when (vipType) {
                13 -> stringResource(R.string.lx_text_netease_svip)
                11 -> stringResource(R.string.lx_text_netease_vip)
                else -> null
            }
            if (vipLabel != null) {
                Text(
                    text = vipLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = onLogout) {
            Icon(
                Icons.Rounded.Logout,
                contentDescription = stringResource(R.string.lx_desc_logout),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


@Composable
private fun MineSubHeader(
    title: String,
    showPlayAll: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    titleExpand: Boolean = true,
    followButton: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.lx_desc_back),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = stringResource(R.string.lx_text_back),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        LxFolderTitleText(
            text = title,
            modifier = if (titleExpand) Modifier.weight(1f) else Modifier,
        )
        if (followButton != null) {
            Spacer(Modifier.width(8.dp))
            followButton()
        }
        if (actions != null) {
            Spacer(Modifier.width(8.dp))
            actions()
        }
        if (!titleExpand) {
            Spacer(Modifier.weight(1f))
        }
        if (showPlayAll) {
            FilledTonalIconButton(onClick = onPlayAll, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.lx_desc_play_all), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
internal fun ArtistFollowButton(
    followed: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val label = if (followed) stringResource(R.string.lx_btn_unfollow) else stringResource(R.string.lx_btn_follow)
    val container = if (followed)
        MaterialTheme.colorScheme.tertiaryContainer
    else
        MaterialTheme.colorScheme.primary
    val content = if (followed)
        MaterialTheme.colorScheme.onTertiaryContainer
    else
        MaterialTheme.colorScheme.onPrimary
    Surface(
        onClick = { if (!busy) onClick() },
        enabled = !busy,
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.5.dp,
                    color = content,
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
internal fun ArtistTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium,
        )
    }
}


private const val MINE_TAB_RECOMMEND = "recommend"
private const val MINE_TAB_FAVORITE = "favorite"

private val MineTabLeftShape = RoundedCornerShape(
    topStart = 26.dp, bottomStart = 26.dp,
    topEnd = 8.dp, bottomEnd = 8.dp,
)

private val MineTabRightShape = RoundedCornerShape(
    topStart = 8.dp, bottomStart = 8.dp,
    topEnd = 26.dp, bottomEnd = 26.dp,
)

@Composable
private fun MineHomeTabSwitcher(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MineTabSegment(
            text = stringResource(R.string.lx_tab_recommend),
            icon = Icons.Rounded.AutoAwesome,
            selected = selected == MINE_TAB_RECOMMEND,
            shape = MineTabLeftShape,
            onClick = { onSelect(MINE_TAB_RECOMMEND) },
        )
        MineTabSegment(
            text = stringResource(R.string.lx_tab_favorite),
            icon = Icons.Rounded.Favorite,
            selected = selected == MINE_TAB_FAVORITE,
            shape = MineTabRightShape,
            onClick = { onSelect(MINE_TAB_FAVORITE) },
        )
    }
}

@Composable
private fun MineTabSegment(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (selected) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 6.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
        modifier = Modifier.height(34.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}


private fun LazyListScope.mineRecommendSection(
    state: LxMusicViewModel.MineState,
    viewModel: LxMusicViewModel,
) {
    val dailyCover = state.recommendPlaylists.firstOrNull { it.name.contains("每日推荐") }?.pic

    item(key = "mine_entry_daily") {
        MineEntryRow(
            title = stringResource(R.string.lx_title_daily_recommend),
            subtitle = stringResource(R.string.lx_subtitle_daily_recommend),
            coverUrl = dailyCover,
            fallbackIcon = Icons.Rounded.AutoAwesome,
            onClick = viewModel::openDailySongs,
        )
    }
    item(key = "mine_entry_heartbeat") {
        MineEntryRow(
            title = stringResource(R.string.lx_title_heartbeat_mode),
            subtitle = stringResource(R.string.lx_subtitle_heartbeat_mode),
            coverUrl = null,
            fallbackIcon = Icons.Rounded.Favorite,
            onClick = viewModel::openHeartbeatMode,
        )
    }
    item(key = "mine_entry_recommend_playlists") {
        MineEntryRow(
            title = stringResource(R.string.lx_title_recommend_playlists),
            subtitle = if (state.recommendPlaylists.isNotEmpty())
                stringResource(R.string.lx_subtitle_recommend_playlists_today, state.recommendPlaylists.size)
            else stringResource(R.string.lx_subtitle_recommend_playlists_default),
            coverUrl = null,
            fallbackIcon = Icons.Rounded.QueueMusic,
            onClick = viewModel::openRecommendPlaylistsPage,
        )
    }
    item(key = "mine_entry_roaming") {
        MineEntryRow(
            title = stringResource(R.string.lx_title_liked_roaming),
            subtitle = stringResource(R.string.lx_subtitle_liked_roaming),
            coverUrl = null,
            fallbackIcon = Icons.Rounded.GraphicEq,
            onClick = viewModel::openLikedRoaming,
        )
    }
    item(key = "mine_entry_personal_fm") {
        MineEntryRow(
            title = stringResource(R.string.lx_title_personal_fm),
            subtitle = stringResource(R.string.lx_subtitle_personal_fm),
            coverUrl = null,
            fallbackIcon = Icons.Rounded.Radio,
            onClick = viewModel::openPersonalFm,
        )
    }
    if (state.loadingHome) {
        item(key = "mine_rec_loading") { MineLoadingRow() }
    }
}

private fun LazyListScope.mineFavoriteSection(
    state: LxMusicViewModel.MineState,
    viewModel: LxMusicViewModel,
    currentSource: String,
) {
    val showArtistEntry = currentSource != LxSources.KUGOU
        && (currentSource != LxSources.KUWO || viewModel.accountWritesEnabled)
        && currentSource != LxSources.TENCENT
    val showAlbumEntry = currentSource != LxSources.KUGOU
        && (currentSource != LxSources.KUWO || viewModel.accountWritesEnabled)
        && (currentSource != LxSources.TENCENT || viewModel.accountWritesEnabled)
    val liked = state.myPlaylists.firstOrNull { it.specialType == 5 }
    if (liked != null) {
        item(key = "mine_entry_liked") {
            MineEntryRow(
                title = liked.name,
                subtitle = liked.trackCount?.let { stringResource(R.string.lx_text_track_count_format, it) } ?: stringResource(R.string.lx_subtitle_liked_music_default),
                coverUrl = liked.pic,
                fallbackIcon = Icons.Rounded.Favorite,
                onClick = viewModel::openLikedPlaylist,
            )
        }
    }
    if (showArtistEntry) {
        item(key = "mine_entry_artists") {
            MineEntryRow(
                title = stringResource(R.string.lx_title_sub_artists),
                subtitle = if (state.subArtists.isNotEmpty()) stringResource(R.string.lx_subtitle_sub_artists_count, state.subArtists.size) else stringResource(R.string.lx_subtitle_sub_artists_default),
                coverUrl = null,
                fallbackIcon = Icons.Rounded.Person,
                showChevron = true,
                onClick = viewModel::openSubArtistsPage,
            )
        }
    }
    if (showAlbumEntry) {
        item(key = "mine_entry_albums") {
            MineEntryRow(
                title = stringResource(R.string.lx_title_sub_albums),
                subtitle = if (state.subAlbums.isNotEmpty()) stringResource(R.string.lx_subtitle_sub_albums_count, state.subAlbums.size) else stringResource(R.string.lx_subtitle_sub_albums_default),
                coverUrl = null,
                fallbackIcon = Icons.Rounded.Album,
                showChevron = true,
                onClick = viewModel::openSubAlbumsPage,
            )
        }
    }
    item(key = "mine_fav_divider") {
        Spacer(Modifier.height(6.dp))
    }
    state.myPlaylists
        .filter { it.specialType != 5 }
        .forEachIndexed { index, playlist ->
            item(key = "mine_my_pl_${playlist.id}") {
                val trackCountFormat = stringResource(R.string.lx_text_track_count_format)
                MineEntryRow(
                    title = playlist.name,
                    subtitle = buildString {
                        playlist.trackCount?.let { append(trackCountFormat.format(it)) }
                        if (!playlist.creator.isNullOrBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(playlist.creator)
                        }
                    },
                    coverUrl = playlist.pic,
                    fallbackIcon = Icons.Rounded.QueueMusic,
                    onClick = { viewModel.openMinePlaylist(playlist) },
                )
            }
        }
    if (state.loadingHome) {
        item(key = "mine_fav_loading") { MineLoadingRow() }
    }
}

@Composable
private fun MineEntryRow(
    title: String,
    subtitle: String?,
    coverUrl: String?,
    fallbackIcon: ImageVector,
    onClick: () -> Unit,
    showChevron: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!coverUrl.isNullOrBlank()) {
            SmartImage(
                model = coverUrl,
                contentDescription = null,
                targetSize = SmartImageListTargetSize,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    fallbackIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showChevron) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}


internal fun LazyListScope.mineSongItems(
    songs: List<LxSong>,
    loading: Boolean,
    error: String?,
    stablePlayerState: com.minoppol.music.presentation.viewmodel.StablePlayerState,
    playLxSong: (LxSong) -> Unit,
    onSongMoreOptions: (LxSong) -> Unit,
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (LxSong) -> Unit = {},
    onSongSelectionToggle: (LxSong) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
) {
    items(songs, key = { "mine_song_${it.source}_${it.songmid}" }) { song ->
        val uiSong = remember(song.source, song.songmid, song.pic) { LxSongMapper.toSong(song) }
        val isCurrent = stablePlayerState.currentSong?.let { cur ->
            LxSongMapper.matchIds(cur).any { it in LxSongMapper.matchIds(uiSong) }
        } ?: false
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
    if (songs.isNotEmpty()) {
        item(key = "mine_songs_count") {
            Text(
                stringResource(R.string.lx_text_total_songs_count, songs.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    } else if (!loading && !error.isNullOrBlank()) {
        item(key = "mine_songs_empty") {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            )
        }
    }
}

private fun LazyListScope.minePlaylistItems(
    playlists: List<LxPlaylist>,
    onClick: (LxPlaylist) -> Unit,
) {
    if (playlists.isEmpty()) {
        item(key = "mine_pl_empty") { MineEmptyText(stringResource(R.string.lx_msg_no_recommend_playlists)) }
        return
    }
    items(playlists, key = { "mine_pl_${it.id}" }) { playlist ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick(playlist) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MineCover(playlist.pic, Icons.Rounded.QueueMusic)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val isRadar = playlist.name.contains("雷达")
                val trackCountFormat = stringResource(R.string.lx_text_track_count_format)
                val subtitle = buildString {
                    playlist.creator?.let { append(it) }
                    if (!isRadar) {
                        if (!playlist.creator.isNullOrBlank() && playlist.trackCount != null) append(" · ")
                        playlist.trackCount?.let { append(trackCountFormat.format(it)) }
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

internal fun LazyListScope.mineArtistItems(
    artists: List<LxArtist>,
    onClick: (LxArtist) -> Unit,
) {
    if (artists.isEmpty()) {
        item(key = "mine_ar_empty") { MineEmptyText(stringResource(R.string.lx_msg_no_followed_artists)) }
        return
    }
    items(artists, key = { "mine_ar_${it.id}" }) { artist ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick(artist) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!artist.pic.isNullOrBlank()) {
                SmartImage(
                    model = artist.pic,
                    contentDescription = null,
                    targetSize = SmartImageListTargetSize,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artist.albumSize > 0) {
                    Text(
                        text = stringResource(R.string.lx_text_album_count_format, artist.albumSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

internal fun LazyListScope.mineAlbumItems(
    albums: List<LxAlbum>,
    onClick: (LxAlbum) -> Unit,
    loading: Boolean = false,
) {
    if (albums.isEmpty()) {
        if (!loading) {
            item(key = "mine_al_empty") { MineEmptyText(stringResource(R.string.lx_msg_no_albums)) }
        }
        return
    }
    items(albums, key = { "mine_al_${it.id}" }) { album ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick(album) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MineCover(album.pic, Icons.Rounded.Album, size = 54.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val trackCountFormat = stringResource(R.string.lx_text_track_count_format)
                val subtitle = buildString {
                    album.artistName?.let { append(it) }
                    if (!album.artistName.isNullOrBlank() && album.size > 0) append(" · ")
                    if (album.size > 0) append(trackCountFormat.format(album.size))
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}


@Composable
private fun MineCover(
    pic: String?,
    fallbackIcon: ImageVector,
    size: androidx.compose.ui.unit.Dp = 56.dp,
) {
    if (!pic.isNullOrBlank()) {
        SmartImage(
            model = pic,
            contentDescription = null,
            targetSize = SmartImageListTargetSize,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                fallbackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
internal fun MineLoadingRow() {
    LxLoadingSkeletonRows(3)
}

@Composable
internal fun LxLoadingSkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
        ) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        ShimmerBox(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
        )
    }
}

@Composable
internal fun LxLoadingSkeletonRows(count: Int = 5) {
    repeat(count) {
        LxLoadingSkeletonRow()
    }
}

@Composable
fun LxLoadMoreButton(
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.lx_btn_load_more_default),
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 6.dp),
        modifier = modifier.height(42.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MineEmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    )
}

@Composable
private fun MineScrollToTopFab(listState: androidx.compose.foundation.lazy.LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val isScrolled = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 120
    androidx.compose.animation.AnimatedVisibility(
        visible = isScrolled,
        enter = androidx.compose.animation.scaleIn(animationSpec = androidx.compose.animation.core.tween(220)) +
            androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(220)),
        exit = androidx.compose.animation.scaleOut(animationSpec = androidx.compose.animation.core.tween(180)) +
            androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(180)),
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

private fun Modifier.mineVerticalDragGate(onVerticalDrag: (Boolean) -> Unit): Modifier =
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
                        if (abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) {
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

package com.minoppol.music.presentation.screens

import com.minoppol.music.presentation.navigation.navigateSafely

import com.minoppol.music.utils.traceSection
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.minoppol.music.R
import com.minoppol.music.data.model.Song
import com.minoppol.music.data.lxmusic.LxQualities
import com.minoppol.music.data.stats.StatsTimeRange
import com.minoppol.music.presentation.components.MiniPlayerHeight
import com.minoppol.music.presentation.components.MultiSelectionBottomSheet
import com.minoppol.music.presentation.components.PlaylistBottomSheet
import com.minoppol.music.presentation.components.RecentlyPlayedRangeSelector
import com.minoppol.music.presentation.components.SongInfoBottomSheet
import com.minoppol.music.presentation.components.resolveNavBarOccupiedHeight
import com.minoppol.music.presentation.components.subcomps.EnhancedSongListItem
import com.minoppol.music.presentation.components.subcomps.SelectionActionRow
import com.minoppol.music.presentation.components.subcomps.SelectionCountPill
import com.minoppol.music.presentation.screens.lxmusic.LxBatchDownloadQualityDialog
import com.minoppol.music.presentation.navigation.Screen
import com.minoppol.music.presentation.screens.lxmusic.LxScrollToTopFab
import com.minoppol.music.ui.theme.RoundedSans
import androidx.compose.ui.res.painterResource
import com.minoppol.music.presentation.model.RecentlyPlayedSongUiModel
import com.minoppol.music.presentation.model.collectRecentlyPlayedSongIds
import com.minoppol.music.presentation.model.mapRecentlyPlayedSongs
import com.minoppol.music.presentation.viewmodel.CloudDownloadsViewModel
import com.minoppol.music.presentation.viewmodel.PlaylistViewModel
import com.minoppol.music.presentation.viewmodel.PlayerViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecentlyPlayedScreen(
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    cloudDownloadsViewModel: CloudDownloadsViewModel = hiltViewModel(),
    navController: NavController,
    showBackButton: Boolean = true,
    showSettingsButton: Boolean = false
) = traceSection("RecentlyPlayedScreen.Composition") {
    val context = LocalContext.current
    val queueRecentlyPlayed = stringResource(R.string.presentation_batch_b_queue_recently_played)
    val shuffleLabel = stringResource(R.string.shortcut_shuffle_short)
    val playbackHistory by playerViewModel.playbackHistory.collectAsStateWithLifecycle()
    val currentSongId by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.currentSong?.id }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

    var selectedRange by rememberSaveable { mutableStateOf(StatsTimeRange.WEEK) }
    val lazyListState = rememberLazyListState()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var playlistSheetSongs by remember { mutableStateOf<ImmutableList<Song>>(persistentListOf()) }
    var showMultiSelectionSheet by remember { mutableStateOf(false) }
    var showBatchDownloadQualityDialog by remember { mutableStateOf(false) }
    var batchDownloadQuality by remember { mutableStateOf(LxQualities.Q320) }
    val multiSelectionState = playerViewModel.multiSelectionStateHolder
    val selectedSongs by multiSelectionState.selectedSongs.collectAsStateWithLifecycle()
    val isSelectionMode by multiSelectionState.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongIds by multiSelectionState.selectedSongIds.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)

    LaunchedEffect(selectedRange) {
        multiSelectionState.clearSelection()
        showMultiSelectionSheet = false
    }

    DisposableEffect(Unit) {
        onDispose { multiSelectionState.clearSelection() }
    }

    BackHandler(enabled = isSelectionMode) {
        showMultiSelectionSheet = false
        showBatchDownloadQualityDialog = false
        multiSelectionState.clearSelection()
    }

    val recentSongIds = remember(playbackHistory, selectedRange) {
        collectRecentlyPlayedSongIds(
            playbackHistory = playbackHistory,
            range = selectedRange,
            maxItems = Int.MAX_VALUE
        )
    }
    val recentSongsInitialValue: List<Song>? = remember(recentSongIds) {
        if (recentSongIds.isEmpty()) emptyList<Song>() else null
    }
    val recentlyPlayedSourceSongs by remember(recentSongIds, playerViewModel) {
        playerViewModel.observeSongs(recentSongIds)
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = recentSongsInitialValue)

    val recentlyPlayedSongs = remember(playbackHistory, recentlyPlayedSourceSongs, selectedRange) {
        val sourceSongs = recentlyPlayedSourceSongs ?: return@remember persistentListOf()
        mapRecentlyPlayedSongs(
            playbackHistory = playbackHistory,
            songs = sourceSongs,
            range = selectedRange,
            maxItems = Int.MAX_VALUE
        ).toImmutableList()
    }
    val groupedSongs = remember(recentlyPlayedSongs, selectedRange, context) {
        groupRecentlyPlayedSongs(
            context = context,
            songs = recentlyPlayedSongs,
            range = selectedRange
        )
    }
    val queueSongs = remember(recentlyPlayedSongs) {
        recentlyPlayedSongs.map { it.song }.toImmutableList()
    }

    val bgColors = listOf(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.background
    )

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = bgColors,
            endY = 1200f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        if (recentlyPlayedSourceSongs == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ContainedLoadingIndicator()
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "recently_played_header") {
                    ExpressiveRecentlyPlayedHeader(
                        title = stringResource(R.string.presentation_batch_b_recently_played_title),
                        songs = recentlyPlayedSongs,
                        selectedRange = selectedRange,
                        scrollState = lazyListState
                    )
                }

                item(key = "recently_played_range_selector") {
                    RecentlyPlayedRangeSelector(
                        selected = selectedRange,
                        onRangeSelected = { selectedRange = it },
                        modifier = Modifier.padding(horizontal = 0.dp)
                    )
                }

                if (recentlyPlayedSongs.isNotEmpty()) {
                    item(key = "recently_played_actions") {
                        AnimatedContent(
                            targetState = isSelectionMode,
                            label = "RecentlyPlayedActionRowModeSwitch",
                            transitionSpec = {
                                (slideInHorizontally { -it } + fadeIn()) togetherWith
                                        (slideOutHorizontally { it } + fadeOut())
                            }
                        ) { inSelectionMode ->
                            if (inSelectionMode) {
                                SelectionActionRow(
                                    selectedCount = selectedSongs.size,
                                    onSelectAll = { multiSelectionState.selectAll(queueSongs) },
                                    onDeselect = { multiSelectionState.clearSelection() },
                                    onOptionsClick = { showMultiSelectionSheet = true },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    trailingContentBeforeOptions = {
                                        SelectionCountPill(selectedCount = selectedSongs.size)
                                    }
                                )
                            } else {
                                RecentlyPlayedActions(
                                    onPlay = {
                                        val firstSong = queueSongs.firstOrNull() ?: return@RecentlyPlayedActions
                                        playerViewModel.playSongs(queueSongs, firstSong, queueRecentlyPlayed)
                                    },
                                    onShuffle = {
                                        playerViewModel.playSongsShuffled(
                                            songsToPlay = queueSongs,
                                            queueName = queueRecentlyPlayed,
                                            startAtZero = true,
                                        )
                                    },
                                    shuffleLabel = shuffleLabel,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }

                if (recentlyPlayedSongs.isEmpty()) {
                    item(key = "recently_played_empty") {
                        RecentlyPlayedEmptyState(
                            range = selectedRange,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    groupedSongs.forEachIndexed { groupIndex, group ->
                        item(key = "recently_played_time_${groupIndex}_${group.key}") {
                            RecentlyPlayedTimestampDivider(
                                label = group.label,
                                isHourBucket = group.isHourBucket,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        items(group.songs, key = { songUi -> songUi.song.id }, contentType = { "recently_played_song" }) { item ->
                            EnhancedSongListItem(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                song = item.song,
                                isCurrentSong = currentSongId == item.song.id,
                                isPlaying = currentSongId == item.song.id && isPlaying,
                                showSourceTag = true,
                                isSelected = selectedSongIds.contains(item.song.id),
                                isSelectionMode = isSelectionMode,
                                selectionIndex = if (isSelectionMode) multiSelectionState.getSelectionIndex(item.song.id) else null,
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    multiSelectionState.toggleSelection(item.song)
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        multiSelectionState.toggleSelection(item.song)
                                    } else {
                                        playerViewModel.playSongs(
                                            songsToPlay = queueSongs,
                                            startSong = item.song,
                                            queueName = queueRecentlyPlayed
                                        )
                                    }
                                },
                                onMoreOptionsClick = { song ->
                                    playerViewModel.selectSongForInfo(song)
                                    showSongInfoBottomSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showSongInfoBottomSheet && selectedSongForInfo != null) {
            val song = selectedSongForInfo!!
            SongInfoBottomSheet(
                song = song,
                isFavorite = favoriteSongIds.contains(song.id),
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(song)
                },
                onDismiss = {
                    showSongInfoBottomSheet = false
                    showPlaylistBottomSheet = false
                },
                onPlaySong = {
                    if (queueSongs.isNotEmpty()) {
                        playerViewModel.playSongs(queueSongs, song, queueRecentlyPlayed)
                    }
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(song)
                    showSongInfoBottomSheet = false
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(song)
                    showSongInfoBottomSheet = false
                },
                onAddToPlayList = {
                    playlistSheetSongs = persistentListOf(song)
                    showPlaylistBottomSheet = true
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                removeFromLibrary = if (song.contentUriString.startsWith("lxmusic://")) {
                    {
                        val activity = context as? android.app.Activity
                        if (activity != null) {
                            playerViewModel.deleteFromDevice(activity, song) { result ->
                                if (result) showSongInfoBottomSheet = false
                            }
                        }
                    }
                } else null,
                onNavigateToAlbum = {
                    navController.navigateSafely(Screen.AlbumDetail.createRoute(song.albumId))
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(song.artistId))
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistById = { artistId ->
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(artistId))
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    song.genre?.let {
                        navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate, customMetadataChanges ->
                    playerViewModel.editSongMetadata(
                        song,
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
                removeFromListTrigger = {}
            )
        }

        if (showPlaylistBottomSheet && playlistSheetSongs.isNotEmpty()) {
            PlaylistBottomSheet(
                playlistUiState = playlistUiState,
                songs = playlistSheetSongs,
                onDismiss = { showPlaylistBottomSheet = false },
                bottomBarHeight = bottomBarHeightDp,
                playerViewModel = playerViewModel,
            )
        }

        if (showMultiSelectionSheet && selectedSongs.isNotEmpty()) {
            MultiSelectionBottomSheet(
                selectedSongs = selectedSongs,
                favoriteSongIds = favoriteSongIds,
                onDismiss = { showMultiSelectionSheet = false },
                onPlayAll = {
                    playerViewModel.playSelectedSongs(selectedSongs)
                    showMultiSelectionSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSelectedToQueue(selectedSongs)
                    showMultiSelectionSheet = false
                },
                onPlayNext = {
                    playerViewModel.addSelectedAsNext(selectedSongs)
                    showMultiSelectionSheet = false
                },
                onAddToPlaylist = {
                    playlistSheetSongs = selectedSongs
                    multiSelectionState.clearSelection()
                    showMultiSelectionSheet = false
                    showPlaylistBottomSheet = true
                },
                onToggleLikeAll = { shouldLike ->
                    if (shouldLike) {
                        playerViewModel.likeSelectedSongs(selectedSongs)
                    } else {
                        playerViewModel.unlikeSelectedSongs(selectedSongs)
                    }
                    showMultiSelectionSheet = false
                },
                onShareAll = {
                    playerViewModel.shareSelectedSongs(selectedSongs)
                    showMultiSelectionSheet = false
                },
                onDownloadAll = {
                    showBatchDownloadQualityDialog = true
                    showMultiSelectionSheet = false
                },
                onDeleteAll = { deleteActivity, onComplete ->
                    playerViewModel.deleteSelectedFromDevice(deleteActivity, selectedSongs) {
                        showMultiSelectionSheet = false
                        onComplete(true)
                    }
                },
                showBatchEdit = false
            )
        }

        if (showBatchDownloadQualityDialog && selectedSongs.isNotEmpty()) {
            LxBatchDownloadQualityDialog(
                songCount = selectedSongs.size,
                selectedQuality = batchDownloadQuality,
                onSelect = { batchDownloadQuality = it },
                onConfirm = {
                    cloudDownloadsViewModel.downloadSelected(selectedSongs, batchDownloadQuality)
                    showBatchDownloadQualityDialog = false
                    multiSelectionState.clearSelection()
                },
                onDismiss = { showBatchDownloadQualityDialog = false }
            )
        }

        LaunchedEffect(cloudDownloadsViewModel) {
            cloudDownloadsViewModel.downloadEvents.collect { message ->
                playerViewModel.sendToast(message)
            }
        }

        if (showBackButton) {
            FilledIconButton(
                onClick = { navController.popBackStack() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 14.dp, top = 10.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.auth_cd_back)
                )
            }
        }

        if (showSettingsButton) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(end = 14.dp, top = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                FilledIconButton(
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
        }

        LxScrollToTopFab(
            listState = lazyListState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = bottomBarHeightDp + MiniPlayerHeight + ListExtraBottomGap + 14.dp
                ),
        )
    }
}

@Composable
private fun ExpressiveRecentlyPlayedHeader(
    title: String,
    songs: ImmutableList<RecentlyPlayedSongUiModel>,
    selectedRange: StatsTimeRange,
    scrollState: LazyListState
) {
    val highlightedArt = remember(songs) { songs.take(4).map { it.song.albumArtUriString } }
    val parallaxOffset by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex == 0) scrollState.firstVisibleItemScrollOffset * 0.36f else 0f
        }
    }
    val headerAlpha by remember {
        derivedStateOf {
            (1f - (scrollState.firstVisibleItemScrollOffset / 520f)).coerceIn(0f, 1f)
        }
    }

    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val titleStyle = rememberRecentlyPlayedTitleStyle()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .graphicsLayer {
                translationY = parallaxOffset
                alpha = headerAlpha
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            secondary.copy(alpha = 0.24f),
                            primary.copy(alpha = 0.10f),
                            surface.copy(alpha = 0.95f),
                            surface
                        ),
                        endY = 520f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun rememberRecentlyPlayedTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = RoundedSans,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 40.sp,
            lineHeight = 44.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun RecentlyPlayedActions(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    shuffleLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onPlay,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            shape = RoundedCornerShape(
                topStart = 52.dp,
                topEnd = 14.dp,
                bottomStart = 52.dp,
                bottomEnd = 14.dp
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.presentation_batch_b_play_latest))
        }

        FilledTonalButton(
            onClick = onShuffle,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 52.dp,
                bottomStart = 14.dp,
                bottomEnd = 52.dp
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(shuffleLabel)
        }
    }
}

@Composable
private fun RecentlyPlayedTimestampDivider(
    label: String,
    isHourBucket: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val railColor = if (isHourBucket) colors.primary else colors.secondary
    val chipContainer = if (isHourBucket) {
        colors.primaryContainer.copy(alpha = 0.78f)
    } else {
        colors.secondaryContainer.copy(alpha = 0.78f)
    }
    val chipContent = if (isHourBucket) colors.onPrimaryContainer else colors.onSecondaryContainer

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            railColor.copy(alpha = 0f),
                            railColor.copy(alpha = 0.50f)
                        )
                    )
                )
        )
        Surface(
            color = chipContainer,
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = 22.dp,
                smoothnessAsPercentTR = 60,
                cornerRadiusTR = 22.dp,
                smoothnessAsPercentBR = 60,
                cornerRadiusBL = 22.dp,
                smoothnessAsPercentBL = 60,
                cornerRadiusBR = 22.dp,
                smoothnessAsPercentTL = 60
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(railColor)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = chipContent
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            railColor.copy(alpha = 0.50f),
                            railColor.copy(alpha = 0f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun RecentlyPlayedEmptyState(
    range: StatsTimeRange,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentTL = 60
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.presentation_batch_b_recent_empty_title,
                    range.displayName.lowercase()
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.presentation_batch_b_recent_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class TimestampGroup(
    val key: String,
    val label: String,
    val isHourBucket: Boolean,
    val songs: List<RecentlyPlayedSongUiModel>
)

private fun groupRecentlyPlayedSongs(
    context: android.content.Context,
    songs: List<RecentlyPlayedSongUiModel>,
    range: StatsTimeRange
): List<TimestampGroup> {
    if (songs.isEmpty()) return emptyList()
    val zoneId = ZoneId.systemDefault()
    val sorted = songs.sortedByDescending { it.lastPlayedTimestamp }

    val groups = mutableListOf<TimestampGroup>()
    var currentBucketKey: String? = null
    var currentLabel: String? = null
    var currentIsHourBucket = false
    var bucket = mutableListOf<RecentlyPlayedSongUiModel>()

    sorted.forEach { item ->
        val timeBucket = resolveTimestampBucket(
            context = context,
            timestamp = item.lastPlayedTimestamp,
            range = range,
            zoneId = zoneId
        )
        if (currentBucketKey == null || currentBucketKey == timeBucket.key) {
            currentBucketKey = timeBucket.key
            currentLabel = timeBucket.label
            currentIsHourBucket = timeBucket.isHourBucket
            bucket += item
        } else {
            groups += TimestampGroup(
                key = currentBucketKey,
                label = currentLabel!!,
                isHourBucket = currentIsHourBucket,
                songs = bucket
            )
            currentBucketKey = timeBucket.key
            currentLabel = timeBucket.label
            currentIsHourBucket = timeBucket.isHourBucket
            bucket = mutableListOf(item)
        }
    }

    if (bucket.isNotEmpty() && currentLabel != null && currentBucketKey != null) {
        groups += TimestampGroup(
            key = currentBucketKey,
            label = currentLabel,
            isHourBucket = currentIsHourBucket,
            songs = bucket
        )
    }

    return groups
}

private data class TimestampBucket(
    val key: String,
    val label: String,
    val isHourBucket: Boolean
)

private fun resolveTimestampBucket(
    context: android.content.Context,
    timestamp: Long,
    range: StatsTimeRange,
    zoneId: ZoneId
): TimestampBucket {
    val safeTimestamp = timestamp.coerceAtLeast(0L)
    val safeNow = System.currentTimeMillis().coerceAtLeast(0L)
    val zonedDateTime = Instant.ofEpochMilli(safeTimestamp).atZone(zoneId)
    val date = zonedDateTime.toLocalDate()
    val nowDate = Instant.ofEpochMilli(safeNow).atZone(zoneId).toLocalDate()

    if (range == StatsTimeRange.DAY) {
        val hourStart = zonedDateTime
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        return TimestampBucket(
            key = hourStart.toInstant().toEpochMilli().toString(),
            label = hourStart.format(DateTimeFormatter.ofPattern("h a", Locale.getDefault())),
            isHourBucket = true
        )
    }

    return when {
        date == nowDate -> {
            TimestampBucket(
                key = date.toString(),
                label = context.getString(R.string.presentation_batch_b_date_today),
                isHourBucket = false
            )
        }
        date == nowDate.minusDays(1) -> {
            TimestampBucket(
                key = date.toString(),
                label = context.getString(R.string.presentation_batch_b_date_yesterday),
                isHourBucket = false
            )
        }
        range == StatsTimeRange.YEAR || range == StatsTimeRange.ALL -> {
            TimestampBucket(
                key = date.toString(),
                label = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())),
                isHourBucket = false
            )
        }
        else -> {
            TimestampBucket(
                key = date.toString(),
                label = date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())),
                isHourBucket = false
            )
        }
    }
}

package com.minoppol.music.presentation.components

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.minoppol.music.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.minoppol.music.data.lxmusic.LxAlbum
import com.minoppol.music.data.lxmusic.LxArtist
import com.minoppol.music.data.lxmusic.LxSources
import com.minoppol.music.data.lxmusic.LxSongMapper
import com.minoppol.music.data.model.Song
import kotlinx.collections.immutable.persistentListOf
import com.minoppol.music.presentation.screens.lxmusic.LxMusicViewModel
import com.minoppol.music.presentation.viewmodel.PlaybackOrigin
import com.minoppol.music.presentation.viewmodel.PlayerViewModel
import com.minoppol.music.presentation.viewmodel.StablePlayerState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt

internal data class SaveQueueOverlayData(
    val songs: ImmutableList<Song>,
    val defaultName: String,
    val onConfirm: (String, Set<String>) -> Unit
)

@Composable
internal fun UnifiedPlayerQueueLayer(
    shouldRenderLayer: Boolean,
    keepQueueSheetWarm: Boolean,
    albumColorScheme: ColorScheme,
    queueScrimAlpha: Float,
    showQueueSheet: Boolean,
    queueHiddenOffsetPx: Float,
    queueSheetOffset: Animatable<Float, AnimationVector1D>,
    queueSheetHeightPx: Float,
    onQueueSheetHeightPxChange: (Float) -> Unit,
    configurationResetKey: Any,
    currentPlaybackQueue: ImmutableList<Song>,
    currentQueueSourceName: String,
    currentMediaItemIndex: Int,
    infrequentPlayerState: StablePlayerState,
    activeTimerValueDisplay: State<String?>,
    activeTimerDurationMinutes: State<Int?>,
    playCount: State<Float>,
    isEndOfTrackTimerActive: State<Boolean>,
    onDismissQueue: () -> Unit,
    onSongInfoClick: (Song) -> Unit,
    onPlaySong: (Song, Int) -> Unit,
    onRemoveSong: (String) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onClearQueue: () -> Unit,
    onSetPredefinedTimer: (Int) -> Unit,
    onSetEndOfTrackTimer: (Boolean) -> Unit,
    onOpenCustomTimePicker: () -> Unit,
    onCancelTimer: () -> Unit,
    onCancelCountedPlay: () -> Unit,
    onPlayCounter: (Int) -> Unit,
    onRequestSaveAsPlaylist: (List<Song>, String, (String, Set<String>) -> Unit) -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit
) {
    if (!shouldRenderLayer) return

    LaunchedEffect(configurationResetKey) {
        onQueueSheetHeightPxChange(0f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (queueScrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(0f)
                    .graphicsLayer { alpha = queueScrimAlpha }
                    .background(MaterialTheme.colorScheme.scrim)
            )
        }

        val shouldRenderQueueSheet = remember(showQueueSheet, keepQueueSheetWarm, queueSheetHeightPx) {
            showQueueSheet || keepQueueSheetWarm || queueSheetHeightPx == 0f
        }

        if (shouldRenderQueueSheet) {
            MaterialTheme(
                colorScheme = albumColorScheme,
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes
            ) {
                QueueBottomSheet(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, queueSheetOffset.value.roundToInt()) }
                        .graphicsLayer {
                            alpha = if (showQueueSheet) 1f else 0f
                        }
                        .onGloballyPositioned { coordinates ->
                            val measuredHeight = coordinates.size.height.toFloat()
                            if (queueSheetHeightPx != measuredHeight) {
                                onQueueSheetHeightPxChange(measuredHeight)
                            }
                        },
                    queue = currentPlaybackQueue,
                    currentQueueSourceName = currentQueueSourceName,
                    currentSongId = infrequentPlayerState.currentSong?.id,
                    currentMediaItemIndex = currentMediaItemIndex,
                    isVisible = showQueueSheet,
                    isPlaying = infrequentPlayerState.isPlaying,
                    onDismiss = onDismissQueue,
                    onSongInfoClick = onSongInfoClick,
                    onPlaySong = onPlaySong,
                    onRemoveSong = onRemoveSong,
                    onReorder = onReorder,
                    repeatMode = infrequentPlayerState.repeatMode,
                    isShuffleOn = infrequentPlayerState.isShuffleEnabled,
                    onToggleRepeat = onToggleRepeat,
                    onToggleShuffle = onToggleShuffle,
                    onClearQueue = onClearQueue,
                    activeTimerValueDisplay = activeTimerValueDisplay,
                    activeTimerDurationMinutes = activeTimerDurationMinutes,
                    playCount = playCount,
                    isEndOfTrackTimerActive = isEndOfTrackTimerActive,
                    onSetPredefinedTimer = onSetPredefinedTimer,
                    onSetEndOfTrackTimer = onSetEndOfTrackTimer,
                    onOpenCustomTimePicker = onOpenCustomTimePicker,
                    onCancelTimer = onCancelTimer,
                    onCancelCountedPlay = onCancelCountedPlay,
                    onPlayCounter = onPlayCounter,
                    onRequestSaveAsPlaylist = onRequestSaveAsPlaylist,
                    onQueueDragStart = onQueueDragStart,
                    onQueueDrag = onQueueDrag,
                    onQueueRelease = onQueueRelease
                )
            }
        }
    }
}

@kotlin.OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun UnifiedPlayerSongInfoLayer(
    selectedSongForInfo: Song?,
    albumColorScheme: ColorScheme,
    playerViewModel: PlayerViewModel,
    currentPlaybackQueueProvider: () -> ImmutableList<Song>,
    currentQueueSourceNameProvider: () -> String,
    onDismissSongInfo: () -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    onNavigateToGenre: (Song) -> Unit
) {
    selectedSongForInfo?.let { staticSong ->
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val lxMusicViewModel: LxMusicViewModel = hiltViewModel(
            viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
        )
        val liveSongState by remember(playerViewModel, staticSong.id) {
            playerViewModel.observeSong(staticSong.id).map { observed ->
                val merged = observed ?: staticSong
                if (merged.lxMusicSource != null &&
                    merged.lxMusicExtraFields.isEmpty() &&
                    staticSong.lxMusicExtraFields.isNotEmpty()
                ) {
                    merged.copy(lxMusicExtraFields = staticSong.lxMusicExtraFields)
                } else {
                    merged
                }
            }
        }.collectAsStateWithLifecycle(initialValue = staticSong)

        val liveSong = liveSongState

        val playbackOrigin by playerViewModel.playbackOriginFlow.collectAsStateWithLifecycle()
        val localFavoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
        val platformLikeSnapshot by playerViewModel.platformLikeSnapshot.collectAsStateWithLifecycle()
        val songFavoriteState: Boolean
        val songFavoriteEnabled: Boolean
        if (playbackOrigin == PlaybackOrigin.LOCAL) {
            songFavoriteState = localFavoriteIds.contains(liveSong.id)
            songFavoriteEnabled = true
        } else {
            val (liked, enabled) = platformLikeSnapshot.let {
                playerViewModel.platformLikeStateOf(liveSong)
            }
            songFavoriteState = liked
            songFavoriteEnabled = enabled
        }

        val isOnlineEntryLxSong = liveSong.lxMusicSource != null && playbackOrigin == PlaybackOrigin.ONLINE
        val lxSongForInfo = remember(liveSong.id, liveSong.lxMusicSource, liveSong.lxMusicSongMid) {
            if (isOnlineEntryLxSong) LxSongMapper.toLxSong(liveSong) else null
        }
        val neteaseLoggedIn by lxMusicViewModel.neteaseLoggedIn.collectAsStateWithLifecycle()
        val kugouLoggedIn by lxMusicViewModel.kugouLoggedIn.collectAsStateWithLifecycle()
        val kuwoLoggedIn by lxMusicViewModel.kuwoLoggedIn.collectAsStateWithLifecycle()
        var showLxArtistPicker by remember { mutableStateOf(false) }
        var lxArtistPickerCandidates by remember {
            mutableStateOf<List<LxArtist>>(emptyList())
        }
        var showLocalPlaylistSheet by remember { mutableStateOf(false) }
        var localPlaylistSongs by remember { mutableStateOf(persistentListOf<Song>()) }
        val playlistViewModel: com.minoppol.music.presentation.viewmodel.PlaylistViewModel = hiltViewModel(
            viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
        )
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

        MaterialTheme(
            colorScheme = albumColorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            SongInfoBottomSheet(
                song = liveSong,
                isFavorite = songFavoriteState,
                favoriteEnabled = songFavoriteEnabled,
                onToggleFavorite = { playerViewModel.toggleFavoriteForQueue(liveSong) },
                onDismiss = {
                    onDismissSongInfo()
                },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(
                        song = liveSong,
                        contextSongs = currentPlaybackQueueProvider(),
                        queueName = currentQueueSourceNameProvider()
                    )
                    onDismissSongInfo()
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(liveSong)
                    onDismissSongInfo()
                    Toast.makeText(context, context.getString(R.string.toast_added_to_queue), Toast.LENGTH_SHORT).show()
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(liveSong)
                    onDismissSongInfo()
                    Toast.makeText(context, context.getString(R.string.toast_playing_next), Toast.LENGTH_SHORT).show()
                },
                onAddToPlayList = {
                    if (isOnlineEntryLxSong && lxSongForInfo != null) {
                        lxMusicViewModel.openOnlineAddToPlaylist(lxSongForInfo)
                        onDismissSongInfo()
                    } else {
                        localPlaylistSongs = persistentListOf(liveSong)
                        showLocalPlaylistSheet = true
                    }
                },
                addToPlaylistEnabled = if (isOnlineEntryLxSong) {
                    when (liveSong.lxMusicSource) {
                        LxSources.NETEASE -> neteaseLoggedIn
                        LxSources.KUGOU -> kugouLoggedIn && lxMusicViewModel.accountWritesEnabled
                        LxSources.KUWO -> kuwoLoggedIn && lxMusicViewModel.accountWritesEnabled
                        else -> false
                    }
                } else true,
                cloudDownloadEnabled = !isOnlineEntryLxSong,
                onDeleteFromDevice = { activity, songToDelete, onResult ->
                    playerViewModel.deleteFromDevice(activity, songToDelete, onResult)
                    onDismissSongInfo()
                },
                onNavigateToAlbum = {
                    if (isOnlineEntryLxSong) {
                        val albumId = when (liveSong.lxMusicSource) {
                            LxSources.TENCENT -> liveSong.lxMusicExtraFields["albumMid"]
                            else -> liveSong.lxMusicExtraFields["albumId"]
                        }
                        if (!albumId.isNullOrBlank()) {
                            lxMusicViewModel.openAlbumFromSearch(
                                LxAlbum(
                                    id = albumId,
                                    name = liveSong.album.orEmpty(),
                                ),
                                liveSong.lxMusicSource
                            )
                            playerViewModel.requestLxMusicTabNavigation()
                            onDismissSongInfo()
                        } else {
                            scope.launch {
                                val album = playerViewModel.resolveLxSongAlbum(liveSong)
                                if (album != null) {
                                    lxMusicViewModel.openAlbumFromSearch(album, liveSong.lxMusicSource)
                                    playerViewModel.requestLxMusicTabNavigation()
                                    onDismissSongInfo()
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.lx_msg_album_not_found),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    } else {
                        onNavigateToAlbum(liveSong)
                    }
                },
                onNavigateToArtist = {
                    if (isOnlineEntryLxSong) {
                        scope.launch {
                            val artists = playerViewModel.resolveLxSongArtists(liveSong)
                            when {
                                artists.size == 1 -> {
                                    lxMusicViewModel.openArtistFromSearch(artists.first(), liveSong.lxMusicSource)
                                    playerViewModel.requestLxMusicTabNavigation()
                                    onDismissSongInfo()
                                }
                                artists.size > 1 -> {
                                    lxArtistPickerCandidates = artists
                                    showLxArtistPicker = true
                                }
                                else -> {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.lx_msg_artist_not_found),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    } else {
                        onNavigateToArtist(liveSong)
                    }
                },
                onNavigateToGenre = { onNavigateToGenre(liveSong) },
                onEditSong = if (isOnlineEntryLxSong) {
                    { _, _, _, _, _, _, _, _, _, _, _, _, _ -> }
                } else {
                    { title, artist, album, albumArtist, composer, genre, lyrics, trackNumber, discNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate, customMetadataChanges ->
                        playerViewModel.editSongMetadata(
                            liveSong,
                            title,
                            artist,
                            album,
                            albumArtist,
                            composer,
                            genre,
                            lyrics,
                            trackNumber,
                            discNumber,
                            replayGainTrackGainDb,
                            replayGainAlbumGainDb,
                            coverArtUpdate,
                            customMetadataChanges
                        )
                        onDismissSongInfo()
                    }
                },
                removeFromListTrigger = {
                    playerViewModel.removeSongFromQueue(liveSong.id)
                    onDismissSongInfo()
                }
            )
        }

        val onlineAddSheetState by lxMusicViewModel.onlineAddSheet.collectAsStateWithLifecycle()
        onlineAddSheetState?.let { addState ->
            NeteaseAddToPlaylistSheet(
                state = addState,
                onDismiss = { lxMusicViewModel.dismissOnlineAddSheet() },
                onConfirm = { ids -> lxMusicViewModel.onlineAddSongToPlaylists(ids) },
            )
        }

        if (showLocalPlaylistSheet && localPlaylistSongs.isNotEmpty()) {
            PlaylistBottomSheet(
                playlistUiState = playlistUiState,
                songs = localPlaylistSongs,
                onDismiss = { showLocalPlaylistSheet = false },
                bottomBarHeight = 0.dp,
                playerViewModel = playerViewModel,
                playlistViewModel = playlistViewModel,
            )
        }

        if (showLxArtistPicker && lxArtistPickerCandidates.isNotEmpty()) {
            LxArtistPickerBottomSheet(
                artists = lxArtistPickerCandidates,
                source = liveSong.lxMusicSource ?: "",
                sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
                onDismiss = { showLxArtistPicker = false },
                onArtistClick = { artist ->
                    lxMusicViewModel.openArtistFromSearch(artist, liveSong.lxMusicSource ?: "")
                    playerViewModel.requestLxMusicTabNavigation()
                    showLxArtistPicker = false
                    onDismissSongInfo()
                }
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun UnifiedPlayerQueueAndSongInfoHost(
    shouldRenderHost: Boolean,
    keepQueueSheetWarm: Boolean,
    isQueueTelemetryActive: Boolean,
    albumColorScheme: ColorScheme,
    queueScrimAlpha: Float,
    showQueueSheet: Boolean,
    queueHiddenOffsetPx: Float,
    queueSheetOffset: Animatable<Float, AnimationVector1D>,
    queueSheetHeightPx: Float,
    onQueueSheetHeightPxChange: (Float) -> Unit,
    configurationResetKey: Any,
    currentQueueSourceName: String,
    infrequentPlayerState: StablePlayerState,
    playerViewModel: PlayerViewModel,
    selectedSongForInfo: Song?,
    onSelectedSongForInfoChange: (Song?) -> Unit,
    onAnimateQueueSheet: (Boolean) -> Unit,
    onBeginQueueDrag: () -> Unit,
    onDragQueueBy: (Float) -> Unit,
    onEndQueueDrag: (Float, Float) -> Unit,
    onLaunchSaveQueueOverlay: (List<Song>, String, (String, Set<String>) -> Unit) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    onNavigateToGenre: (Song) -> Unit
) {
    if (!shouldRenderHost) return

    val currentPlaybackQueue by playerViewModel.queueFlow.collectAsStateWithLifecycle()
    val latestPlaybackQueue = rememberUpdatedState(currentPlaybackQueue)
    val latestQueueSourceName = rememberUpdatedState(currentQueueSourceName)
    val inactiveTimerValueDisplayState = rememberUpdatedState<String?>(null)
    val inactiveTimerDurationMinutesState = rememberUpdatedState<Int?>(null)
    val inactivePlayCountState = rememberUpdatedState(0f)
    val inactiveEndOfTrackTimerActiveState = rememberUpdatedState(false)
    val activeTimerValueDisplay: State<String?> =
        if (isQueueTelemetryActive) {
            playerViewModel.activeTimerValueDisplay.collectAsStateWithLifecycle()
        } else {
            inactiveTimerValueDisplayState
        }
    val activeTimerDurationMinutes: State<Int?> =
        if (isQueueTelemetryActive) {
            playerViewModel.activeTimerDurationMinutes.collectAsStateWithLifecycle()
        } else {
            inactiveTimerDurationMinutesState
        }
    val playCount: State<Float> =
        if (isQueueTelemetryActive) {
            playerViewModel.playCount.collectAsStateWithLifecycle()
        } else {
            inactivePlayCountState
        }
    val isEndOfTrackTimerActive: State<Boolean> =
        if (isQueueTelemetryActive) {
            playerViewModel.isEndOfTrackTimerActive.collectAsStateWithLifecycle()
        } else {
            inactiveEndOfTrackTimerActiveState
        }

    CompositionLocalProvider(
        LocalMaterialTheme provides albumColorScheme
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val onDismissQueueRequest = remember(onAnimateQueueSheet) { { onAnimateQueueSheet(false) } }
            val onQueueSongInfoClick = remember(onSelectedSongForInfoChange) {
                { song: Song -> onSelectedSongForInfoChange(song) }
            }
            val onPlayQueueSong = remember(playerViewModel) {
                { song: Song, index: Int ->
                    playerViewModel.showAndPlaySong(
                        song = song,
                        contextSongs = latestPlaybackQueue.value,
                        queueName = latestQueueSourceName.value,
                        indexInQueue = index
                    )
                }
            }
            val onRemoveQueueSong = remember(playerViewModel) {
                { id: String -> playerViewModel.removeSongFromQueue(id) }
            }
            val onReorderQueue = remember(playerViewModel) {
                { from: Int, to: Int -> playerViewModel.reorderQueueItem(from, to) }
            }
            val onToggleRepeat = remember(playerViewModel) { { playerViewModel.cycleRepeatMode() } }
            val onToggleShuffle = remember(playerViewModel) { { playerViewModel.toggleShuffle() } }
            val onClearQueue = remember(playerViewModel) { { playerViewModel.clearQueueExceptCurrent() } }
            val onSetPredefinedTimer = remember(playerViewModel) {
                { minutes: Int -> playerViewModel.setSleepTimer(minutes) }
            }
            val onSetEndOfTrackTimer = remember(playerViewModel) {
                { enable: Boolean -> playerViewModel.setEndOfTrackTimer(enable) }
            }
            val onOpenCustomTimePicker: () -> Unit = remember {
                { Timber.tag("TimerOptions").d("OpenCustomTimePicker clicked") }
            }
            val onCancelTimer = remember(playerViewModel) { { playerViewModel.cancelSleepTimer() } }
            val onCancelCountedPlay = remember(playerViewModel) { playerViewModel::cancelCountedPlay }
            val onPlayCounter = remember(playerViewModel) { playerViewModel::playCounted }
            val onRequestSavePlaylist = remember(onLaunchSaveQueueOverlay) {
                { songs: List<Song>, defName: String, onConf: (String, Set<String>) -> Unit ->
                    onLaunchSaveQueueOverlay(songs, defName, onConf)
                }
            }
            val onQueueStartDrag = remember(onBeginQueueDrag) { { onBeginQueueDrag() } }
            val onQueueDrag = remember(onDragQueueBy) { { drag: Float -> onDragQueueBy(drag) } }
            val onQueueRelease = remember(onEndQueueDrag) {
                { drag: Float, vel: Float -> onEndQueueDrag(drag, vel) }
            }
            val playbackQueueProvider = remember {
                { latestPlaybackQueue.value }
            }
            val queueSourceNameProvider = remember {
                { latestQueueSourceName.value }
            }

            UnifiedPlayerQueueLayer(
                shouldRenderLayer = true,
                keepQueueSheetWarm = keepQueueSheetWarm,
                albumColorScheme = albumColorScheme,
                queueScrimAlpha = queueScrimAlpha,
                showQueueSheet = showQueueSheet,
                queueHiddenOffsetPx = queueHiddenOffsetPx,
                queueSheetOffset = queueSheetOffset,
                queueSheetHeightPx = queueSheetHeightPx,
                onQueueSheetHeightPxChange = onQueueSheetHeightPxChange,
                configurationResetKey = configurationResetKey,
                currentPlaybackQueue = currentPlaybackQueue,
                currentQueueSourceName = currentQueueSourceName,
                currentMediaItemIndex = infrequentPlayerState.currentMediaItemIndex,
                infrequentPlayerState = infrequentPlayerState,
                activeTimerValueDisplay = activeTimerValueDisplay,
                activeTimerDurationMinutes = activeTimerDurationMinutes,
                playCount = playCount,
                isEndOfTrackTimerActive = isEndOfTrackTimerActive,
                onDismissQueue = onDismissQueueRequest,
                onSongInfoClick = onQueueSongInfoClick,
                onPlaySong = onPlayQueueSong,
                onRemoveSong = onRemoveQueueSong,
                onReorder = onReorderQueue,
                onToggleRepeat = onToggleRepeat,
                onToggleShuffle = onToggleShuffle,
                onClearQueue = onClearQueue,
                onSetPredefinedTimer = onSetPredefinedTimer,
                onSetEndOfTrackTimer = onSetEndOfTrackTimer,
                onOpenCustomTimePicker = onOpenCustomTimePicker,
                onCancelTimer = onCancelTimer,
                onCancelCountedPlay = onCancelCountedPlay,
                onPlayCounter = onPlayCounter,
                onRequestSaveAsPlaylist = onRequestSavePlaylist,
                onQueueDragStart = onQueueStartDrag,
                onQueueDrag = onQueueDrag,
                onQueueRelease = onQueueRelease
            )

            UnifiedPlayerSongInfoLayer(
                selectedSongForInfo = selectedSongForInfo,
                albumColorScheme = albumColorScheme,
                playerViewModel = playerViewModel,
                currentPlaybackQueueProvider = playbackQueueProvider,
                currentQueueSourceNameProvider = queueSourceNameProvider,
                onDismissSongInfo = { onSelectedSongForInfoChange(null) },
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToArtist = onNavigateToArtist,
                onNavigateToGenre = onNavigateToGenre
            )
        }
    }
}

@Composable
internal fun UnifiedPlayerSaveQueueLayer(
    pendingOverlay: SaveQueueOverlayData?,
    onDismissOverlay: () -> Unit
) {
    pendingOverlay?.let { overlay ->
        SaveQueueAsPlaylistSheet(
            songs = overlay.songs,
            defaultName = overlay.defaultName,
            onDismiss = onDismissOverlay,
            onConfirm = { name, selectedIds ->
                overlay.onConfirm(name, selectedIds)
                onDismissOverlay()
            }
        )
    }
}

package com.minoppol.music.presentation.screens

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.minoppol.music.data.model.Song
import com.minoppol.music.presentation.components.subcomps.EnhancedSongListItem
import com.minoppol.music.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Immutable
internal data class LibrarySongPlaybackUiState(
    val isCurrentSong: Boolean = false,
    val isPlaying: Boolean = false
)

@OptIn(UnstableApi::class)
@Composable
internal fun LibraryPlaybackAwareSongItem(
    song: Song,
    playerViewModel: PlayerViewModel,
    albumArtSize: Dp = 50.dp,
    showAlbumArt: Boolean = true,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    showSourceTag: Boolean = true,
    onLongPress: () -> Unit = {},
    onMoreOptionsClick: (Song) -> Unit,
    onClick: () -> Unit
) {
    val playbackUiState by remember(song.id, playerViewModel) {
        playerViewModel.stablePlayerState
            .map { state ->
                val isCurrentSong = state.currentSong?.id == song.id
                LibrarySongPlaybackUiState(
                    isCurrentSong = isCurrentSong,
                    isPlaying = isCurrentSong && state.isPlaying
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = LibrarySongPlaybackUiState())

    EnhancedSongListItem(
        song = song,
        isPlaying = playbackUiState.isPlaying,
        isCurrentSong = playbackUiState.isCurrentSong,
        isLoading = false,
        albumArtSize = albumArtSize,
        showAlbumArt = showAlbumArt,
        isSelected = isSelected,
        selectionIndex = selectionIndex,
        isSelectionMode = isSelectionMode,
        showSourceTag = showSourceTag,
        onLongPress = onLongPress,
        onMoreOptionsClick = onMoreOptionsClick,
        onClick = onClick
    )
}

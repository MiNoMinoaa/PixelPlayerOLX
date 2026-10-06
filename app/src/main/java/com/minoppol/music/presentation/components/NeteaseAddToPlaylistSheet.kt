package com.minoppol.music.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minoppol.music.R
import com.minoppol.music.data.lxmusic.LxPlaylist
import com.minoppol.music.presentation.screens.lxmusic.LxMusicViewModel
import com.minoppol.music.ui.theme.RoundedSans

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeteaseAddToPlaylistSheet(
    state: LxMusicViewModel.OnlineAddSheetState,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val sheetState = rememberModalSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery, state.playlists) {
        if (searchQuery.isBlank()) state.playlists
        else state.playlists.filter { it.name.contains(searchQuery, true) }
    }
    val selectedPlaylists = remember { mutableStateMapOf<String, Boolean>() }
    val isAnySelected = selectedPlaylists.values.any { it }

    val alpha by animateFloatAsState(
        targetValue = if (isAnySelected && !state.adding) 1f else 0.4f,
        label = "fab_alpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { BottomSheetDefaults.modalWindowInsets }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.playlist_picker_select_playlists),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = RoundedSans
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        unfocusedTrailingIconColor = Color.Transparent,
                        focusedSupportingTextColor = Color.Transparent,
                    ),
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.search_playlists_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = CircleShape,
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, null)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    state.loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                    filtered.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.lx_msg_no_own_playlist),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filtered, key = { it.id }) { pl ->
                                val checked = selectedPlaylists[pl.id] == true
                                NeteasePlaylistRow(
                                    playlist = pl,
                                    checked = checked,
                                    enabled = !state.adding,
                                    onClick = {
                                        selectedPlaylists[pl.id] = !checked
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(96.dp)) }
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 18.dp, end = 8.dp)
                    .graphicsLayer { this.alpha = alpha },
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
                onClick = {
                    if (!isAnySelected || state.adding) return@ExtendedFloatingActionButton
                    onConfirm(selectedPlaylists.filter { it.value }.keys.toList())
                }
            ) {
                if (state.adding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.song_picker_action_add))
            }
        }
    }
}

@Composable
private fun NeteasePlaylistRow(
    playlist: LxPlaylist,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (checked) MaterialTheme.colorScheme.surfaceContainerHigh
                else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmartImage(
            model = playlist.pic,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            playlist.trackCount?.let { count ->
                Text(
                    text = stringResource(R.string.lx_text_track_count_format, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (checked) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

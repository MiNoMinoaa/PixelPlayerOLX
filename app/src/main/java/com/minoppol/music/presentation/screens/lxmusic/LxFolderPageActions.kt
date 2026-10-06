package com.minoppol.music.presentation.screens.lxmusic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import com.minoppol.music.R
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle


internal object LxShareUrls {
    fun playlist(id: String): String = "https://music.163.com/#/playlist?id=$id"
    fun album(id: String): String = "https://music.163.com/#/album?id=$id"
    fun of(kind: LxMusicViewModel.CollectKind, id: String): String =
        if (kind == LxMusicViewModel.CollectKind.PLAYLIST) playlist(id) else album(id)
}

@Composable
internal fun LxBottomPlayActions(
    onShuffle: () -> Unit,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilledTonalIconButton(
            onClick = onShuffle,
            enabled = enabled,
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
            onClick = onPlayAll,
            enabled = enabled,
            shape = CircleShape,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.lx_desc_play_all), modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
internal fun rememberLocateButtonState(
    listState: LazyListState,
    currentSongId: String?,
    songIds: List<String>,
    leadingItems: Int = 0,
    perSongMatchIds: List<Set<String>>? = null,
    currentMatchIds: Set<String>? = null,
): Pair<Boolean, Int> {
    val targetIndex = remember(currentSongId, songIds, leadingItems, perSongMatchIds, currentMatchIds) {
        val i = if (perSongMatchIds != null && currentMatchIds != null) {
            perSongMatchIds.indexOfFirst { ids -> ids.any { it in currentMatchIds } }
        } else {
            currentSongId?.let { songIds.indexOf(it) } ?: -1
        }
        if (i >= 0) i + leadingItems else -1
    }
    val rawVisible by remember(targetIndex, listState) {
        derivedStateOf {
            if (targetIndex < 0) {
                false
            } else {
                listState.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }
            }
        }
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(rawVisible) {
        if (rawVisible) {
            delay(250)
            if (rawVisible) visible = true
        } else {
            visible = false
        }
    }
    return visible to targetIndex
}

private val capsuleHeight: Dp = 42.dp
private val capsuleOuterCorner = 26.dp
private val capsuleInnerCorner = 8.dp

@Composable
internal fun LxFolderTitleText(
    text: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = if (expanded) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { expanded = !expanded },
    )
}

@Composable
internal fun LxFolderActionCapsule(
    showLocate: Boolean,
    onLocate: () -> Unit,
    collectState: LxMusicViewModel.CollectState?,
    collectBusy: Boolean,
    onCollectClick: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    buttonElevation: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val gapLocate by animateDpAsState(
        targetValue = if (showLocate) 4.dp else 0.dp,
        label = "gapLocate",
    )
    val collectStartCorner by animateDpAsState(
        targetValue = if (showLocate) capsuleInnerCorner else capsuleOuterCorner,
        label = "collectStartCorner",
    )
    val shareStartCorner by animateDpAsState(
        targetValue = if (collectState == null) capsuleOuterCorner else capsuleInnerCorner,
        label = "shareStartCorner",
    )
    val floating = buttonElevation > 0.dp
    val locateShape = RoundedCornerShape(
        topStart = capsuleOuterCorner,
        bottomStart = capsuleOuterCorner,
        topEnd = capsuleInnerCorner,
        bottomEnd = capsuleInnerCorner,
    )
    val collectShape = RoundedCornerShape(
        topStart = collectStartCorner,
        bottomStart = collectStartCorner,
        topEnd = capsuleInnerCorner,
        bottomEnd = capsuleInnerCorner,
    )
    val shareShape = RoundedCornerShape(
        topStart = shareStartCorner,
        bottomStart = shareStartCorner,
        topEnd = capsuleOuterCorner,
        bottomEnd = capsuleOuterCorner,
    )
    fun Modifier.floatShadow(shape: androidx.compose.ui.graphics.Shape): Modifier =
        if (floating) this.shadow(buttonElevation, shape) else this

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = showLocate,
            enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut(),
        ) {
            FilledTonalIconButton(
                onClick = onLocate,
                shape = locateShape,
                modifier = Modifier
                    .floatShadow(locateShape)
                    .size(capsuleHeight),
            ) {
                Icon(
                    Icons.Rounded.MyLocation,
                    contentDescription = stringResource(R.string.lx_desc_locate_current),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.width(gapLocate))

        if (collectState != null) {
            val locked = collectState == LxMusicViewModel.CollectState.COLLECTED_LOCKED
            FilledTonalIconButton(
                onClick = { if (!locked && !collectBusy) onCollectClick() },
                enabled = !locked && !collectBusy,
                shape = collectShape,
                modifier = Modifier
                    .floatShadow(collectShape)
                    .size(capsuleHeight),
            ) {
                AnimatedContent(
                    targetState = collectState,
                    transitionSpec = {
                        (fadeIn(tween(160)) togetherWith fadeOut(tween(120)))
                    },
                    label = "collectIcon",
                ) { state ->
                    Box(contentAlignment = Alignment.Center) {
                        if (collectBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            val collected = state != LxMusicViewModel.CollectState.UNCOLLECTED
                            if (collected) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = if (locked) stringResource(R.string.lx_desc_collected_locked) else stringResource(R.string.lx_desc_uncollect),
                                    modifier = Modifier.size(21.dp),
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.CreateNewFolder,
                                    contentDescription = stringResource(R.string.lx_desc_collect),
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
        }

        FilledTonalIconButton(
            onClick = onShare,
            shape = shareShape,
            modifier = Modifier
                .floatShadow(shareShape)
                .size(capsuleHeight),
        ) {
            Icon(
                Icons.Rounded.Share,
                contentDescription = stringResource(R.string.lx_desc_share),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val lxUncollectCancelShape = RoundedCornerShape(
    topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 30, bottomEndPercent = 30
)
private val lxUncollectConfirmShape = RoundedCornerShape(
    topStartPercent = 30, bottomStartPercent = 30, topEndPercent = 50, bottomEndPercent = 50
)

@Composable
internal fun LxUncollectConfirmDialog(
    kind: LxMusicViewModel.CollectKind,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val target = if (kind == LxMusicViewModel.CollectKind.PLAYLIST) stringResource(R.string.lx_text_playlist_label) else stringResource(R.string.lx_text_album_label)
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lx_title_uncollect_confirm)) },
        text = { Text(stringResource(R.string.lx_msg_uncollect_confirm, target)) },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onDismiss,
                    shape = lxUncollectCancelShape,
                    modifier = Modifier
                        .shadow(2.dp, lxUncollectCancelShape)
                        .size(42.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lx_desc_cancel))
                }
                FilledTonalIconButton(
                    onClick = onConfirm,
                    shape = lxUncollectConfirmShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .shadow(2.dp, lxUncollectConfirmShape)
                        .size(42.dp),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.lx_desc_uncollect))
                }
            }
        },
    )
}

@Composable
internal fun LxRemoveFromPlaylistConfirmDialog(
    songTitle: String,
    playlistName: String,
    removing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onDismissRequest = { if (!removing) onDismiss() },
        title = { Text(stringResource(R.string.lx_title_remove_from_playlist_confirm)) },
        text = { Text(stringResource(R.string.lx_msg_remove_from_playlist_confirm, songTitle, playlistName)) },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onDismiss,
                    enabled = !removing,
                    shape = lxUncollectCancelShape,
                    modifier = Modifier
                        .shadow(2.dp, lxUncollectCancelShape)
                        .size(42.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lx_desc_cancel))
                }
                FilledTonalIconButton(
                    onClick = onConfirm,
                    enabled = !removing,
                    shape = lxUncollectConfirmShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .shadow(2.dp, lxUncollectConfirmShape)
                        .size(42.dp),
                ) {
                    if (removing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.lx_action_remove_from_playlist))
                    }
                }
            }
        },
    )
}

@Composable
internal fun LxBatchRemoveFromPlaylistConfirmDialog(
    count: Int,
    playlistName: String,
    removing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onDismissRequest = { if (!removing) onDismiss() },
        title = { Text(stringResource(R.string.lx_title_remove_from_playlist_confirm)) },
        text = { Text(stringResource(R.string.lx_msg_batch_remove_from_playlist_confirm, playlistName, count)) },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onDismiss,
                    enabled = !removing,
                    shape = lxUncollectCancelShape,
                    modifier = Modifier
                        .shadow(2.dp, lxUncollectCancelShape)
                        .size(42.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lx_desc_cancel))
                }
                FilledTonalIconButton(
                    onClick = onConfirm,
                    enabled = !removing,
                    shape = lxUncollectConfirmShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .shadow(2.dp, lxUncollectConfirmShape)
                        .size(42.dp),
                ) {
                    if (removing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.lx_action_remove_from_playlist))
                    }
                }
            }
        },
    )
}

@Composable
internal fun rememberFolderShare(
    viewModel: LxMusicViewModel,
): (LxMusicViewModel.CollectKind, String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val shareCopiedMsg = stringResource(R.string.lx_msg_share_link_copied)
    return remember(viewModel, clipboard, shareCopiedMsg) {
        { kind, id ->
            clipboard.setText(AnnotatedString(LxShareUrls.of(kind, id)))
            viewModel.emitToast(shareCopiedMsg)
        }
    }
}

@Composable
internal fun rememberCollectState(
    viewModel: LxMusicViewModel,
    kind: LxMusicViewModel.CollectKind,
    id: String?,
    gid: String? = null,
    name: String? = null,
): LxMusicViewModel.CollectState? {
    val version by viewModel.collectDataVersion.collectAsStateWithLifecycle()
    return remember(kind, id, gid, name, version) { viewModel.collectStateOf(kind, id, gid, name) }
}

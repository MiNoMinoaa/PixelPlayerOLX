@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.minoppol.music.presentation.components

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minoppol.music.R
import com.minoppol.music.presentation.utils.LocalAppHapticsConfig
import com.minoppol.music.presentation.utils.performAppCompatHapticFeedback
import com.minoppol.music.ui.theme.RoundedSans
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReorderPlatformsSheet(
    platforms: ImmutableList<String>,
    platformName: @Composable (String) -> String,
    onReorder: (List<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var localPlatforms by remember { mutableStateOf(platforms) }

    LaunchedEffect(platforms) {
        localPlatforms = platforms
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reorder_tabs_reset_dialog_title)) },
            text = { Text(stringResource(R.string.reorder_tabs_reset_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        localPlatforms = platforms
                        showResetDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_reset), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false }
                ) {
                    Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current

    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            localPlatforms = localPlatforms.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }.toImmutableList()
            performAppCompatHapticFeedback(
                view,
                appHapticsConfig,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        },
        lazyListState = listState
    )
    var isLoading by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.lx_platform_order_title),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = RoundedSans
                    )
                }
            },
            floatingActionButton = {
                FloatingToolBar(
                    modifier = Modifier,
                    onReset = { showResetDialog = true },
                    onDismiss = onDismiss,
                    onClick = {
                        scope.launch {
                            isLoading = true
                            delay(700)
                            onReorder(localPlatforms)
                            isLoading = false
                            onDismiss()
                        }
                    }
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ContainedLoadingIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.reorder_tabs_reordering))
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(localPlatforms, key = { it }) { platform ->
                            ReorderableItem(reorderableState, key = platform) { isDragging ->
                                LaunchedEffect(isDragging) {
                                    if (isDragging) {
                                        performAppCompatHapticFeedback(
                                            view,
                                            appHapticsConfig,
                                            HapticFeedbackConstants.GESTURE_START
                                        )
                                    }
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(CircleShape),
                                    shadowElevation = if (isDragging) 4.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragIndicator,
                                            contentDescription = stringResource(R.string.cd_drag_handle),
                                            modifier = Modifier.draggableHandle()
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = platformName(platform),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

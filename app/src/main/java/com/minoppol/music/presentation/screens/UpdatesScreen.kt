package com.minoppol.music.presentation.screens

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minoppol.music.R
import com.minoppol.music.data.update.UpdateInstallStatus
import com.minoppol.music.presentation.components.AppUpdateDialog
import com.minoppol.music.presentation.components.CollapsibleCommonTopBar
import com.minoppol.music.presentation.components.MiniPlayerHeight
import com.minoppol.music.presentation.settings.search.settingHighlight
import com.minoppol.music.presentation.viewmodel.AppUpdateViewModel
import com.minoppol.music.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun UpdatesScreen(
    highlightKey: String? = null,
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    appUpdateViewModel: AppUpdateViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val updateInfo by appUpdateViewModel.updateInfo.collectAsStateWithLifecycle()
    val installStatus by appUpdateViewModel.installStatus.collectAsStateWithLifecycle()

    var showUpdateDialog by remember { mutableStateOf(false) }

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "0.0.0"
    }

    LaunchedEffect(Unit) { appUpdateViewModel.checkForUpdates(versionName) }
    LaunchedEffect(Unit) {
        appUpdateViewModel.toast.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        appUpdateViewModel.showDialog.collect { showUpdateDialog = true }
    }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp
    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }

    LaunchedEffect(minTopBarHeightPx, maxTopBarHeightPx) {
        val clamped = topBarHeight.value.coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
        if (clamped != topBarHeight.value) topBarHeight.snapTo(clamped)
    }
    val collapseFraction = 1f - (
        (topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)
        ).coerceIn(0f, 1f)

    val nestedScrollConnection = remember(minTopBarHeightPx, maxTopBarHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val scrollingUp = available.y < 0f
                if (!scrollingUp && (
                    lazyListState.firstVisibleItemIndex > 0 ||
                        lazyListState.firstVisibleItemScrollOffset > 0
                    )
                ) return Offset.Zero

                val previous = topBarHeight.value
                val next = (previous + available.y).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = next - previous
                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(next) }
                }
                return if (scrollingUp && next == minTopBarHeightPx) Offset.Zero else Offset(0f, consumed)
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val canExpand = lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0
            val expand = canExpand && topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2f
            topBarHeight.animateTo(
                targetValue = if (expand) maxTopBarHeightPx else minTopBarHeightPx,
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        CollapsibleCommonTopBar(
            title = stringResource(R.string.settings_updates_row_title),
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onBack
        )
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = MiniPlayerHeight +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            )
        ) {
            item {
                UpdatesSubsection(title = stringResource(R.string.setcat_updates_section)) {
                    updateInfo?.let { update ->
                        SettingsItem(
                            title = stringResource(
                                R.string.update_available_title,
                                update.latestVersion
                            ),
                            subtitle = update.releaseNotes
                                ?.takeIf { it.isNotBlank() }
                                ?.lineSequence()
                                ?.firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                                ?: stringResource(R.string.update_available_download),
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Download,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = { showUpdateDialog = true }
                        )
                    }
                    SwitchSettingItem(
                        title = stringResource(R.string.setcat_auto_check_updates_title),
                        subtitle = stringResource(R.string.setcat_auto_check_updates_subtitle),
                        checked = uiState.autoCheckUpdates,
                        onCheckedChange = { settingsViewModel.setAutoCheckUpdates(it) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Update,
                                null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        modifier = Modifier.settingHighlight(
                            "item_about_auto_check_updates",
                            highlightKey
                        )
                    )
                    SettingsItem(
                        title = stringResource(R.string.setcat_updates_check_now_title),
                        subtitle = stringResource(
                            R.string.setcat_updates_check_now_subtitle,
                            versionName
                        ),
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Cached,
                                null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        trailingIcon = {
                            when (installStatus) {
                                is UpdateInstallStatus.Downloading,
                                is UpdateInstallStatus.Installing,
                                is UpdateInstallStatus.AwaitingConfirmation -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                else -> {
                                    Icon(
                                        Icons.Rounded.ChevronRight,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = { appUpdateViewModel.checkForUpdatesManual(versionName) }
                    )
                }
            }
        }

        if (showUpdateDialog) {
            updateInfo?.let { update ->
                AppUpdateDialog(
                    update = update,
                    appUpdateViewModel = appUpdateViewModel,
                    onDismiss = {
                        showUpdateDialog = false
                        appUpdateViewModel.dismissUpdate()
                    }
                )
            }
        }
    }
}

@Composable
private fun UpdatesSubsection(
    title: String,
    addBottomSpace: Boolean = true,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        content()
    }
    if (addBottomSpace) {
        Spacer(modifier = Modifier.height(10.dp))
    }
}

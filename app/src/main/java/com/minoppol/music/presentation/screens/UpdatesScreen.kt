package com.minoppol.music.presentation.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minoppol.music.R
import com.minoppol.music.data.update.UpdateInstallStatus
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
    val isChecking by appUpdateViewModel.isChecking.collectAsStateWithLifecycle()

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
                    UpdateStatusCard(
                        versionName = versionName,
                        updateInfo = updateInfo,
                        installStatus = installStatus,
                        isChecking = isChecking,
                        appUpdateViewModel = appUpdateViewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                }
            }
        }
    }
}

@Composable
private fun UpdateStatusCard(
    versionName: String,
    updateInfo: com.minoppol.music.data.update.AppUpdateInfo?,
    installStatus: UpdateInstallStatus,
    isChecking: Boolean,
    appUpdateViewModel: AppUpdateViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                isChecking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.update_card_checking),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                updateInfo != null -> {
                    Text(
                        text = stringResource(
                            R.string.update_available_title,
                            updateInfo.latestVersion
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    updateInfo.releaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                        val firstLine = notes.lineSequence()
                            .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                        firstLine?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    UpdateCardActions(
                        updateInfo = updateInfo,
                        installStatus = installStatus,
                        appUpdateViewModel = appUpdateViewModel,
                        versionName = versionName,
                        context = context
                    )
                }

                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.update_card_no_update),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        CardTextButton(
                            text = stringResource(R.string.setcat_updates_check_now_title),
                            onClick = { appUpdateViewModel.checkForUpdatesManual(versionName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateCardActions(
    updateInfo: com.minoppol.music.data.update.AppUpdateInfo,
    installStatus: UpdateInstallStatus,
    appUpdateViewModel: AppUpdateViewModel,
    versionName: String,
    context: android.content.Context
) {
    when (installStatus) {
        is UpdateInstallStatus.Downloading -> {
            val progress = installStatus.progress
            if (progress != null) {
                val percent = (progress * 100).roundToInt()
                Text(
                    text = stringResource(
                        R.string.update_card_progress_format,
                        percent
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(
                            R.string.update_install_downloading,
                            installStatus.version
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                CardIconButton(
                    text = stringResource(R.string.update_install_cancel_download),
                    icon = Icons.Rounded.Close,
                    selected = false,
                    onClick = { appUpdateViewModel.stopInstall() }
                )
            }
        }

        is UpdateInstallStatus.AwaitingInstallPermission -> {
            Text(
                text = stringResource(R.string.update_install_awaiting_permission),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardIconButton(
                    text = stringResource(R.string.update_install_allow),
                    icon = Icons.Rounded.Check,
                    selected = true,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    "package:${context.packageName}".toUri()
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
                Spacer(Modifier.weight(1f))
                CardIconButton(
                    text = null,
                    icon = Icons.Rounded.Download,
                    selected = false,
                    contentDescription = stringResource(R.string.update_install_confirm),
                    onClick = { appUpdateViewModel.resumeInstall() }
                )
            }
        }

        is UpdateInstallStatus.AwaitingConfirmation -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.update_install_installing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                CardIconButton(
                    text = null,
                    icon = Icons.Rounded.Download,
                    selected = true,
                    contentDescription = stringResource(R.string.update_install_confirm),
                    onClick = { appUpdateViewModel.openInstallConfirmation() }
                )
            }
        }

        is UpdateInstallStatus.Installing -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.update_install_installing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        is UpdateInstallStatus.Failed -> {
            val reasonRes = when (installStatus.error) {
                com.minoppol.music.data.update.UpdateInstallError.DOWNLOAD_FAILED -> R.string.update_error_download
                com.minoppol.music.data.update.UpdateInstallError.INVALID_APK -> R.string.update_error_invalid
                com.minoppol.music.data.update.UpdateInstallError.NOT_NEWER -> R.string.update_error_not_newer
                com.minoppol.music.data.update.UpdateInstallError.INSTALL_FAILED -> R.string.update_error_install
            }
            Text(
                text = stringResource(R.string.update_install_failed, stringResource(reasonRes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardIconButton(
                    text = null,
                    icon = Icons.Rounded.Refresh,
                    selected = true,
                    contentDescription = stringResource(R.string.update_available_download),
                    onClick = { appUpdateViewModel.startInstall() }
                )
                Spacer(Modifier.weight(1f))
                CardIconButton(
                    text = stringResource(R.string.update_available_cancel),
                    icon = Icons.Rounded.Close,
                    selected = false,
                    onClick = { appUpdateViewModel.dismissInstallError() }
                )
            }
        }

        UpdateInstallStatus.RequiresManualInstall -> {
            Text(
                text = stringResource(R.string.update_install_requires_manual),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardIconButton(
                    text = stringResource(R.string.update_install_open_page),
                    icon = Icons.Rounded.Download,
                    selected = true,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    updateInfo.downloadUrl.toUri()
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
                Spacer(Modifier.weight(1f))
                CardIconButton(
                    text = stringResource(R.string.update_available_cancel),
                    icon = Icons.Rounded.Close,
                    selected = false,
                    onClick = { appUpdateViewModel.dismissInstallError() }
                )
            }
        }

        UpdateInstallStatus.Idle -> {
            if (updateInfo.apkUrl != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CardIconButton(
                        text = stringResource(R.string.update_available_browser_download),
                        icon = Icons.Rounded.OpenInBrowser,
                        selected = false,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        updateInfo.downloadUrl.toUri()
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                    CardIconButton(
                        text = stringResource(R.string.update_available_in_app_download),
                        icon = Icons.Rounded.Download,
                        selected = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { appUpdateViewModel.startInstall() }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.weight(1f))
                    CardIconButton(
                        text = stringResource(R.string.update_available_go_download),
                        icon = Icons.Rounded.Download,
                        selected = true,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        updateInfo.downloadUrl.toUri()
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardIconButton(
                    text = stringResource(R.string.update_available_ignore),
                    icon = Icons.Rounded.Block,
                    selected = false,
                    onClick = { appUpdateViewModel.ignoreVersion(updateInfo.latestVersion) }
                )
                Spacer(Modifier.weight(1f))
                CardIconButton(
                    text = stringResource(R.string.setcat_updates_check_now_title),
                    icon = Icons.Rounded.Refresh,
                    selected = false,
                    onClick = { appUpdateViewModel.checkForUpdatesManual(versionName) }
                )
            }
        }
    }
}

@Composable
private fun CardIconButton(
    text: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (text == null) 10.dp else 12.dp,
                vertical = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = if (selected)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (text != null) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CardTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 1,
            softWrap = false,
        )
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

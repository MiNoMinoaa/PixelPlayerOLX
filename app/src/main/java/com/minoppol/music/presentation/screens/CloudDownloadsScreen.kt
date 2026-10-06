package com.minoppol.music.presentation.screens

import android.text.format.Formatter
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minoppol.music.R
import com.minoppol.music.data.offline.OfflineDownload
import com.minoppol.music.data.offline.OfflineDownloadStatus
import com.minoppol.music.presentation.components.MiniPlayerHeight
import com.minoppol.music.presentation.viewmodel.CloudDownloadsViewModel

@Composable
fun CloudDownloadsScreen(
    onBack: () -> Unit,
    viewModel: CloudDownloadsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackCacheSpaceBytes by viewModel.playbackCacheSpaceBytes.collectAsStateWithLifecycle()
    val playbackCacheLimitMb by viewModel.playbackCacheLimitMb.collectAsStateWithLifecycle()
    val downloadMaxThreads by viewModel.downloadMaxThreads.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.cloud_downloads_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.auth_cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        }
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = scaffoldPadding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = MiniPlayerHeight + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StorageSummaryCard(
                    usedBytes = state.usedBytes,
                    completedCount = state.completed.size,
                    totalCount = state.totalCount
                )
            }

            item {
                PlaybackCacheCard(
                    spaceBytes = playbackCacheSpaceBytes,
                    limitMb = playbackCacheLimitMb,
                    onLimitChange = viewModel::setPlaybackCacheLimitMb,
                    onClear = {
                        viewModel.clearPlaybackCache()
                        Toast.makeText(
                            context,
                            context.getString(R.string.playback_cache_cleared_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            item {
                DownloadThreadsCard(
                    threads = downloadMaxThreads,
                    onThreadsChange = viewModel::setDownloadMaxThreads
                )
            }

            if (state.totalCount == 0) {
                item { DownloadsEmptyState() }
            }

            if (state.downloading.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.cloud_downloads_downloading_section),
                        count = state.downloading.size
                    )
                }
                items(state.downloading, key = OfflineDownload::downloadId) { download ->
                    DownloadItemCard(download = download, onRemove = { viewModel.remove(download) })
                }
            }

            if (state.queued.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.cloud_downloads_queued_section),
                        count = state.queued.size
                    )
                }
                items(state.queued, key = OfflineDownload::downloadId) { download ->
                    DownloadItemCard(download = download, onRemove = { viewModel.remove(download) })
                }
            }

            if (state.failed.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.cloud_downloads_failed_section),
                        count = state.failed.size
                    )
                }
                items(state.failed, key = OfflineDownload::downloadId) { download ->
                    DownloadItemCard(
                        download = download,
                        onRemove = { viewModel.remove(download) },
                        onRetry = { viewModel.retry(download) }
                    )
                }
            }

            if (state.completed.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.cloud_downloads_completed_section),
                        count = state.completed.size
                    )
                }
                items(state.completed, key = OfflineDownload::downloadId) { download ->
                    DownloadItemCard(download = download, onRemove = { viewModel.remove(download) })
                }
            }
        }
    }
}

@Composable
private fun StorageSummaryCard(usedBytes: Long, completedCount: Int, totalCount: Int) {
    val context = LocalContext.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cloud_downloads_storage_used),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = Formatter.formatShortFileSize(context, usedBytes),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(
                        R.string.cloud_downloads_storage_summary,
                        completedCount,
                        totalCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun PlaybackCacheCard(
    spaceBytes: Long,
    limitMb: Int,
    onLimitChange: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    var limitDraft by remember(limitMb) { mutableFloatStateOf(limitMb.toFloat()) }

    Column {
        Text(
            text = stringResource(R.string.playback_cache_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                MaterialTheme.colorScheme.tertiaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Cached,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.playback_cache_usage_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Formatter.formatShortFileSize(context, spaceBytes),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    FilledTonalIconButton(onClick = onClear) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.playback_cache_clear),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.playback_cache_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
                SliderSettingsItem(
                    label = stringResource(R.string.playback_cache_limit_label),
                    value = limitDraft,
                    valueRange = 0f..10240f,
                    steps = 159,
                    onValueChange = { limitDraft = it },
                    onValueChangeFinished = {
                        val selected = limitDraft.toInt()
                        if (selected != limitMb) onLimitChange(selected)
                    },
                    valueText = { value ->
                        val mb = value.toInt()
                        when {
                            mb <= 0 -> context.getString(R.string.playback_cache_disabled)
                            mb >= 1024 -> context.getString(
                                R.string.playback_cache_limit_value_gb,
                                String.format("%.1f", mb / 1024f)
                            )
                            else -> context.getString(R.string.playback_cache_limit_value_mb, mb.toString())
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloadThreadsCard(
    threads: Int,
    onThreadsChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    var threadsDraft by remember(threads) { mutableFloatStateOf(threads.toFloat()) }

    Column {
        Text(
            text = stringResource(R.string.download_max_threads_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp)
        )
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.download_max_threads_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SliderSettingsItem(
                    label = stringResource(R.string.download_max_threads_title),
                    value = threadsDraft,
                    valueRange = 1f..5f,
                    steps = 3,
                    onValueChange = { threadsDraft = it },
                    onValueChangeFinished = {
                        val selected = threadsDraft.toInt()
                        if (selected != threads) onThreadsChange(selected)
                    },
                    valueText = { value ->
                        context.getString(R.string.download_max_threads_value, value.toInt())
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        text = stringResource(R.string.cloud_downloads_section_count, title, count),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp)
    )
}

@Composable
private fun DownloadItemCard(
    download: OfflineDownload,
    onRemove: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val visual = download.status.visual()
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(visual.containerColor(), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = null,
                    tint = visual.contentColor()
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title.ifBlank {
                        stringResource(R.string.cloud_downloads_unknown_track)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = download.provider.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = statusText(download, context),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (download.status == OfflineDownloadStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (download.status == OfflineDownloadStatus.DOWNLOADING) {
                    Spacer(Modifier.height(8.dp))
                    val progress = download.progress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            onRetry?.let {
                IconButton(onClick = it) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cloud_download_retry),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.cloud_remove_download),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DownloadsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.cloud_downloads_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.cloud_downloads_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class DownloadVisual(
    val icon: ImageVector,
    val containerColor: @Composable () -> Color,
    val contentColor: @Composable () -> Color
)

@Composable
private fun OfflineDownloadStatus.visual(): DownloadVisual = when (this) {
    OfflineDownloadStatus.COMPLETE -> DownloadVisual(
        Icons.Rounded.CheckCircle,
        { MaterialTheme.colorScheme.secondaryContainer },
        { MaterialTheme.colorScheme.onSecondaryContainer }
    )
    OfflineDownloadStatus.QUEUED -> DownloadVisual(
        Icons.Rounded.HourglassTop,
        { MaterialTheme.colorScheme.tertiaryContainer },
        { MaterialTheme.colorScheme.onTertiaryContainer }
    )
    OfflineDownloadStatus.DOWNLOADING -> DownloadVisual(
        Icons.Rounded.CloudDownload,
        { MaterialTheme.colorScheme.primaryContainer },
        { MaterialTheme.colorScheme.onPrimaryContainer }
    )
    OfflineDownloadStatus.FAILED -> DownloadVisual(
        Icons.Rounded.Error,
        { MaterialTheme.colorScheme.errorContainer },
        { MaterialTheme.colorScheme.onErrorContainer }
    )
}

@Composable
private fun statusText(download: OfflineDownload, context: android.content.Context): String =
    when (download.status) {
        OfflineDownloadStatus.COMPLETE -> context.getString(
            R.string.cloud_downloads_downloaded_size,
            Formatter.formatShortFileSize(context, download.bytesDownloaded)
        )
        OfflineDownloadStatus.QUEUED -> context.getString(R.string.cloud_download_queued)
        OfflineDownloadStatus.DOWNLOADING -> download.progress?.let {
            context.getString(R.string.cloud_download_progress, (it * 100).toInt())
        } ?: context.getString(R.string.cloud_downloading)
        OfflineDownloadStatus.FAILED -> download.errorMessage
            ?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.cloud_downloads_failed_fallback)
    }

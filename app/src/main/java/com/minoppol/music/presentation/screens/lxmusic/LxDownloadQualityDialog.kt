package com.minoppol.music.presentation.screens.lxmusic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minoppol.music.R
import com.minoppol.music.data.lxmusic.LxDownloadQualityProber
import com.minoppol.music.data.lxmusic.LxQualities
import com.minoppol.music.presentation.viewmodel.SongInfoBottomSheetViewModel
import java.util.Locale

private val lxDownloadCancelShape = RoundedCornerShape(
    topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 30, bottomEndPercent = 30
)
private val lxDownloadConfirmShape = RoundedCornerShape(
    topStartPercent = 30, bottomStartPercent = 30, topEndPercent = 50, bottomEndPercent = 50
)

internal fun formatLxDownloadSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0L) return null
    val mb = bytes / 1024.0 / 1024.0
    return when {
        mb >= 1024.0 -> String.format(Locale.US, "%.2f GB", mb / 1024.0)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}

@Composable
fun LxDownloadQualityDialog(
    state: SongInfoBottomSheetViewModel.LxDownloadDialogState,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        LxQualities.MASTER to (R.string.lxmusic_quality_master to R.string.lxmusic_quality_master_desc),
        LxQualities.ATMOS to (R.string.lxmusic_quality_atmos to R.string.lxmusic_quality_atmos_desc),
        LxQualities.FLAC24 to (R.string.lxmusic_quality_flac24bit to R.string.lxmusic_quality_flac24bit_desc),
        LxQualities.FLAC to (R.string.lxmusic_quality_flac to R.string.lxmusic_quality_flac_desc),
        LxQualities.Q320 to (R.string.lxmusic_quality_320k to R.string.lxmusic_quality_320k_desc),
        LxQualities.Q128 to (R.string.lxmusic_quality_128k to R.string.lxmusic_quality_128k_desc),
    )
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lx_title_download_quality)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                options.forEach { (key, labelDesc) ->
                    val (labelRes, descRes) = labelDesc
                    val probe = state.probes[key]
                    val availability = probe?.availability
                        ?: LxDownloadQualityProber.QualityAvailability.PROBING
                    val isAvailable = availability == LxDownloadQualityProber.QualityAvailability.AVAILABLE
                    val isSelected = state.selectedQuality == key && isAvailable
                    val rowAlpha = if (isAvailable) 1f else 0.45f

                    Surface(
                        onClick = { if (isAvailable) onSelect(key) },
                        enabled = isAvailable,
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .alpha(rowAlpha),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(descRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val sizeText = when (availability) {
                                    LxDownloadQualityProber.QualityAvailability.UNAVAILABLE ->
                                        stringResource(R.string.lx_download_quality_unsupported)
                                    else -> formatLxDownloadSize(probe?.bytes)?.let { size ->
                                        if (probe?.bytesEstimated == true) {
                                            stringResource(R.string.lx_download_estimated_size_approx, size)
                                        } else {
                                            stringResource(R.string.lx_download_estimated_size, size)
                                        }
                                    } ?: stringResource(R.string.lx_download_size_unknown)
                                }
                                Text(
                                    text = sizeText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            when (availability) {
                                LxDownloadQualityProber.QualityAvailability.PROBING -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                }
                                else -> Unit
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onDismiss,
                    shape = lxDownloadCancelShape,
                    modifier = Modifier
                        .shadow(2.dp, lxDownloadCancelShape)
                        .size(42.dp),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.lx_desc_cancel),
                    )
                }
                FilledTonalIconButton(
                    onClick = onConfirm,
                    enabled = state.canDownload,
                    shape = lxDownloadConfirmShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier
                        .shadow(2.dp, lxDownloadConfirmShape)
                        .size(42.dp),
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.lx_desc_download),
                    )
                }
            }
        },
    )
}

package com.minoppol.music.presentation.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minoppol.music.R
import com.minoppol.music.data.update.AppUpdateInfo
import com.minoppol.music.data.update.UpdateInstallError
import com.minoppol.music.data.update.UpdateInstallStatus
import com.minoppol.music.presentation.viewmodel.AppUpdateViewModel

@Composable
fun AppUpdateDialog(
    update: AppUpdateInfo,
    appUpdateViewModel: AppUpdateViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val installStatus by appUpdateViewModel.installStatus.collectAsStateWithLifecycle()
    val statusText = when (val status = installStatus) {
        is UpdateInstallStatus.Downloading ->
            stringResource(R.string.update_install_downloading, status.version)
        is UpdateInstallStatus.AwaitingInstallPermission ->
            stringResource(R.string.update_install_awaiting_permission)
        is UpdateInstallStatus.AwaitingConfirmation ->
            stringResource(R.string.update_install_installing)
        is UpdateInstallStatus.Installing ->
            stringResource(R.string.update_install_installing)
        is UpdateInstallStatus.Failed -> {
            val reasonRes = when (status.error) {
                UpdateInstallError.DOWNLOAD_FAILED -> R.string.update_error_download
                UpdateInstallError.INVALID_APK -> R.string.update_error_invalid
                UpdateInstallError.NOT_NEWER -> R.string.update_error_not_newer
                UpdateInstallError.INSTALL_FAILED -> R.string.update_error_install
            }
            stringResource(R.string.update_install_failed, stringResource(reasonRes))
        }
        UpdateInstallStatus.RequiresManualInstall ->
            stringResource(R.string.update_install_requires_manual)
        UpdateInstallStatus.Idle -> null
    }
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_available_title, update.latestVersion),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (installStatus is UpdateInstallStatus.Idle) {
                    update.releaseNotes?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                statusText?.let {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (
                            installStatus is UpdateInstallStatus.Downloading ||
                            installStatus is UpdateInstallStatus.Installing ||
                            installStatus is UpdateInstallStatus.AwaitingConfirmation
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (installStatus) {
                        UpdateInstallStatus.Idle -> {
                            UpdateDialogButton(
                                label = stringResource(R.string.update_available_ignore),
                                icon = Icons.Rounded.Block,
                                selected = false,
                                onClick = {
                                    onDismiss()
                                    appUpdateViewModel.ignoreVersion(update.latestVersion)
                                }
                            )
                            Spacer(Modifier.weight(1f))
                            UpdateDialogButton(
                                label = stringResource(R.string.update_available_cancel),
                                icon = Icons.Rounded.Close,
                                selected = false,
                                onClick = onDismiss
                            )
                            if (update.apkUrl != null) {
                                UpdateDialogButton(
                                    label = null,
                                    icon = Icons.Rounded.Download,
                                    selected = true,
                                    contentDescription = stringResource(R.string.update_available_download),
                                    onClick = { appUpdateViewModel.startInstall() }
                                )
                            } else {
                                UpdateDialogButton(
                                    label = null,
                                    icon = Icons.Rounded.Download,
                                    selected = true,
                                    contentDescription = stringResource(R.string.update_available_go_download),
                                    onClick = {
                                        appUpdateViewModel.dismissUpdate()
                                        onDismiss()
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    update.downloadUrl.toUri()
                                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        is UpdateInstallStatus.Downloading -> {
                            Spacer(Modifier.weight(1f))
                            UpdateDialogButton(
                                label = stringResource(R.string.update_install_cancel_download),
                                icon = Icons.Rounded.Close,
                                selected = false,
                                onClick = { appUpdateViewModel.stopInstall() }
                            )
                        }
                        is UpdateInstallStatus.AwaitingInstallPermission -> {
                            UpdateDialogButton(
                                label = stringResource(R.string.update_install_allow),
                                icon = Icons.Rounded.Close,
                                selected = false,
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
                            UpdateDialogButton(
                                label = null,
                                icon = Icons.Rounded.Download,
                                selected = true,
                                contentDescription = stringResource(R.string.update_install_confirm),
                                onClick = { appUpdateViewModel.resumeInstall() }
                            )
                        }
                        is UpdateInstallStatus.AwaitingConfirmation -> {
                            Spacer(Modifier.weight(1f))
                            UpdateDialogButton(
                                label = null,
                                icon = Icons.Rounded.Download,
                                selected = true,
                                contentDescription = stringResource(R.string.update_install_confirm),
                                onClick = { appUpdateViewModel.openInstallConfirmation() }
                            )
                        }
                        is UpdateInstallStatus.Installing -> Unit
                        is UpdateInstallStatus.Failed -> {
                            UpdateDialogButton(
                                label = null,
                                icon = Icons.Rounded.Refresh,
                                selected = true,
                                contentDescription = stringResource(R.string.update_available_download),
                                onClick = { appUpdateViewModel.startInstall() }
                            )
                            Spacer(Modifier.weight(1f))
                            UpdateDialogButton(
                                label = stringResource(R.string.update_available_cancel),
                                icon = Icons.Rounded.Close,
                                selected = false,
                                onClick = {
                                    appUpdateViewModel.dismissInstallError()
                                    onDismiss()
                                }
                            )
                        }
                        UpdateInstallStatus.RequiresManualInstall -> {
                            UpdateDialogButton(
                                label = stringResource(R.string.update_install_open_page),
                                icon = Icons.Rounded.Download,
                                selected = true,
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                update.downloadUrl.toUri()
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            )
                            Spacer(Modifier.weight(1f))
                            UpdateDialogButton(
                                label = stringResource(R.string.update_available_cancel),
                                icon = Icons.Rounded.Close,
                                selected = false,
                                onClick = {
                                    appUpdateViewModel.dismissInstallError()
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateDialogButton(
    label: String?,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (label == null) 10.dp else 8.dp,
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
            if (label != null) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

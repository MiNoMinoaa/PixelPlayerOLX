package com.minoppol.music.data.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.minoppol.music.R

/** Notifies about self-update downloads and install confirmations while the app is off screen. */
internal object UpdateNotifications {
    const val DOWNLOAD_ID = 42
    const val CONFIRM_ID = 43
    private const val CHANNEL_ID = "app_updates"

    fun downloadProgress(context: Context, version: String, progress: Float?): Notification {
        ensureChannel(context)
        val builder = baseBuilder(context)
            .setContentTitle(context.getString(R.string.updates_notification_downloading, version))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (progress != null) {
            builder.setProgress(100, (progress * 100).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    fun updateDownloadProgress(context: Context, version: String, progress: Float?) {
        if (!canPost(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(DOWNLOAD_ID, downloadProgress(context, version, progress))
    }

    fun showInstallConfirmation(context: Context, confirmIntent: Intent) {
        if (!canPost(context)) return
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(
            CONFIRM_ID,
            baseBuilder(context)
                .setContentTitle(context.getString(R.string.updates_notification_confirm_title))
                .setContentText(context.getString(R.string.updates_notification_confirm_text))
                .setContentIntent(activityIntent(context, confirmIntent, CONFIRM_ID))
                .setAutoCancel(true)
                .build()
        )
    }

    fun dismissInstallConfirmation(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(CONFIRM_ID)
    }

    private fun baseBuilder(context: Context) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.monochrome_player)
        .setContentIntent(activityIntent(context, launchIntent(context), DOWNLOAD_ID))

    private fun launchIntent(context: Context): Intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()

    private fun activityIntent(context: Context, intent: Intent, id: Int): PendingIntent? {
        if (intent.action == null && intent.component == null && intent.data == null) return null
        return PendingIntent.getActivity(
            context,
            id,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.updates_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null) }
        )
    }
}

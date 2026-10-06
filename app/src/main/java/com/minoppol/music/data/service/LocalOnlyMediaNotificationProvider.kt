package com.minoppol.music.data.service

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

@androidx.annotation.OptIn(UnstableApi::class)
class LocalOnlyMediaNotificationProvider(
    private val context: Context,
    private val delegate: DefaultMediaNotificationProvider =
        DefaultMediaNotificationProvider.Builder(context).build(),
) : MediaNotification.Provider {

    @Volatile
    var currentLyricLine: String? = null

    @Volatile
    var cachedActionFactory: MediaNotification.ActionFactory? = null

    @Volatile
    var cachedCallback: MediaNotification.Provider.Callback? = null

    @Volatile
    var cachedCustomLayout: ImmutableList<CommandButton> = ImmutableList.of()

    fun setSmallIcon(iconResId: Int) {
        delegate.setSmallIcon(iconResId)
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        cachedActionFactory = actionFactory
        cachedCallback = callback
        cachedCustomLayout = customLayout
        val mediaNotification = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            callback
        )
        return MediaNotification(
            mediaNotification.notificationId,
            decorate(mediaNotification.notification)
        )
    }

    fun buildNotification(mediaSession: MediaSession): MediaNotification? {
        val factory = cachedActionFactory ?: return null
        val callback = cachedCallback ?: return null
        return runCatching {
            val mediaNotification = delegate.createNotification(
                mediaSession,
                cachedCustomLayout,
                factory,
                callback
            )
            MediaNotification(mediaNotification.notificationId, decorate(mediaNotification.notification))
        }.getOrNull()
    }

    private fun decorate(notification: Notification): Notification {
        return runCatching {
            val builder = Notification.Builder.recoverBuilder(context, notification)
                .setLocalOnly(true)
            val line = currentLyricLine?.takeIf { it.isNotBlank() }
            line?.let {
                val songTitle = notification.extras?.getString(Notification.EXTRA_TITLE)
                val songArtist = notification.extras?.getString(Notification.EXTRA_TEXT)
                builder.setContentTitle(it)
                if (!songTitle.isNullOrBlank()) {
                    builder.setContentText(songTitle)
                    if (!songArtist.isNullOrBlank()) {
                        builder.setSubText(songArtist)
                    }
                }
            }
            builder.build()
        }.getOrElse {
            notification
        }
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.getNotificationChannelInfo()
}

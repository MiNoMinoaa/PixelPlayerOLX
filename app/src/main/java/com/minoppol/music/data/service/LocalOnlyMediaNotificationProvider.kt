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
    private var cachedBaseNotification: MediaNotification? = null

    @Volatile
    private var cachedSongTitle: String? = null

    @Volatile
    private var cachedSongArtist: String? = null

    fun setSmallIcon(iconResId: Int) {
        delegate.setSmallIcon(iconResId)
    }

    fun invalidateCache() {
        cachedBaseNotification = null
        cachedSongTitle = null
        cachedSongArtist = null
        currentLyricLine = null
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val mediaNotification = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            callback
        )
        cachedBaseNotification = mediaNotification
        cachedSongTitle = mediaNotification.notification.extras
            ?.getString(Notification.EXTRA_TITLE)
        cachedSongArtist = mediaNotification.notification.extras
            ?.getString(Notification.EXTRA_TEXT)
        return MediaNotification(
            mediaNotification.notificationId,
            decorate(mediaNotification.notification)
        )
    }

    fun buildNotification(mediaSession: MediaSession): MediaNotification? {
        val base = cachedBaseNotification ?: return null
        return MediaNotification(base.notificationId, decorate(base.notification))
    }

    private fun decorate(notification: Notification): Notification {
        return runCatching {
            val builder = Notification.Builder.recoverBuilder(context, notification)
                .setLocalOnly(true)
            val line = currentLyricLine?.takeIf { it.isNotBlank() }
            if (line != null) {
                builder.setContentTitle(line)
                builder.setContentText(cachedSongTitle ?: "")
                val artist = cachedSongArtist
                if (!artist.isNullOrBlank()) {
                    builder.setSubText(artist)
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

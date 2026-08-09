package com.lostf1sh.pixelplayeross.data.service.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.data.provider.SharedArtworkContentProvider
import com.lostf1sh.pixelplayeross.utils.LocalArtworkUri

/**
 * Rewrites app-internal artwork URIs on outgoing media items to shareable content URIs, so
 * external session consumers (system media surfaces, Android Auto, etc.) can actually load
 * the artwork instead of receiving an unreadable custom scheme.
 */
@OptIn(UnstableApi::class)
class MappingPlayer(
    val innerPlayer: Player,
    private val context: Context
) : ForwardingPlayer(innerPlayer) {

    private fun mapMediaItem(mediaItem: MediaItem?): MediaItem? {
        if (mediaItem == null) return null
        val artworkUri = mediaItem.mediaMetadata.artworkUri
        if (artworkUri != null && LocalArtworkUri.isLocalArtworkUri(artworkUri)) {
            val songId = LocalArtworkUri.parseSongId(artworkUri.toString()) ?: return mediaItem
            val contentUri = SharedArtworkContentProvider.buildSongUri(
                context = context,
                songId = songId,
                cacheBustToken = LocalArtworkUri.extractCacheBustToken(artworkUri.toString())
            )
            val mappedMetadata = mediaItem.mediaMetadata.buildUpon()
                .setArtworkUri(contentUri)
                .build()
            return mediaItem.buildUpon()
                .setMediaMetadata(mappedMetadata)
                .build()
        }
        return mediaItem
    }

    override fun getCurrentMediaItem(): MediaItem? {
        return mapMediaItem(super.getCurrentMediaItem())
    }

    override fun getMediaItemAt(index: Int): MediaItem {
        return mapMediaItem(super.getMediaItemAt(index))!!
    }

    override fun getMediaMetadata(): MediaMetadata {
        val metadata = super.getMediaMetadata()
        val artworkUri = metadata.artworkUri
        if (artworkUri != null && LocalArtworkUri.isLocalArtworkUri(artworkUri)) {
            val songId = LocalArtworkUri.parseSongId(artworkUri.toString()) ?: return metadata
            val contentUri = SharedArtworkContentProvider.buildSongUri(
                context = context,
                songId = songId,
                cacheBustToken = LocalArtworkUri.extractCacheBustToken(artworkUri.toString())
            )
            return metadata.buildUpon()
                .setArtworkUri(contentUri)
                .build()
        }
        return metadata
    }

    override fun getCurrentTimeline(): Timeline {
        val timeline = super.getCurrentTimeline()
        if (timeline.isEmpty) return timeline
        return object : Timeline() {
            override fun getWindowCount(): Int = timeline.windowCount
            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                val w = timeline.getWindow(windowIndex, window, defaultPositionProjectionUs)
                w.mediaItem = mapMediaItem(w.mediaItem) ?: MediaItem.EMPTY
                return w
            }
            override fun getPeriodCount(): Int = timeline.periodCount
            override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
                return timeline.getPeriod(periodIndex, period, setIds)
            }
            override fun getIndexOfPeriod(periodUid: Any): Int = timeline.getIndexOfPeriod(periodUid)
            override fun getUidOfPeriod(periodIndex: Int): Any = timeline.getUidOfPeriod(periodIndex)
        }
    }
}

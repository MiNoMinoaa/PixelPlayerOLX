package com.minoppol.music.data.media

import android.content.Context
import androidx.media3.common.MediaItem
import com.minoppol.music.R
import com.minoppol.music.data.model.Song
import com.minoppol.music.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper to map MediaItem to Song.
 * Note: This does NOT have access to the full song library master list,
 * so it should be used for strictly metadata-based mapping or fallback.
 * The ViewModel should try lookup by ID first.
 */
@Singleton
class MediaMapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun resolveSongFromMediaItem(mediaItem: MediaItem): Song? {
        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras
        val contentUri = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)
            ?: mediaItem.localConfiguration?.uri?.toString()
            ?: return null

        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_song_title)
        val artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_artist)
        val album = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM)?.takeIf { it.isNotBlank() }
            ?: metadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_album)
        val albumId = -1L
        val duration = extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION) ?: 0L
        val dateAdded = extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DATE_ADDED) ?: System.currentTimeMillis()
        val filePath = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH)
            ?.takeIf { it.isNotBlank() }
            ?: mediaItem.localConfiguration?.uri
                ?.takeIf { it.scheme.equals("file", ignoreCase = true) }
                ?.path
                .orEmpty()
        val id = mediaItem.mediaId

        val lxParsed = com.minoppol.music.data.lxmusic.LxSongMapper.parseContentUri(contentUri)
        val lxHash = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_LX_HASH)
        val lxExtraFields = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_LX_EXTRA_FIELDS)
            ?.takeIf { it.isNotBlank() }
            ?.let { json ->
                runCatching {
                    val obj = JSONObject(json)
                    obj.keys().asSequence().associateWith { key -> obj.optString(key, "") }
                }.getOrDefault(emptyMap())
            } ?: emptyMap()

        return Song(
            id = id,
            title = title,
            artist = artist,
            artistId = -1L,
            album = album,
            albumId = albumId,
            path = filePath,
            contentUriString = contentUri,
            albumArtUriString = metadata.artworkUri?.toString(),
            duration = duration,
            dateAdded = dateAdded,
            mimeType = null,
            bitrate = null,
            sampleRate = null,
            lxMusicSource = lxParsed?.first,
            lxMusicSongMid = lxParsed?.second,
            lxMusicHash = lxHash,
            lxMusicExtraFields = lxExtraFields,
        )
    }
}

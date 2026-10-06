package com.minoppol.music.data.media

import android.graphics.BitmapFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import timber.log.Timber

@Singleton
class LxDownloadTagWriter @Inject constructor() {

    fun writeTags(
        file: File,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray? = null,
        lyrics: String? = null,
    ): Boolean = runCatching {
        java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tag ?: audioFile.createDefaultTag()

        if (title.isNotBlank()) tag.setField(FieldKey.TITLE, title)
        if (artist.isNotBlank()) {
            tag.setField(FieldKey.ARTIST, artist)
            tag.setField(FieldKey.ALBUM_ARTIST, artist)
        }
        if (album.isNotBlank()) tag.setField(FieldKey.ALBUM, album)
        if (!lyrics.isNullOrBlank()) tag.setField(FieldKey.LYRICS, lyrics)

        if (coverBytes != null && coverBytes.isNotEmpty()) {
            runCatching {
                tag.deleteArtworkField()
                val artwork = AndroidArtwork().apply {
                    binaryData = coverBytes
                    mimeType = detectImageMime(coverBytes)
                    pictureType = 3
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, options)
                    width = options.outWidth
                    height = options.outHeight
                }
                tag.setField(artwork)
            }.onFailure { Timber.w(it, "embed cover failed") }
        }

        audioFile.commit()
        true
    }.onFailure { Timber.w(it, "LxDownloadTagWriter failed: ${file.absolutePath}") }
        .getOrDefault(false)

    private fun detectImageMime(bytes: ByteArray): String {
        if (bytes.size < 12) return "image/jpeg"
        return when {
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
            bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[8] == 0x57.toByte() -> "image/webp"
            else -> "image/jpeg"
        }
    }
}

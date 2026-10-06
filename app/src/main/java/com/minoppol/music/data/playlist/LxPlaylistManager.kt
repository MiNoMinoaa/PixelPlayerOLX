package com.minoppol.music.data.playlist

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.minoppol.music.data.lxmusic.LxSong
import com.minoppol.music.data.model.Playlist
import com.minoppol.music.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LxPlaylistManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportLxPlaylist(playlist: Playlist, songs: List<Song>): ByteArray? {
        val json = exportLxPlaylistJson(playlist, songs) ?: return null
        return gzip(json.toByteArray(Charsets.UTF_8))
    }

    fun exportLxPlaylistJson(playlist: Playlist, songs: List<Song>): String? {
        val lxSongs = songs.filter { !it.lxMusicSource.isNullOrBlank() && !it.lxMusicSongMid.isNullOrBlank() }
        if (lxSongs.isEmpty()) return null

        val list = JsonArray().apply {
            for (song in lxSongs) {
                val source = song.lxMusicSource!!
                val songmid = song.lxMusicSongMid!!
                val meta = JsonObject().apply {
                    addProperty("songId", songmid)
                    addProperty("albumName", song.album)
                    song.albumArtUriString?.let { addProperty("picUrl", it) }
                    song.lxMusicHash?.let { addProperty("hash", it) }
                    song.lxMusicCopyrightId?.let { addProperty("copyrightId", it) }
                    add("qualitys", JsonArray())
                    add("_qualitys", JsonObject())
                }
                add(JsonObject().apply {
                    addProperty("id", "${source}_$songmid")
                    addProperty("name", song.title)
                    addProperty("singer", song.artist)
                    addProperty("source", source)
                    addProperty("interval", formatInterval(song.duration))
                    add("meta", meta)
                })
            }
        }

        val data = JsonObject().apply {
            addProperty("id", playlist.id)
            addProperty("name", playlist.name)
            add("list", list)
        }
        val root = JsonObject().apply {
            addProperty("type", "playListPart_v2")
            add("data", data)
        }
        return gson.toJson(root)
    }

    fun parseLxPlaylist(uri: Uri): Pair<String, List<LxSong>> {
        val content = readFileText(uri)

        val root = JsonParser.parseString(content).asJsonObject
        val data = root.getAsJsonObject("data") ?: root
        val name = data.get("name")?.asString ?: "Imported LX Playlist"
        val listArr = data.getAsJsonArray("list") ?: JsonArray()

        val songs = listArr.mapNotNull { elem ->
            runCatching { parseSong(elem.asJsonObject) }.getOrNull()
        }
        return name to songs
    }

    fun readFileText(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ""
        return decodeText(bytes)
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            return runCatching {
                GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            }.getOrDefault(String(bytes, Charsets.UTF_8))
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun parseSong(obj: JsonObject): LxSong? {
        val name = obj.get("name")?.asString ?: return null
        val singer = obj.get("singer")?.asString ?: ""
        val source = obj.get("source")?.asString ?: return null
        if (source.isBlank()) return null

        val meta = obj.getAsJsonObject("meta")
        val songmid = meta?.get("songId")?.asString
            ?: obj.get("songmid")?.asString
            ?: obj.get("songId")?.asString
            ?: return null
        if (songmid.isBlank()) return null

        val albumName = meta?.get("albumName")?.asString
            ?: obj.get("albumName")?.asString
            ?: ""
        val pic = meta?.get("picUrl")?.asString
            ?: obj.get("pic")?.asString
        val interval = obj.get("interval")?.let { el ->
            if (el.isJsonPrimitive) {
                val raw = el.asString
                parseIntervalSeconds(raw)
            } else null
        } ?: 0L
        val hash = meta?.get("hash")?.asString
        val copyrightId = meta?.get("copyrightId")?.asString

        return LxSong(
            source = source,
            songmid = songmid,
            name = name,
            singer = singer,
            albumName = albumName,
            interval = interval,
            pic = pic,
            hash = hash,
            copyrightId = copyrightId,
        )
    }

    private fun formatInterval(durationMs: Long): String {
        if (durationMs <= 0) return "00:00"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun parseIntervalSeconds(value: String): Long {
        val parts = value.split(':')
        return if (parts.size == 2) {
            parts[0].toLongOrNull()?.times(60)?.plus(parts[1].toLongOrNull() ?: 0) ?: 0L
        } else {
            value.toLongOrNull() ?: 0L
        }
    }

    companion object {
        fun isLxPlaylistContent(content: String): Boolean {
            val trimmed = content.trimStart()
            if (!trimmed.startsWith("{")) return false
            return runCatching {
                val root = JsonParser.parseString(trimmed).asJsonObject
                val type = root.get("type")?.asString
                type == "playListPart_v2" || type == "playListPart" ||
                    type == "playList_v2" || root.has("data") && root.getAsJsonObject("data").has("list")
            }.getOrDefault(false)
        }
    }
}

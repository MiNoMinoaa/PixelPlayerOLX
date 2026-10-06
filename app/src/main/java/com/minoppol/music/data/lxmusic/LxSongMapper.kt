package com.minoppol.music.data.lxmusic

import com.minoppol.music.data.database.SongEntity
import com.minoppol.music.data.database.SourceType
import com.minoppol.music.data.model.Song

object LxSongMapper {

    private fun fnv1a64(raw: String): Long {
        var hash = -3750763034362895579L // FNV offset basis 0xcbf29ce484222325
        val prime = 1099511628211L
        for (c in raw) {
            hash = hash xor c.code.toLong()
            hash *= prime
        }
        return hash
    }

    // 在线歌没有数字 id，用 FNV 哈希成负数，和本地歌的正数 id 区分开
    private fun negativeNamespacedId(namespace: String, key: String): Long {
        var h = fnv1a64("${namespace}_$key") and Long.MAX_VALUE
        if (h == 0L) h = 1L
        return -h
    }

    fun toSong(song: LxSong): Song {
        val unifiedId = unifiedSongId(song.source, song.songmid)
        return Song(
            id = unifiedId.toString(),
            title = song.name,
            artist = song.singer,
            artistId = -1L,
            album = song.albumName,
            albumId = -1L,
            path = "",
            contentUriString = toContentUri(song.source, song.songmid),
            albumArtUriString = song.pic,
            duration = song.interval * 1000L,
            mimeType = "audio/mpeg",
            bitrate = null,
            sampleRate = null,
            lxMusicSource = song.source,
            lxMusicSongMid = song.songmid,
            lxMusicHash = song.hash,
            lxMusicCopyrightId = song.copyrightId,
            lxMusicExtraFields = song.extraFields,
        )
    }

    fun toContentUri(source: String, songmid: String): String =
        "lxmusic://$source/$songmid"

    // 各平台歌曲的网页地址，分享和外链跳转用
    fun webSongUrl(
        source: String,
        songmid: String,
        hash: String? = null,
        copyrightId: String? = null,
    ): String? = when (source) {
        "wy" -> "https://music.163.com/#/song?id=$songmid"
        "kw" -> "http://www.kuwo.cn/play_detail/$songmid"
        "tx" -> "https://y.qq.com/n/yqq/song/$songmid.html"
        "kg" -> "https://www.kugou.com/song/#hash=${hash?.takeIf { it.isNotBlank() } ?: songmid}"
        "mg" -> "https://music.migu.cn/v3/music/song/${copyrightId?.takeIf { it.isNotBlank() } ?: songmid}"
        else -> null
    }

    fun webSongUrl(song: Song): String? {
        val source = song.lxMusicSource ?: return null
        val songmid = song.lxMusicSongMid ?: return null
        if (source.isBlank() || songmid.isBlank()) return null
        return webSongUrl(source, songmid, song.lxMusicHash, song.lxMusicCopyrightId)
    }

    fun parseContentUri(uri: String?): Pair<String, String>? {
        if (uri.isNullOrBlank()) return null
        if (!uri.startsWith("lxmusic://")) return null
        val rest = uri.removePrefix("lxmusic://")
        val slash = rest.indexOf('/')
        if (slash <= 0) return null
        val source = rest.substring(0, slash)
        val songmid = rest.substring(slash + 1)
        if (source.isBlank() || songmid.isBlank()) return null
        return source to songmid
    }

    fun unifiedSongId(source: String, songmid: String): Long =
        negativeNamespacedId("lx_song_$source", songmid)

    fun matchIds(source: String, songmid: String, hash: String?): Set<String> {
        val ids = mutableSetOf(unifiedSongId(source, songmid).toString())
        hash?.takeIf { it.isNotBlank() }?.let { ids.add(unifiedSongId(source, it).toString()) }
        return ids
    }

    fun matchIds(song: Song): Set<String> {
        val source = song.lxMusicSource ?: return setOf(song.id)
        val mid = song.lxMusicSongMid ?: return setOf(song.id)
        return matchIds(source, mid, song.lxMusicHash)
    }

    fun toLxSong(song: Song): LxSong? {
        val source = song.lxMusicSource ?: return null
        val songmid = song.lxMusicSongMid ?: return null
        if (source.isBlank() || songmid.isBlank()) return null
        return LxSong(
            source = source,
            songmid = songmid,
            name = song.title,
            singer = song.artist,
            albumName = song.album,
            interval = song.duration / 1000L,
            pic = song.albumArtUriString,
            hash = song.lxMusicHash,
            copyrightId = song.lxMusicCopyrightId,
            extraFields = song.lxMusicExtraFields,
        )
    }

    fun toSongEntity(song: LxSong): SongEntity {
        val unifiedId = unifiedSongId(song.source, song.songmid)
        val artistId = toUnifiedArtistId(song.singer)
        val albumName = song.albumName.ifBlank { "未知专辑" }
        val albumId = toUnifiedAlbumId(albumName)
        val now = System.currentTimeMillis()
        return SongEntity(
            id = unifiedId,
            title = song.name,
            artistName = song.singer.ifBlank { "未知歌手" },
            artistId = artistId,
            albumArtist = song.singer.ifBlank { null },
            albumArtistId = artistId,
            albumName = albumName,
            albumId = albumId,
            contentUriString = toContentUri(song.source, song.songmid),
            albumArtUriString = song.pic,
            duration = song.interval * 1000L,
            genre = null,
            filePath = "",
            parentDirectoryPath = "",
            sourceType = SourceType.LX_MUSIC,
            dateAdded = now,
            mimeType = "audio/mpeg",
            lxHash = song.hash,
            lxExtraFieldsJson = if (song.extraFields.isEmpty()) null else org.json.JSONObject(song.extraFields as Map<*, *>).toString(),
        )
    }

    fun toUnifiedArtistId(artistName: String): Long {
        val name = artistName.ifBlank { "未知歌手" }
        return negativeNamespacedId("lx_artist", name.lowercase())
    }

    fun toUnifiedAlbumId(albumName: String): Long =
        negativeNamespacedId("lx_album", albumName.lowercase())
}

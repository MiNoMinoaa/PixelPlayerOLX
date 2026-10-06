package com.minoppol.music.data.lxmusic

import androidx.compose.ui.graphics.Color


object LxSources {
    const val KUWO = "kw"
    const val KUGOU = "kg"
    const val TENCENT = "tx"
    const val NETEASE = "wy"
    const val MIGU = "mg"
    const val XIMALAYA = "xm"
    const val LOCAL = "local"

    val ALL = listOf(NETEASE, KUWO, TENCENT, KUGOU)

    fun shortLabel(source: String): String = if (source == TENCENT) "qq" else source

    fun englishName(source: String): String = when (source) {
        NETEASE -> "NetEase"
        KUWO -> "Kuwo"
        KUGOU -> "Kugou"
        TENCENT -> "QQ Music"
        MIGU -> "Migu"
        else -> source.uppercase()
    }

    fun brandColor(source: String): Color = when (source) {
        NETEASE -> Color(0xFFEC4141)
        KUWO -> Color(0xFFFFB300)
        TENCENT -> Color(0xFF31C27C)
        KUGOU -> Color(0xFF2BA1E0)
        else -> Color(0xFF8A8A8A)
    }

    fun onBrandColor(source: String): Color =
        if (source == KUWO) Color(0xFF3B2500) else Color.White
}

enum class LxAddToPlaylistResult { ADDED, ALREADY_EXISTS, FAILED }

object LxQualities {
    const val Q128 = "128k"
    const val Q320 = "320k"
    const val FLAC = "flac"
    const val FLAC24 = "flac24bit"
    const val ATMOS = "atmos"
    const val MASTER = "master"

    val ALL = listOf(MASTER, ATMOS, FLAC24, FLAC, Q320, Q128)
}

data class LxSong(
    val source: String,
    val songmid: String,
    val name: String,
    val singer: String,
    val albumName: String = "",
    val interval: Long = 0L,
    val pic: String? = null,
    val lrc: String? = null,
    val qualitys: List<String> = emptyList(),
    val hash: String? = null,
    val copyrightId: String? = null,
    val extraFields: Map<String, String> = emptyMap(),
)

data class LxLyricResult(
    val lyric: String,
    val tlyric: String? = null,
    val rlyric: String? = null,
    val lxlyric: String? = null,
)

data class LxBoard(
    val id: String,
    val name: String,
    val bangid: String,
)

data class LxPlaylistTag(
    val id: String,
    val name: String,
)

data class LxPlaylist(
    val id: String,
    val name: String,
    val pic: String? = null,
    val playCount: Long? = null,
    val trackCount: Int? = null,
    val creator: String? = null,
    val specialType: Int = 0,
    val subscribed: Boolean? = null,
    val originGid: String? = null,
    val creatorUserId: String? = null,
)

data class LxSongPage(
    val isEnd: Boolean,
    val songs: List<LxSong>,
)

data class LxSongList(
    val id: String,
    val name: String,
    val cover: String? = null,
    val creator: String = "",
    val playCount: Long = 0L,
)

data class LxNeteaseProfile(
    val uid: Long,
    val nickname: String,
    val avatarUrl: String? = null,
    val vipType: Int = 0,
)

data class LxArtist(
    val id: String,
    val name: String,
    val pic: String? = null,
    val albumSize: Int = 0,
    val briefDesc: String? = null,
)

data class LxArtistDetail(
    val artist: LxArtist,
    val followed: Boolean? = null,
    val briefDesc: String? = null,
)

data class LxAlbum(
    val id: String,
    val name: String,
    val pic: String? = null,
    val artistName: String? = null,
    val publishTime: Long = 0L,
    val size: Int = 0,
)

data class LxScriptMeta(
    val assetPath: String,
    val name: String,
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val isImported: Boolean = false,
)

data class LxSourceInfo(
    val source: String,
    val type: String = "music",
    val actions: List<String> = emptyList(),
    val qualitys: List<String> = emptyList(),
)

data class LxScriptCapabilities(
    val sources: Map<String, LxSourceInfo> = emptyMap(),
)

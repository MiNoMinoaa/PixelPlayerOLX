package com.minoppol.music.data.lxmusic

import com.minoppol.music.data.lxmusic.platform.PlatformResult
import timber.log.Timber

internal interface LxAccountExtension {
    val isAvailable: Boolean

    suspend fun kgSendSmsCode(mobile: String): Boolean

    suspend fun kgLoginBySmsCode(mobile: String, code: String): Boolean

    suspend fun kgApplySongLike(
        songmid: String,
        hash: String?,
        name: String,
        singer: String,
        albumId: String?,
        mixSongId: String?,
        like: Boolean,
    ): Boolean

    suspend fun kgQueryFollowInfo(singerId: String): Boolean?

    suspend fun kgApplyFollowSinger(singerId: String, follow: Boolean, source: Int): Boolean

    suspend fun kgSubscribePlaylist(name: String, gid: String): Boolean

    suspend fun kgUnsubscribe(gid: String?, name: String?): Boolean

    suspend fun kgDeleteCloudList(listid: String): Boolean

    suspend fun kgCollectByCopy(name: String, songs: List<LxSong>): Boolean

    suspend fun kgAddSongsToPlaylist(listid: String, songs: List<LxSong>): LxAddToPlaylistResult

    suspend fun kgRemoveSongsFromPlaylist(listid: String, songs: List<LxSong>): Boolean

    suspend fun kwApplySongLike(songId: String, like: Boolean): Boolean

    suspend fun kwAddSongsToPlaylist(playlistId: String, songIds: List<String>): LxAddToPlaylistResult

    suspend fun kwRemoveSongsFromPlaylist(playlistId: String, songIds: List<String>): Boolean

    suspend fun kwSetPlaylistCollect(playlistId: String, collect: Boolean): Boolean

    suspend fun kwApplyArtistFollow(artistId: String, follow: Boolean): Boolean

    suspend fun kwApplyAlbumLike(albumId: String, like: Boolean): Boolean

    suspend fun txApplySongLike(songId: String, like: Boolean): Boolean


    suspend fun kwFetchProfile(): Pair<String?, String?>?

    suspend fun kwFetchUserPlaylists(): List<LxPlaylist>?

    suspend fun kwFetchFollowedArtists(): List<LxArtist>?

    suspend fun kwFetchLikedAlbums(): List<LxAlbum>?

    suspend fun kwFetchLikedSongIds(): Set<String>?

    suspend fun kwFetchLikedSongs(): List<LxSong>?

    suspend fun kwFetchUserPlaylistSongs(playlistId: String): List<LxSong>?


    suspend fun kgFetchUserPlaylists(): List<LxPlaylist>?

    suspend fun kgFetchCloudPlaylistSongs(listid: String): List<LxSong>?

    suspend fun kgFetchLikedSongIds(): Set<String>?

    suspend fun kgFetchLikedSongs(): List<LxSong>?

    suspend fun kgFetchProfile(): Pair<String?, String?>?


    suspend fun txFetchProfile(): Pair<String?, String?>?

    suspend fun txFetchLikedSongs(): List<LxSong>?

    suspend fun txFetchLikedAlbums(): List<LxAlbum>?

    suspend fun txFetchUserPlaylists(): List<LxPlaylist>?

    suspend fun txFetchPlaylistSongs(disstid: String): List<LxSong>?


    suspend fun txQrCreate(type: LxTxQrType): Pair<ByteArray, String>?

    suspend fun txQrPoll(type: LxTxQrType, session: String): LxTxQrPoll


    suspend fun kwFetchPlaylistSquareSongs(playlistId: String): List<LxSong>?

    suspend fun kwResolvePlayUrl(songmid: String): PlatformResult?

    fun clearReadCaches()
}

internal object LxUnavailableAccountExtension : LxAccountExtension {
    override val isAvailable: Boolean = false
    override suspend fun kgSendSmsCode(mobile: String): Boolean = false
    override suspend fun kgLoginBySmsCode(mobile: String, code: String): Boolean = false
    override suspend fun kgApplySongLike(
        songmid: String,
        hash: String?,
        name: String,
        singer: String,
        albumId: String?,
        mixSongId: String?,
        like: Boolean,
    ): Boolean = false
    override suspend fun kgQueryFollowInfo(singerId: String): Boolean? = null
    override suspend fun kgApplyFollowSinger(singerId: String, follow: Boolean, source: Int): Boolean = false
    override suspend fun kgSubscribePlaylist(name: String, gid: String): Boolean = false
    override suspend fun kgUnsubscribe(gid: String?, name: String?): Boolean = false
    override suspend fun kgDeleteCloudList(listid: String): Boolean = false
    override suspend fun kgCollectByCopy(name: String, songs: List<LxSong>): Boolean = false
    override suspend fun kgAddSongsToPlaylist(listid: String, songs: List<LxSong>): LxAddToPlaylistResult = LxAddToPlaylistResult.FAILED
    override suspend fun kgRemoveSongsFromPlaylist(listid: String, songs: List<LxSong>): Boolean = false
    override suspend fun kwApplySongLike(songId: String, like: Boolean): Boolean = false
    override suspend fun kwAddSongsToPlaylist(playlistId: String, songIds: List<String>): LxAddToPlaylistResult = LxAddToPlaylistResult.FAILED
    override suspend fun kwRemoveSongsFromPlaylist(playlistId: String, songIds: List<String>): Boolean = false
    override suspend fun kwSetPlaylistCollect(playlistId: String, collect: Boolean): Boolean = false
    override suspend fun kwApplyArtistFollow(artistId: String, follow: Boolean): Boolean = false
    override suspend fun kwApplyAlbumLike(albumId: String, like: Boolean): Boolean = false
    override suspend fun txApplySongLike(songId: String, like: Boolean): Boolean = false
    override suspend fun kwFetchProfile(): Pair<String?, String?>? = null
    override suspend fun kwFetchUserPlaylists(): List<LxPlaylist>? = null
    override suspend fun kwFetchFollowedArtists(): List<LxArtist>? = null
    override suspend fun kwFetchLikedAlbums(): List<LxAlbum>? = null
    override suspend fun kwFetchLikedSongIds(): Set<String>? = null
    override suspend fun kwFetchLikedSongs(): List<LxSong>? = null
    override suspend fun kwFetchUserPlaylistSongs(playlistId: String): List<LxSong>? = null
    override suspend fun kgFetchUserPlaylists(): List<LxPlaylist>? = null
    override suspend fun kgFetchCloudPlaylistSongs(listid: String): List<LxSong>? = null
    override suspend fun kgFetchLikedSongIds(): Set<String>? = null
    override suspend fun kgFetchLikedSongs(): List<LxSong>? = null
    override suspend fun kgFetchProfile(): Pair<String?, String?>? = null
    override suspend fun txFetchProfile(): Pair<String?, String?>? = null
    override suspend fun txFetchLikedSongs(): List<LxSong>? = null
    override suspend fun txFetchLikedAlbums(): List<LxAlbum>? = null
    override suspend fun txFetchUserPlaylists(): List<LxPlaylist>? = null
    override suspend fun txFetchPlaylistSongs(disstid: String): List<LxSong>? = null
    override suspend fun txQrCreate(type: LxTxQrType): Pair<ByteArray, String>? = null
    override suspend fun txQrPoll(type: LxTxQrType, session: String): LxTxQrPoll =
        LxTxQrPoll(LxTxQrPoll.Status.ERROR)
    override suspend fun kwFetchPlaylistSquareSongs(playlistId: String): List<LxSong>? = null
    override suspend fun kwResolvePlayUrl(songmid: String): PlatformResult? = null
    override fun clearReadCaches() {}
}

enum class LxTxQrType { QQ, WECHAT }

data class LxTxQrPoll(
    val status: Status,
    val cookie: String? = null,
) {
    enum class Status { PENDING, SCANNED, EXPIRED, SUCCESS, ERROR }
}

internal object LxAccountExtensionLoader {
    private const val PRIVATE_CLASS_NAME =
        "com.minoppol.music.data.lxmusic.LxPrivateAccountExtension"

    // 账号实现类加载失败时退回全失败实现，账号相关功能整体只读
    fun load(host: LxMusicRepository): LxAccountExtension =
        runCatching {
            val clazz = Class.forName(PRIVATE_CLASS_NAME)
            val ctor = clazz.getDeclaredConstructor(LxMusicRepository::class.java)
            ctor.isAccessible = true
            (ctor.newInstance(host) as LxAccountExtension)
                .also { Timber.i("LxAccountExtension: private implementation loaded") }
        }.getOrElse { e ->
            Timber.i("LxAccountExtension: private impl unavailable, read-only mode (${e.javaClass.simpleName})")
            LxUnavailableAccountExtension
        }
}

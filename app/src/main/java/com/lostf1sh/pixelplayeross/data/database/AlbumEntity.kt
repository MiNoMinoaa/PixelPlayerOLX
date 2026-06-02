package com.lostf1sh.pixelplayeross.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lostf1sh.pixelplayeross.data.model.Album
import com.lostf1sh.pixelplayeross.utils.LocalArtworkUri
import com.lostf1sh.pixelplayeross.utils.normalizeMetadataTextOrEmpty

@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["title"], unique = false),
        Index(value = ["artist_id"], unique = false), // To look up albums by artist
        Index(value = ["artist_name"], unique = false), // New index for lookups by the album's artist name
        Index(value = ["album_artist"], unique = false) // Album artist tag from metadata (TPE2)
    ]
)
data class AlbumEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String, // The album's artist name
    @ColumnInfo(name = "artist_id") val artistId: Long, // ID of the album's main artist (if applicable)
    @ColumnInfo(name = "album_art_uri_string") val albumArtUriString: String?,
    @ColumnInfo(name = "song_count") val songCount: Int,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "album_artist") val albumArtist: String? = null
)

fun AlbumEntity.toAlbum(): Album {
    val effectiveAlbumArtUri = when {
        this.albumArtUriString.isNullOrBlank() -> null
        // Beta 6 stored per-song FileProvider URIs (e.g.
        // content://...provider/cache/song_art_<id>.jpg) as the album's
        // representative art. After the v0.7 artwork-pipeline rewrite those
        // cache files no longer exist, so the raw URI 404s. Recover the song
        // id embedded in the filename and remap to the stable
        // pixelplayer_local_art:// scheme so LocalArtworkCoilFetcher can
        // re-extract embedded art on demand. Songs go through the same remap
        // in LocalArtworkUri.resolveSongArtworkUri; without this, album rows
        // would render placeholders until a metadata-save re-syncs the row.
        LocalArtworkUri.looksLikeVolatileArtworkUri(this.albumArtUriString) ->
            LocalArtworkUri.parseSongIdFromVolatileArtworkUri(this.albumArtUriString)
                ?.let { LocalArtworkUri.buildSongUri(it) }
        else -> this.albumArtUriString
    }

    return Album(
        id = this.id,
        title = this.title.normalizeMetadataTextOrEmpty(),
        artist = this.artistName.normalizeMetadataTextOrEmpty(),
        albumArtist = this.albumArtist?.normalizeMetadataTextOrEmpty()?.takeIf { it.isNotBlank() },
        albumArtUriString = effectiveAlbumArtUri,
        songCount = this.songCount,
        dateAdded = this.dateAdded,
        year = this.year
    )
}

fun List<AlbumEntity>.toAlbums(): List<Album> {
    return this.map { it.toAlbum() }
}

fun Album.toEntity(artistIdForAlbum: Long): AlbumEntity { // We need to pass the artistId since the Album model doesn't hold it directly
    return AlbumEntity(
        id = this.id,
        title = this.title,
        artistName = this.artist,
        artistId = artistIdForAlbum, // Assign the artist ID
        albumArtUriString = this.albumArtUriString,
        songCount = this.songCount,
        dateAdded = this.dateAdded,
        year = this.year,
        albumArtist = this.albumArtist
    )
}

package com.lostf1sh.pixelplayeross.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lostf1sh.pixelplayeross.data.model.Artist
import com.lostf1sh.pixelplayeross.utils.normalizeMetadataTextOrEmpty

@Entity(
    tableName = "artists",
    indices = [Index(value = ["name"], unique = false)] // Index on the name for fast lookups
)
data class ArtistEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    @ColumnInfo(name = "custom_image_uri") val customImageUri: String? = null
)

fun ArtistEntity.toArtist(): Artist {
    return Artist(
        id = this.id,
        name = this.name.normalizeMetadataTextOrEmpty(),
        songCount = this.trackCount, // The Artist model uses songCount, MediaStore uses NUMBER_OF_TRACKS
        imageUrl = this.imageUrl,
        customImageUri = this.customImageUri
    )
}

fun List<ArtistEntity>.toArtists(): List<Artist> {
    return this.map { it.toArtist() }
}

fun Artist.toEntity(): ArtistEntity {
    return ArtistEntity(
        id = this.id,
        name = this.name,
        trackCount = this.songCount,
        imageUrl = this.imageUrl,
        customImageUri = this.customImageUri
    )
}

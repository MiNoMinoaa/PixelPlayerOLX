package com.minoppol.music.presentation.components

import com.minoppol.music.data.model.Album
import com.minoppol.music.data.model.Artist
import com.minoppol.music.data.model.Playlist
import com.minoppol.music.data.model.Song
import com.minoppol.music.data.model.SortOption

internal fun songFastScrollLabel(song: Song?, sortOption: SortOption): String? =
    when (sortOption) {
        SortOption.SongTitleAZ,
        SortOption.SongTitleZA,
        SortOption.LikedSongTitleAZ,
        SortOption.LikedSongTitleZA -> extractFastScrollGlyph(song?.title)

        SortOption.SongArtist,
        SortOption.SongArtistDesc,
        SortOption.LikedSongArtist,
        SortOption.LikedSongArtistDesc -> extractFastScrollGlyph(song?.artist)

        SortOption.SongAlbum,
        SortOption.SongAlbumDesc,
        SortOption.LikedSongAlbum,
        SortOption.LikedSongAlbumDesc -> extractFastScrollGlyph(song?.album)

        else -> null
    }

internal fun albumFastScrollLabel(album: Album?, sortOption: SortOption): String? =
    when (sortOption) {
        SortOption.AlbumTitleAZ,
        SortOption.AlbumTitleZA -> extractFastScrollGlyph(album?.title)

        SortOption.AlbumArtist,
        SortOption.AlbumArtistDesc -> extractFastScrollGlyph(album?.artist)

        else -> null
    }

internal fun artistFastScrollLabel(artist: Artist?, sortOption: SortOption): String? =
    when (sortOption) {
        SortOption.ArtistNameAZ,
        SortOption.ArtistNameZA -> extractFastScrollGlyph(artist?.name)
        else -> null
    }

internal fun playlistFastScrollLabel(playlist: Playlist?, sortOption: SortOption?): String? =
    when (sortOption) {
        SortOption.PlaylistNameAZ,
        SortOption.PlaylistNameZA -> extractFastScrollGlyph(playlist?.name)
        else -> null
    }

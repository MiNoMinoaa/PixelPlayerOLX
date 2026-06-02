package com.lostf1sh.pixelplayeross.data.model

import kotlinx.serialization.Serializable

/**
 * Data model for song lyrics.
 *
 * @param plain List of plain-text lines (unsynced).
 * @param synced List of (milliseconds, line) pairs for synced lyrics.
 * @param areFromRemote Indicates whether the lyrics were fetched from a remote source.
 */
@Serializable
data class Lyrics(
    val plain: List<String>? = null,
    val synced: List<SyncedLine>? = null,
    val areFromRemote: Boolean = false
)

@Serializable
data class SyncedLine(
    val time: Int,
    val line: String,
    val words: List<SyncedWord>? = null, // Null if not a word-by-word synced lyric
    val translation: String? = null, // Translation text paired by identical timestamp
    val romanization: String? = null // Romanization text paired by identical timestamp
)

@Serializable
data class SyncedWord(
    val time: Int,
    val word: String,
    val startsNewWord: Boolean = true
)

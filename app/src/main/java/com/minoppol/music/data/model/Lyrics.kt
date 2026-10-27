package com.minoppol.music.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Lyrics(
    val plain: List<String>? = null,
    val synced: List<SyncedLine>? = null,
    val areFromRemote: Boolean = false,
    val remoteSource: String? = null
)

@Serializable
data class SyncedLine(
    val time: Int,
    val line: String,
    val words: List<SyncedWord>? = null,
    val translation: String? = null,
    val romanization: String? = null,
    /** Singer number from enhanced-LRC `vN:` prefix or TTML `ttm:agent`; null when unmarked. */
    val voice: Int? = null,
    /** Backing vocals sung under this line. */
    val background: BackgroundVocals? = null
)

@Serializable
data class BackgroundVocals(
    val text: String,
    val words: List<SyncedWord>? = null
)

@Serializable
data class SyncedWord(
    val time: Int,
    val word: String,
    val startsNewWord: Boolean = true,
    /** End timestamp in ms, when the source provides it. */
    val endTime: Int? = null
)

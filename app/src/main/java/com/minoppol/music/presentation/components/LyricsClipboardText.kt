package com.minoppol.music.presentation.components

import com.minoppol.music.data.model.Lyrics
import com.minoppol.music.data.model.SyncedLine
import com.minoppol.music.data.model.SyncedWord

internal object LyricsClipboardText {

    fun syncedLine(line: SyncedLine, includeBackground: Boolean = true): String {
        val background = line.background
            ?.takeIf { includeBackground }
            ?.let { shownRows(it.words, it.text) }
            .orEmpty()
        if (line.line.isBlank() && background.isEmpty()) return ""
        return (shownRows(line.words, line.line) + background).joinToString("\n")
    }

    fun plainLine(entry: String): String =
        sanitizeLyricLineText(entry.substringBefore('\n')).trim()

    fun fullLyrics(lyrics: Lyrics?, includeBackground: Boolean = true): String = buildString {
        var pendingStanzaBreak = false
        for (row in rows(lyrics, includeBackground)) {
            if (row.isEmpty()) {
                pendingStanzaBreak = isNotEmpty()
                continue
            }
            if (isNotEmpty()) append(if (pendingStanzaBreak) "\n\n" else "\n")
            append(row)
            pendingStanzaBreak = false
        }
    }

    fun hasCopyableText(lyrics: Lyrics?, includeBackground: Boolean = true): Boolean =
        rows(lyrics, includeBackground).any { it.isNotEmpty() }

    private fun rows(lyrics: Lyrics?, includeBackground: Boolean): Sequence<String> {
        val synced = lyrics?.synced
        return if (!synced.isNullOrEmpty()) {
            synced.asSequence().map { syncedLine(it, includeBackground) }
        } else {
            lyrics?.plain.orEmpty().asSequence().map(::plainLine)
        }
    }

    private fun shownRows(words: List<SyncedWord>?, text: String): List<String> {
        val clusters = buildLyricClusters(words)
        if (!clusters.isNullOrEmpty()) return listOf(clusters.joinToString(" ") { it.text })
        return text.lines()
            .map { sanitizeLyricLineText(it).trim() }
            .filter { it.isNotEmpty() }
    }
}

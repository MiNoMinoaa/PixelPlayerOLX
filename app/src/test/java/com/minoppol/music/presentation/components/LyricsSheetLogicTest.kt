package com.minoppol.music.presentation.components

import androidx.compose.ui.unit.dp
import com.minoppol.music.data.model.SyncedLine
import com.minoppol.music.data.model.SyncedWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSheetLogicTest {

    @Test
    fun sanitizeSyncedWords_removesLeadingTags_preventsOverlap() {
        val words = listOf(
            SyncedWord(time = 0, word = "v1:"),
            SyncedWord(time = 120, word = "Hello"),
            SyncedWord(time = 240, word = "world")
        )

        val sanitized = sanitizeSyncedWords(words)

        assertEquals(listOf("Hello", "world"), sanitized.map { it.word })
        assertEquals(listOf(120, 240), sanitized.map { it.time })
        assertTrue(sanitized.all { it.startsNewWord })
    }

    @Test
    fun highlightSnapOffsetPx_alignsLineWithHighlightZone() {
        val viewportHeight = 960
        val itemSize = 120
        val highlightOffsetPx = 80f

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx)

        val expectedCenter = viewportHeight / 2f - highlightOffsetPx
        assertEquals(expectedCenter - itemSize / 2f, offset.toFloat(), 0.5f)
    }

    @Test
    fun highlightSnapOffsetPx_clampsWithinViewportForEndOfList() {
        val viewportHeight = 420
        val itemSize = 320
        val highlightOffsetPx = -160f

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx)
        val center = offset + itemSize / 2f

        assertTrue(center <= viewportHeight.toFloat())
        assertTrue(center >= itemSize / 2f)
    }

    @Test
    fun highlightSnapOffsetPx_handlesOversizedItems() {
        val viewportHeight = 200
        val itemSize = 260

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx = 60f)

        assertEquals(0, offset)
    }

    @Test
    fun calculateHighlightMetrics_reservesBottomSpace() {
        val metrics = calculateHighlightMetrics(
            containerHeight = 480.dp,
            highlightZoneFraction = 0.22f,
            highlightOffset = 48.dp
        )

        assertTrue(metrics.bottomPadding > 0.dp)
        assertTrue(metrics.topPadding >= 0.dp)
        assertTrue(metrics.zoneHeight > 0.dp)
    }

    @Test
    fun sanitizeLyricLineText_stripsLrcTimestampTags() {
        val raw = "[00:26.42][01:12.34] Three in the morning, I ain't slept all weekend"

        val sanitized = sanitizeLyricLineText(raw)

        assertEquals("Three in the morning, I ain't slept all weekend", sanitized)
    }

    @Test
    fun normalizeWordEndTime_guaranteesForwardRangeWhenTimestampsMatch() {
        val endTime = normalizeWordEndTime(
            currentWordTimeMs = 2_000L,
            nextWordTimeMs = 2_000L,
            lineEndTimeMs = 2_800L
        )

        assertEquals(2_001L, endTime)
    }

    @Test
    fun normalizeWordEndTime_clampsToLineEnd() {
        val endTime = normalizeWordEndTime(
            currentWordTimeMs = 5_000L,
            nextWordTimeMs = 8_000L,
            lineEndTimeMs = 5_400L
        )

        assertEquals(5_400L, endTime)
    }

    @Test
    fun resolveLineEndTimeMs_extendsPastNextLineWhenLastWordStartsLater() {
        val line = SyncedLine(
            time = 1_000,
            line = "abc",
            words = listOf(
                SyncedWord(1_000, "a"),
                SyncedWord(1_600, "b"),
                SyncedWord(2_000, "c")
            )
        )

        val lineEnd = resolveLineEndTimeMs(line, nextLineStartMs = 2_000)

        assertEquals(2_001L, lineEnd)
    }

    @Test
    fun sanitizeSyncedWords_promotesFirstVisibleWordAfterLeadingMarker() {
        val words = listOf(
            SyncedWord(time = 1000, word = "v1:"),
            SyncedWord(time = 1200, word = "fall", startsNewWord = false)
        )

        val sanitized = sanitizeSyncedWords(words)

        assertEquals(1, sanitized.size)
        assertEquals("fall", sanitized[0].word)
        assertTrue(sanitized[0].startsNewWord)
    }

    @Test
    fun clusterSyncedWords_keepsSyllablesInsideSameWord() {
        val clusters = clusterSyncedWords(
            listOf(
                SyncedWord(time = 1000, word = "to", startsNewWord = true),
                SyncedWord(time = 1100, word = "geth", startsNewWord = false),
                SyncedWord(time = 1200, word = "er", startsNewWord = false),
                SyncedWord(time = 1500, word = "now", startsNewWord = true)
            )
        )

        assertEquals(2, clusters.size)
        assertEquals(listOf("to", "geth", "er"), clusters[0].words.map { it.word })
        assertEquals(listOf("now"), clusters[1].words.map { it.word })
        assertEquals(0, clusters[0].startIndex)
        assertEquals(3, clusters[1].startIndex)
    }

    @Test
    fun resolveHighlightedWordIndex_picksLastVisibleWordAtLineEnd() {
        val words = listOf(
            SyncedWord(time = 10_000, word = "fall"),
            SyncedWord(time = 10_250, word = "in"),
            SyncedWord(time = 10_500, word = "love")
        )

        val idx = resolveHighlightedWordIndex(
            words = words,
            positionMs = 10_900,
            lineStartTimeMs = 10_000,
            lineEndTimeMs = 11_000
        )

        assertEquals(2, idx)
    }

    @Test
    fun resolveClusterHighlightFrame_tracksFastConsecutiveWords() {
        val words = listOf(
            SyncedWord(time = 1_000, word = "one"),
            SyncedWord(time = 1_050, word = "two"),
            SyncedWord(time = 1_100, word = "three")
        )

        val frame = resolveClusterHighlightFrame(
            words = words,
            positionMs = 1_075,
            nextClusterStartMs = null,
            lineEndMs = 1_200
        )

        // "two" is the syllable being sung; 25ms into its 50ms span.
        assertEquals(1, frame.syllableIndex)
        assertEquals(0.5f, frame.fraction, 0.001f)
    }

    @Test
    fun resolveClusterHighlightFrame_usesLastDuplicateTimestampAsActiveWord() {
        val words = listOf(
            SyncedWord(time = 2_000, word = "first"),
            SyncedWord(time = 2_000, word = "second"),
            SyncedWord(time = 2_100, word = "third")
        )

        val frame = resolveClusterHighlightFrame(
            words = words,
            positionMs = 2_000,
            nextClusterStartMs = null,
            lineEndMs = 2_200
        )

        assertEquals(1, frame.syllableIndex)
        assertEquals(0f, frame.fraction, 0f)
    }

    @Test
    fun resolveClusterHighlightFrame_zeroLengthLineSingsInstantly() {
        val frame = resolveClusterHighlightFrame(
            words = listOf(SyncedWord(time = 2_000, word = "instant")),
            positionMs = 2_000,
            nextClusterStartMs = null,
            lineEndMs = 2_000
        )

        // normalizeWordEndTime guarantees at least +1ms, so the word is active at 0 progress.
        assertEquals(0, frame.syllableIndex)
        assertEquals(0f, frame.fraction, 0f)
    }

    @Test
    fun resolveClusterHighlightFrame_accumulatesCompletedWords() {
        val words = listOf(
            SyncedWord(time = 3_000, word = "we"),
            SyncedWord(time = 3_200, word = "keep"),
            SyncedWord(time = 3_400, word = "these"),
            SyncedWord(time = 3_600, word = "lit")
        )

        val frame = resolveClusterHighlightFrame(
            words = words,
            positionMs = 3_500,
            nextClusterStartMs = null,
            lineEndMs = 3_800
        )

        // "these" (index 2) is active; earlier words completed by index alone.
        assertEquals(2, frame.syllableIndex)
        assertEquals(0.5f, frame.fraction, 0.001f)
    }

    @Test
    fun resolveWordEmphasisScale_reducedMotionKeepsStableSize() {
        val scale = resolveWordEmphasisScale(
            positionMs = 4_050,
            wordStartMs = 4_000,
            wordEndMs = 4_500,
            motionEnabled = false
        )

        assertEquals(1f, scale, 0f)
    }

    @Test
    fun resolveWordEmphasisScale_longWordSettlesWithinBoundedTime() {
        val scale = resolveWordEmphasisScale(
            positionMs = 4_300,
            wordStartMs = 4_000,
            wordEndMs = 6_000,
            motionEnabled = true
        )

        // 300ms > MAX_WORD_EMPHASIS_DURATION_MS (240ms) → settled back to 1x.
        assertEquals(1f, scale, 0f)
    }

    @Test
    fun resolveSeekPositionMs_subtractsPositiveLyricsOffset() {
        val seekPosition = resolveSeekPositionMs(
            lineTimeMs = 12_000L,
            lyricsSyncOffsetMs = 750
        )

        assertEquals(11_250L, seekPosition)
    }

    @Test
    fun resolveSeekPositionMs_addsNegativeLyricsOffset() {
        val seekPosition = resolveSeekPositionMs(
            lineTimeMs = 12_000L,
            lyricsSyncOffsetMs = -750
        )

        assertEquals(12_750L, seekPosition)
    }

    @Test
    fun resolveSeekPositionMs_clampsToZeroWhenOffsetWouldGoNegative() {
        val seekPosition = resolveSeekPositionMs(
            lineTimeMs = 300L,
            lyricsSyncOffsetMs = 750
        )

        assertEquals(0L, seekPosition)
    }
}

package com.minoppol.music.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.util.lerp
import com.minoppol.music.data.model.SyncedLine
import com.minoppol.music.data.model.SyncedWord
import com.minoppol.music.data.preferences.LyricsWordHighlightStyle

@Immutable
internal class LyricClusterText(
    val words: List<SyncedWord>,
    val text: String,
    val syllableStarts: IntArray
) {
    val startTimeMs: Long get() = words.first().time.toLong()

    fun syllableEnd(index: Int): Int = syllableStarts.getOrNull(index + 1) ?: text.length
}

internal fun buildClusterText(words: List<SyncedWord>, appendTrailingSpace: Boolean = false): LyricClusterText {
    val starts = IntArray(words.size)
    val text = buildString {
        words.forEachIndexed { index, word ->
            starts[index] = length
            append(word.word)
        }
        if (appendTrailingSpace) append(' ')
    }
    return LyricClusterText(words = words, text = text, syllableStarts = starts)
}

private val LeadingTagRegex = Regex("^v\\d+:\\s*", RegexOption.IGNORE_CASE)

internal fun sanitizeSyncedWords(words: List<SyncedWord>): List<SyncedWord> =
    buildList {
        words.forEachIndexed { index, word ->
            val sanitized = if (index == 0) LeadingTagRegex.replace(word.word, "") else word.word
            val normalized = sanitized.trim()
            if (normalized.isEmpty()) return@forEachIndexed
            add(word.copy(word = normalized, startsNewWord = if (isEmpty()) true else word.startsNewWord))
        }
    }

internal data class SyncedWordCluster(val startIndex: Int, val words: List<SyncedWord>)

internal fun clusterSyncedWords(words: List<SyncedWord>): List<SyncedWordCluster> {
    if (words.isEmpty()) return emptyList()
    val clusters = mutableListOf<SyncedWordCluster>()
    var currentWords = mutableListOf<SyncedWord>()
    var currentStartIndex = 0
    words.forEachIndexed { index, word ->
        if (word.startsNewWord && currentWords.isNotEmpty()) {
            clusters += SyncedWordCluster(startIndex = currentStartIndex, words = currentWords.toList())
            currentWords = mutableListOf()
            currentStartIndex = index
        } else if (currentWords.isEmpty()) {
            currentStartIndex = index
        }
        currentWords += word
    }
    if (currentWords.isNotEmpty()) clusters += SyncedWordCluster(startIndex = currentStartIndex, words = currentWords.toList())
    return clusters
}

internal fun buildLyricClusters(words: List<SyncedWord>?): List<LyricClusterText>? =
    words
        ?.let(::sanitizeSyncedWords)
        ?.takeIf { it.isNotEmpty() }
        ?.let(::clusterSyncedWords)
        ?.let { clusters ->
            clusters.mapIndexed { index, cluster ->
                buildClusterText(cluster.words, appendTrailingSpace = index < clusters.lastIndex)
            }
        }

internal enum class LyricWordPhase {
    Future,
    Active,
    Completed
}

internal data class ClusterHighlightFrame(
    val syllableIndex: Int,
    val fraction: Float
) {
    companion object {
        val NotStarted = ClusterHighlightFrame(syllableIndex = -1, fraction = 0f)
    }
}

internal const val MAX_TRAILING_SYLLABLE_SWEEP_MS = 1_200L
private const val MAX_WORD_EMPHASIS_DURATION_MS = 240L
private const val ACTIVE_WORD_SCALE_DELTA = 0.08f

private const val SWEEP_FEATHER_EM = 0.5f

internal const val GROUP_VOICE = 1_000

internal fun resolveActiveClusterIndex(
    clusterStartTimes: List<Long>,
    positionMs: Long,
    rangeStartMs: Long,
    rangeEndMs: Long
): Int {
    if (positionMs < rangeStartMs || positionMs >= rangeEndMs) return -1
    return clusterStartTimes.indexOfLast { it <= positionMs }
}

internal fun resolveSyllableEndMs(
    words: List<SyncedWord>,
    index: Int,
    nextClusterStartMs: Long?,
    lineEndMs: Long
): Long {
    val word = words[index]
    val startMs = word.time.toLong()
    val endMs = word.endTime?.toLong()?.takeIf { it > startMs }
        ?: words.getOrNull(index + 1)?.time?.toLong()
        ?: nextClusterStartMs
        ?: (startMs + MAX_TRAILING_SYLLABLE_SWEEP_MS)
    return normalizeWordEndTime(
        currentWordTimeMs = startMs,
        nextWordTimeMs = endMs,
        lineEndTimeMs = lineEndMs
    )
}

internal fun resolveClusterHighlightFrame(
    words: List<SyncedWord>,
    positionMs: Long,
    nextClusterStartMs: Long?,
    lineEndMs: Long
): ClusterHighlightFrame {
    val index = words.indexOfLast { it.time.toLong() <= positionMs }
    if (index < 0) return ClusterHighlightFrame.NotStarted
    val startMs = words[index].time.toLong()
    val endMs = resolveSyllableEndMs(words, index, nextClusterStartMs, lineEndMs)
    val fraction = ((positionMs - startMs).toFloat() / (endMs - startMs).toFloat()).coerceIn(0f, 1f)
    return ClusterHighlightFrame(syllableIndex = index, fraction = fraction)
}

internal fun resolveWordEmphasisScale(
    positionMs: Long,
    wordStartMs: Long,
    wordEndMs: Long,
    motionEnabled: Boolean
): Float {
    if (!motionEnabled || positionMs < wordStartMs) return 1f
    val emphasisEnd = minOf(wordEndMs, wordStartMs + MAX_WORD_EMPHASIS_DURATION_MS)
        .coerceAtLeast(wordStartMs + 1L)
    val progress = ((positionMs - wordStartMs).toFloat() / (emphasisEnd - wordStartMs).toFloat())
        .coerceIn(0f, 1f)
    return 1f + ACTIVE_WORD_SCALE_DELTA * (1f - progress)
}

internal fun resolveLineAlignment(
    voice: Int?,
    userAlignment: String,
    duetLayout: Boolean
): String = when {
    !duetLayout || voice == null || voice <= 0 -> userAlignment
    voice >= GROUP_VOICE -> "center"
    voice % 2 == 1 -> "left"
    else -> "right"
}

internal fun hasMultipleVoices(lines: List<SyncedLine>): Boolean {
    val voices = HashSet<Int>()
    for (line in lines) {
        line.voice?.let(voices::add)
        if (voices.size >= 2) return true
    }
    return false
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SyncedWordFlow(
    clusters: List<LyricClusterText>,
    positionProvider: () -> Long,
    rangeStartMs: Long,
    rangeEndMs: Long,
    style: TextStyle,
    fontWeight: FontWeight,
    reserveBoldWidth: Boolean,
    highlightedColor: Color,
    unhighlightedColor: Color,
    highlightStyle: LyricsWordHighlightStyle,
    emphasisEnabled: Boolean,
    lyricsAlignment: String,
    modifier: Modifier = Modifier
) {
    val clusterStartTimes = remember(clusters) { clusters.map { it.startTimeMs } }
    val textAlign = when (lyricsAlignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> TextAlign.Left
    }
    val activeClusterIndex by remember(clusterStartTimes, rangeStartMs, rangeEndMs, positionProvider) {
        derivedStateOf {
            resolveActiveClusterIndex(clusterStartTimes, positionProvider(), rangeStartMs, rangeEndMs)
        }
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = when (lyricsAlignment) {
            "center" -> Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)
            "right" -> Arrangement.spacedBy(3.dp, Alignment.End)
            else -> Arrangement.spacedBy(3.dp, Alignment.Start)
        },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        clusters.forEachIndexed { index, cluster ->
            val phase = when {
                activeClusterIndex < 0 || index > activeClusterIndex -> LyricWordPhase.Future
                index < activeClusterIndex -> LyricWordPhase.Completed
                else -> LyricWordPhase.Active
            }
            key(index) {
                LyricWordCluster(
                    cluster = cluster,
                    phase = phase,
                    positionProvider = positionProvider,
                    nextClusterStartMs = clusterStartTimes.getOrNull(index + 1),
                    lineEndMs = rangeEndMs,
                    style = style,
                    fontWeight = fontWeight,
                    reserveBoldWidth = reserveBoldWidth,
                    highlightedColor = highlightedColor,
                    unhighlightedColor = unhighlightedColor,
                    highlightStyle = highlightStyle,
                    emphasisEnabled = emphasisEnabled,
                    textAlign = textAlign
                )
            }
        }
    }
}

private class TextLayoutHolder {
    var layout: TextLayoutResult? = null
}

@Composable
private fun LyricWordCluster(
    cluster: LyricClusterText,
    phase: LyricWordPhase,
    positionProvider: () -> Long,
    nextClusterStartMs: Long?,
    lineEndMs: Long,
    style: TextStyle,
    fontWeight: FontWeight,
    reserveBoldWidth: Boolean,
    highlightedColor: Color,
    unhighlightedColor: Color,
    highlightStyle: LyricsWordHighlightStyle,
    emphasisEnabled: Boolean,
    textAlign: TextAlign
) {
    val isActive = phase == LyricWordPhase.Active
    val density = LocalDensity.current
    val featherPx = remember(style.fontSize, density) {
        with(density) { if (style.fontSize.isSpecified) style.fontSize.toPx() * SWEEP_FEATHER_EM else 8.dp.toPx() }
    }
    val layoutHolder = remember { TextLayoutHolder() }

    val emphasisModifier = if (isActive && emphasisEnabled) {
        Modifier.graphicsLayer {
            val scale = resolveWordEmphasisScale(
                positionMs = positionProvider(),
                wordStartMs = cluster.startTimeMs,
                wordEndMs = nextClusterStartMs ?: lineEndMs,
                motionEnabled = true
            )
            scaleX = scale
            scaleY = scale
        }
    } else {
        Modifier
    }
    val sweepModifier = if (isActive) {
        Modifier.drawWithContent {
            drawContent()
            val layout = layoutHolder.layout ?: return@drawWithContent
            val frame = resolveClusterHighlightFrame(
                words = cluster.words,
                positionMs = positionProvider(),
                nextClusterStartMs = nextClusterStartMs,
                lineEndMs = lineEndMs
            )
            drawSungPortion(layout, cluster, frame, highlightStyle, highlightedColor, featherPx)
        }
    } else {
        Modifier
    }

    val clusterAlignment = when (textAlign) {
        TextAlign.Left, TextAlign.Start -> Alignment.CenterStart
        TextAlign.Right, TextAlign.End -> Alignment.CenterEnd
        else -> Alignment.Center
    }
    Box(modifier = emphasisModifier, contentAlignment = clusterAlignment) {
        if (reserveBoldWidth) {
            Text(
                text = cluster.text,
                style = style,
                color = Color.Transparent,
                fontWeight = FontWeight.Bold,
                textAlign = textAlign,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
        Text(
            text = cluster.text,
            style = style,
            color = if (phase == LyricWordPhase.Completed) highlightedColor else unhighlightedColor,
            fontWeight = fontWeight,
            textAlign = textAlign,
            onTextLayout = { layoutHolder.layout = it },
            modifier = sweepModifier
        )
    }
}

private fun DrawScope.drawSungPortion(
    layout: TextLayoutResult,
    cluster: LyricClusterText,
    frame: ClusterHighlightFrame,
    style: LyricsWordHighlightStyle,
    color: Color,
    featherPx: Float
) {
    val syllable = frame.syllableIndex
    if (syllable < 0 || layout.layoutInput.text.text != cluster.text) return
    val syllableStart = cluster.syllableStarts[syllable]
    val syllableEnd = cluster.syllableEnd(syllable)
    if (syllableEnd <= syllableStart) return

    val span = resolveSyllableSpan(layout, syllableStart, syllableEnd)
    if (style != LyricsWordHighlightStyle.WIPE) {
        val syllableAlpha = if (style == LyricsWordHighlightStyle.FADE) frame.fraction else 1f
        forEachLitPart(span, syllableAlpha) { line, left, right, alpha ->
            clipLine(layout, line) {
                clipBetween(left, right) { drawText(layout, color = color, alpha = alpha) }
            }
        }
        return
    }

    val edge = resolveSweepEdge(layout, span, syllableStart, syllableEnd, frame.fraction, featherPx)
    for (line in 0 until edge.line) {
        clipLine(layout, line) { drawText(layout, color = color) }
    }
    val litTowardsLeft = !span.rtl
    val half = featherPx / 2f
    val brush = Brush.horizontalGradient(
        colors = listOf(color, color.copy(alpha = 0f)),
        startX = if (litTowardsLeft) edge.x - half else edge.x + half,
        endX = if (litTowardsLeft) edge.x + half else edge.x - half
    )
    clipLine(layout, edge.line) {
        drawText(layout, brush = brush, alpha = 1f)
    }
}

internal class SyllableSpan(
    val startLine: Int,
    val endLine: Int,
    val fromX: Float,
    val toX: Float,
    val rtl: Boolean
)

private fun resolveSyllableSpan(layout: TextLayoutResult, syllableStart: Int, syllableEnd: Int): SyllableSpan {
    val rtl = layout.getBidiRunDirection(syllableStart) == ResolvedTextDirection.Rtl
    val first = layout.getBoundingBox(syllableStart)
    val last = layout.getBoundingBox(syllableEnd - 1)
    return SyllableSpan(
        startLine = layout.getLineForOffset(syllableStart),
        endLine = layout.getLineForOffset(syllableEnd - 1),
        fromX = if (rtl) first.right else first.left,
        toX = if (rtl) last.left else last.right,
        rtl = rtl
    )
}

internal inline fun forEachLitPart(
    span: SyllableSpan,
    syllableAlpha: Float,
    part: (line: Int, left: Float, right: Float, alpha: Float) -> Unit
) {
    val open = Float.POSITIVE_INFINITY
    if (syllableAlpha >= 1f) {
        for (line in 0 until span.endLine) part(line, -open, open, 1f)
        if (span.rtl) part(span.endLine, span.toX, open, 1f) else part(span.endLine, -open, span.toX, 1f)
        return
    }

    for (line in 0 until span.startLine) part(line, -open, open, 1f)
    if (span.rtl) part(span.startLine, span.fromX, open, 1f) else part(span.startLine, -open, span.fromX, 1f)
    if (span.startLine == span.endLine) {
        part(span.startLine, minOf(span.fromX, span.toX), maxOf(span.fromX, span.toX), syllableAlpha)
        return
    }
    if (span.rtl) {
        part(span.startLine, -open, span.fromX, syllableAlpha)
    } else {
        part(span.startLine, span.fromX, open, syllableAlpha)
    }
    for (line in span.startLine + 1 until span.endLine) part(line, -open, open, syllableAlpha)
    if (span.rtl) {
        part(span.endLine, span.toX, open, syllableAlpha)
    } else {
        part(span.endLine, -open, span.toX, syllableAlpha)
    }
}

private class SweepEdge(val line: Int, val x: Float)

private fun resolveSweepEdge(
    layout: TextLayoutResult,
    span: SyllableSpan,
    syllableStart: Int,
    syllableEnd: Int,
    fraction: Float,
    featherPx: Float
): SweepEdge {
    if (span.startLine != span.endLine) {
        val position = syllableStart + fraction * (syllableEnd - syllableStart)
        val charIndex = position.toInt().coerceIn(syllableStart, syllableEnd - 1)
        val box = layout.getBoundingBox(charIndex)
        val charFraction = position - charIndex
        val x = if (span.rtl) lerp(box.right, box.left, charFraction) else lerp(box.left, box.right, charFraction)
        return SweepEdge(layout.getLineForOffset(charIndex), x)
    }

    val outward = if (span.rtl) -featherPx / 2f else featherPx / 2f
    val startsLine = syllableStart == 0 || layout.getLineForOffset(syllableStart - 1) != span.startLine
    val endsLine = syllableEnd >= layout.layoutInput.text.length ||
        layout.getLineForOffset(syllableEnd) != span.startLine
    val sweepFromX = if (startsLine) span.fromX - outward else span.fromX
    val sweepToX = if (endsLine) span.toX + outward else span.toX
    return SweepEdge(span.startLine, lerp(sweepFromX, sweepToX, fraction))
}

private inline fun DrawScope.clipLine(layout: TextLayoutResult, line: Int, block: DrawScope.() -> Unit) {
    val overflow = size.height + size.width
    clipRect(
        left = -overflow,
        top = if (line == 0) -overflow else layout.getLineTop(line),
        right = size.width + overflow,
        bottom = if (line == layout.lineCount - 1) size.height + overflow else layout.getLineBottom(line),
        block = block
    )
}

private inline fun DrawScope.clipBetween(left: Float, right: Float, block: DrawScope.() -> Unit) {
    val overflow = size.height + size.width
    clipRect(
        left = left.coerceAtLeast(-overflow),
        top = -overflow,
        right = right.coerceAtMost(size.width + overflow),
        bottom = size.height + overflow,
        block = block
    )
}

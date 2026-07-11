package com.lostf1sh.pixelplayeross.presentation.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.lostf1sh.pixelplayeross.R

// The play triangle is modeled as two quads so that each can morph into one of the two
// pause bars via androidx.graphics.shapes' Morph. Geometry matches the Material icon
// paths, normalized from their 24x24 viewport: play_arrow is the triangle (8,5) (8,19)
// (19,12); pause is the bars 6..10 and 14..18 spanning y 5..19.
//
// In the play state the halves overlap around the split line (left ends at 0.58, right
// starts at 0.54) with their seam edges collinear with the triangle's edges, and the seam
// vertices use zero corner rounding — so the union of the two fills reads as one clean
// triangle with rounding only on its real corners. The halves separate mid-morph.
private val ICON_CORNER = CornerRounding(radius = 0.07f)
private val SEAM_CORNER = CornerRounding.Unrounded

private fun quad(vertices: FloatArray, roundings: List<CornerRounding>) =
    RoundedPolygon(vertices, perVertexRounding = roundings)

private val LEFT_MORPH = Morph(
    // Left half of the triangle (vertices: TL, seam-top, seam-bottom, BL)...
    quad(
        floatArrayOf(0.333f, 0.208f, 0.58f, 0.365f, 0.58f, 0.635f, 0.333f, 0.792f),
        listOf(ICON_CORNER, SEAM_CORNER, SEAM_CORNER, ICON_CORNER)
    ),
    // ...into the left pause bar.
    quad(
        floatArrayOf(0.25f, 0.208f, 0.417f, 0.208f, 0.417f, 0.792f, 0.25f, 0.792f),
        listOf(ICON_CORNER, ICON_CORNER, ICON_CORNER, ICON_CORNER)
    )
)

private val RIGHT_MORPH = Morph(
    // Right half of the triangle (the apex is split into two near-coincident vertices so
    // both quads have four corners; the rounding blends them into a single rounded tip)...
    quad(
        floatArrayOf(0.54f, 0.34f, 0.792f, 0.485f, 0.792f, 0.515f, 0.54f, 0.66f),
        listOf(SEAM_CORNER, ICON_CORNER, ICON_CORNER, SEAM_CORNER)
    ),
    // ...into the right pause bar.
    quad(
        floatArrayOf(0.583f, 0.208f, 0.75f, 0.208f, 0.75f, 0.792f, 0.583f, 0.792f),
        listOf(ICON_CORNER, ICON_CORNER, ICON_CORNER, ICON_CORNER)
    )
)

/**
 * A play/pause glyph that morphs between its two states by interpolating shape geometry
 * (YouTube-style) instead of crossfading two icons.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playPauseMorph"
    )
    val leftAndroidPath = remember { android.graphics.Path() }
    val rightAndroidPath = remember { android.graphics.Path() }
    val leftPath = remember { leftAndroidPath.asComposePath() }
    val rightPath = remember { rightAndroidPath.asComposePath() }
    val glyphPath = remember { androidx.compose.ui.graphics.Path() }
    val contentDesc = if (isPlaying) {
        stringResource(R.string.cd_pause)
    } else {
        stringResource(R.string.cd_play)
    }

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = contentDesc }
    ) {
        // progress is read here, in the draw phase, so spring frames only invalidate
        // drawing and never recompose the (hot) playback-controls composition.
        val fraction = progress.coerceIn(0f, 1f)
        LEFT_MORPH.toPath(fraction, leftAndroidPath)
        RIGHT_MORPH.toPath(fraction, rightAndroidPath)
        // Both halves go into one path and one draw call: with non-zero winding the
        // overlap unions away, and there is no anti-aliased seam between two fills.
        glyphPath.reset()
        glyphPath.addPath(leftPath)
        glyphPath.addPath(rightPath)
        scale(this.size.minDimension, pivot = Offset.Zero) {
            drawPath(glyphPath, tint)
        }
    }
}

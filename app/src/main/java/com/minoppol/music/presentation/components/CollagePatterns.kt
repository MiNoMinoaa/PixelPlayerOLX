package com.minoppol.music.presentation.components

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.minoppol.music.data.preferences.CollagePattern
import com.minoppol.music.utils.shapes.RoundedStarShape

@Stable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun buildCollageConfigs(
    pattern: CollagePattern,
    min: Dp,
    boxMaxHeight: Dp
): List<Config> = when (pattern) {
    CollagePattern.COSMIC_SWIRL -> cosmicSwirlConfigs(min, boxMaxHeight)
    CollagePattern.HONEYCOMB_GROOVE -> honeycombGrooveConfigs(min, boxMaxHeight)
    CollagePattern.VINYL_STACK -> vinylStackConfigs(min, boxMaxHeight)
    CollagePattern.PIXEL_MOSAIC -> pixelMosaicConfigs(min, boxMaxHeight)
    CollagePattern.STARDUST_SCATTER -> stardustScatterConfigs(min, boxMaxHeight)
    CollagePattern.SOFT_ORBIT -> softOrbitConfigs(min, boxMaxHeight)
    CollagePattern.ARROW_FANFARE -> arrowFanfareConfigs(min, boxMaxHeight)
    CollagePattern.SUNNY_BLOOM -> sunnyBloomConfigs(min, boxMaxHeight)
    CollagePattern.PIXEL_COOKIE -> pixelCookieConfigs(min, boxMaxHeight)
    CollagePattern.HEART_GARDEN -> heartGardenConfigs(min, boxMaxHeight)
}

/**
 * Fits a [RoundedPolygon] into the outline size with uniform scaling and centres it, so wide tiles
 * keep the polygon's proportions instead of stretching it. The polygon path and its bounds are
 * computed once; each outline transforms a copy.
 */
private class MaterialPolygonShape(polygon: RoundedPolygon) : Shape {
    private val basePath: android.graphics.Path = polygon.toPath()
    private val bounds = RectF().also { basePath.computeBounds(it, true) }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val androidPath = android.graphics.Path(basePath)
        val sourceWidth = bounds.width()
        val sourceHeight = bounds.height()
        if (sourceWidth > 0f && sourceHeight > 0f) {
            val matrix = Matrix()
            matrix.postTranslate(-bounds.left, -bounds.top)
            val scale = minOf(size.width / sourceWidth, size.height / sourceHeight)
            matrix.postScale(scale, scale)
            matrix.postTranslate(
                (size.width - sourceWidth * scale) / 2f,
                (size.height - sourceHeight * scale) / 2f,
            )
            androidPath.transform(matrix)
        }

        return Outline.Generic(androidPath.asComposePath())
    }
}

private fun RoundedPolygon.asShape(): Shape = MaterialPolygonShape(this)

/** Original pattern — preserved exactly as-is. */
private fun cosmicSwirlConfigs(min: Dp, boxMaxHeight: Dp): List<Config> = listOf(
    Config(size = min * 0.8f, width = min * 0.48f, height = min * 0.8f, align = Alignment.Center, rot = 45f, shape = RoundedCornerShape(percent = 50), offsetX = 0.dp, offsetY = 0.dp),
    Config(size = min * 0.4f, width = min * 0.24f, height = min * 0.24f, align = Alignment.TopStart, rot = 0f, shape = CircleShape, offsetX = (300.dp * 0.05f), offsetY = (boxMaxHeight * 0.05f)),
    Config(size = min * 0.4f, width = min * 0.24f, height = min * 0.24f, align = Alignment.BottomEnd, rot = 0f, shape = CircleShape, offsetX = -(300.dp * 0.05f), offsetY = -(boxMaxHeight * 0.05f)),
    Config(size = min * 0.6f, width = min * 0.35f, height = min * 0.35f, align = Alignment.TopStart, rot = -20f, shape = RoundedCornerShape(20.dp), offsetX = (300.dp * 0.1f), offsetY = (boxMaxHeight * 0.1f)),
    Config(size = min * 0.9f, width = min * 0.9f, height = min * 0.9f, align = Alignment.BottomEnd, rot = 0f, shape = RoundedStarShape(sides = 6, curve = 0.09, rotation = 45f), offsetX = 42.dp, offsetY = 0.dp)
)

/** Hexagon-themed organic layout with rounded stars and mixed shapes. */
private fun honeycombGrooveConfigs(min: Dp, boxMaxHeight: Dp): List<Config> = listOf(
    Config(size = min * 0.7f, width = min * 0.7f, height = min * 0.7f, align = Alignment.Center, rot = 0f, shape = RoundedStarShape(sides = 6, curve = 0.05, rotation = 0f), offsetX = 0.dp, offsetY = 0.dp),
    Config(size = min * 0.35f, width = min * 0.22f, height = min * 0.22f, align = Alignment.TopEnd, rot = 15f, shape = RoundedCornerShape(16.dp), offsetX = -(300.dp * 0.03f), offsetY = (boxMaxHeight * 0.04f)),
    Config(size = min * 0.3f, width = min * 0.18f, height = min * 0.18f, align = Alignment.BottomStart, rot = 0f, shape = CircleShape, offsetX = (300.dp * 0.04f), offsetY = -(boxMaxHeight * 0.04f)),
    Config(size = min * 0.55f, width = min * 0.55f, height = min * 0.55f, align = Alignment.BottomStart, rot = -10f, shape = RoundedStarShape(sides = 6, curve = 0.05, rotation = 30f), offsetX = (300.dp * 0.02f), offsetY = -(boxMaxHeight * 0.02f)),
    Config(size = min * 0.55f, width = min * 0.42f, height = min * 0.55f, align = Alignment.TopEnd, rot = 30f, shape = RoundedCornerShape(percent = 50), offsetX = -(300.dp * 0.06f), offsetY = (boxMaxHeight * 0.03f))
)

/** Cascading discs / record-inspired circles. */
private fun vinylStackConfigs(min: Dp, boxMaxHeight: Dp): List<Config> = listOf(
    Config(size = min * 0.55f, width = min * 0.55f, height = min * 0.55f, align = Alignment.CenterStart, rot = 0f, shape = CircleShape, offsetX = (300.dp * 0.02f), offsetY = 0.dp),
    Config(size = min * 0.38f, width = min * 0.38f, height = min * 0.38f, align = Alignment.CenterEnd, rot = 0f, shape = CircleShape, offsetX = -(300.dp * 0.04f), offsetY = -(boxMaxHeight * 0.08f)),
    Config(size = min * 0.0f, width = min * 0.15f, height = min * 0.15f, align = Alignment.TopEnd, rot = -45f, shape = RoundedCornerShape(percent = 50), offsetX = -(300.dp * 0.02f), offsetY = (boxMaxHeight * 0.43f)),
    Config(size = min * 0.5f, width = min * 0.5f, height = min * 0.5f, align = Alignment.Center, rot = 0f, shape = CircleShape, offsetX = 70.dp, offsetY = -(boxMaxHeight * 0.02f)),
    Config(size = min * 0.35f, width = min * 0.35f, height = min * 0.35f, align = Alignment.BottomStart, rot = 0f, shape = RoundedStarShape(sides = 8, curve = 0.06, rotation = 22f), offsetX = (300.dp * 0.05f), offsetY = -(boxMaxHeight * 0.03f))
)

/** Grid-like arrangement of tilted rounded rectangles. */
private fun pixelMosaicConfigs(min: Dp, boxMaxHeight: Dp): List<Config> = listOf(
    Config(size = min * 0.65f, width = min * 0.42f, height = min * 0.65f, align = Alignment.TopStart, rot = 0f, shape = RoundedCornerShape(24.dp), offsetX = (300.dp * 0.03f), offsetY = (boxMaxHeight * 0.02f)),
    Config(size = min * 0.45f, width = min * 0.52f, height = min * 0.42f, align = Alignment.TopEnd, rot = 8f, shape = RoundedCornerShape(20.dp), offsetX = -(300.dp * 0.04f), offsetY = (boxMaxHeight * 0.06f)),
    Config(size = min * 0.1f, width = min * 0.52f, height = min * 0.12f, align = Alignment.BottomEnd, rot = -5f, shape = RoundedCornerShape(12.dp), offsetX = -(300.dp * 0.06f), offsetY = -(boxMaxHeight * 0.05f)),
    Config(size = min * 0.55f, width = min * 0.42f, height = min * 0.52f, align = Alignment.BottomEnd, rot = -12f, shape = RoundedCornerShape(28.dp), offsetX = -(300.dp * 0.02f), offsetY = -(boxMaxHeight * 0.02f)),
    Config(size = min * 0.4f, width = min * 0.50f, height = min * 0.48f, align = Alignment.TopStart, rot = 5f, shape = RoundedCornerShape(16.dp), offsetX = (300.dp * 0.04f), offsetY = (boxMaxHeight * 0.04f))
)

/** Star-heavy whimsical arrangement with mixed star variants. */
private fun stardustScatterConfigs(min: Dp, boxMaxHeight: Dp): List<Config> = listOf(
    Config(size = min * 0.65f, width = min * 0.65f, height = min * 0.65f, align = Alignment.Center, rot = 10f, shape = RoundedStarShape(sides = 5, curve = 0.12, rotation = 0f), offsetX = 0.dp, offsetY = 0.dp),
    Config(size = min * 0.37f, width = min * 0.22f, height = min * 0.22f, align = Alignment.TopStart, rot = 0f, shape = CircleShape, offsetX = (300.dp * 0.04f), offsetY = (boxMaxHeight * 0.03f)),
    Config(size = min * 0.36f, width = min * 0.26f, height = min * 0.26f, align = Alignment.BottomEnd, rot = 0f, shape = RoundedStarShape(sides = 4, curve = 0.18, rotation = 45f), offsetX = -(300.dp * 0.06f), offsetY = (boxMaxHeight * 0.00f)),
    Config(size = min * 0.5f, width = min * 0.5f, height = min * 0.5f, align = Alignment.CenterEnd, rot = -15f, shape = RoundedStarShape(sides = 8, curve = 0.04, rotation = 0f), offsetX = -(300.dp * 0.04f), offsetY = 0.dp),
    Config(size = min * 0.5f, width = min * 0.5f, height = min * 0.42f, align = Alignment.BottomStart, rot = 25f, shape = RoundedCornerShape(percent = 50), offsetX = (300.dp * 0.06f), offsetY = -(boxMaxHeight * 0.03f))
)

/** Soft rounded pieces that feel like artwork drifting in a calm orbit. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun softOrbitConfigs(min: Dp, boxMaxHeight: Dp): List<Config> = listOf(
    Config(size = min * 0.34f, width = min * 0.34f, height = min * 0.34f, align = Alignment.TopStart, rot = 0f, shape = MaterialShapes.Puffy.asShape(), offsetX = 10.dp, offsetY = 4.dp),
    Config(size = min * 0.46f, width = min * 0.46f, height = min * 0.28f, align = Alignment.TopEnd, rot = 0f, shape = MaterialShapes.Bun.asShape(), offsetX = (-10).dp, offsetY = 10.dp),
    Config(size = min * 0.42f, width = min * 0.42f, height = min * 0.42f, align = Alignment.BottomCenter, rot = 0f, shape = MaterialShapes.Ghostish.asShape(), offsetX = 0.dp, offsetY = (-6).dp),
    Config(size = min * 0.50f, width = min * 0.50f, height = min * 0.20f, align = Alignment.TopStart, rot = 0f, shape = MaterialShapes.Pill.asShape(), offsetX = 8.dp, offsetY = 4.dp),
    Config(size = min * 0.32f, width = min * 0.32f, height = min * 0.32f, align = Alignment.TopEnd, rot = 0f, shape = MaterialShapes.PuffyDiamond.asShape(), offsetX = (-12).dp, offsetY = 0.dp),
    Config(size = min * 0.54f, width = min * 0.54f, height = min * 0.18f, align = Alignment.BottomCenter, rot = 0f, shape = MaterialShapes.Oval.asShape(), offsetX = 0.dp, offsetY = (-2).dp),
)

/** Directional shapes with a musical "sweep" from left to right. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun arrowFanfareConfigs(min: Dp, boxMaxHeight: Dp): List<Config> = listOf(
    Config(size = min * 0.44f, width = min * 0.44f, height = min * 0.32f, align = Alignment.TopStart, rot = 0f, shape = MaterialShapes.Arrow.asShape(), offsetX = 8.dp, offsetY = 6.dp),
    Config(size = min * 0.36f, width = min * 0.36f, height = min * 0.36f, align = Alignment.TopEnd, rot = 0f, shape = MaterialShapes.Fan.asShape(), offsetX = (-12).dp, offsetY = 4.dp),
    Config(size = min * 0.54f, width = min * 0.54f, height = min * 0.20f, align = Alignment.BottomCenter, rot = 0f, shape = MaterialShapes.Slanted.asShape(), offsetX = 0.dp, offsetY = (-8).dp),
    Config(size = min * 0.32f, width = min * 0.32f, height = min * 0.32f, align = Alignment.CenterStart, rot = 0f, shape = MaterialShapes.Gem.asShape(), offsetX = 0.dp, offsetY = 0.dp),
    Config(size = min * 0.32f, width = min * 0.32f, height = min * 0.22f, align = Alignment.CenterEnd, rot = 0f, shape = MaterialShapes.Arch.asShape(), offsetX = 0.dp, offsetY = 0.dp),
    Config(size = min * 0.32f, width = min * 0.32f, height = min * 0.32f, align = Alignment.Center, rot = 0f, shape = MaterialShapes.Triangle.asShape(), offsetX = 0.dp, offsetY = 0.dp),
)

/** Bright radial accents, but balanced with softer silhouettes so it does not become noisy. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun sunnyBloomConfigs(min: Dp, boxMaxHeight: Dp): List<Config> = listOf(
    Config(size = min * 0.34f, width = min * 0.34f, height = min * 0.34f, align = Alignment.TopStart, rot = 0f, shape = MaterialShapes.Sunny.asShape(), offsetX = 8.dp, offsetY = 2.dp),
    Config(size = min * 0.32f, width = min * 0.32f, height = min * 0.32f, align = Alignment.TopEnd, rot = 0f, shape = MaterialShapes.Flower.asShape(), offsetX = (-12).dp, offsetY = 6.dp),
    Config(size = min * 0.44f, width = min * 0.44f, height = min * 0.44f, align = Alignment.BottomCenter, rot = 0f, shape = MaterialShapes.SoftBurst.asShape(), offsetX = 0.dp, offsetY = 0.dp),
    Config(size = min * 0.32f, width = min * 0.32f, height = min * 0.32f, align = Alignment.TopStart, rot = 0f, shape = MaterialShapes.Clover4Leaf.asShape(), offsetX = 8.dp, offsetY = 0.dp),
    Config(size = min * 0.32f, width = min * 0.32f, height = min * 0.32f, align = Alignment.TopEnd, rot = 0f, shape = MaterialShapes.Boom.asShape(), offsetX = (-8).dp, offsetY = 0.dp),
    Config(size = min * 0.50f, width = min * 0.50f, height = min * 0.18f, align = Alignment.BottomCenter, rot = 0f, shape = MaterialShapes.ClamShell.asShape(), offsetX = 0.dp, offsetY = (-2).dp),
)

/** Pixelated and cookie-like shapes for a crisp, playful grid that differs from Pixel Mosaic. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun pixelCookieConfigs(min: Dp, boxMaxHeight: Dp): List<Config> = listOf(
    Config(size = min * 0.36f, width = min * 0.36f, height = min * 0.36f, align = Alignment.TopStart, rot = 0f, shape = MaterialShapes.PixelCircle.asShape(), offsetX = 8.dp, offsetY = 4.dp),
    Config(size = min * 0.34f, width = min * 0.34f, height = min * 0.34f, align = Alignment.TopEnd, rot = 0f, shape = MaterialShapes.Cookie4Sided.asShape(), offsetX = (-10).dp, offsetY = 8.dp),
    Config(size = min * 0.40f, width = min * 0.40f, height = min * 0.40f, align = Alignment.BottomCenter, rot = 0f, shape = MaterialShapes.PixelTriangle.asShape(), offsetX = 0.dp, offsetY = 0.dp),
    Config(size = min * 0.32f, width = min * 0.32f, height = min * 0.32f, align = Alignment.CenterStart, rot = 0f, shape = MaterialShapes.Cookie7Sided.asShape(), offsetX = 0.dp, offsetY = 0.dp),
    Config(size = min * 0.32f, width = min * 0.32f, height = min * 0.32f, align = Alignment.CenterEnd, rot = 0f, shape = MaterialShapes.Cookie12Sided.asShape(), offsetX = 0.dp, offsetY = 0.dp),
    Config(size = min * 0.32f, width = min * 0.32f, height = min * 0.32f, align = Alignment.Center, rot = 0f, shape = MaterialShapes.Cookie6Sided.asShape(), offsetX = 0.dp, offsetY = 0.dp),
)

/** Friendly, rounded motifs with hearts and clovers arranged like a small garden. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun heartGardenConfigs(min: Dp, boxMaxHeight: Dp): List<Config> = listOf(
    Config(size = min * 0.36f, width = min * 0.36f, height = min * 0.34f, align = Alignment.TopStart, rot = 0f, shape = MaterialShapes.Heart.asShape(), offsetX = 8.dp, offsetY = 4.dp),
    Config(size = min * 0.34f, width = min * 0.34f, height = min * 0.34f, align = Alignment.TopEnd, rot = 0f, shape = MaterialShapes.Clover8Leaf.asShape(), offsetX = (-10).dp, offsetY = 4.dp),
    Config(size = min * 0.54f, width = min * 0.54f, height = min * 0.20f, align = Alignment.BottomCenter, rot = 0f, shape = MaterialShapes.SemiCircle.asShape(), offsetX = 0.dp, offsetY = (-2).dp),
    Config(size = min * 0.30f, width = min * 0.30f, height = min * 0.24f, align = Alignment.CenterStart, rot = 0f, shape = MaterialShapes.ClamShell.asShape(), offsetX = 0.dp, offsetY = 0.dp),
    Config(size = min * 0.30f, width = min * 0.30f, height = min * 0.18f, align = Alignment.CenterEnd, rot = 0f, shape = MaterialShapes.Oval.asShape(), offsetX = 0.dp, offsetY = 0.dp),
    Config(size = min * 0.30f, width = min * 0.30f, height = min * 0.30f, align = Alignment.Center, rot = 0f, shape = MaterialShapes.Diamond.asShape(), offsetX = 0.dp, offsetY = 0.dp),
)

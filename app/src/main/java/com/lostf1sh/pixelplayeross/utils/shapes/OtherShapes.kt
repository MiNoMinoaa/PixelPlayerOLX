package com.lostf1sh.pixelplayeross.utils.shapes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

fun createHexagonShape() = object : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val radius = minOf(size.width, size.height) / 2f
            val angle = 2.0 * Math.PI / 6
            moveTo(size.width / 2f + radius * cos(0.0).toFloat(), size.height / 2f + radius * sin(0.0).toFloat())
            for (i in 1..6) {
                lineTo(size.width / 2f + radius * cos(angle * i).toFloat(), size.height / 2f + radius * sin(angle * i).toFloat())
            }
            close()
        })
    }
}

// Similar implementations for createRoundedTriangleShape, createSemiCircleShape
// (These can be more complex depending on the exact design you want)

// Simple rounded triangle example (you'd need to adjust it)
fun createRoundedTriangleShape() = object : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            val path = Path()
            path.moveTo(size.width / 2f, 0f)
            path.lineTo(size.width, size.height)
            path.lineTo(0f, size.height)
            path.close()

            // To round the corners, you could use CornerPathEffect in a Modifier.drawBehind,
            // or build the shape with arcs and lines. Clipping with Shape only clips.
            // A simple approach is to use a RoundRect for the clip with large radii, but that isn't a real triangle.
            // For a precise rounded triangle, you'd have to draw the shape with arcs.
            // For now, we'll keep the simple clip or you'll need a more advanced implementation.

            // Simple alternative: clip to a rectangle with rounded corners
            // return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(16f, 16f)))
            // This isn't a triangle. You need a real rounded-triangle shape implementation.
            // For simplicity in this example, we'll use more standard shapes or library ones.

            // For the example, we'll simply use a basic triangle without complex rounding in the clip.
            // If you need real rounded triangles, look for more advanced implementations.
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height * 0.8f) // Adjustment so the base doesn't reach all the way to the bottom
            lineTo(0f, size.height * 0.8f)
            close()

        })
    }
}

// Simple semicircle example (you'd need to adjust it)
fun createSemiCircleShape() = object : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            arcTo(
                rect = Rect(0f, 0f, size.width, size.width), // A circle based on the width
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(size.width / 2f, size.width / 2f) // Draw a line to the center if you need to close it like a pie slice
            close() // Optional: close the shape
        })
    }
}

/**
 * Creates a hexagon shape with rounded corners.
 */
fun createRoundedHexagonShape(cornerRadius: Dp) = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val width = size.width
            val height = size.height
            val radius = min(width, height) / 2f
            val cornerRadiusPx = with(density) { cornerRadius.toPx() }

            // Hexagon points without rounding
            val points = (0..5).map { i ->
                val angle = PI / 3 * i
                Offset(
                    x = width / 2f + radius * cos(angle).toFloat(),
                    y = height / 2f + radius * sin(angle).toFloat()
                )
            }

            // Move to the first point with an offset to start the arc
            moveTo(points[0].x + cornerRadiusPx * cos(PI / 3.0).toFloat(), points[0].y + cornerRadiusPx * sin(PI / 3.0).toFloat())

            for (i in 0..5) {
                val p1 = points[i]
                val p2 = points[(i + 1) % 6]
                val p3 = points[(i + 2) % 6]

                // Line to the arc's start point
                lineTo(p2.x - cornerRadiusPx * cos(PI / 3.0).toFloat(), p2.y - cornerRadiusPx * sin(PI / 3.0).toFloat())

                // Arc at the corner
                arcTo(
                    rect = Rect(
                        left = p2.x - cornerRadiusPx,
                        top = p2.y - cornerRadiusPx,
                        right = p2.x + cornerRadiusPx,
                        bottom = p2.y + cornerRadiusPx
                    ),
                    startAngleDegrees = (i * 60 + 30).toFloat(), // Arc start angle
                    sweepAngleDegrees = 60f, // Arc sweep angle
                    forceMoveTo = false
                )
            }
            close()
        })
    }
}

/**
 * Creates a triangle shape with rounded corners.
 * Simple implementation for clipping.
 */
fun createRoundedTriangleShape(cornerRadius: Dp) = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            val width = size.width
            val height = size.height
            val cornerRadiusPx = with(density) { cornerRadius.toPx() }

            // Triangle points
            val p1 = Offset(width / 2f, 0f) // Top
            val p2 = Offset(width, height) // Bottom right
            val p3 = Offset(0f, height) // Bottom left

            // To simplify the rounding in the clip, we'll use arcs.
            // This isn't a perfect triangle with tangent arcs, but a practical approach for clipping.

            // Compute control points for the arcs
            val control12 = Offset(p1.x + (p2.x - p1.x) * 0.8f, p1.y + (p2.y - p1.y) * 0.8f)
            val control23 = Offset(p2.x + (p3.x - p2.x) * 0.2f, p2.y + (p3.y - p2.y) * 0.2f)
            val control31 = Offset(p3.x + (p1.x - p3.x) * 0.8f, p3.y + (p1.y - p3.y) * 0.8f)


            moveTo(p1.x, p1.y + cornerRadiusPx * 2) // Start a bit below the top vertex

            // Top-right arc
            quadraticTo(p1.x, p1.y, p1.x + cornerRadiusPx * sqrt(2f), p1.y + cornerRadiusPx * sqrt(2f))
            lineTo(p2.x - cornerRadiusPx * sqrt(2f), p2.y - cornerRadiusPx * sqrt(2f))

            // Bottom-right arc
            quadraticTo(p2.x, p2.y, p2.x - cornerRadiusPx * sqrt(2f), p2.y + cornerRadiusPx * sqrt(2f))
            lineTo(p3.x + cornerRadiusPx * sqrt(2f), p3.y + cornerRadiusPx * sqrt(2f))

            // Bottom-left arc
            quadraticTo(p3.x, p3.y, p3.x + cornerRadiusPx * sqrt(2f), p3.y - cornerRadiusPx * sqrt(2f))
            lineTo(p1.x - cornerRadiusPx * sqrt(2f), p1.y + cornerRadiusPx * sqrt(2f))

            close() // Close the shape
        })
    }
}


/**
 * Creates a semicircle shape with a slightly rounded base.
 */
fun createSemiCircleShape(cornerRadius: Dp) = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            val width = size.width
            val height = size.height
            val radius = width / 2f
            val cornerRadiusPx = with(density) { cornerRadius.toPx() }

            // Top arc (semicircle)
            arcTo(
                rect = Rect(0f, 0f, width, width), // A circle based on the width
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )

            // Base (line with arcs at the ends)
            val startBaseX = 0f + cornerRadiusPx
            val endBaseX = width - cornerRadiusPx
            val baseY = width / 2f // The base sits at half the circle's diameter

            lineTo(endBaseX, baseY) // Line to the end of the base

            // Bottom-right arc
            arcTo(
                rect = Rect(endBaseX - cornerRadiusPx, baseY - cornerRadiusPx, endBaseX + cornerRadiusPx, baseY + cornerRadiusPx),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -90f, // Arc downward
                forceMoveTo = false
            )

            lineTo(startBaseX, baseY + cornerRadiusPx) // Bottom line

            // Bottom-left arc
            arcTo(
                rect = Rect(startBaseX - cornerRadiusPx, baseY - cornerRadiusPx, startBaseX + cornerRadiusPx, baseY + cornerRadiusPx),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f, // Arc downward
                forceMoveTo = false
            )

            close() // Close the shape
        })
    }
}
package com.lostf1sh.pixelplayeross.ui.glancewidget.subcomponents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import com.lostf1sh.pixelplayeross.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A LinearProgressIndicator for Glance that simulates a wave effect by generating a Bitmap.
 *
 * IMPORTANT: The wave animation requires an external mechanism (e.g. CoroutineWorker)
 * that calls `GlanceAppWidget.update` periodically with an updated `phaseShift`.
 *
 * @param progress The current progress (between 0.0f and 1.0f).
 * @param isPlaying Whether the associated content is playing (to show the wave).
 * @param modifier The GlanceModifier to apply.
 * @param height The total height of the component. Important so it takes up the correct space.
 * @param phaseShift The phase shift for the wave animation (from 0 to 2*PI).
 * Must be updated externally to create the illusion of movement.
 * @param activeTrackColor Color of the active part of the track (the wave/covered part).
 * @param trackBackgroundColor Color of the track background (the uncovered part).
 * @param thumbColor Color of the circle at the end of the progress.
 * @param hideInactiveTrackPortion If `true`, the part of the track background that has already been
 * covered will not be drawn.
 * @param trackHeight Height of the track line.
 * @param thumbRadius Radius of the circle.
 * @param waveAmplitude Amplitude of the wave when `isPlaying` is true.
 * @param waveFrequency Frequency of the wave (higher = more ripples).
 */
@Composable
fun WavyLinearProgressIndicator(
    progress: Float,
    isPlaying: Boolean,
    modifier: GlanceModifier = GlanceModifier,
    height: Dp = 24.dp, // Parameter to control the height
    phaseShift: Float = 0f,
    activeTrackColor: Color = Color(0xFF6200EE),
    trackBackgroundColor: Color = Color(0xFF6200EE).copy(alpha = 0.24f),
    thumbColor: Color = Color(0xFF6200EE),
    hideInactiveTrackPortion: Boolean = true, // New parameter
    trackHeight: Dp = 6.dp,
    thumbRadius: Dp = 8.dp,
    waveAmplitude: Dp = 3.dp,
    waveFrequency: Float = 0.08f
) {
    val context = LocalContext.current
    val progressPercent = (progress * 100f).toInt().coerceIn(0, 100)
    // The Glance component is simply an Image. The Bitmap is generated on demand.
    Image(
        provider = ImageProvider(
            createWavyProgressBitmap(
                context = context, // Context is passed for the density
                progress = progress,
                isPlaying = isPlaying,
                phaseShift = phaseShift,
                activeTrackColor = activeTrackColor,
                trackBackgroundColor = trackBackgroundColor,
                thumbColor = thumbColor,
                hideInactiveTrackPortion = hideInactiveTrackPortion,
                trackHeight = trackHeight,
                thumbRadius = thumbRadius,
                waveAmplitude = waveAmplitude,
                waveFrequency = waveFrequency,
                bitmapHeight = (height.value * context.resources.displayMetrics.density).toInt()
            )
        ),
        contentDescription = context.getString(R.string.widget_wavy_progress_bar_desc, progressPercent),
        modifier = modifier.height(height) // The height is applied to the modifier
    )
}

/**
 * Utility function that generates a Bitmap with the drawing of the wavy progress bar.
 * This function adapts the logic of the original WavyMusicSlider to draw on an Android Canvas.
 */
private fun createWavyProgressBitmap(
    context: Context,
    progress: Float,
    isPlaying: Boolean,
    phaseShift: Float,
    activeTrackColor: Color,
    trackBackgroundColor: Color,
    thumbColor: Color,
    hideInactiveTrackPortion: Boolean,
    trackHeight: Dp,
    thumbRadius: Dp,
    waveAmplitude: Dp,
    waveFrequency: Float,
    bitmapWidth: Int = 1000, // Fixed width for the bitmap resolution
    bitmapHeight: Int
): Bitmap {
    val bmp = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    // Use the real device density to convert Dp to Px.
    val density = context.resources.displayMetrics.density
    val trackHeightPx = trackHeight.value * density
    val thumbRadiusPx = thumbRadius.value * density
    val waveAmplitudePx = if (isPlaying) waveAmplitude.value * density else 0f

    val centerY = canvas.height / 2f
    val trackStart = thumbRadiusPx
    val trackEnd = canvas.width - thumbRadiusPx
    val trackWidth = trackEnd - trackStart

    // Configure Paints
    val activePaint = Paint().apply {
        color = activeTrackColor.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = trackHeightPx
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    val inactivePaint = Paint().apply {
        color = trackBackgroundColor.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = trackHeightPx
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    val thumbPaint = Paint().apply {
        color = thumbColor.toArgb()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    val activeTrackEndPx = trackStart + trackWidth * progress

    // Draw background track
    if (hideInactiveTrackPortion) {
        // Only draw the part that remains to be covered
        canvas.drawLine(activeTrackEndPx, centerY, trackEnd, centerY, inactivePaint)
    } else {
        // Draw the entire background track
        canvas.drawLine(trackStart, centerY, trackEnd, centerY, inactivePaint)
    }

    // Draw active track (wave or line)
    if (progress > 0) {
        if (waveAmplitudePx > 0.1f) {
            val wavePath = Path()
            wavePath.moveTo(
                trackStart,
                centerY + waveAmplitudePx * sin(waveFrequency * trackStart + phaseShift)
            )
            val step = 5f // Increase the step to improve performance
            var x = trackStart + step
            while (x < activeTrackEndPx) {
                val waveY = centerY + waveAmplitudePx * sin(waveFrequency * x + phaseShift)
                wavePath.lineTo(x, waveY)
                x += step
            }
            wavePath.lineTo(
                activeTrackEndPx,
                centerY + waveAmplitudePx * sin(waveFrequency * activeTrackEndPx + phaseShift)
            )
            canvas.drawPath(wavePath, activePaint)
        } else {
            canvas.drawLine(trackStart, centerY, activeTrackEndPx, centerY, activePaint)
        }
    }

    // Draw Thumb
    val thumbX = activeTrackEndPx
    canvas.drawCircle(thumbX, centerY, thumbRadiusPx, thumbPaint)

    return bmp
}
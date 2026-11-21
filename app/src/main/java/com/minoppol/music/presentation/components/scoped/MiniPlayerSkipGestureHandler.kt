package com.minoppol.music.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

/** Finger travel, as a fraction of the screen width, that commits a skip. */
private const val SKIP_COMMIT_DISTANCE_FRACTION = 0.22f

/** Screen-width fraction the card asymptotically approaches while being dragged. */
private const val SKIP_DRAG_TRAVEL_FRACTION = 0.5f

/** Fling speed (px/s) that commits a skip on its own, for short flicks. */
private const val SKIP_FLING_VELOCITY = 620f

/** A fling still needs this much travel so press jitter can never skip a track. */
private const val SKIP_FLING_MIN_DISTANCE_FRACTION = 0.05f

/** How much further the outgoing card travels before the track actually changes. */
private const val SKIP_EXIT_DISTANCE_FRACTION = 0.34f

/** Where the incoming card starts from, on the opposite side. */
private const val SKIP_ENTER_DISTANCE_FRACTION = 0.26f

private const val SKIP_EXIT_DURATION_MS = 130
private const val SKIP_EXIT_FADE_DURATION_MS = 110
private const val SKIP_ENTER_FADE_DURATION_MS = 190

/**
 * Horizontal mini-player gesture for the "swipe to change song" mode: swipe left for the
 * next track, right for the previous one.
 *
 * Every value it writes lives in an [Animatable] that the sheet reads inside `graphicsLayer`,
 * so a drag costs a draw pass and never a recomposition of the player sheet.
 */
internal class MiniPlayerSkipGestureHandler(
    private val scope: CoroutineScope,
    private val hapticFeedback: HapticFeedback,
    private val translationXAnimatable: Animatable<Float, AnimationVector1D>,
    private val contentAlphaAnimatable: Animatable<Float, AnimationVector1D>,
    private val screenWidthPx: Float,
    private val onSkipToNext: () -> Unit,
    private val onSkipToPrevious: () -> Unit
) {
    private val velocityTracker = VelocityTracker()
    private var accumulatedDragX = 0f
    private var pastCommitThreshold = false
    private var animationJob: Job? = null

    /** A committed swap owns the card until it settles; drags in that window are ignored. */
    private var swapInFlight = false

    private val dragTravelPx: Float
        get() = (screenWidthPx * SKIP_DRAG_TRAVEL_FRACTION).coerceAtLeast(1f)

    private val commitDistancePx: Float
        get() = screenWidthPx * SKIP_COMMIT_DISTANCE_FRACTION

    fun onDragStart() {
        if (swapInFlight) return
        accumulatedDragX = 0f
        pastCommitThreshold = false
        velocityTracker.resetTracking()
        animationJob?.cancel()
        animationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            translationXAnimatable.stop()
        }
    }

    fun onHorizontalDrag(uptimeMillis: Long, dragAmount: Float) {
        if (swapInFlight) return
        accumulatedDragX += dragAmount

        // The card is translated by an ancestor graphicsLayer, so `change.position` follows the
        // card rather than the finger and would read as almost stationary. `dragAmount` is a
        // true finger delta, so the accumulated value is the honest track for velocity.
        velocityTracker.addPosition(uptimeMillis, Offset(accumulatedDragX, 0f))

        // Asymptotic resistance: 1:1 for the first few dp, progressively heavier after that, so
        // the card can never be dragged past the distance the swap animation starts from.
        val travel = dragTravelPx
        val visualOffset = travel * tanh(accumulatedDragX / travel)
        animationJob?.cancel()
        animationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            translationXAnimatable.snapTo(visualOffset)
        }

        val past = abs(accumulatedDragX) >= commitDistancePx
        if (past != pastCommitThreshold) {
            pastCommitThreshold = past
            if (past) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    fun onDragEnd() {
        if (swapInFlight) return
        val dragX = accumulatedDragX
        accumulatedDragX = 0f
        pastCommitThreshold = false

        val velocityX = velocityTracker.calculateVelocity().x
        val flungFarEnough = abs(dragX) > screenWidthPx * SKIP_FLING_MIN_DISTANCE_FRACTION
        val direction = when {
            abs(dragX) >= commitDistancePx -> dragX.sign
            abs(velocityX) >= SKIP_FLING_VELOCITY &&
                flungFarEnough &&
                velocityX.sign == dragX.sign -> velocityX.sign
            else -> 0f
        }

        animationJob?.cancel()
        if (direction == 0f) {
            animationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                translationXAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            return
        }
        animateSwap(direction)
    }

    /**
     * A cancelled pointer stream (window losing pointers, another node winning) is not intent, so
     * it settles the card back home without committing a track change.
     */
    fun onDragCancel() {
        if (swapInFlight) return
        accumulatedDragX = 0f
        pastCommitThreshold = false
        animationJob?.cancel()
        animationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            translationXAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    /**
     * Shared-axis swap: the card finishes leaving in the swipe direction while it fades out, the
     * track changes while it is invisible, then the new card springs in from the opposite side.
     * Switching only once the card is transparent keeps the metadata change from popping
     * mid-slide, at the cost of a delay short enough to read as part of the gesture.
     */
    private fun animateSwap(direction: Float) {
        swapInFlight = true
        animationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                coroutineScope {
                    launch {
                        translationXAnimatable.animateTo(
                            targetValue = direction * screenWidthPx * SKIP_EXIT_DISTANCE_FRACTION,
                            animationSpec = tween(
                                durationMillis = SKIP_EXIT_DURATION_MS,
                                easing = FastOutLinearInEasing
                            )
                        )
                    }
                    launch {
                        contentAlphaAnimatable.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = SKIP_EXIT_FADE_DURATION_MS,
                                easing = LinearEasing
                            )
                        )
                    }
                }

                if (direction < 0f) onSkipToNext() else onSkipToPrevious()

                translationXAnimatable.snapTo(
                    -direction * screenWidthPx * SKIP_ENTER_DISTANCE_FRACTION
                )
                coroutineScope {
                    launch {
                        translationXAnimatable.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                    launch {
                        contentAlphaAnimatable.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = SKIP_ENTER_FADE_DURATION_MS,
                                easing = LinearOutSlowInEasing
                            )
                        )
                    }
                }
            } finally {
                swapInFlight = false
            }
        }
    }
}

@Composable
internal fun rememberMiniPlayerSkipGestureHandler(
    scope: CoroutineScope,
    hapticFeedback: HapticFeedback,
    translationXAnimatable: Animatable<Float, AnimationVector1D>,
    contentAlphaAnimatable: Animatable<Float, AnimationVector1D>,
    screenWidthPx: Float,
    onSkipToNext: () -> Unit,
    onSkipToPrevious: () -> Unit
): MiniPlayerSkipGestureHandler {
    val onSkipToNextState = rememberUpdatedState(onSkipToNext)
    val onSkipToPreviousState = rememberUpdatedState(onSkipToPrevious)
    return remember(
        scope,
        hapticFeedback,
        translationXAnimatable,
        contentAlphaAnimatable,
        screenWidthPx
    ) {
        MiniPlayerSkipGestureHandler(
            scope = scope,
            hapticFeedback = hapticFeedback,
            translationXAnimatable = translationXAnimatable,
            contentAlphaAnimatable = contentAlphaAnimatable,
            screenWidthPx = screenWidthPx,
            onSkipToNext = { onSkipToNextState.value() },
            onSkipToPrevious = { onSkipToPreviousState.value() }
        )
    }
}

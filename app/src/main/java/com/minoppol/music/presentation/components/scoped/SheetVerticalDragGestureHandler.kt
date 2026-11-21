package com.minoppol.music.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.minoppol.music.presentation.viewmodel.PlayerSheetState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Fraction of the off-screen travel the finger must cover to commit a downward dismiss. */
private const val COLLAPSED_DISMISS_COMMIT_FRACTION = 0.38f

/** Downward fling speed (px/s) that commits a dismiss on its own. */
private const val COLLAPSED_DISMISS_FLING_VELOCITY = 900f

/**
 * Encapsulates vertical drag gesture state and target resolution for the player sheet.
 *
 * The expand/collapse behavior is unchanged from the original inline implementation. When
 * [collapsedDismissEnabledProvider] reports true — the "swipe to change song" mode, where the
 * horizontal swipe is taken over by track skipping — a downward drag on the *collapsed* mini
 * player is rerouted to a dismiss instead of rubber-banding against the collapsed stop.
 */
internal class SheetVerticalDragGestureHandler(
    private val scope: CoroutineScope,
    private val velocityTracker: VelocityTracker,
    private val densityProvider: () -> Density,
    private val sheetMotionController: SheetMotionController,
    private val playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    private val currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    private val expandedYProvider: () -> Float,
    private val collapsedYProvider: () -> Float,
    private val miniHeightPxProvider: () -> Float,
    private val currentSheetStateProvider: () -> PlayerSheetState,
    private val visualOvershootScaleY: Animatable<Float, AnimationVector1D>,
    private val onDraggingChange: (Boolean) -> Unit,
    private val onDraggingPlayerAreaChange: (Boolean) -> Unit,
    private val onAnimateSheet: suspend (
        targetExpanded: Boolean,
        animationSpec: AnimationSpec<Float>?,
        initialVelocity: Float
    ) -> Unit,
    private val onExpandSheetState: () -> Unit,
    private val onCollapseSheetState: () -> Unit,
    private val dismissOffsetY: Animatable<Float, AnimationVector1D>,
    private val collapsedDismissEnabledProvider: () -> Boolean,
    private val collapsedDismissTravelPxProvider: () -> Float,
    private val onCollapsedDismissStarted: () -> Unit,
    private val onCollapsedDismissCommitted: () -> Unit
) {
    private var initialFractionOnDragStart = 0f
    private var initialYOnDragStart = 0f
    private var accumulatedDragYSinceStart = 0f
    private var dragSnapJob: Job? = null

    /** Resolved once per gesture: whether a downward drag may turn into a dismiss. */
    private var collapsedDismissCandidate = false

    /** True once the gesture has actually moved downwards and taken over the card. */
    private var collapsedDismissActive = false
    private var dismissJob: Job? = null

    fun onDragStart() {
        dragSnapJob?.cancel()
        dragSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sheetMotionController.stop()
        }
        onDraggingChange(true)
        onDraggingPlayerAreaChange(true)
        velocityTracker.resetTracking()
        initialFractionOnDragStart = playerContentExpansionFraction.value
        initialYOnDragStart = currentSheetTranslationY.value
        accumulatedDragYSinceStart = 0f
        collapsedDismissCandidate = collapsedDismissEnabledProvider() &&
            currentSheetStateProvider() == PlayerSheetState.COLLAPSED &&
            playerContentExpansionFraction.value < 0.01f
        collapsedDismissActive = false
        if (collapsedDismissCandidate) {
            dismissJob?.cancel()
            dismissJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                dismissOffsetY.stop()
            }
        }
    }

    fun onVerticalDrag(
        uptimeMillis: Long,
        position: Offset,
        dragAmount: Float
    ) {
        accumulatedDragYSinceStart += dragAmount

        if (collapsedDismissCandidate) {
            onCollapsedCandidateDrag(uptimeMillis, position)
            return
        }

        val dragFrame = computeSheetVerticalDragFrame(
            currentTranslationY = currentSheetTranslationY.value,
            dragAmount = dragAmount,
            expandedY = expandedYProvider(),
            collapsedY = collapsedYProvider(),
            miniHeightPx = miniHeightPxProvider(),
            initialFractionOnDragStart = initialFractionOnDragStart,
            initialYOnDragStart = initialYOnDragStart
        )
        dragSnapJob?.cancel()
        dragSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sheetMotionController.snapTo(
                translationYValue = dragFrame.translationY,
                expansionFractionValue = dragFrame.expansionFraction
            )
        }
        velocityTracker.addPosition(uptimeMillis, position)
    }

    /**
     * Drag frame for a collapsed mini player that may be dismissed downwards.
     *
     * Downward drags translate the card and leave the sheet parked at its collapsed stop; upward
     * drags fall back to the normal expand track. Both branches are resolved from the *absolute*
     * accumulated drag rather than the per-frame delta, so crossing back and forth over the start
     * point stays exact instead of drifting by the frames spent on the other branch.
     */
    private fun onCollapsedCandidateDrag(uptimeMillis: Long, position: Offset) {
        val accumulated = accumulatedDragYSinceStart

        if (accumulated > 0f) {
            if (!collapsedDismissActive) {
                collapsedDismissActive = true
                dragSnapJob?.cancel()
                dragSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    sheetMotionController.snapTo(
                        translationYValue = collapsedYProvider(),
                        expansionFractionValue = 0f
                    )
                }
            }
            val travel = collapsedDismissTravelPxProvider()
            dismissJob?.cancel()
            dismissJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                dismissOffsetY.snapTo(accumulated.coerceAtMost(travel))
            }
        } else {
            if (collapsedDismissActive) {
                collapsedDismissActive = false
                dismissJob?.cancel()
                dismissJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    dismissOffsetY.snapTo(0f)
                }
            }
            val dragFrame = computeSheetVerticalDragFrame(
                currentTranslationY = initialYOnDragStart,
                dragAmount = accumulated,
                expandedY = expandedYProvider(),
                collapsedY = collapsedYProvider(),
                miniHeightPx = miniHeightPxProvider(),
                initialFractionOnDragStart = initialFractionOnDragStart,
                initialYOnDragStart = initialYOnDragStart
            )
            dragSnapJob?.cancel()
            dragSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                sheetMotionController.snapTo(
                    translationYValue = dragFrame.translationY,
                    expansionFractionValue = dragFrame.expansionFraction
                )
            }
        }

        // The card follows the finger through an ancestor graphicsLayer, so `position` reads as
        // nearly stationary. Track the accumulated drag instead to get an honest fling velocity.
        velocityTracker.addPosition(uptimeMillis, Offset(position.x, accumulated))
    }

    fun onDragEnd() = settleDrag(cancelled = false)

    fun onDragCancel() = settleDrag(cancelled = true)

    private fun settleDrag(cancelled: Boolean) {
        val accumulatedDragY = accumulatedDragYSinceStart
        accumulatedDragYSinceStart = 0f
        val dismissWasActive = collapsedDismissActive
        collapsedDismissCandidate = false
        collapsedDismissActive = false

        dragSnapJob?.cancel()
        dragSnapJob = null
        onDraggingChange(false)
        onDraggingPlayerAreaChange(false)

        val verticalVelocity = velocityTracker.calculateVelocity().y

        if (dismissWasActive) {
            finishCollapsedDismiss(accumulatedDragY, verticalVelocity, cancelled)
            return
        }

        val currentFraction = playerContentExpansionFraction.value
        val minDragThresholdPx = with(densityProvider()) { 5.dp.toPx() }
        val velocityThreshold = 150f

        val targetState = resolveVerticalSheetTargetState(
            currentSheetContentState = currentSheetStateProvider(),
            accumulatedDragY = accumulatedDragY,
            minDragThresholdPx = minDragThresholdPx,
            verticalVelocity = verticalVelocity,
            velocityThreshold = velocityThreshold,
            currentFraction = currentFraction
        )

        scope.launch {
            if (targetState == PlayerSheetState.EXPANDED) {
                launch {
                    onAnimateSheet(true, null, 0f)
                }
                onExpandSheetState()
            } else {
                val dynamicDamping = collapseSpringDampingForFraction(currentFraction)
                launch {
                    val initialSquash = collapseInitialSquashForFraction(currentFraction)
                    visualOvershootScaleY.snapTo(initialSquash)
                    visualOvershootScaleY.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessVeryLow
                        )
                    )
                }
                launch {
                    onAnimateSheet(
                        false,
                        spring(
                            dampingRatio = dynamicDamping,
                            stiffness = Spring.StiffnessLow
                        ),
                        verticalVelocity
                    )
                }
                onCollapseSheetState()
            }
        }
    }

    /**
     * Settles a downward mini-player drag: either it carries on off-screen and the queue is
     * dismissed, or it springs back. Mirrors the timing of the horizontal dismiss so both
     * gestures feel like the same object. A [cancelled] pointer stream is not intent, so it
     * always springs back.
     */
    private fun finishCollapsedDismiss(
        accumulatedDragY: Float,
        verticalVelocity: Float,
        cancelled: Boolean
    ) {
        val travel = collapsedDismissTravelPxProvider().coerceAtLeast(1f)
        val minFlingDistancePx = with(densityProvider()) { 12.dp.toPx() }
        val shouldDismiss = !cancelled &&
            (accumulatedDragY > travel * COLLAPSED_DISMISS_COMMIT_FRACTION ||
                (verticalVelocity > COLLAPSED_DISMISS_FLING_VELOCITY &&
                    accumulatedDragY > minFlingDistancePx))

        dismissJob?.cancel()
        dismissJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (shouldDismiss) {
                onCollapsedDismissStarted()
                dismissOffsetY.animateTo(
                    targetValue = travel,
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = FastOutSlowInEasing
                    )
                )
                onCollapsedDismissCommitted()
                dismissOffsetY.snapTo(0f)
            } else {
                dismissOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }
}

internal fun Modifier.playerSheetVerticalDragGesture(
    enabled: Boolean,
    handler: SheetVerticalDragGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled, handler) {
        detectVerticalDragGestures(
            onDragStart = { handler.onDragStart() },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                handler.onVerticalDrag(
                    uptimeMillis = change.uptimeMillis,
                    position = change.position,
                    dragAmount = dragAmount
                )
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragCancel() }
        )
    }
}

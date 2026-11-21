package com.minoppol.music.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.minoppol.music.R
import com.minoppol.music.data.model.Song
import com.minoppol.music.data.preferences.SwipeToQueueAction
import com.minoppol.music.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

private val SwipeToQueueThreshold = 72.dp

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun rememberSwipeToQueue(playerViewModel: PlayerViewModel): ((Song) -> Unit)? {
    val action by playerViewModel.swipeToQueueAction.collectAsStateWithLifecycle()
    return remember(action, playerViewModel) {
        if (action == SwipeToQueueAction.OFF) null else playerViewModel::queueSongFromSwipe
    }
}

@Composable
fun SwipeToQueueBox(
    onSwipe: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    if (onSwipe == null) {
        content(modifier)
        return
    }

    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { SwipeToQueueThreshold.toPx() }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    Box(modifier) {
        Icon(
            painter = painterResource(R.drawable.rounded_queue_music_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
                .graphicsLayer {
                    val progress = (offset.value / thresholdPx).coerceIn(0f, 1f)
                    alpha = progress
                    scaleX = 0.6f + 0.4f * progress
                    scaleY = scaleX
                }
        )
        content(
            Modifier
                .graphicsLayer { translationX = offset.value * direction }
                .pointerInput(direction, thresholdPx, touchSlop) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragged = 0f
                        var netDx = 0f
                        var netDy = 0f
                        var dragging = false
                        fun settle() {
                            dragged = 0f
                            scope.launch { offset.animateTo(0f) }
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (!change.pressed) {
                                if (dragging && dragged >= thresholdPx) currentOnSwipe()
                                break
                            }
                            if (dragging) {
                                if (change.isConsumed) {
                                    settle()
                                    break
                                }
                                val dragAmount = change.positionChangeIgnoreConsumed().x * direction
                                change.consume()
                                val wasArmed = dragged >= thresholdPx
                                dragged = (dragged + dragAmount).coerceIn(0f, thresholdPx * 1.5f)
                                if (!wasArmed && dragged >= thresholdPx) {
                                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                }
                                val target = dragged
                                scope.launch { offset.snapTo(target) }
                            } else {
                                val delta = change.positionChangeIgnoreConsumed()
                                netDx += delta.x
                                netDy += delta.y
                                if (abs(netDx) > touchSlop && abs(netDx) > abs(netDy)) {
                                    dragging = true
                                    dragged = 0f
                                    change.consume()
                                }
                            }
                        }
                        settle()
                    }
                }
        )
    }
}

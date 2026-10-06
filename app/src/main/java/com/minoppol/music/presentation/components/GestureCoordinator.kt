package com.minoppol.music.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

fun Modifier.pagerGestureCoordinator(): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { change ->
                if (change.isConsumed) {
                    change.consume()
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

fun Modifier.verticalDragGate(onVerticalDrag: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var triggered = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    if (!triggered) {
                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) {
                            triggered = true
                            onVerticalDrag(true)
                        }
                    }
                    if (event.changes.none { it.pressed }) break
                }
            } finally {
                if (triggered) onVerticalDrag(false)
            }
        }
    }

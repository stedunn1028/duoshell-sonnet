package com.duoshell.sonnet.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Stable callback holder. The pointer-input coroutine is keyed on this object (so a drag is never
 * restarted by recomposition) while the fields are refreshed on every composition.
 */
class IconHandlers {
    var onTap: (Offset) -> Unit = {}
    var onLongPress: () -> Unit = {}
    var onDragStart: () -> Unit = {}
    var onDrag: (local: Offset) -> Unit = {}
    var onDragEnd: () -> Unit = {}
}

/**
 * Tap / long-press / long-press-then-drag in one detector, so the same touch can open a context
 * menu and, if the finger keeps moving, turn into a drag (iOS-style).
 * Quick swipes are NOT consumed, so the parent pager still scrolls when you swipe across icons.
 */
fun Modifier.iconGestures(h: IconHandlers): Modifier = pointerInput(h) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var outcome: Boolean? = null // true = tap, false = cancelled / scrolling
        val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (outcome == null) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    outcome = false
                } else if (change.changedToUpIgnoreConsumed()) {
                    change.consume(); outcome = true
                } else if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    outcome = false
                }
            }
            true
        }
        if (finished != null) {
            if (outcome == true) h.onTap(down.position)
            return@awaitEachGesture
        }
        // long press reached
        h.onLongPress()
        var started = false
        var total = Offset.Zero
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUpIgnoreConsumed() || !change.pressed) { change.consume(); break }
            val delta = change.positionChange()
            if (delta != Offset.Zero) {
                total += delta
                if (!started && total.getDistance() > viewConfiguration.touchSlop) { started = true; h.onDragStart() }
                if (started) h.onDrag(change.position)
                change.consume()
            }
        }
        if (started) h.onDragEnd()
    }
}

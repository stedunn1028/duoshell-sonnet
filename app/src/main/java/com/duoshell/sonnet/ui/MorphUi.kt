package com.duoshell.sonnet.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.duoshell.sonnet.core.Curve
import com.duoshell.sonnet.core.FRect
import com.duoshell.sonnet.core.Morph
import com.duoshell.sonnet.core.Place
import com.duoshell.sonnet.core.Resolved
import kotlin.math.roundToInt

private val IDENT = Resolved(0f, 0f, 1f, 1f, 1f)

/**
 * Drives keyed shared-element transitions. Every element is laid out ONCE at its final rect and
 * the animation is applied purely in the draw/graphics layer (translation, scale, alpha), so a
 * fold transition never triggers re-composition or re-layout per frame.
 */
@Stable
class MorphState {
    var progress by mutableFloatStateOf(1f)
    var active = false
    var from: Map<String, Place> = emptyMap()
    var curve: Curve = Curve.FAST_OUT_SLOW_IN
    var stagger = 0.2f
    var originX = 0f
    var originY = 0f
    var maxDist = 1f
    var blurDp = 0f
    var pending = false
    var durationMs = 0
    var label = ""
    var ghosts by mutableStateOf<List<Pair<String, Place>>>(emptyList())

    fun resolve(key: String, to: Place): Resolved {
        val p = progress
        if (!active || p >= 1f) return IDENT
        val delay = Morph.delayFor(to.rect.cx to to.rect.cy, originX to originY, maxDist, stagger)
        return Morph.resolve(from[key], to, p, delay, curve)
    }

    /** Where each element is drawn right now (used to restart a morph mid-flight without a jump). */
    fun snapshotVisual(places: Map<String, Place>): Map<String, Place> {
        if (!active || progress >= 1f) return places
        val out = LinkedHashMap<String, Place>()
        for ((k, to) in places) {
            val r = resolve(k, to)
            out[k] = Place(FRect.center(to.rect.cx + r.dx, to.rect.cy + r.dy, to.rect.w * r.sx, to.rect.h * r.sy), to.ref * r.sx, to.uniform)
        }
        return out
    }
}

@Composable
fun MorphBox(
    morph: MorphState,
    key: String,
    to: Place,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val d = LocalDensity.current
    Box(
        modifier
            .offset { IntOffset(to.rect.l.roundToInt(), to.rect.t.roundToInt()) }
            .size(with(d) { to.rect.w.toDp() }, with(d) { to.rect.h.toDp() })
            .graphicsLayer {
                val r = morph.resolve(key, to)
                translationX = r.dx; translationY = r.dy
                scaleX = r.sx; scaleY = r.sy
                alpha = r.alpha
            },
        content = content,
    )
}

/** An element that existed in the old layout but not the new one: shrinks and fades out in place. */
@Composable
fun MorphGhost(morph: MorphState, place: Place, content: @Composable BoxScope.() -> Unit) {
    val d = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(place.rect.l.roundToInt(), place.rect.t.roundToInt()) }
            .size(with(d) { place.rect.w.toDp() }, with(d) { place.rect.h.toDp() })
            .graphicsLayer {
                val r = Morph.resolve(place, null, morph.progress, 0f, morph.curve)
                scaleX = r.sx; scaleY = r.sy; alpha = r.alpha
            },
        content = content,
    )
}

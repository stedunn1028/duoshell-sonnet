package com.duoshell.sonnet.core

import kotlin.math.hypot
import kotlin.math.max

/** Where an element sits: its rect, and its visual reference size (icon edge) for uniform scaling. */
data class Place(val rect: FRect, val ref: Float, val uniform: Boolean = true)

/** Result of resolving an element at morph progress t: translate / scale about centre, plus alpha. */
data class Resolved(val dx: Float, val dy: Float, val sx: Float, val sy: Float, val alpha: Float)

enum class Curve { FAST_OUT_SLOW_IN, OVERSHOOT }

object Morph {
    /** Map rects from an old viewport to the new one: fit-and-centre, with an optional scale cap. */
    class Viewport(val scale: Float, val dx: Float, val dy: Float) {
        fun map(p: Place) = Place(
            FRect.center(
                p.rect.cx * scale + dx, p.rect.cy * scale + dy, p.rect.w * scale, p.rect.h * scale,
            ),
            p.ref * scale, p.uniform,
        )
    }

    fun fit(oldW: Float, oldH: Float, newW: Float, newH: Float, densityRatio: Float = 1f): Viewport {
        val fitS = minOf(newW / oldW, newH / oldH)
        val s = minOf(fitS, densityRatio).coerceAtLeast(0.2f)
        return Viewport(s, (newW - oldW * s) / 2f, (newH - oldH * s) / 2f)
    }

    fun cubic(x: Float): Float { // smooth ease in/out
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun easeOutQuint(x: Float): Float {
        val t = 1f - x.coerceIn(0f, 1f)
        return 1f - t * t * t * t * t
    }

    fun overshoot(x: Float, tension: Float = 1.4f): Float {
        val t = x.coerceIn(0f, 1f) - 1f
        return t * t * ((tension + 1f) * t + tension) + 1f
    }

    fun ease(curve: Curve, x: Float) = if (curve == Curve.OVERSHOOT) overshoot(x) else easeOutQuint(x)

    /**
     * Resolve one element.
     *  - [from] null  -> element is new: fades/scales in from its own target.
     *  - [to]   null  -> element is leaving (ghost): drifts toward [origin] and fades out.
     * [delayFrac] staggers elements (0..~0.3 of the timeline).
     */
    fun resolve(
        from: Place?, to: Place?, t: Float, delayFrac: Float, curve: Curve,
    ): Resolved {
        val lt = ((t - delayFrac) / (1f - delayFrac).coerceAtLeast(0.05f)).coerceIn(0f, 1f)
        val e = ease(curve, lt)
        if (from != null && to != null) {
            val sx0: Float
            val sy0: Float
            if (to.uniform) {
                val s = if (to.ref > 0.01f) from.ref / to.ref else 1f
                sx0 = s; sy0 = s
            } else {
                sx0 = if (to.rect.w > 0.01f) from.rect.w / to.rect.w else 1f
                sy0 = if (to.rect.h > 0.01f) from.rect.h / to.rect.h else 1f
            }
            val dx0 = from.rect.cx - to.rect.cx
            val dy0 = from.rect.cy - to.rect.cy
            // alpha: stay opaque while moving between two known places
            return Resolved(dx0 * (1f - e), dy0 * (1f - e), lerp(sx0, 1f, e), lerp(sy0, 1f, e), 1f)
        }
        if (to != null) {
            // entering element: grow in from 0.8x with a soft fade, late in the timeline
            val a = Morph.cubic(((t - 0.25f) / 0.6f))
            val s = lerp(0.82f, 1f, e)
            return Resolved(0f, 0f, s, s, a)
        }
        if (from != null) {
            val a = 1f - Morph.cubic(t / 0.55f)
            val s = lerp(1f, 0.85f, Morph.cubic(t))
            return Resolved(0f, 0f, s, s, a)
        }
        return Resolved(0f, 0f, 1f, 1f, 1f)
    }

    fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

    /** Stagger delay: elements farther from [origin] start later (ripple out from hinge / dock). */
    fun delayFor(center: Pair<Float, Float>, origin: Pair<Float, Float>, maxDist: Float, maxDelay: Float): Float {
        if (maxDist <= 1f) return 0f
        val dist = hypot(center.first - origin.first, center.second - origin.second)
        return (dist / maxDist).coerceIn(0f, 1f) * maxDelay
    }

    /** Duration (ms) per animation-intensity setting. 0 = no animation. */
    fun durationMs(intensity: Int) = when (intensity) { 0 -> 0; 1 -> 360; 3 -> 820; else -> 580 }
    fun staggerFor(intensity: Int) = when (intensity) { 0 -> 0f; 1 -> 0.10f; 3 -> 0.30f; else -> 0.20f }
    fun curveFor(intensity: Int) = if (intensity >= 3) Curve.OVERSHOOT else Curve.FAST_OUT_SLOW_IN
    fun blurRadiusDp(intensity: Int) = when (intensity) { 0, 1 -> 0f; 2 -> 10f; else -> 16f }
}

object Placements {
    /**
     * Keyed element placements for the CURRENT spread. Keys are stable identities:
     *   app:<key>, dock:<key>, dock:panel, island, pill, dots, pane:today, pane:library
     * This is a pure function of geometry + state, so the previous layout can be rebuilt at any time
     * (used both for live fold transitions and for the Activity-recreation fallback).
     */
    fun compute(
        geo: Geo,
        spread: Int,
        panesTotal: Int,
        pageKeys: (Int) -> List<String>, // page index -> app keys
        dockKeys: List<String>,
        pageCount: Int,
    ): Map<String, Place> {
        val out = LinkedHashMap<String, Place>()
        val inSpread = Paging.panesInSpread(spread, geo.duo, panesTotal)
        inSpread.forEachIndexed { slotInSpread, pane ->
            val gi = slotInSpread.coerceAtMost(geo.grids.size - 1)
            val grid = geo.grids[gi]
            when (val k = Paging.kind(pane, pageCount)) {
                is PaneKind.Today -> out["pane:today"] = Place(grid.pane, 0f, uniform = false)
                is PaneKind.Library -> out["pane:library"] = Place(grid.pane, 0f, uniform = false)
                is PaneKind.Page -> pageKeys(k.index).forEachIndexed { slot, key ->
                    if (slot < grid.cols * grid.rows) out["app:$key"] = Place(grid.cellRect(slot), grid.icon)
                }
            }
        }
        out["dock:panel"] = Place(geo.dockRect, 0f, uniform = false)
        dockKeys.forEachIndexed { i, key ->
            if (i < geo.dockSlots.size) out["dock:$key"] = Place(geo.dockSlots[i], geo.dockIcon)
        }
        out["island"] = Place(geo.island, geo.island.w)
        out["pill"] = Place(geo.pill, 0f, uniform = false)
        out["dots"] = Place(geo.dots, 0f, uniform = false)
        return out
    }

    fun maxDist(geo: Geo) = max(1f, hypot(geo.width, geo.height))
}

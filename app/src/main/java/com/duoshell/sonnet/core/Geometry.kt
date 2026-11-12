package com.duoshell.sonnet.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Pure geometry (no Compose / Android types) so the fold layout maths is unit-testable. */

data class FRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w: Float get() = r - l
    val h: Float get() = b - t
    val cx: Float get() = (l + r) / 2f
    val cy: Float get() = (t + b) / 2f
    fun contains(x: Float, y: Float) = x >= l && x < r && y >= t && y < b
    fun inset(d: Float) = FRect(l + d, t + d, r - d, b - d)
    fun shifted(dx: Float, dy: Float) = FRect(l + dx, t + dy, r + dx, b + dy)
    companion object {
        fun xywh(x: Float, y: Float, w: Float, h: Float) = FRect(x, y, x + w, y + h)
        fun center(cx: Float, cy: Float, w: Float, h: Float) = FRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }
}

data class EdgeInsets(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f)

/** Fold hinge in window pixels. [vertical] = the hinge line runs top-to-bottom (book posture). */
data class Hinge(val bounds: FRect, val vertical: Boolean, val halfOpened: Boolean, val separating: Boolean)

data class LayoutInput(
    val width: Float,
    val height: Float,
    val insets: EdgeInsets,
    val density: Float,
    val fontScale: Float,
    val dockPlacement: DockPlacement,
    val dockSide: DockSide,
    val gridDensity: Int,
    val dockCount: Int,
    val showLabels: Boolean,
    val hinge: Hinge?,
)

class GridSpec(
    val pane: FRect,
    val cols: Int,
    val rows: Int,
    val cellW: Float,
    val cellH: Float,
    val icon: Float,
) {
    /** Rect for the whole cell content (icon + label), centred in its cell. Used as the morph rect. */
    fun cellRect(slot: Int): FRect {
        val c = slot % cols
        val r = slot / cols
        return FRect.xywh(pane.l + c * cellW, pane.t + r * cellH, cellW, cellH)
    }

    /** Slot under a point (window px), or -1 outside the grid. */
    fun slotAt(x: Float, y: Float): Int {
        if (x < pane.l || x >= pane.l + cols * cellW || y < pane.t || y >= pane.t + rows * cellH) return -1
        val c = ((x - pane.l) / cellW).toInt().coerceIn(0, cols - 1)
        val r = ((y - pane.t) / cellH).toInt().coerceIn(0, rows - 1)
        return r * cols + c
    }
}

class Geo(
    val input: LayoutInput,
    val duo: Boolean,
    val stacked: Boolean,
    val dockVertical: Boolean,
    val dockRight: Boolean,
    val dockRect: FRect,
    val dockIcon: Float,
    val dockSlots: List<FRect>,
    val panes: List<FRect>,
    val grids: List<GridSpec>,
    val island: FRect,
    val islandRight: Boolean,
    val pill: FRect,
    val dots: FRect,
    val gutter: FRect?,
    val labelH: Float,
) {
    val width get() = input.width
    val height get() = input.height
}

object GeometryEngine {
    const val DUO_MIN_SHORT_DP = 560f

    fun isDuo(w: Float, h: Float, density: Float) = min(w, h) / density >= DUO_MIN_SHORT_DP

    fun compute(inp: LayoutInput): Geo {
        val d = inp.density
        val w = inp.width
        val h = inp.height
        val ins = inp.insets
        val duo = isDuo(w, h, d)
        val landscape = w > h
        val stacked = duo && inp.hinge != null && !inp.hinge.vertical

        val dockVertical = when (inp.dockPlacement) {
            DockPlacement.SIDE -> true
            DockPlacement.BOTTOM -> false
            DockPlacement.AUTO -> false // iOS-style floating bottom dock, wider when unfolded
        }
        val dockRight = inp.dockSide == DockSide.RIGHT
        val islandRight = false

        val margin = 10f * d
        val pad = 10f * d
        val gap = 8f * d
        val n = max(1, inp.dockCount)
        var dockIcon = (if (duo) 58f else 52f) * d
        if (dockVertical) {
            // shrink dock icons if a tall dock would not fit (e.g. folded landscape)
            val avail = h - ins.top - ins.bottom - 24f * d
            val fitIcon = (avail - 2 * pad - (n - 1) * gap) / n
            dockIcon = min(dockIcon, max(fitIcon, 36f * d))
        }
        val dockThick = dockIcon + 2 * pad
        val dockLong = n * dockIcon + (n - 1) * gap + 2 * pad

        // ---- reserved edges
        val islandW = 124f * d
        val islandH = 34f * d
        var resL = ins.left + 6f * d
        var resR = ins.right + 6f * d
        val island = FRect.xywh(w / 2f - islandW / 2f, ins.top + 6f * d, islandW, islandH)
        var resT = island.b + 10f * d
        var resB = ins.bottom + 6f * d

        val dockRect: FRect
        if (dockVertical) {
            val x = if (dockRight) w - ins.right - margin - dockThick else ins.left + margin
            val availT = ins.top + 8f * d
            val availB = h - ins.bottom - 8f * d
            val len = min(dockLong, availB - availT)
            val cy = (availT + availB) / 2f
            dockRect = FRect.xywh(x, cy - len / 2f, dockThick, len)
            if (dockRight) resR += dockThick + margin * 1.4f else resL += dockThick + margin * 1.4f
        } else {
            val len = min(dockLong, w - ins.left - ins.right - 2 * margin)
            val y = h - ins.bottom - margin - dockThick
            val cx = (ins.left + (w - ins.right)) / 2f
            dockRect = FRect.xywh(cx - len / 2f, y, len, dockThick)
            resB += dockThick + margin * 1.4f
        }

        val dockSlots = ArrayList<FRect>(n)
        for (i in 0 until n) {
            dockSlots += if (dockVertical) {
                val startY = dockRect.cy - (n * dockIcon + (n - 1) * gap) / 2f
                FRect.xywh(dockRect.cx - dockIcon / 2f, startY + i * (dockIcon + gap), dockIcon, dockIcon)
            } else {
                val startX = dockRect.cx - (n * dockIcon + (n - 1) * gap) / 2f
                FRect.xywh(startX + i * (dockIcon + gap), dockRect.cy - dockIcon / 2f, dockIcon, dockIcon)
            }
        }

        // ---- bottom controls (page dots + search pill) sit above the dock / nav bar, below the panes
        val dotsH = 14f * d
        val pillH = 34f * d
        val controlsH = dotsH + pillH + 6f * d
        val contentL = resL
        val contentR = w - resR
        val contentT = resT
        val controlsBottom = h - resB
        val contentB = controlsBottom - controlsH
        val cxContent = (contentL + contentR) / 2f
        val pill = FRect.xywh(cxContent - 62f * d, controlsBottom - pillH, 124f * d, pillH)
        val dots = FRect.xywh(cxContent - 60f * d, pill.t - 4f * d - dotsH, 120f * d, dotsH)

        // ---- panes
        val content = FRect(contentL, contentT, contentR, contentB)
        val panes = ArrayList<FRect>()
        var gutterRect: FRect? = null
        if (!duo) {
            panes += content
        } else {
            val hb = inp.hinge?.bounds
            val minGutter = 28f * d
            if (!stacked) {
                var hx = hb?.cx ?: ((w) / 2f)
                val g = max(minGutter, (hb?.w ?: 0f).let { if (inp.hinge?.separating == true) it else 0f })
                val minPane = 160f * d
                if (hx - g / 2 - content.l < minPane || content.r - (hx + g / 2) < minPane) hx = content.cx
                panes += FRect(content.l, content.t, hx - g / 2f, content.b)
                panes += FRect(hx + g / 2f, content.t, content.r, content.b)
                gutterRect = FRect(hx - g / 2f, content.t, hx + g / 2f, content.b)
            } else {
                var hy = hb?.cy ?: (content.cy)
                val g = max(minGutter, (hb?.h ?: 0f).let { if (inp.hinge?.separating == true) it else 0f })
                val minPane = 120f * d
                if (hy - g / 2 - content.t < minPane || content.b - (hy + g / 2) < minPane) hy = content.cy
                panes += FRect(content.l, content.t, content.r, hy - g / 2f)
                panes += FRect(content.l, hy + g / 2f, content.r, content.b)
                gutterRect = FRect(content.l, hy - g / 2f, content.r, hy + g / 2f)
            }
        }

        // ---- grid selection: fixed capacity, best (cols x rows) factor pair for the pane shape.
        val cap = Settings.capacityFor(inp.gridDensity)
        val labelH = if (inp.showLabels) 11f * inp.fontScale * d * 1.35f else 0f
        val labelGap = if (inp.showLabels) 5f * d else 0f
        val grids = chooseGrids(panes, cap, labelH + labelGap, d)


        return Geo(
            inp, duo, stacked, dockVertical, dockRight, dockRect, dockIcon, dockSlots,
            panes, grids, island, islandRight, pill, dots, gutterRect, labelH + labelGap,
        )
    }

    private fun candidateCols(cap: Int): List<Int> =
        (3..8).filter { cap % it == 0 && cap / it >= 3 }

    private fun iconFor(pane: FRect, cols: Int, rows: Int, labelBlock: Float, d: Float): Float {
        val cw = pane.w / cols
        val ch = pane.h / rows
        val byW = cw * 0.70f
        val byH = ch - labelBlock - 10f * d
        return min(min(byW, byH), 68f * d)
    }

    fun chooseGrids(panes: List<FRect>, cap: Int, labelBlock: Float, d: Float): List<GridSpec> {
        val cands = candidateCols(cap)
        var bestCols = cands.first()
        var bestIcon = -1f
        for (c in cands) {
            val rows = cap / c
            val icon = panes.minOf { iconFor(it, c, rows, labelBlock, d) }
            // prefer larger icons; near-ties prefer the narrower grid (fewer columns)
            if (icon > bestIcon + 0.5f * d) { bestIcon = icon; bestCols = c }
        }
        val rows = cap / bestCols
        val icon = max(bestIcon, 28f * d)
        return panes.map { p ->
            GridSpec(p, bestCols, rows, p.w / bestCols, p.h / rows, icon)
        }
    }
}

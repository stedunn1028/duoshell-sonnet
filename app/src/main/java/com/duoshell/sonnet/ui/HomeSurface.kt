package com.duoshell.sonnet.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duoshell.sonnet.FoldStats
import com.duoshell.sonnet.LauncherController
import com.duoshell.sonnet.MenuState
import com.duoshell.sonnet.Panel
import com.duoshell.sonnet.core.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Window description handed in by Root (real window or the scaled developer preview). */
class WindowSpec(
    val w: Float, val h: Float, val insets: EdgeInsets, val density: Float, val fontScale: Float, val hinge: Hinge?,
)

/** Maps virtual (layout) px to real screen px. Real display: fit = 1, t = 0. */
class ViewParams(val fit: Float, val tx: Float, val ty: Float, val dev: Boolean)

private class Prev {
    var places: Map<String, Place>? = null
    var input: LayoutInput? = null
    var spread: Int = -1
    var view: ViewParams? = null
}

private enum class MorphKind { NONE, LAYOUT, FOLD }

@Composable
fun HomeSurface(c: LauncherController, win: WindowSpec, view: ViewParams, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val d = win.density
    val settings = c.settings
    val homeKeys = c.homeKeys
    val dockKeys = c.dockKeys
    val cap = settings.pageCapacity

    val input = LayoutInput(
        win.w, win.h, win.insets, d, win.fontScale, settings.dockPlacement, settings.dockSide,
        settings.gridDensity, max(1, dockKeys.size), settings.showLabels, win.hinge,
    )
    val geo = remember(input) { GeometryEngine.compute(input) }
    val pageCount = Paging.pageCount(homeKeys.size, cap, c.editing)
    val paneTotal = Paging.paneCount(pageCount)
    val focus = settings.focusPane.coerceIn(0, paneTotal - 1)
    val spread = Paging.spreadForFocus(focus, geo.duo, paneTotal)
    val maxSpread = Paging.spreadCount(paneTotal, geo.duo) - 1
    val pk: (Int) -> List<String> = { p -> Paging.pageKeys(homeKeys, p, cap) }

    val places = remember(geo, spread, homeKeys, dockKeys, pageCount) {
        Placements.compute(geo, spread, paneTotal, pk, dockKeys, pageCount)
    }

    // ---------- morph orchestration (runs in composition so the first frame is never the final layout)
    val morph = remember { MorphState() }
    val prev = remember { Prev() }
    val intensity = settings.animationIntensity
    val token = remember(places) {
        beginMorph(c, morph, prev, input, geo, places, spread, view, intensity, pageCount, homeKeys, dockKeys)
    }
    val refreshRate = LocalView.current.display?.refreshRate ?: 60f
    LaunchedEffect(token) {
        if (!morph.pending) return@LaunchedEffect
        morph.pending = false
        val dur = morph.durationMs
        if (dur <= 0) { morph.active = false; morph.progress = 1f; return@LaunchedEffect }
        val isFold = morph.label == "fold"
        val times = ArrayList<Long>(128)
        val sampler = if (isFold) launch { while (isActive) withFrameNanos { times.add(it) } } else null
        animate(0f, 1f, animationSpec = tween(dur, easing = LinearEasing)) { v, _ -> morph.progress = v }
        sampler?.cancelAndJoin()
        morph.active = false
        morph.progress = 1f
        if (isFold && times.size > 3) {
            val deltas = (1 until times.size).map { (times[it] - times[it - 1]) / 1_000_000f }.sorted()
            val budget = 1000f / refreshRate
            c.lastFoldStats = FoldStats(
                frames = deltas.size, avgMs = deltas.average().toFloat(),
                p95Ms = deltas[((deltas.size - 1) * 0.95f).roundToInt()], maxMs = deltas.last(),
                slow = deltas.count { it > budget * 1.5f }, durationMs = dur, label = "${refreshRate.roundToInt()} Hz",
            )
        }
    }
    LaunchedEffect(input, focus) { c.saveMemo(input, focus) }

    // ---------- paging gesture state
    val scope = rememberCoroutineScope()
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var dragSource by remember { mutableStateOf("home") }
    var edgeSide by remember { mutableIntStateOf(0) }

    fun gotoSpread(s: Int) {
        val ns = s.coerceIn(0, maxSpread)
        c.setFocus(Paging.focusForSpread(ns, geo.duo, paneTotal))
    }

    LaunchedEffect(edgeSide, dragKey) {
        if (edgeSide != 0 && dragKey != null) {
            kotlinx.coroutines.delay(650)
            if (dragKey != null && edgeSide != 0) { gotoSpread(spread + edgeSide); edgeSide = 0 }
        }
    }

    val wobble: State<Float>? = if (c.editing) {
        rememberInfiniteTransition(label = "wobble").animateFloat(
            -1.7f, 1.7f, infiniteRepeatable(tween(125, easing = LinearEasing), RepeatMode.Reverse), label = "wobble",
        )
    } else null

    fun settle(v: Float) {
        val w = geo.width
        val target = when {
            (dragX > w * 0.20f || v > 900f) && spread > 0 -> 1
            (dragX < -w * 0.20f || v < -900f) && spread < maxSpread -> -1
            else -> 0
        }
        scope.launch {
            animate(dragX, target * w, animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { x, _ -> dragX = x }
            if (target != 0) gotoSpread(spread - target)
            dragX = 0f
        }
    }

    fun dropIcon(key: String, pos: Offset) {
        if (geo.dockRect.inset(-14f * d).contains(pos.x, pos.y)) {
            var idx = 0
            var best = Float.MAX_VALUE
            geo.dockSlots.forEachIndexed { i, r ->
                val dd = hypot(pos.x - r.cx, pos.y - r.cy)
                if (dd < best) { best = dd; idx = i }
            }
            c.pinToDock(key, idx)
            return
        }
        val inSpread = Paging.panesInSpread(spread, geo.duo, paneTotal)
        inSpread.forEachIndexed { i, pane ->
            val grid = geo.grids[min(i, geo.grids.size - 1)]
            val k = Paging.kind(pane, pageCount)
            if (k is PaneKind.Page) {
                val slot = grid.slotAt(pos.x, pos.y)
                if (slot >= 0) { c.reorderHome(key, k.index * cap + slot); return }
            }
        }
    }

    fun toReal(r: FRect) = FRect(r.l * view.fit + view.tx, r.t * view.fit + view.ty, r.r * view.fit + view.tx, r.b * view.fit + view.ty)

    fun handlers(h: IconHandlers, key: String, rect: FRect, source: String, iconTop: Float, iconLeft: Float) {
        h.onTap = { pos ->
            if (c.editing) {
                val hit = 30f * d
                if (source == "home" && pos.x < iconLeft + hit && pos.y < iconTop + hit) c.removeFromHome(key)
                else if (source == "dock" && pos.x < iconLeft + hit && pos.y < iconTop + hit) c.unpinFromDock(key)
            } else {
                val r = toReal(rect)
                c.launch(key, android.graphics.Rect(r.l.roundToInt(), r.t.roundToInt(), r.r.roundToInt(), r.b.roundToInt()))
            }
        }
        h.onLongPress = { if (!c.editing) c.menu = MenuState(key, source, toReal(rect)) }
        h.onDragStart = { c.menu = null; c.editing = true; dragKey = key; dragSource = source }
        h.onDrag = { local ->
            val p = Offset(rect.l + local.x, rect.t + local.y)
            dragPos = p
            edgeSide = when { p.x < 28f * d -> -1; p.x > geo.width - 28f * d -> 1; else -> 0 }
        }
        h.onDragEnd = { val k = dragKey; dragKey = null; edgeSide = 0; if (k != null) dropIcon(k, dragPos) }
    }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(spread, maxSpread, geo.width) {
                val tracker = VelocityTracker()
                var acc = Offset.Zero
                var lock = 0
                detectDragGestures(
                    onDragStart = { tracker.resetTracking(); acc = Offset.Zero; lock = 0 },
                    onDrag = { change, amt ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        acc += amt
                        if (lock == 0) lock = if (abs(acc.x) >= abs(acc.y)) 1 else 2
                        if (lock == 1) {
                            val next = dragX + amt.x
                            val atStart = spread == 0 && next > 0
                            val atEnd = spread == maxSpread && next < 0
                            dragX = if (atStart || atEnd) dragX + amt.x * 0.35f else next
                        }
                    },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x
                        if (lock == 1) settle(v)
                        else if (lock == 2 && abs(acc.y) > 70f * d && !c.editing) c.panel = Panel.SEARCH
                    },
                    onDragCancel = { settle(0f) },
                )
            },
    ) {
        // everything that should blur/frost during a fold transition
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val b = if (morph.active && morph.label == "fold") morph.blurDp * d * (1f - Morph.cubic(morph.progress)) else 0f
                if (b > 0.5f) renderEffect = BlurEffect(b, b, TileMode.Clamp)
            },
        ) {
            // empty-area long press => jiggle mode; tap empty area exits it
            Box(
                Modifier.fillMaxSize().pointerInput(c.editing) {
                    detectTapGestures(
                        onLongPress = { if (!c.editing) c.editing = true },
                        onTap = { if (c.editing) c.editing = false; c.menu = null; c.islandOpen = false },
                    )
                },
            )

            // ---- pages (translate with the pager)
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = dragX }) {
                for ((k, p) in places) {
                    if (k.startsWith("app:")) {
                        val key = k.substring(4)
                        val h = remember(key) { IconHandlers() }
                        val grid = geo.grids[0]
                        val iconLeft = (p.rect.w - p.ref) / 2f
                        val iconTop = (p.rect.h - p.ref - geo.labelH) / 2f
                        handlers(h, key, p.rect, "home", iconTop, iconLeft)
                        MorphBox(morph, k, p, Modifier.iconGestures(h).then(contentDesc(c.appsByKey[key]?.label ?: "App"))) {
                            AppCellBody(c, key, p.ref, p.rect.w, settings.showLabels, c.editing, dragKey == key, wobble, if (c.editing) ({}) else null)
                        }
                    } else if (k == "pane:today") {
                        MorphBox(morph, k, p) { TodayPane(c, Modifier.fillMaxSize()) }
                    } else if (k == "pane:library") {
                        MorphBox(morph, k, p) { LibraryPane(c, Modifier.fillMaxSize()) }
                    }
                }
            }

            // ---- icons that exist only in the previous layout shrink and fade out instead of popping
            if (morph.active) {
                for ((k, p) in morph.from) {
                    if (places.containsKey(k) || k == "dock:panel") continue
                    if (k.startsWith("app:") || k.startsWith("dock:")) {
                        val gk = k.substringAfter(':')
                        MorphGhost(morph, p) { AppCellBody(c, gk, p.ref, p.rect.w, k.startsWith("app:") && settings.showLabels, false, false, null, null) }
                    }
                }
            }

            // ---- neighbouring spreads, only while the finger is paging
            if (dragX != 0f) {
                val nb = remember(geo, spread, homeKeys, pageCount) {
                    listOf(-1, 1).map { off ->
                        val s = spread + off
                        if (s < 0 || s > maxSpread) emptyMap() else
                            Placements.compute(geo, s, paneTotal, pk, emptyList(), pageCount).filterKeys { it.startsWith("app:") || it.startsWith("pane:") }
                    }
                }
                StaticPlaces(c, nb[0], settings.showLabels) { dragX - geo.width }
                StaticPlaces(c, nb[1], settings.showLabels) { dragX + geo.width }
            }

            // ---- dock
            places["dock:panel"]?.let { p ->
                if (dockKeys.isNotEmpty() || c.editing) MorphBox(morph, "dock:panel", p) {
                    GlassSurface(Modifier.fillMaxSize(), shape = androidx.compose.foundation.shape.RoundedCornerShape((min(p.rect.w, p.rect.h) * 0.30f / d).dp), strongShadow = true)
                }
            }
            for ((k, p) in places) {
                if (!k.startsWith("dock:") || k == "dock:panel") continue
                val key = k.substring(5)
                val h = remember(key + "#dock") { IconHandlers() }
                handlers(h, key, p.rect, "dock", 0f, 0f)
                MorphBox(morph, k, p, Modifier.iconGestures(h).then(contentDesc(c.appsByKey[key]?.label ?: "App", "Dock"))) {
                    AppCellBody(c, key, p.ref, p.rect.w, false, c.editing, dragKey == key, wobble, if (c.editing) ({}) else null)
                }
            }

            // ---- search pill + page dots
            places["dots"]?.let { p -> MorphBox(morph, "dots", p) { PageDots(paneTotal, focus, geo.duo) } }
            places["pill"]?.let { p ->
                MorphBox(morph, "pill", p) {
                    if (c.editing) EditDone(c) else SearchPill(c)
                }
            }
        }

        // ---- island sits above the blur layer so it stays crisp during the fold
        DynamicIsland(c, geo, view)

        // ---- icon being dragged
        val dk = dragKey
        if (dk != null) {
            val iconPx = geo.grids[0].icon * 1.12f
            Box(
                Modifier
                    .offset { IntOffset((dragPos.x - iconPx / 2f).roundToInt(), (dragPos.y - iconPx / 2f).roundToInt()) }
                    .size(with(density) { iconPx.toDp() }),
            ) { AppIconImage(c, dk, with(density) { iconPx.toDp() }) }
        }

        // ---- frost veil that clears as the layout expands (draw-phase only: no per-frame recomposition)
        Box(
            Modifier.fillMaxSize().drawBehind {
                if (morph.active && morph.label == "fold") {
                    val a = (1f - Morph.cubic(morph.progress)) * 0.15f
                    if (a > 0.003f) drawRect(Color.White.copy(alpha = a))
                }
            },
        )
    }
}

private fun beginMorph(
    c: LauncherController, morph: MorphState, prev: Prev, input: LayoutInput, geo: Geo,
    places: Map<String, Place>, spread: Int, view: ViewParams, intensity: Int,
    pageCount: Int, homeKeys: List<String>, dockKeys: List<String>,
): Any {
    var old = prev.places
    var oldInput = prev.input
    var oldView = prev.view
    var oldSpread = prev.spread
    var snapshotDone = false

    if (old == null) {
        // Activity/process recreated mid-fold? Rebuild the previous layout from the persisted memo.
        val memo = c.loadMemo()
        if (memo != null && System.currentTimeMillis() - memo.atMs < 8000 && memo.input != input) {
            val og = GeometryEngine.compute(memo.input)
            val cap = Settings.capacityFor(memo.input.gridDensity)
            val pc = Paging.pageCount(homeKeys.size, cap, false)
            val pt = Paging.paneCount(pc)
            val f = memo.focus.coerceIn(0, pt - 1)
            val s = Paging.spreadForFocus(f, og.duo, pt)
            old = Placements.compute(og, s, pt, { p -> Paging.pageKeys(homeKeys, p, cap) }, dockKeys, pc)
            oldInput = memo.input
            oldView = ViewParams(1f, 0f, 0f, false)
            oldSpread = s
            snapshotDone = true
        }
    }

    var kind = MorphKind.NONE
    var from: Map<String, Place> = emptyMap()
    if (old != null && oldInput != null) {
        val windowChanged = abs(oldInput.width - input.width) > 1f || abs(oldInput.height - input.height) > 1f ||
            oldInput.hinge != input.hinge || oldInput.density != input.density || (oldView != null && (oldView.fit != view.fit || oldView.tx != view.tx))
        val visual = if (snapshotDone) old else morph.snapshotVisual(old)
        if (windowChanged) {
            kind = MorphKind.FOLD
            val vp: Morph.Viewport = if (view.dev || (oldView?.dev == true)) {
                val ov = oldView ?: ViewParams(1f, 0f, 0f, false)
                Morph.Viewport(ov.fit / view.fit, (ov.tx - view.tx) / view.fit, (ov.ty - view.ty) / view.fit)
            } else {
                Morph.fit(oldInput.width, oldInput.height, input.width, input.height, input.density / oldInput.density)
            }
            from = visual.mapValues { vp.map(it.value) }
        } else if (oldSpread == spread) {
            kind = MorphKind.LAYOUT
            from = visual
        }
    }

    prev.places = places
    prev.input = input
    prev.spread = spread
    prev.view = view

    val dur = when (kind) {
        MorphKind.FOLD -> Morph.durationMs(intensity)
        MorphKind.LAYOUT -> (Morph.durationMs(intensity) * 0.45f).toInt().coerceAtMost(280)
        MorphKind.NONE -> 0
    }
    if (kind == MorphKind.NONE || dur <= 0) {
        morph.active = false; morph.progress = 1f; morph.pending = false; morph.durationMs = 0; morph.from = emptyMap()
        return Any()
    }
    morph.from = from
    morph.curve = if (kind == MorphKind.FOLD) Morph.curveFor(intensity) else Curve.FAST_OUT_SLOW_IN
    morph.stagger = if (kind == MorphKind.FOLD) Morph.staggerFor(intensity) else 0f
    morph.originX = geo.panes.firstOrNull()?.let { if (geo.duo) (geo.panes[0].r + geo.panes[1].l) / 2f else geo.width / 2f } ?: geo.width / 2f
    morph.originY = geo.height / 2f
    morph.maxDist = hypot(geo.width, geo.height) / 2f
    morph.blurDp = if (kind == MorphKind.FOLD) Morph.blurRadiusDp(intensity) else 0f
    morph.durationMs = dur
    morph.label = if (kind == MorphKind.FOLD) "fold" else "layout"
    morph.active = true
    morph.progress = 0f
    morph.pending = true
    return Any()
}

@Composable
private fun StaticPlaces(c: LauncherController, places: Map<String, Place>, showLabels: Boolean, dx: () -> Float) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().graphicsLayer { translationX = dx() }) {
        for ((k, p) in places) {
            Box(
                Modifier
                    .offset { IntOffset(p.rect.l.roundToInt(), p.rect.t.roundToInt()) }
                    .size(with(density) { p.rect.w.toDp() }, with(density) { p.rect.h.toDp() }),
            ) {
                when {
                    k.startsWith("app:") -> AppCellBody(c, k.substring(4), p.ref, p.rect.w, showLabels, false, false, null, null)
                    k == "pane:today" -> TodayPane(c, Modifier.fillMaxSize())
                    k == "pane:library" -> LibraryPane(c, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun PageDots(total: Int, focus: Int, duo: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val gap = 11.dp.toPx()
        val r = 3.1.dp.toPx()
        val startX = size.width / 2f - (total - 1) * gap / 2f
        for (i in 0 until total) {
            val active = if (duo) (i / 2 == focus / 2) else i == focus
            drawCircle(Color.White.copy(alpha = if (active) 0.95f else 0.38f), if (active) r * 1.18f else r, Offset(startX + i * gap, size.height / 2f))
        }
    }
}

@Composable
private fun SearchPill(c: LauncherController) {
    GlassSurface(Modifier.fillMaxSize().tap({ c.panel = Panel.SEARCH }, "Search apps"), shape = GlassPill) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val t = LocalGlass.current
            BasicText("Search", style = txt(14.sp, t.panelText, FontWeight.Medium))
        }
    }
}

@Composable
private fun EditDone(c: LauncherController) {
    GlassSurface(Modifier.fillMaxSize().tap({ c.editing = false }, "Done editing"), shape = GlassPill, tint = Color(0xFF0A84FF)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText("Done", style = txt(14.sp, Color.White, FontWeight.SemiBold))
        }
    }
}

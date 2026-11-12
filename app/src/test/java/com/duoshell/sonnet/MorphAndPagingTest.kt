package com.duoshell.sonnet

import com.duoshell.sonnet.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MorphAndPagingTest {
    @Test fun focusRoundTripsAcrossFold() {
        val panes = Paging.paneCount(pageCount = 4) // Today, P1..P4, Library = 6
        // folded on Page 2 (pane 2) -> unfold -> spread 1 (Page2 | Page3)
        val spread = Paging.spreadForFocus(2, duo = true, paneCount = panes)
        assertEquals(1, spread)
        assertEquals(listOf(2, 3), Paging.panesInSpread(spread, true, panes))
        // user has not swiped: fold back must keep pane 2, not jump to 3
        assertEquals(2, Paging.spreadForFocus(2, false, panes))
        // after the user swipes to spread 2 and folds, we land on the right-hand pane
        assertEquals(5, Paging.focusForSpread(2, true, panes))
        assertEquals(panes - 1, Paging.focusForSpread(99, true, panes))
    }

    @Test fun lastSpreadMayHaveSinglePane() {
        val panes = Paging.paneCount(2) // 4 panes: [T,P1] [P2,Lib]
        assertEquals(2, Paging.spreadCount(panes, true))
        val p5 = Paging.paneCount(3) // 5 panes -> 3 spreads, last has one pane
        assertEquals(3, Paging.spreadCount(p5, true))
        assertEquals(listOf(4), Paging.panesInSpread(2, true, p5))
    }

    @Test fun kindsAndPageCounts() {
        assertEquals(PaneKind.Today, Paging.kind(0, 3))
        assertEquals(PaneKind.Page(0), Paging.kind(1, 3))
        assertEquals(PaneKind.Library, Paging.kind(4, 3))
        assertEquals(1, Paging.pageCount(0, 24, false))
        assertEquals(2, Paging.pageCount(25, 24, false))
        assertEquals(2, Paging.pageCount(24, 24, true)) // trailing empty page while editing
    }

    @Test fun moveKeepsOrderOtherwise() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("a", "c", "d", "b"), Paging.move(l, "b", 3))
        assertEquals(listOf("d", "a", "b", "c"), Paging.move(l, "d", 0))
        assertEquals(listOf("a", "b", "c", "d"), Paging.move(l, "c", 2))
        assertEquals(listOf("a", "b", "d", "c"), Paging.move(l, "c", 99))
    }

    @Test fun morphEndpointsAreExact() {
        val from = Place(FRect.xywh(0f, 0f, 100f, 100f), 50f)
        val to = Place(FRect.xywh(300f, 400f, 120f, 120f), 60f)
        for (curve in Curve.values()) {
            val end = Morph.resolve(from, to, 1f, 0.2f, curve)
            assertEquals(0f, end.dx, 0.001f); assertEquals(0f, end.dy, 0.001f)
            assertEquals(1f, end.sx, 0.001f); assertEquals(1f, end.alpha, 0.001f)
            val start = Morph.resolve(from, to, 0f, 0.2f, curve)
            assertEquals(from.rect.cx - to.rect.cx, start.dx, 0.001f)
            assertEquals(50f / 60f, start.sx, 0.001f)
        }
    }

    @Test fun enteringFadesInAndLeavingFadesOut() {
        val to = Place(FRect.xywh(0f, 0f, 10f, 10f), 10f)
        assertEquals(0f, Morph.resolve(null, to, 0f, 0f, Curve.FAST_OUT_SLOW_IN).alpha, 0.001f)
        assertEquals(1f, Morph.resolve(null, to, 1f, 0f, Curve.FAST_OUT_SLOW_IN).alpha, 0.001f)
        assertEquals(1f, Morph.resolve(to, null, 0f, 0f, Curve.FAST_OUT_SLOW_IN).alpha, 0.001f)
        assertEquals(0f, Morph.resolve(to, null, 1f, 0f, Curve.FAST_OUT_SLOW_IN).alpha, 0.001f)
    }

    @Test fun viewportFitCentresAndCaps() {
        val v = Morph.fit(904f, 2316f, 1968f, 2184f)
        assertTrue(v.scale < 1f)
        val p = v.map(Place(FRect.xywh(0f, 0f, 904f, 2316f), 100f))
        assertEquals(1968f / 2f, p.rect.cx, 0.5f)
        assertEquals(2184f / 2f, p.rect.cy, 0.5f)
        val shrink = Morph.fit(1968f, 2184f, 904f, 2316f)
        assertEquals(904f / 1968f, shrink.scale, 0.001f)
    }

    @Test fun appIdentityIsPreservedAcrossFoldedAndUnfoldedPlacements() {
        val keys = (1..60).map { "pkg.app$it/Main" }
        val cap = 24
        val pageCount = Paging.pageCount(keys.size, cap, false)
        val panes = Paging.paneCount(pageCount)
        fun inp(w: Float, h: Float) = LayoutInput(w, h, EdgeInsets(0f, 90f, 0f, 70f), 2.8f, 1f,
            DockPlacement.AUTO, DockSide.RIGHT, 1, 5, true, null)
        val folded = GeometryEngine.compute(inp(1080f, 2340f))
        val unfolded = GeometryEngine.compute(inp(2184f, 1968f))
        val pk = { p: Int -> Paging.pageKeys(keys, p, cap) }
        val dock = listOf("d1", "d2", "d3")
        // folded on Page1 (pane 1) vs unfolded spread 0 (Today | Page1): Page1 icons exist in both
        val f = Placements.compute(folded, Paging.spreadForFocus(1, false, panes), panes, pk, dock, pageCount)
        val u = Placements.compute(unfolded, Paging.spreadForFocus(1, true, panes), panes, pk, dock, pageCount)
        for (k in Paging.pageKeys(keys, 0, cap)) {
            assertTrue("app:$k in folded", f.containsKey("app:$k"))
            assertTrue("app:$k in unfolded", u.containsKey("app:$k"))
        }
        assertTrue(u.containsKey("pane:today") && !f.containsKey("pane:today"))
        // ordering: slot i always precedes slot i+1 in reading order (row-major), no random shuffle
        val order = Paging.pageKeys(keys, 0, cap).map { u["app:$it"]!!.rect }
        for (i in 0 until order.size - 1) {
            val a = order[i]; val b = order[i + 1]
            assertTrue(a.t < b.t - 0.1f || (kotlin.math.abs(a.t - b.t) < 0.1f && a.l < b.l))
        }
    }
}

class MoveBeforeTest {
    @Test fun moveBefore() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("c", "a", "b", "d"), Paging.moveBefore(l, "c", "a"))
        assertEquals(listOf("a", "b", "d", "c"), Paging.moveBefore(l, "c", null))
        assertEquals(listOf("a", "b", "d", "c"), Paging.moveBefore(l, "c", "zzz"))
    }
}

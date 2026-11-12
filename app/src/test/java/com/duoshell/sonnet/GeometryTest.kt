package com.duoshell.sonnet

import com.duoshell.sonnet.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    private val d = 2.8f

    private fun input(
        w: Float, h: Float, hinge: Hinge? = null, dock: DockPlacement = DockPlacement.AUTO,
        density: Int = 1, dockCount: Int = 5, font: Float = 1f,
        ins: EdgeInsets = EdgeInsets(0f, 90f, 0f, 70f),
    ) = LayoutInput(w, h, ins, d, font, dock, DockSide.RIGHT, density, dockCount, true, hinge)

    private fun overlaps(a: FRect, b: FRect) = a.l < b.r && b.l < a.r && a.t < b.b && b.t < a.b

    @Test fun foldedPortraitIsSinglePaneBottomDock() {
        val g = GeometryEngine.compute(input(1080f, 2340f))
        assertFalse(g.duo)
        assertEquals(1, g.panes.size)
        assertFalse(g.dockVertical)
        assertTrue(g.dockRect.b <= 2340f - 70f)
        assertFalse(overlaps(g.dockRect, g.panes[0]))
        assertFalse(overlaps(g.island, g.panes[0]))
    }

    @Test fun unfoldedHasTwoPanesAndAvoidsHinge() {
        val hinge = Hinge(FRect(1086f, 0f, 1098f, 1968f), vertical = true, halfOpened = false, separating = false)
        val g = GeometryEngine.compute(input(2184f, 1968f, hinge))
        assertTrue(g.duo)
        assertEquals(2, g.panes.size)
        assertTrue(g.dockVertical)
        val hx = hinge.bounds.cx
        assertTrue("left pane stays left of hinge", g.panes[0].r <= hx)
        assertTrue("right pane stays right of hinge", g.panes[1].l >= hx)
        assertTrue("gutter >= 28dp", g.panes[1].l - g.panes[0].r >= 28f * d - 0.01f)
        for (p in g.panes) { assertFalse(overlaps(p, g.dockRect)); assertFalse(overlaps(p, g.island)) }
    }

    @Test fun stackedWhenHingeIsHorizontal() {
        val hinge = Hinge(FRect(0f, 980f, 2184f, 988f), vertical = false, halfOpened = true, separating = true)
        val g = GeometryEngine.compute(input(2184f, 1968f, hinge))
        assertTrue(g.stacked)
        assertTrue(g.panes[0].b <= hinge.bounds.t + 0.01f)
        assertTrue(g.panes[1].t >= hinge.bounds.b - 0.01f)
    }

    @Test fun capacityIsFixedAcrossFoldStates() {
        for (dens in 0..2) {
            val cap = Settings.capacityFor(dens)
            val f = GeometryEngine.compute(input(1080f, 2340f, density = dens))
            val u = GeometryEngine.compute(input(2184f, 1968f, density = dens))
            assertEquals(cap, f.grids[0].cols * f.grids[0].rows)
            assertEquals(cap, u.grids[0].cols * u.grids[0].rows)
            assertEquals("same columns in both panes", u.grids[0].cols, u.grids[1].cols)
        }
    }

    @Test fun cellsStayInsidePaneAndLargeFontsDoNotOverflow() {
        for (font in listOf(1f, 1.3f, 2f)) {
            val g = GeometryEngine.compute(input(1080f, 2340f, font = font))
            val grid = g.grids[0]
            for (s in 0 until grid.cols * grid.rows) {
                val r = grid.cellRect(s)
                assertTrue(r.l >= grid.pane.l - 0.01f && r.r <= grid.pane.r + 0.01f)
                assertTrue(r.t >= grid.pane.t - 0.01f && r.b <= grid.pane.b + 0.01f)
            }
            assertTrue("icon + label must fit in cell", grid.icon + g.labelH <= grid.cellH + 0.01f)
        }
    }

    @Test fun slotAtInvertsCellRect() {
        val g = GeometryEngine.compute(input(2184f, 1968f))
        val grid = g.grids[1]
        for (s in listOf(0, 5, grid.cols * grid.rows - 1)) {
            val r = grid.cellRect(s)
            assertEquals(s, grid.slotAt(r.cx, r.cy))
        }
        assertEquals(-1, grid.slotAt(-5f, -5f))
    }

    @Test fun sideDockShrinksIconsInShortLandscape() {
        val g = GeometryEngine.compute(input(2340f, 1080f, dockCount = 6, ins = EdgeInsets(0f, 60f, 0f, 60f)))
        assertFalse(g.duo)
        assertTrue(g.dockVertical)
        assertTrue(g.dockRect.t >= 0f && g.dockRect.b <= 1080f)
        assertTrue(g.dockSlots.last().b <= g.dockRect.b + 0.01f)
    }
}

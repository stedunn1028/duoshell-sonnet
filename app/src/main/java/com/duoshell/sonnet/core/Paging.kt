package com.duoshell.sonnet.core

/**
 * Pane model shared by the folded and unfolded layouts.
 *
 * Pane list = [Today, Page1, Page2, ... PageN, Library].
 * Folded shows ONE pane per "spread"; unfolded shows TWO consecutive panes per spread
 * (Today | Page1, Page2 | Page3, ...). The canonical user position is `focus`, a pane index:
 * it is the single value that survives fold / unfold / recreation.
 */
sealed class PaneKind {
    object Today : PaneKind()
    data class Page(val index: Int) : PaneKind()
    object Library : PaneKind()
}

object Paging {
    fun pageCount(appCount: Int, capacity: Int, editing: Boolean): Int {
        val needed = if (editing) appCount + 1 else appCount
        return maxOf(1, (needed + capacity - 1) / capacity)
    }

    /** Home pages are always an even number (min 2) so two-page spreads pair up: [Today|P1] [P2|P3] ... [Pn|Library]. */
    fun pairedPageCount(appCount: Int, capacity: Int, editing: Boolean): Int {
        val pc = pageCount(appCount, capacity, editing)
        return maxOf(2, pc + (pc % 2))
    }

    fun paneCount(pageCount: Int) = pageCount + 2

    fun kind(pane: Int, pageCount: Int): PaneKind = when {
        pane <= 0 -> PaneKind.Today
        pane >= pageCount + 1 -> PaneKind.Library
        else -> PaneKind.Page(pane - 1)
    }

    fun spreadCount(paneCount: Int, duo: Boolean) = if (duo) (paneCount + 1) / 2 else paneCount

    fun spreadForFocus(focus: Int, duo: Boolean, paneCount: Int): Int {
        val f = focus.coerceIn(0, paneCount - 1)
        return if (duo) f / 2 else f
    }

    /** New focus after the user settles on [spread]. Unfolded focuses the right-hand pane. */
    fun focusForSpread(spread: Int, duo: Boolean, paneCount: Int): Int =
        if (duo) minOf(spread * 2 + 1, paneCount - 1) else spread.coerceIn(0, paneCount - 1)

    fun panesInSpread(spread: Int, duo: Boolean, paneCount: Int): List<Int> =
        if (!duo) listOf(spread).filter { it in 0 until paneCount }
        else listOf(spread * 2, spread * 2 + 1).filter { it in 0 until paneCount }

    fun pageKeys(apps: List<String>, page: Int, capacity: Int): List<String> {
        val from = page * capacity
        if (from >= apps.size) return emptyList()
        return apps.subList(from, minOf(apps.size, from + capacity))
    }

    /** Moves [key] to flat position [target] (clamped), keeping everything else in order. */
    fun move(apps: List<String>, key: String, target: Int): List<String> {
        val without = apps.filter { it != key }
        val t = target.coerceIn(0, without.size)
        return without.toMutableList().also { it.add(t, key) }
    }

    /** Moves [key] so it sits immediately before [before] (or at the end when null / not found). */
    fun moveBefore(apps: List<String>, key: String, before: String?): List<String> {
        val without = apps.filter { it != key }.toMutableList()
        val i = if (before == null) -1 else without.indexOf(before)
        if (i < 0) without.add(key) else without.add(i, key)
        return without
    }
}

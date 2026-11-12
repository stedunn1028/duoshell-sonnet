package com.duoshell.sonnet

import com.duoshell.sonnet.core.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test fun settingsRoundTrip() {
        val s = Settings(
            appearance = Appearance.DARK, wallpaper = WallpaperKind.DUSK, gridDensity = 2,
            dockPlacement = DockPlacement.SIDE, dockSide = DockSide.LEFT, animationIntensity = 3,
            seeded = true, homeApps = listOf("a/A", "b/B"), dockApps = listOf("c/C"),
            noteText = "line1\nline \"2\"", focusPane = 3, appWidgetIds = listOf(7, 9),
            scenes = listOf(Scene("s1", "Studio", "♪", 0xFF112233, listOf("a/A"), listOf("c/C"),
                Schedule(true, 0b0011111, 22 * 60, 6 * 60))),
            activeSceneId = "s1", customWallpaperUri = "content://x/y",
        )
        val back = SettingsCodec.decode(SettingsCodec.encode(s))
        assertEquals(s, back)
    }

    @Test fun corruptOrMissingSettingsFallBackToDefaults() {
        assertEquals(Settings(), SettingsCodec.decode(null))
        assertEquals(Settings(), SettingsCodec.decode("{not json"))
        assertEquals(Settings(), SettingsCodec.decode(""))
        val partial = SettingsCodec.decode("{\"gridDensity\": 99, \"animationIntensity\": -4}")
        assertEquals(2, partial.gridDensity)
        assertEquals(0, partial.animationIntensity)
    }

    @Test fun unknownEnumValuesFallBack() {
        val s = SettingsCodec.decode("{\"appearance\":\"NEON\",\"dockPlacement\":\"???\"}")
        assertEquals(Appearance.SYSTEM, s.appearance)
        assertEquals(DockPlacement.AUTO, s.dockPlacement)
    }

    @Test fun scheduleWrapsMidnight() {
        val sch = Schedule(true, 0b1111111, 22 * 60, 6 * 60)
        assertTrue(sch.matches(0, 23 * 60))
        assertTrue(sch.matches(1, 2 * 60))
        assertFalse(sch.matches(1, 12 * 60))
        val weekdays = Schedule(true, 0b0011111, 9 * 60, 17 * 60)
        assertTrue(weekdays.matches(2, 10 * 60))
        assertFalse(weekdays.matches(5, 10 * 60))
        assertFalse(Schedule(false).matches(0, 600))
        // night window belongs to the day it STARTED on: Fri 23:00 -> Sat 02:00 when only Fri enabled
        val fri = Schedule(true, 1 shl 4, 22 * 60, 6 * 60)
        assertTrue(fri.matches(5, 2 * 60))
        assertFalse(fri.matches(6, 2 * 60))
    }

    @Test fun sceneVisibilityKeepsBaseOrder() {
        val base = listOf("a", "b", "c", "d")
        val sc = Scene("x", "X", "x", 0, apps = listOf("d", "b"))
        assertEquals(listOf("b", "d"), SceneLogic.visibleHome(base, sc))
        assertEquals(base, SceneLogic.visibleHome(base, null))
        assertEquals(base, SceneLogic.visibleHome(base, sc.copy(apps = emptyList())))
        assertNull(SceneLogic.scheduled(listOf(sc), 0, 0))
    }

    @Test fun habitSuggestionUsesTimeWindowAndWeekdayClass() {
        val log = ArrayList<LaunchEvent>()
        repeat(5) { log += LaunchEvent("music", 21 * 60 + 10, 1, 1L) }
        repeat(3) { log += LaunchEvent("notes", 21 * 60 + 40, 2, 2L) }
        repeat(9) { log += LaunchEvent("mail", 9 * 60, 1, 3L) }      // wrong time
        repeat(8) { log += LaunchEvent("game", 21 * 60, 6, 4L) }     // weekend
        val s = SceneLogic.suggestApps(log, dow = 3, minuteOfDay = 21 * 60)
        assertEquals(listOf("music", "notes"), s)
        assertEquals("mail", SceneLogic.frequent(log).first())
    }

    @Test fun launchLogRoundTrips() {
        val l = listOf(LaunchEvent("a/B", 5, 1, 99L), LaunchEvent("c/D", 1439, 6, 100L))
        assertEquals(l, LaunchLogCodec.decode(LaunchLogCodec.encode(l)))
        assertEquals(emptyList<LaunchEvent>(), LaunchLogCodec.decode("garbage"))
    }
}

class LibraryTest {
    @Test fun categorizer() {
        assertEquals("Games", Categorizer.categorize("com.x.y", "Foo", 0))
        assertEquals("Social", Categorizer.categorize("com.whatsapp", "WhatsApp", -1))
        assertEquals("Essentials", Categorizer.categorize("com.samsung.android.dialer", "Phone", -1))
        assertEquals("Creativity", Categorizer.categorize("com.sec.android.gallery3d", "Gallery", -1))
        assertEquals("Utilities", Categorizer.categorize("com.random.thing", "Zzz", -1))
    }

    @Test fun searchRanksPrefixThenWordThenFuzzy() {
        val items = listOf(
            Triple("1", "Spotify", "com.spotify.music"), Triple("2", "Chrome", "com.android.chrome"),
            Triple("3", "My Spot", "com.my.spot"), Triple("4", "Samsung Internet", "com.sec.android.app.sbrowser"),
        )
        assertEquals(listOf("1", "3"), SearchLogic.rank("spo", items))
        assertEquals(listOf("4"), SearchLogic.rank("inter", items))
        assertEquals(listOf("2"), SearchLogic.rank("chm", items)) // subsequence
        assertEquals(emptyList<String>(), SearchLogic.rank("   ", items))
        val two = listOf(Triple("a", "Spot", "x.a"), Triple("b", "Spotify", "x.b"))
        assertEquals(listOf("a", "b"), SearchLogic.rank("spo", two))
        assertEquals(listOf("b", "a"), SearchLogic.rank("spo", two, mapOf("b" to 50))) // frequency bonus
    }

    @Test fun layoutMemoRoundTrip() {
        val i = LayoutInput(2184f, 1968f, EdgeInsets(0f, 90f, 12f, 70f), 2.8f, 1.15f, DockPlacement.SIDE, DockSide.LEFT, 2, 4, true,
            Hinge(FRect(1086f, 0f, 1098f, 1968f), true, false, false))
        val m = LayoutMemo.decode(LayoutMemo.encode(i, 3, 12345L))!!
        assertEquals(i, m.input); assertEquals(3, m.focus); assertEquals(12345L, m.atMs)
        assertNull(LayoutMemo.decode("junk;1"))
        val noHinge = i.copy(hinge = null)
        assertEquals(noHinge, LayoutMemo.decode(LayoutMemo.encode(noHinge, 0, 1L))!!.input)
    }
}

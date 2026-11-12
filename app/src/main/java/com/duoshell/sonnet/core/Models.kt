package com.duoshell.sonnet.core

import org.json.JSONArray
import org.json.JSONObject

/** Pure (Android-free) settings model so it can be unit-tested on the JVM. */

enum class Appearance { SYSTEM, LIGHT, DARK }
enum class DockPlacement { AUTO, SIDE, BOTTOM }
enum class DockSide { LEFT, RIGHT }
enum class WallpaperKind(val label: String) {
    SYSTEM("System"), AURORA("Aurora"), DUSK("Dusk"), LAGOON("Lagoon"),
    EMBER("Ember"), GRAPHITE("Graphite"), MEADOW("Meadow"), CUSTOM("Photo")
}

/** Days are bit0 = Monday ... bit6 = Sunday. Minutes are minutes since local midnight. */
data class Schedule(
    val enabled: Boolean = false,
    val daysMask: Int = 0b1111111,
    val startMin: Int = 9 * 60,
    val endMin: Int = 17 * 60,
) {
    /** True if [dow] (0 = Monday) at [minuteOfDay] falls in the window. Windows may wrap midnight. */
    fun matches(dow: Int, minuteOfDay: Int): Boolean {
        if (!enabled) return false
        return if (startMin <= endMin) {
            (daysMask shr dow) and 1 == 1 && minuteOfDay in startMin until endMin
        } else {
            // wraps past midnight: the early-morning tail belongs to the previous day's window
            val prev = (dow + 6) % 7
            ((daysMask shr dow) and 1 == 1 && minuteOfDay >= startMin) ||
                ((daysMask shr prev) and 1 == 1 && minuteOfDay < endMin)
        }
    }
}

/**
 * A Fold Scene: a named Home profile. Empty [apps] means "show every Home app".
 * Empty [dock] means "use the default Dock".
 */
data class Scene(
    val id: String,
    val name: String,
    val glyph: String,
    val accent: Long,
    val apps: List<String> = emptyList(),
    val dock: List<String> = emptyList(),
    val schedule: Schedule = Schedule(),
)

data class TodayItem(val kind: String, val arg: String = "") {
    fun encode() = "$kind|$arg"
    companion object {
        fun decode(s: String): TodayItem {
            val i = s.indexOf('|')
            return if (i < 0) TodayItem(s) else TodayItem(s.substring(0, i), s.substring(i + 1))
        }
    }
}

data class Settings(
    val appearance: Appearance = Appearance.SYSTEM,
    val wallpaper: WallpaperKind = WallpaperKind.SYSTEM,
    val customWallpaperUri: String? = null,
    /** 0 = Large (20/page), 1 = Standard (24/page), 2 = Compact (30/page). */
    val gridDensity: Int = 1,
    val dockPlacement: DockPlacement = DockPlacement.AUTO,
    val dockSide: DockSide = DockSide.RIGHT,
    /** 0 = Off (instant), 1 = Subtle, 2 = Standard, 3 = Expressive. */
    val animationIntensity: Int = 2,
    val showLabels: Boolean = true,
    val glassStrength: Int = 2, // 0 clear .. 3 frosted
    val seeded: Boolean = false,
    val homeApps: List<String> = emptyList(),
    val dockApps: List<String> = emptyList(),
    val today: List<TodayItem> = listOf(
        TodayItem("clock"), TodayItem("scenes"), TodayItem("frequent"), TodayItem("battery"), TodayItem("notes")
    ),
    val noteText: String = "",
    val scenes: List<Scene> = emptyList(),
    val activeSceneId: String? = null,
    val autoScenes: Boolean = true,
    val focusPane: Int = 1,
    val appWidgetIds: List<Int> = emptyList(),
) {
    val pageCapacity: Int get() = capacityFor(gridDensity)

    companion object {
        fun capacityFor(density: Int) = when (density) { 0 -> 20; 2 -> 30; else -> 24 }
    }
}

object SettingsCodec {
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun List<String>.toJson() = JSONArray().also { a -> forEach { a.put(it) } }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, def: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: def

    fun encode(s: Settings): String = JSONObject().apply {
        put("v", 1)
        put("appearance", s.appearance.name)
        put("wallpaper", s.wallpaper.name)
        s.customWallpaperUri?.let { put("customWallpaperUri", it) }
        put("gridDensity", s.gridDensity)
        put("dockPlacement", s.dockPlacement.name)
        put("dockSide", s.dockSide.name)
        put("animationIntensity", s.animationIntensity)
        put("showLabels", s.showLabels)
        put("glassStrength", s.glassStrength)
        put("seeded", s.seeded)
        put("homeApps", s.homeApps.toJson())
        put("dockApps", s.dockApps.toJson())
        put("today", s.today.map { it.encode() }.toJson())
        put("noteText", s.noteText)
        put("scenes", JSONArray().also { arr -> s.scenes.forEach { arr.put(encodeScene(it)) } })
        s.activeSceneId?.let { put("activeSceneId", it) }
        put("autoScenes", s.autoScenes)
        put("focusPane", s.focusPane)
        put("appWidgetIds", JSONArray().also { a -> s.appWidgetIds.forEach { a.put(it) } })
    }.toString()

    fun decode(json: String?): Settings {
        if (json.isNullOrBlank()) return Settings()
        return try {
            val o = JSONObject(json)
            val d = Settings()
            Settings(
                appearance = enumOr(o.optString("appearance"), d.appearance),
                wallpaper = enumOr(o.optString("wallpaper"), d.wallpaper),
                customWallpaperUri = if (o.has("customWallpaperUri")) o.getString("customWallpaperUri") else null,
                gridDensity = o.optInt("gridDensity", d.gridDensity).coerceIn(0, 2),
                dockPlacement = enumOr(o.optString("dockPlacement"), d.dockPlacement),
                dockSide = enumOr(o.optString("dockSide"), d.dockSide),
                animationIntensity = o.optInt("animationIntensity", d.animationIntensity).coerceIn(0, 3),
                showLabels = o.optBoolean("showLabels", d.showLabels),
                glassStrength = o.optInt("glassStrength", d.glassStrength).coerceIn(0, 3),
                seeded = o.optBoolean("seeded", false),
                homeApps = o.optJSONArray("homeApps")?.strings() ?: emptyList(),
                dockApps = o.optJSONArray("dockApps")?.strings() ?: emptyList(),
                today = o.optJSONArray("today")?.strings()?.map { TodayItem.decode(it) } ?: d.today,
                noteText = o.optString("noteText", ""),
                scenes = o.optJSONArray("scenes")?.let { a ->
                    (0 until a.length()).mapNotNull { runCatching { decodeScene(a.getJSONObject(it)) }.getOrNull() }
                } ?: emptyList(),
                activeSceneId = if (o.has("activeSceneId")) o.getString("activeSceneId") else null,
                autoScenes = o.optBoolean("autoScenes", true),
                focusPane = o.optInt("focusPane", 1).coerceAtLeast(0),
                appWidgetIds = o.optJSONArray("appWidgetIds")?.let { a -> (0 until a.length()).map { a.getInt(it) } }
                    ?: emptyList(),
            )
        } catch (t: Throwable) {
            Settings() // corrupted blob: fall back to defaults rather than crash the Home screen
        }
    }

    private fun encodeScene(s: Scene) = JSONObject().apply {
        put("id", s.id); put("name", s.name); put("glyph", s.glyph); put("accent", s.accent)
        put("apps", s.apps.toJson()); put("dock", s.dock.toJson())
        put("sEnabled", s.schedule.enabled); put("sDays", s.schedule.daysMask)
        put("sStart", s.schedule.startMin); put("sEnd", s.schedule.endMin)
    }

    private fun decodeScene(o: JSONObject) = Scene(
        id = o.getString("id"), name = o.getString("name"), glyph = o.optString("glyph", "●"),
        accent = o.optLong("accent", 0xFF7C8CFF),
        apps = o.optJSONArray("apps")?.strings() ?: emptyList(),
        dock = o.optJSONArray("dock")?.strings() ?: emptyList(),
        schedule = Schedule(
            o.optBoolean("sEnabled", false), o.optInt("sDays", 127),
            o.optInt("sStart", 540), o.optInt("sEnd", 1020),
        ),
    )
}

/** Local-only launch history used for "Frequent", Library "Suggested" and scene suggestions. */
data class LaunchEvent(val key: String, val minuteOfDay: Int, val dow: Int, val epochMinute: Long)

object LaunchLogCodec {
    fun encode(l: List<LaunchEvent>): String = JSONArray().also { a ->
        l.forEach { a.put("${it.key}\t${it.minuteOfDay}\t${it.dow}\t${it.epochMinute}") }
    }.toString()

    fun decode(s: String?): List<LaunchEvent> {
        if (s.isNullOrBlank()) return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).mapNotNull {
                val p = a.getString(it).split('\t')
                if (p.size == 4) LaunchEvent(p[0], p[1].toInt(), p[2].toInt(), p[3].toLong()) else null
            }
        } catch (t: Throwable) { emptyList() }
    }
}

object SceneLogic {
    /** First enabled scene whose schedule matches now, or null. Earlier scenes in the list win. */
    fun scheduled(scenes: List<Scene>, dow: Int, minuteOfDay: Int): Scene? =
        scenes.firstOrNull { it.schedule.matches(dow, minuteOfDay) }

    /** Apps (in base order) visible under [scene]; null / empty scene = all. */
    fun visibleHome(base: List<String>, scene: Scene?): List<String> =
        if (scene == null || scene.apps.isEmpty()) base else {
            val allowed = scene.apps.toHashSet()
            base.filter { it in allowed }
        }

    /**
     * Habit-learning: top apps launched within +-[windowMin] of [minuteOfDay] on weekday/weekend
     * class matching [dow]. Purely local; nothing leaves the device.
     */
    fun suggestApps(log: List<LaunchEvent>, dow: Int, minuteOfDay: Int, windowMin: Int = 90, max: Int = 8): List<String> {
        val weekend = dow >= 5
        val counts = HashMap<String, Int>()
        for (e in log) {
            if ((e.dow >= 5) != weekend) continue
            val d = Math.abs(e.minuteOfDay - minuteOfDay).let { minOf(it, 1440 - it) }
            if (d <= windowMin) counts[e.key] = (counts[e.key] ?: 0) + 1
        }
        return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(max).map { it.key }
    }

    fun frequent(log: List<LaunchEvent>, max: Int = 6): List<String> {
        val counts = HashMap<String, Int>()
        for (e in log) counts[e.key] = (counts[e.key] ?: 0) + 1
        return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(max).map { it.key }
    }
}

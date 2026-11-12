package com.duoshell.sonnet.core

/** Pure App Library helpers: categorisation, search ranking. */
object Categorizer {
    val order = listOf(
        "Essentials", "Social", "Entertainment", "Creativity", "Productivity",
        "Travel", "Games", "Finance", "Utilities",
    )

    // Mirrors android.content.pm.ApplicationInfo.CATEGORY_* (kept as ints so this stays Android-free).
    private const val GAME = 0
    private const val AUDIO = 1
    private const val VIDEO = 2
    private const val IMAGE = 3
    private const val SOCIAL = 4
    private const val NEWS = 5
    private const val MAPS = 6
    private const val PRODUCTIVITY = 7

    private val essentials = listOf("dialer", "phone", "contacts", "messag", "sms", "settings", "clock", "calculator", "myfiles", "files")
    private val social = listOf("whatsapp", "instagram", "facebook", "telegram", "snapchat", "tiktok", "twitter", "discord", "reddit", "signal", "linkedin", "pinterest", "threads", "messenger")
    private val entertainment = listOf("spotify", "youtube", "netflix", "music", "video", "player", "podcast", "hulu", "disney", "twitch", "plex", "audible")
    private val creativity = listOf("camera", "gallery", "photo", "editor", "canva", "studio", "draw", "sketch", "ableton", "daw", "capcut", "lightroom")
    private val productivity = listOf("mail", "calendar", "docs", "drive", "sheets", "slides", "notes", "office", "outlook", "word", "excel", "powerpoint", "slack", "teams", "zoom", "notion", "todo", "keep", "github")
    private val travel = listOf("maps", "uber", "lyft", "airbnb", "booking", "flight", "transit", "waze", "travel", "trip")
    private val finance = listOf("bank", "pay", "wallet", "finance", "venmo", "paypal", "chase", "coinbase", "invest", "stock")

    fun categorize(pkg: String, label: String, platformCategory: Int): String {
        val hay = (pkg + " " + label).lowercase()
        fun has(words: List<String>) = words.any { hay.contains(it) }
        return when {
            platformCategory == GAME || hay.contains(".game") -> "Games"
            has(essentials) -> "Essentials"
            platformCategory == SOCIAL || has(social) -> "Social"
            platformCategory == AUDIO || platformCategory == VIDEO || platformCategory == NEWS || has(entertainment) -> "Entertainment"
            platformCategory == IMAGE || has(creativity) -> "Creativity"
            platformCategory == PRODUCTIVITY || has(productivity) -> "Productivity"
            platformCategory == MAPS || has(travel) -> "Travel"
            has(finance) -> "Finance"
            else -> "Utilities"
        }
    }
}

object SearchLogic {
    /** Ranked keys for [query]. Empty query returns an empty list. */
    fun rank(
        query: String,
        items: List<Triple<String, String, String>>, // key, label, package
        freq: Map<String, Int> = emptyMap(),
    ): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Int, Triple<String, String, String>>>()
        for (it in items) {
            val label = it.second.lowercase()
            var s = when {
                label.startsWith(q) -> 100
                label.split(' ', '-', '_', '.').any { w -> w.startsWith(q) } -> 80
                label.contains(q) -> 60
                subsequence(q, label) -> 30
                it.third.lowercase().contains(q) -> 20
                else -> 0
            }
            if (s > 0) {
                s += minOf(freq[it.first] ?: 0, 10)
                scored += s to it
            }
        }
        return scored.sortedWith(compareByDescending<Pair<Int, Triple<String, String, String>>> { it.first }
            .thenBy { it.second.second.lowercase() }).map { it.second.first }
    }

    private fun subsequence(q: String, s: String): Boolean {
        var i = 0
        for (c in s) if (i < q.length && c == q[i]) i++
        return i == q.length && q.length >= 2
    }
}

/**
 * Snapshot of the last layout, persisted so that if Android ever recreates the Activity (or the
 * process) during a fold, the previous layout can be rebuilt from pure geometry and the same morph
 * can still run (the "recreation fallback").
 */
object LayoutMemo {
    data class Memo(val input: LayoutInput, val focus: Int, val atMs: Long)

    fun encode(i: LayoutInput, focus: Int, atMs: Long): String {
        val h = i.hinge
        val hs = if (h == null) "-" else "${h.bounds.l},${h.bounds.t},${h.bounds.r},${h.bounds.b},${h.vertical},${h.halfOpened},${h.separating}"
        return listOf(
            i.width, i.height, i.insets.left, i.insets.top, i.insets.right, i.insets.bottom, i.density, i.fontScale,
            i.dockPlacement.name, i.dockSide.name, i.gridDensity, i.dockCount, i.showLabels, focus, atMs, hs,
        ).joinToString(";")
    }

    fun decode(s: String?): Memo? {
        if (s.isNullOrBlank()) return null
        return try {
            val p = s.split(";")
            if (p.size < 16) return null
            val hinge = if (p[15] == "-") null else p[15].split(",").let {
                Hinge(FRect(it[0].toFloat(), it[1].toFloat(), it[2].toFloat(), it[3].toFloat()), it[4].toBoolean(), it[5].toBoolean(), it[6].toBoolean())
            }
            Memo(
                LayoutInput(
                    p[0].toFloat(), p[1].toFloat(), EdgeInsets(p[2].toFloat(), p[3].toFloat(), p[4].toFloat(), p[5].toFloat()),
                    p[6].toFloat(), p[7].toFloat(), DockPlacement.valueOf(p[8]), DockSide.valueOf(p[9]),
                    p[10].toInt(), p[11].toInt(), p[12].toBoolean(), hinge,
                ),
                p[13].toInt(), p[14].toLong(),
            )
        } catch (t: Throwable) { null }
    }
}

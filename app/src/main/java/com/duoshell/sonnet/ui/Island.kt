package com.duoshell.sonnet.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duoshell.sonnet.LauncherController
import com.duoshell.sonnet.core.Geo
import kotlin.math.roundToInt

/**
 * Compact "island" status surface. Original design: a slim vertical glass capsule on the edge opposite
 * the dock that shows clock, battery and (optionally) now-playing; tap to expand into a panel.
 * It only shows information we can read through public APIs. Toasts are produced by this launcher
 * itself (scene changes, charging state, launch failures) - never imitating other apps' notifications.
 */
@Composable
fun DynamicIsland(c: LauncherController, geo: Geo, view: ViewParams) {
    val density = LocalDensity.current
    val ctx = LocalContext.current
    val t = LocalGlass.current
    val r = geo.island
    val expanded = c.islandOpen
    val exp by animateFloatAsState(if (expanded) 1f else 0f, spring(0.82f, Spring.StiffnessMediumLow), label = "islandExp")
    val x by animateFloatAsState(r.l, spring(0.9f, Spring.StiffnessLow), label = "islandX")
    val y by animateFloatAsState(r.t, spring(0.9f, Spring.StiffnessLow), label = "islandY")
    val d = geo.input.density

    val now = c.nowMs
    val batt = c.battery.info
    val media = c.media.info
    val toast = c.toast
    val scene = c.activeScene
    val a11y = buildString {
        append("Status. ").append(Fmt.time(ctx, now)).append(", battery ").append(batt.percent).append(" percent")
        if (batt.charging) append(", charging")
        if (media != null) append(", ").append(if (media.playing) "playing " else "paused ").append(media.title)
        append(". Double tap to ").append(if (expanded) "collapse" else "expand")
    }

    // ---------- compact capsule: clock on the left, battery (or media / scene glyph) on the right
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(with(density) { r.w.toDp() }, with(density) { r.h.toDp() })
            .graphicsLayer { alpha = 1f - exp; val s = 1f - 0.08f * exp; scaleX = s; scaleY = s }
            .semantics { contentDescription = a11y }
            .tap({ c.islandOpen = true; c.menu = null }, a11y),
    ) {
        GlassSurface(Modifier.fillMaxSize(), shape = GlassPill, tint = Color.Black.copy(alpha = 0.62f)) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(Fmt.time(ctx, now), style = txt(13.sp, Color.White, FontWeight.SemiBold))
                if (media != null && media.playing) {
                    BasicText("\u266A", style = txt(14.sp, Color(0xFF30D158), FontWeight.Bold))
                } else if (scene != null) {
                    BasicText(scene.glyph, style = txt(13.sp, Color(scene.accent), FontWeight.Bold))
                }
                BatteryGlyph(batt.percent, batt.charging)
            }
        }
    }

    // ---------- transient toast capsule, dropping out of the island
    if (toast != null && exp < 0.5f) {
        val tw = 230.dp
        val th = 48.dp
        val twPx = with(density) { tw.toPx() }
        val tx = r.cx - twPx / 2f
        var shown by androidx.compose.runtime.remember(toast.id) { androidx.compose.runtime.mutableStateOf(0f) }
        androidx.compose.runtime.LaunchedEffect(toast.id) { shown = 1f }
        val alpha by animateFloatAsState(shown, tween(220), label = "toastA")
        Box(
            Modifier
                .offset { IntOffset(tx.roundToInt(), (r.b + 8f * d).roundToInt()) }
                .size(tw, th)
                .graphicsLayer { this.alpha = alpha; transformOrigin = TransformOrigin(0.5f, 0f); val s = 0.85f + 0.15f * alpha; scaleX = s; scaleY = s }
                .semantics { contentDescription = toast.title + if (toast.detail.isNotEmpty()) ", " + toast.detail else "" },
        ) {
            GlassSurface(Modifier.fillMaxSize(), shape = GlassPill, tint = Color.Black.copy(alpha = 0.62f)) {
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.Center) {
                    BasicText(toast.title, style = txt(13.sp, Color.White, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (toast.detail.isNotEmpty()) BasicText(toast.detail, style = txt(11.sp, Color(0xCCFFFFFF), FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    // ---------- expanded panel (opaque enough to read over any page)
    if (exp > 0.01f) {
        val panelW = minOf(340f * d, geo.width - 24f * d)
        val panelLeft = r.cx - panelW / 2f
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = exp }.tap({ c.islandOpen = false }, "Close status panel"))
        Box(
            Modifier
                .offset { IntOffset(panelLeft.roundToInt(), r.t.roundToInt()) }
                .width(with(density) { panelW.toDp() })
                .consumeTaps()
                .graphicsLayer {
                    alpha = exp
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    val s = 0.5f + 0.5f * exp
                    scaleX = s; scaleY = s
                },
        ) {
            GlassSurface(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), strongShadow = true,
                tint = if (t.dark) Color(0xE8101319) else Color(0xE8F6F7FB),
            ) { IslandPanel(c) }
        }
    }
}

@Composable
private fun BatteryGlyph(pct: Int, charging: Boolean) {
    val low = pct <= 15 && !charging
    Canvas(Modifier.size(24.dp, 12.dp)) {
        val w = size.width - 2.dp.toPx()
        val h = size.height
        drawRoundRect(Color.White.copy(alpha = 0.38f), Offset(0f, 0f), Size(w, h), CornerRadius(3.5.dp.toPx()))
        val fw = (w - 3.dp.toPx()) * (pct / 100f).coerceIn(0.06f, 1f)
        drawRoundRect(
            if (charging) Color(0xFF30D158) else if (low) Color(0xFFFF453A) else Color.White,
            Offset(1.5.dp.toPx(), 1.5.dp.toPx()), Size(fw, h - 3.dp.toPx()), CornerRadius(2.dp.toPx()),
        )
        drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(w + 0.5.dp.toPx(), h * 0.3f), Size(1.5.dp.toPx(), h * 0.4f), CornerRadius(1.dp.toPx()))
    }
}

@Composable
private fun IslandPanel(c: LauncherController) {
    val ctx = LocalContext.current
    val t = LocalGlass.current
    val batt = c.battery.info
    val media = c.media.info
    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(Fmt.time(ctx, c.nowMs) + " " + Fmt.ampm(ctx, c.nowMs), style = txt(30.sp, t.panelText, FontWeight.Bold))
                BasicText(Fmt.date(c.nowMs), style = txt(13.sp, t.panelSecondary, FontWeight.Medium))
            }
            Column(horizontalAlignment = Alignment.End) {
                BasicText("${batt.percent}%", style = txt(18.sp, t.panelText, FontWeight.SemiBold))
                BasicText(if (batt.charging) "Charging" else "On battery", style = txt(12.sp, t.panelSecondary, FontWeight.Normal))
            }
        }
        DividerLine()
        if (media != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(media.title, style = txt(15.sp, t.panelText, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (media.artist.isNotEmpty()) BasicText(media.artist, style = txt(12.sp, t.panelSecondary, FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Chip("Prev", false, { c.media.previous() })
                    Chip(if (media.playing) "Pause" else "Play", true, { c.media.playPause() })
                    Chip("Next", false, { c.media.next() })
                }
            }
        } else if (!c.media.enabled) {
            ActionRow("Show now playing", "Optional. Opens system Notification access; nothing is read until you allow it.") { c.islandOpen = false; c.openNotificationAccess() }
        } else {
            BasicText("Nothing playing", style = txt(13.sp, t.panelSecondary, FontWeight.Normal))
        }
        if (c.settings.scenes.isNotEmpty()) {
            DividerLine()
            BasicText("Fold Scenes", style = txt(12.sp, t.panelSecondary, FontWeight.SemiBold))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("All", c.settings.activeSceneId == null, { c.activateScene(null) })
                for (s in c.settings.scenes.take(3)) {
                    Chip(s.glyph + " " + s.name, c.settings.activeSceneId == s.id, { c.activateScene(s.id) }, accent = Color(s.accent))
                }
            }
        }
    }
}

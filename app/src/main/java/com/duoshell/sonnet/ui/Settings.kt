package com.duoshell.sonnet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duoshell.sonnet.DevViewport
import com.duoshell.sonnet.LauncherController
import com.duoshell.sonnet.Panel
import com.duoshell.sonnet.core.Appearance
import com.duoshell.sonnet.core.DockPlacement
import com.duoshell.sonnet.core.DockSide
import com.duoshell.sonnet.core.WallpaperKind

@Composable
fun SettingsPanel(c: LauncherController) {
    val t = LocalGlass.current
    val s = c.settings
    Sheet("Settings", onClose = { c.closePanel() }) {
        SectionTitle("Appearance")
        Segmented(listOf("System", "Light", "Dark"), s.appearance.ordinal, { i -> c.update { it.copy(appearance = Appearance.values()[i]) } })

        SectionTitle("Wallpaper")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in WallpaperKind.values()) {
                if (k == WallpaperKind.CUSTOM) continue
                Chip(k.label, s.wallpaper == k, { c.setWallpaper(k) })
            }
            Chip("Photo…", s.wallpaper == WallpaperKind.CUSTOM, { c.onPickPhoto() })
        }
        ActionRow("Change system wallpaper", "Opens the system picker. “System” shows your current wallpaper behind Home.") { c.onSystemWallpaper() }

        SectionTitle("Home Screen")
        BasicText("Grid density", style = txt(13.sp, t.panelSecondary, FontWeight.Medium), modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Segmented(listOf("Large", "Standard", "Compact"), s.gridDensity, { i -> c.update { it.copy(gridDensity = i) } })
        ToggleRow("Show app names", s.showLabels, { v -> c.update { it.copy(showLabels = v) } })
        ActionRow("Edit Home Screen", "Rearrange, remove or pin apps. You can also long-press empty space.") { c.closePanel(); c.editing = true }

        SectionTitle("Dock")
        Segmented(listOf("Auto", "Side", "Bottom"), s.dockPlacement.ordinal, { i -> c.update { it.copy(dockPlacement = DockPlacement.values()[i]) } })
        Spacer(Modifier.height(8.dp))
        Segmented(listOf("Left", "Right"), s.dockSide.ordinal, { i -> c.update { it.copy(dockSide = DockSide.values()[i]) } })
        BasicText("Side dock sits on the chosen edge; the status island moves to the opposite edge. Long-press a Dock app to unpin it, or drag apps in and out while editing (up to 6).",
            Modifier.padding(4.dp, 6.dp), style = txt(12.sp, t.panelSecondary, FontWeight.Normal))

        SectionTitle("Liquid Glass")
        Segmented(listOf("Clear", "Light", "Standard", "Frosted"), s.glassStrength, { i -> c.update { it.copy(glassStrength = i) } })
        BasicText("On a photo or built-in wallpaper (Android 12+) glass blurs what is behind it. With the system wallpaper, Android does not let apps read it, so glass uses a tinted translucent body instead.",
            Modifier.padding(4.dp, 6.dp), style = txt(12.sp, t.panelSecondary, FontWeight.Normal))

        SectionTitle("Animation")
        Segmented(listOf("Off", "Subtle", "Standard", "Expressive"), s.animationIntensity, { i -> c.update { it.copy(animationIntensity = i) } })
        BasicText("Controls the fold/unfold continuity morph and layout changes. Off switches instantly.", Modifier.padding(4.dp, 6.dp), style = txt(12.sp, t.panelSecondary, FontWeight.Normal))

        SectionTitle("Today View")
        s.today.forEachIndexed { i, item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BasicText(todayLabel(c, item), Modifier.weight(1f).padding(start = 4.dp), style = txt(15.sp, t.panelText))
                Chip("↑", false, { c.moveToday(i, -1) }); Spacer(Modifier.size(6.dp))
                Chip("↓", false, { c.moveToday(i, 1) }); Spacer(Modifier.size(6.dp))
                Chip("Remove", false, { c.removeToday(i) })
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((k, label) in listOf("clock" to "Clock", "scenes" to "Scenes", "frequent" to "Frequent", "battery" to "Battery", "notes" to "Note")) {
                if (s.today.none { it.kind == k }) Chip("+ $label", false, { c.addToday(k) })
            }
            Chip("+ App widget", false, { c.panel = Panel.WIDGETS })
        }

        SectionTitle("Fold Scenes")
        ActionRow("Manage scenes", "${s.scenes.size} saved. Scenes reshape Home and the Dock for a context, optionally on a schedule.") { c.panel = Panel.SCENES }

        SectionTitle("Default launcher")
        val isHome = c.isDefaultHome()
        ActionRow(if (isHome) "DuoShell is your Home app" else "Set DuoShell as Home app", "Opens system settings. You stay in control; nothing changes until you choose.") { c.openDefaultHomeSettings() }
        BasicText(
            "Restore One UI Home: Settings › Apps › Choose default apps › Home app › One UI Home. Or open this screen from the button above. Uninstalling DuoShell also restores it.",
            Modifier.padding(4.dp, 4.dp), style = txt(12.sp, t.panelSecondary, FontWeight.Normal),
        )

        SectionTitle("Status island")
        ActionRow(
            if (c.media.enabled) "Now playing: on" else "Now playing: off",
            "Optional. Needs Notification access in system settings. DuoShell only reads media title and playback state, never notification content.",
        ) { c.openNotificationAccess() }

        SectionTitle("Developer · fold preview")
        BasicText(
            "Preview the fold morph without folding the device. This animates DuoShell's own layout between folded and unfolded sizes; it does not control Samsung's system fold animation.",
            Modifier.padding(4.dp, 0.dp, 4.dp, 8.dp), style = txt(12.sp, t.panelSecondary, FontWeight.Normal),
        )
        Segmented(DevViewport.values().map { it.label }, c.devViewport.ordinal, { i -> c.setViewport(DevViewport.values()[i]) })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (c.devLoopRunning) Chip("Stop loop", true, { c.stopFoldLoop() })
            else Chip("Run 3 fold cycles", false, { c.closePanel(); c.runFoldLoop(3) })
            Chip("Toggle once", false, { c.closePanel(); c.cycleViewport() })
        }
        val st = c.lastFoldStats
        BasicText(
            if (st == null) "No fold measured yet. Fold or run a preview; frame timing from the last fold appears here."
            else "Last fold (${st.label}): ${st.frames} frames over ${st.durationMs} ms; avg ${"%.1f".format(st.avgMs)} ms, p95 ${"%.1f".format(st.p95Ms)} ms, worst ${"%.1f".format(st.maxMs)} ms; ${st.slow} slow frames. Measured with Choreographer frame times, not a GPU profiler.",
            Modifier.padding(4.dp, 8.dp), style = txt(12.sp, t.panelText, FontWeight.Normal),
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun todayLabel(c: LauncherController, item: com.duoshell.sonnet.core.TodayItem): String = when (item.kind) {
    "clock" -> "Clock"; "scenes" -> "Fold Scenes"; "frequent" -> "Frequent apps"
    "battery" -> "Battery"; "notes" -> "Quick note"
    "appwidget" -> c.widgets.info(item.arg.toIntOrNull() ?: -1)?.let { c.widgets.labelOf(it) } ?: "Widget (unavailable)"
    else -> item.kind
}

@Composable
fun WidgetsPanel(c: LauncherController) {
    val t = LocalGlass.current
    val providers = androidx.compose.runtime.remember { c.widgets.providers() }
    Sheet("Add widget", onClose = { c.closePanel() }) {
        BasicText("Widgets come from your installed apps. Android asks your permission to bind each one.", Modifier.padding(4.dp, 4.dp, 4.dp, 8.dp), style = txt(13.sp, t.panelSecondary, FontWeight.Normal))
        if (providers.isEmpty()) BasicText("No home-screen widgets available.", Modifier.padding(4.dp), style = txt(15.sp, t.panelText))
        for (p in providers) {
            ActionRow(c.widgets.labelOf(p), c.widgets.sizeLabel(p)) { c.onPickWidget(p) }
            DividerLine()
        }
    }
}

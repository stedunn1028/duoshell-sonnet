package com.duoshell.sonnet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duoshell.sonnet.LauncherController
import com.duoshell.sonnet.core.Scene
import com.duoshell.sonnet.core.Schedule

private val GLYPHS = listOf("☀", "☾", "⚡", "♪", "⌂", "✦", "❖", "●")
private val ACCENTS = listOf(0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFFF375F, 0xFFBF5AF2, 0xFF64D2FF)
private val DAYS = listOf("M", "T", "W", "T", "F", "S", "S")

/** Fold Scenes manager: list + editor. */
@Composable
fun ScenesPanel(c: LauncherController) {
    val editId = c.sceneEditId
    val editing = editId?.let { id -> c.settings.scenes.firstOrNull { it.id == id } }
    if (editId != null && editing != null) SceneEditor(c, editing) else SceneList(c)
}

@Composable
private fun SceneList(c: LauncherController) {
    val t = LocalGlass.current
    val s = c.settings
    Sheet("Fold Scenes", onClose = { c.closePanel() }) {
        BasicText(
            "A scene is a Home profile: it filters which Home apps show and can swap the Dock. Switch by hand from Today or the status island, or let a schedule switch it. Suggestions come from your own launch history on this device.",
            Modifier.padding(4.dp, 4.dp, 4.dp, 8.dp), style = txt(13.sp, t.panelSecondary, FontWeight.Normal),
        )
        ToggleRow("Switch on schedule", s.autoScenes, { v -> c.update { it.copy(autoScenes = v) } }, "Scenes with a schedule turn on and off automatically")
        DividerLine()
        ActionRow("All apps", if (s.activeSceneId == null) "Active" else "Show every Home app") { c.activateScene(null) }
        for (sc in s.scenes) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(sc.accent)), contentAlignment = Alignment.Center) {
                    BasicText(sc.glyph, style = txt(16.sp, Color.White, FontWeight.Bold))
                }
                Box(Modifier.weight(1f)) {
                    ActionRow(
                        sc.name,
                        (if (s.activeSceneId == sc.id) "Active · " else "") + (if (sc.apps.isEmpty()) "all apps" else "${sc.apps.size} apps") +
                            (if (sc.schedule.enabled) " · ${Fmt.minutes(sc.schedule.startMin)}–${Fmt.minutes(sc.schedule.endMin)}" else ""),
                    ) { c.activateScene(sc.id) }
                }
                Chip("Edit", false, { c.sceneEditId = sc.id })
            }
        }
        Spacer(Modifier.height(10.dp))
        Chip("New scene", true, {
            val id = c.newSceneId()
            c.upsertScene(Scene(id, "New scene", GLYPHS[s.scenes.size % GLYPHS.size], ACCENTS[s.scenes.size % ACCENTS.size]))
            c.sceneEditId = id
        })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SceneEditor(c: LauncherController, original: Scene) {
    val t = LocalGlass.current
    var sc by remember(original.id) { mutableStateOf(original) }
    fun save(n: Scene) { sc = n; c.upsertScene(n) }
    Sheet("Edit scene", onClose = { c.sceneEditId = null }, scroll = true) {
        SectionTitle("Name")
        GlassSurface(Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp), elevated = false) {
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = sc.name, onValueChange = { save(sc.copy(name = it.take(24))) }, singleLine = true,
                    textStyle = txt(16.sp, t.panelText, FontWeight.Medium), cursorBrush = SolidColor(t.accent), modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        SectionTitle("Look")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (g in GLYPHS) Chip(g, sc.glyph == g, { save(sc.copy(glyph = g)) }, accent = Color(sc.accent))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (a in ACCENTS) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(a)).tap({ save(sc.copy(accent = a)) }, "Accent colour"),
                    contentAlignment = Alignment.Center,
                ) { if (sc.accent == a) BasicText("✓", style = txt(18.sp, Color.White, FontWeight.Bold)) }
            }
        }

        SectionTitle("Apps shown on Home")
        BasicText(
            if (sc.apps.isEmpty()) "Showing all Home apps. Pick apps to limit this scene." else "${sc.apps.size} selected.",
            Modifier.padding(4.dp, 0.dp, 4.dp, 8.dp), style = txt(12.sp, t.panelSecondary, FontWeight.Normal),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Fill with suggestions", false, {
                val sug = c.suggestedForNow().ifEmpty { c.frequentKeys(8) }
                if (sug.isEmpty()) c.postToast("No history yet", "Use some apps first") else save(sc.copy(apps = sug))
            })
            Chip("Clear", false, { save(sc.copy(apps = emptyList())) })
        }
        Spacer(Modifier.height(8.dp))
        AppPicker(c, sc.apps) { keys -> save(sc.copy(apps = keys)) }

        SectionTitle("Dock")
        ToggleRow("Custom Dock for this scene", sc.dock.isNotEmpty(), { on ->
            save(sc.copy(dock = if (on) c.settings.dockApps.take(4).ifEmpty { sc.apps.take(4) } else emptyList()))
        })
        if (sc.dock.isNotEmpty()) AppPicker(c, sc.dock, max = 5) { keys -> save(sc.copy(dock = keys)) }

        SectionTitle("Schedule")
        ToggleRow("Turn on automatically", sc.schedule.enabled, { v -> save(sc.copy(schedule = sc.schedule.copy(enabled = v))) })
        if (sc.schedule.enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                DAYS.forEachIndexed { i, d ->
                    val on = (sc.schedule.daysMask shr i) and 1 == 1
                    Chip(d, on, { save(sc.copy(schedule = sc.schedule.copy(daysMask = sc.schedule.daysMask xor (1 shl i)))) })
                }
            }
            TimeRow("From", sc.schedule.startMin) { save(sc.copy(schedule = sc.schedule.copy(startMin = it))) }
            TimeRow("To", sc.schedule.endMin) { save(sc.copy(schedule = sc.schedule.copy(endMin = it))) }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Use now", true, { c.activateScene(sc.id) })
            Chip("Delete scene", false, { c.deleteScene(sc.id); c.sceneEditId = null })
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TimeRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    val t = LocalGlass.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f).padding(start = 4.dp), style = txt(15.sp, t.panelText))
        Chip("−", false, { onChange(Math.floorMod(minutes - 30, 1440)) })
        BasicText(Fmt.minutes(minutes), Modifier.padding(horizontal = 12.dp), style = txt(15.sp, t.panelText, FontWeight.SemiBold))
        Chip("+", false, { onChange((minutes + 30) % 1440) })
    }
}

/** Tap-to-toggle grid of the user's installed apps; selection keeps tap order. */
@Composable
private fun AppPicker(c: LauncherController, selected: List<String>, max: Int = Int.MAX_VALUE, onChange: (List<String>) -> Unit) {
    val t = LocalGlass.current
    val apps = c.repo.apps
    Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(18.dp)).background(if (t.dark) Color(0x22FFFFFF) else Color(0x12000000))) {
        LazyVerticalGrid(GridCells.Adaptive(72.dp), Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(apps, key = { it.key }) { a ->
                val on = a.key in selected
                Column(
                    Modifier.fillMaxWidth().tap({
                        if (on) onChange(selected - a.key) else if (selected.size < max) onChange(selected + a.key)
                    }, (if (on) "Deselect " else "Select ") + a.label),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        AppIconImage(c, a.key, 46.dp, Modifier.alpha(if (on) 1f else 0.55f))
                        if (on) Box(Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape).background(Color(0xFF30D158)), contentAlignment = Alignment.Center) {
                            BasicText("✓", style = txt(11.sp, Color.White, FontWeight.Bold))
                        }
                    }
                    BasicText(a.label, style = txt(10.sp, t.panelText, FontWeight.Normal), maxLines = 1)
                }
            }
        }
    }
}

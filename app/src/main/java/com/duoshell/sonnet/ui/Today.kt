package com.duoshell.sonnet.ui

import android.appwidget.AppWidgetHostView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.duoshell.sonnet.LauncherController
import com.duoshell.sonnet.Panel
import com.duoshell.sonnet.core.TodayItem

/** Left-hand "Today View" pane: clock, Fold Scenes, frequent apps, battery, notes and real AppWidgets. */
@Composable
fun TodayPane(c: LauncherController, modifier: Modifier = Modifier) {
    val t = LocalGlass.current
    val ctx = LocalContext.current
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Today", Modifier.weight(1f), style = txt(26.sp, Color.White, FontWeight.Bold, shadow = true))
            Box(Modifier.size(44.dp).tap({ c.panel = Panel.WIDGETS }, "Add a widget"), contentAlignment = Alignment.Center) {
                GlassSurface(Modifier.size(34.dp), shape = CircleShape) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BasicText("+", style = txt(20.sp, t.panelText, FontWeight.Medium)) }
                }
            }
        }
        c.settings.today.forEachIndexed { i, item ->
            when (item.kind) {
                "clock" -> ClockCard(c)
                "scenes" -> ScenesCard(c)
                "frequent" -> FrequentCard(c)
                "battery" -> BatteryCard(c)
                "notes" -> NotesCard(c)
                "appwidget" -> AppWidgetCard(c, item)
            }
        }
        if (c.settings.today.isEmpty()) {
            GlassSurface(Modifier.fillMaxWidth()) {
                BasicText("Nothing here yet. Tap + to add a widget.", Modifier.padding(18.dp), style = txt(14.sp, t.panelText))
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun Card(title: String?, content: @Composable () -> Unit) {
    val t = LocalGlass.current
    GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) BasicText(title.uppercase(), style = txt(11.sp, t.panelSecondary, FontWeight.SemiBold))
            content()
        }
    }
}

@Composable
private fun ClockCard(c: LauncherController) {
    val ctx = LocalContext.current
    val t = LocalGlass.current
    Card(null) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(Fmt.time(ctx, c.nowMs), style = txt(46.sp, t.panelText, FontWeight.Bold))
            Spacer(Modifier.width(6.dp))
            BasicText(Fmt.ampm(ctx, c.nowMs), Modifier.padding(bottom = 8.dp), style = txt(16.sp, t.panelSecondary, FontWeight.Medium))
        }
        BasicText(Fmt.date(c.nowMs), style = txt(15.sp, t.panelSecondary, FontWeight.Medium))
    }
}

@Composable
private fun ScenesCard(c: LauncherController) {
    val t = LocalGlass.current
    Card("Fold Scenes") {
        if (c.settings.scenes.isEmpty()) {
            BasicText("Scenes reshape your Home for a moment of your day.", style = txt(13.sp, t.panelSecondary, FontWeight.Normal))
            Chip("Create a scene", true, { c.panel = Panel.SCENES })
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("All apps", c.settings.activeSceneId == null, { c.activateScene(null) })
                for (s in c.settings.scenes) Chip(s.glyph + " " + s.name, c.settings.activeSceneId == s.id, { c.activateScene(s.id) }, accent = Color(s.accent))
            }
            val sug = c.suggestedForNow()
            if (sug.isNotEmpty() && c.activeScene == null) {
                BasicText("Often used around now", style = txt(11.sp, t.panelSecondary, FontWeight.Medium))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (k in sug.take(5)) AppIconImage(c, k, 44.dp, Modifier.tap({ c.launch(k, null) }, c.appsByKey[k]?.label))
                }
            }
        }
    }
}

@Composable
private fun FrequentCard(c: LauncherController) {
    val t = LocalGlass.current
    val keys = c.frequentKeys(6)
    Card("Frequent") {
        if (keys.isEmpty()) {
            BasicText("Apps you open often will appear here. Learned on this device only.", style = txt(13.sp, t.panelSecondary, FontWeight.Normal))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (k in keys.take(5)) AppIconImage(c, k, 48.dp, Modifier.tap({ c.launch(k, null) }, c.appsByKey[k]?.label))
            }
        }
    }
}

@Composable
private fun BatteryCard(c: LauncherController) {
    val t = LocalGlass.current
    val b = c.battery.info
    Card("Battery") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("${b.percent}%", Modifier.weight(1f), style = txt(26.sp, t.panelText, FontWeight.Bold))
            BasicText(if (b.charging) "Charging" else "", style = txt(13.sp, Color(0xFF30D158), FontWeight.SemiBold))
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (t.dark) Color(0x33FFFFFF) else Color(0x22000000))) {
            Box(
                Modifier.fillMaxWidth(b.percent / 100f).height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (b.charging) Color(0xFF30D158) else if (b.percent <= 15) Color(0xFFFF453A) else t.accent),
            )
        }
    }
}

@Composable
private fun NotesCard(c: LauncherController) {
    val t = LocalGlass.current
    var text by remember { mutableStateOf(c.settings.noteText) }
    Card("Quick note") {
        Box(Modifier.fillMaxWidth().height(96.dp)) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; c.setNote(it) },
                textStyle = txt(15.sp, t.panelText, FontWeight.Normal),
                cursorBrush = SolidColor(t.accent),
                modifier = Modifier.fillMaxSize(),
            )
            if (text.isEmpty()) BasicText("Jot something down", style = txt(15.sp, t.panelSecondary, FontWeight.Normal))
        }
    }
}

@Composable
private fun AppWidgetCard(c: LauncherController, item: TodayItem) {
    val id = item.arg.toIntOrNull() ?: return
    val ctx = LocalContext.current
    val info = c.widgets.info(id)
    if (info == null) {
        Card("Widget unavailable") {
            val t = LocalGlass.current
            BasicText("This widget's app was removed.", style = txt(13.sp, t.panelSecondary, FontWeight.Normal))
            Chip("Remove", false, { c.removeToday(c.settings.today.indexOf(item)) })
        }
        return
    }
    val density = ctx.resources.displayMetrics.density
    val h: Dp = (info.minHeight / density).dp.coerceIn(70.dp, 320.dp)
    GlassSurface(Modifier.fillMaxWidth().height(h), shape = RoundedCornerShape(24.dp)) {
        AndroidView(
            factory = { context: android.content.Context -> c.widgets.createView(context, id, info) as AppWidgetHostView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}


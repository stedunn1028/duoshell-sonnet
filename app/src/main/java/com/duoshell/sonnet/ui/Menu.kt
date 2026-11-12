package com.duoshell.sonnet.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duoshell.sonnet.LauncherController
import com.duoshell.sonnet.MenuState
import kotlin.math.roundToInt

/**
 * Long-press quick menu. Dynamic/manifest shortcuts are only readable by the *default* Home app
 * (platform rule), so they appear once DuoShell is set as Home; the other actions always work.
 */
@Composable
fun QuickMenu(c: LauncherController, m: MenuState, winW: Float, winH: Float) {
    val density = LocalDensity.current
    val d = density.density
    val t = LocalGlass.current
    val entry = c.appsByKey[m.key] ?: run { c.menu = null; return }
    val shortcuts = remember(m.key) { c.repo.shortcuts(entry) }
    val inHome = m.key in c.settings.homeApps
    val inDock = m.key in c.settings.dockApps
    var target by remember { mutableStateOf(0f) }
    LaunchedEffect(m) { target = 1f }
    val scale by animateFloatAsState(target, spring(0.78f, Spring.StiffnessMedium), label = "menuScale")

    val menuW = 250f * d
    val rowH = 50f * d
    val rows = shortcuts.size + 2 + (if (m.source == "library") 1 else 2) + (if (m.source == "dock" || !inDock) 1 else 1)
    val menuH = rows * rowH + 16f * d
    val x = (m.anchor.cx - menuW / 2f).coerceIn(8f * d, winW - menuW - 8f * d)
    val below = m.anchor.b + 8f * d + menuH < winH - 8f * d
    val y = if (below) m.anchor.b + 8f * d else (m.anchor.t - 8f * d - menuH).coerceAtLeast(8f * d)

    Box(Modifier.fillMaxSize().tap({ c.menu = null }, "Dismiss menu"))
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .width(250.dp)
            .consumeTaps()
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, if (below) 0f else 1f)
                scaleX = 0.8f + 0.2f * scale; scaleY = 0.8f + 0.2f * scale; alpha = scale.coerceIn(0f, 1f)
            },
    ) {
        GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), strongShadow = true) {
            Column(Modifier.padding(vertical = 8.dp, horizontal = 8.dp)) {
                BasicText(entry.label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = txt(13.sp, t.panelSecondary, FontWeight.SemiBold), maxLines = 1)
                for (s in shortcuts) {
                    ActionRow(s.shortLabel?.toString() ?: s.id) {
                        c.menu = null
                        if (!c.repo.launchShortcut(s)) c.postToast("Shortcut unavailable")
                    }
                }
                if (shortcuts.isNotEmpty()) DividerLine()
                if (m.source != "library") ActionRow("Edit Home Screen") { c.menu = null; c.editing = true }
                if (inDock) ActionRow("Unpin from Dock") { c.menu = null; c.unpinFromDock(m.key) }
                else ActionRow("Pin to Dock") { c.menu = null; c.pinToDock(m.key) }
                if (inHome) ActionRow("Remove from Home Screen") { c.menu = null; c.removeFromHome(m.key) }
                else if (!inDock) ActionRow("Add to Home Screen") { c.menu = null; c.addToHome(m.key); c.postToast("Added", entry.label) }
                ActionRow("App info") { c.menu = null; if (!c.repo.openAppInfo(entry)) c.postToast("App info unavailable") }
                ActionRow("Uninstall") { c.menu = null; c.openUninstall(m.key) }
            }
        }
    }
}

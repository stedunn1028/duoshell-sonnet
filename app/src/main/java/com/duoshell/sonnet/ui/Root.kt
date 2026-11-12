package com.duoshell.sonnet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.duoshell.sonnet.DevViewport
import com.duoshell.sonnet.LauncherController
import com.duoshell.sonnet.Panel
import com.duoshell.sonnet.core.Appearance
import com.duoshell.sonnet.core.EdgeInsets
import com.duoshell.sonnet.core.FRect
import com.duoshell.sonnet.core.Hinge
import com.duoshell.sonnet.core.WallpaperKind
import kotlin.math.min

@Composable
fun Root(c: LauncherController) {
    val s = c.settings
    val dark = when (s.appearance) {
        Appearance.DARK -> true
        Appearance.LIGHT -> false
        Appearance.SYSTEM -> isSystemInDarkTheme()
    }
    val glass = remember(dark, s.glassStrength) { glassTheme(dark, s.glassStrength) }
    val density = LocalDensity.current
    val d = density.density
    val ld = LocalLayoutDirection.current
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val realInsets = EdgeInsets(
        bars.getLeft(density, ld).toFloat(), bars.getTop(density).toFloat(),
        bars.getRight(density, ld).toFloat(), bars.getBottom(density).toFloat(),
    )

    BackHandler(enabled = true) { c.onBack() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val realW = constraints.maxWidth.toFloat()
        val realH = constraints.maxHeight.toFloat()

        // Developer preview: lay Home out for a virtual folded/unfolded window and scale it into the real one.
        val dev = c.devViewport
        val virtW: Float
        val virtH: Float
        val insets: EdgeInsets
        val hinge: Hinge?
        when (dev) {
            DevViewport.AUTO -> { virtW = realW; virtH = realH; insets = realInsets; hinge = c.hinge }
            DevViewport.FOLDED -> {
                virtW = 380f * d; virtH = 820f * d
                insets = EdgeInsets(0f, 30f * d, 0f, 20f * d); hinge = null
            }
            DevViewport.UNFOLDED -> {
                virtW = 780f * d; virtH = 703f * d
                insets = EdgeInsets(0f, 30f * d, 0f, 20f * d)
                hinge = Hinge(FRect(virtW / 2f, 0f, virtW / 2f, virtH), vertical = true, halfOpened = false, separating = false)
            }
        }
        val fit = if (dev == DevViewport.AUTO) 1f else min((realW - 24f * d) / virtW, (realH - 24f * d) / virtH)
        val tx = if (dev == DevViewport.AUTO) 0f else (realW - virtW * fit) / 2f
        val ty = if (dev == DevViewport.AUTO) 0f else (realH - virtH * fit) / 2f
        val view = ViewParams(fit, tx, ty, dev != DevViewport.AUTO)
        val win = WindowSpec(virtW, virtH, insets, d, density.fontScale, hinge)

        val img = c.wallpaperImage
        val backdrop = BackdropInfo(s.wallpaper, if (s.wallpaper == WallpaperKind.CUSTOM) img?.image else null, img?.luma ?: 0.4f, virtW, virtH)

        CompositionLocalProvider(LocalGlass provides glass, LocalBackdrop provides backdrop) {
            if (dev != DevViewport.AUTO) Box(Modifier.fillMaxSize().background(Color(0xFF050608)))
            // ---- home (virtual window)
            Box(
                Modifier
                    .requiredSize(with(density) { virtW.toDp() }, with(density) { virtH.toDp() })
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = fit; scaleY = fit; translationX = tx; translationY = ty
                        if (dev != DevViewport.AUTO) { clip = true; shape = RoundedCornerShape(28.dp) }
                    },
            ) {
                WallpaperLayer(backdrop, dark)
                HomeSurface(c, win, view, Modifier.fillMaxSize())
            }

            // ---- overlays (real window coordinates)
            val panel = c.panel
            if (panel != Panel.NONE) Appear {
                when (panel) {
                    Panel.SEARCH, Panel.CATEGORY -> LibraryPanel(c)
                    Panel.SETTINGS -> SettingsPanel(c)
                    Panel.WIDGETS -> WidgetsPanel(c)
                    Panel.SCENES -> ScenesPanel(c)
                    Panel.NONE -> {}
                }
            }
            val m = c.menu
            if (m != null) QuickMenu(c, m, realW, realH)
        }
    }
}

/** Fade + slight scale-in for full-screen overlays. */
@Composable
private fun Appear(content: @Composable () -> Unit) {
    var target by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { target = 1f }
    val a by animateFloatAsState(target, tween(220), label = "appear")
    Box(Modifier.fillMaxSize().consumeTaps().graphicsLayer { alpha = a; val sc = 0.97f + 0.03f * a; scaleX = sc; scaleY = sc }) { content() }
}

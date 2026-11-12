package com.duoshell.sonnet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.duoshell.sonnet.core.WallpaperKind
import kotlin.math.max
import kotlin.math.roundToInt

/** Original procedural wallpapers: smooth gradients with soft colour blobs (no third-party art). */
private class Blob(val x: Float, val y: Float, val r: Float, val c: Long)
private class Pal(val top: Long, val bottom: Long, val blobs: List<Blob>)

private fun palette(kind: WallpaperKind): Pal = when (kind) {
    WallpaperKind.DUSK -> Pal(0xFF1B1035, 0xFF5A2A4F, listOf(Blob(.8f, .15f, .7f, 0xFFFF7A59), Blob(.2f, .6f, .7f, 0xFF9B5CFF), Blob(.7f, .9f, .6f, 0xFFFFB36B)))
    WallpaperKind.LAGOON -> Pal(0xFF062B3A, 0xFF0E5C6B, listOf(Blob(.3f, .25f, .7f, 0xFF2EE6C5), Blob(.9f, .7f, .7f, 0xFF2B9CFF), Blob(.1f, .9f, .5f, 0xFF8AF2D4)))
    WallpaperKind.EMBER -> Pal(0xFF260A0A, 0xFF6B1F12, listOf(Blob(.7f, .2f, .7f, 0xFFFF6A3D), Blob(.2f, .8f, .7f, 0xFFFFB347), Blob(.5f, .5f, .5f, 0xFFB3262E)))
    WallpaperKind.GRAPHITE -> Pal(0xFF0E0F13, 0xFF22242B, listOf(Blob(.3f, .2f, .7f, 0xFF3A3F55), Blob(.8f, .8f, .6f, 0xFF2A2F3F)))
    WallpaperKind.MEADOW -> Pal(0xFF0F2A1C, 0xFF2D5A3A, listOf(Blob(.2f, .2f, .7f, 0xFF7BD88F), Blob(.85f, .55f, .6f, 0xFFC9E265), Blob(.4f, .9f, .7f, 0xFF3FA796)))
    else -> Pal(0xFF0B1030, 0xFF123A55, listOf(Blob(.25f, .2f, .7f, 0xFF6C4CFF), Blob(.85f, .35f, .6f, 0xFF14C6B5), Blob(.4f, .85f, .8f, 0xFF2A7BFF)))
}

/** Draws wallpaper art covering a [w] x [h] canvas. SYSTEM draws nothing (window is transparent). */
fun DrawScope.drawWallpaperArt(kind: WallpaperKind, image: ImageBitmap?, w: Float, h: Float) {
    if (kind == WallpaperKind.SYSTEM) return
    if (kind == WallpaperKind.CUSTOM) {
        if (image != null) {
            val s = max(w / image.width, h / image.height)
            val srcW = (w / s).roundToInt().coerceIn(1, image.width)
            val srcH = (h / s).roundToInt().coerceIn(1, image.height)
            drawImage(
                image,
                srcOffset = IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            )
        } else {
            drawRect(Color(0xFF14161C), size = androidx.compose.ui.geometry.Size(w, h))
        }
        return
    }
    val p = palette(kind)
    drawRect(
        Brush.verticalGradient(listOf(Color(p.top), Color(p.bottom)), startY = 0f, endY = h),
        size = androidx.compose.ui.geometry.Size(w, h),
    )
    for (b in p.blobs) {
        val r = b.r * max(w, h) * 0.75f
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(b.c).copy(alpha = 0.55f), Color.Transparent), center = Offset(b.x * w, b.y * h), radius = r),
            radius = r, center = Offset(b.x * w, b.y * h),
        )
    }
}

/** Wallpaper art plus legibility scrims. Same normalised geometry in every layout = continuous across folds. */
@Composable
fun WallpaperLayer(backdrop: BackdropInfo, dark: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier) {
        drawWallpaperArt(backdrop.kind, backdrop.image, size.width, size.height)
        // legibility: gentle top and bottom scrims; stronger when the wallpaper is bright or unknown
        val unknown = backdrop.kind == WallpaperKind.SYSTEM
        val bright = if (unknown) 0.5f else backdrop.luma
        val base = (0.14f + 0.30f * bright).coerceIn(0.1f, 0.42f) + if (dark) 0.06f else 0f
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = base), Color.Transparent), startY = 0f, endY = size.height * 0.28f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = base * 1.15f)), startY = size.height * 0.62f, endY = size.height))
        if (dark) drawRect(Color.Black.copy(alpha = 0.10f))
    }
}

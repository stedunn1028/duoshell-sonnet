package com.duoshell.sonnet.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import com.duoshell.sonnet.core.WallpaperKind

val GlassPill = RoundedCornerShape(percent = 50)
fun glassCard(r: Int = 26) = RoundedCornerShape(r.dp)

private fun DrawScope.outlineOf(shape: Shape): Outline = shape.createOutline(size, layoutDirection, this)

private fun DrawScope.drawSoftShadow(shape: Shape, strong: Boolean) {
    val outline = outlineOf(shape)
    val path = Path().apply { addOutline(outline) }
    clipPath(path, ClipOp.Difference) {
        translate(0f, 7.dp.toPx()) {
            scale(1.03f, 1.03f) { drawOutline(outline, Color.Black.copy(alpha = if (strong) 0.20f else 0.12f)) }
        }
        translate(0f, 2.dp.toPx()) { drawOutline(outline, Color.Black.copy(alpha = if (strong) 0.16f else 0.09f)) }
    }
}

private fun DrawScope.drawGlassLayers(shape: Shape, dark: Boolean, strength: Int, frosted: Boolean, tint: Color?) {
    val outline = outlineOf(shape)
    val w = size.width
    val h = size.height
    // base: translucent body. Without a blurred backdrop we add a little body colour for legibility.
    if (!frosted) drawOutline(outline, if (dark) Color(0xFF12141A).copy(alpha = 0.26f) else Color.White.copy(alpha = 0.10f))
    val a = if (dark) 0.10f + 0.045f * strength else 0.30f + 0.07f * strength
    drawOutline(outline, Color.White.copy(alpha = a + if (frosted) 0f else 0.04f))
    if (tint != null) drawOutline(outline, tint.copy(alpha = 0.22f))
    // top sheen
    drawOutline(outline, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.24f), Color.Transparent), startY = 0f, endY = h * 0.55f))
    // soft specular bloom (top-left)
    drawOutline(outline, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.20f), Color.Transparent), center = Offset(w * 0.18f, h * 0.06f), radius = w * 0.65f))
    // rim light
    drawOutline(
        outline,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.26f)),
            start = Offset(0f, 0f), end = Offset(w, h),
        ),
        style = Stroke(width = 1.2.dp.toPx()),
    )
}

/**
 * Original "liquid glass" material: soft shadow, frosted backdrop (a blurred re-draw of our own
 * wallpaper on API 31+), translucent body, sheen, specular bloom and a rim light.
 * With the system wallpaper (which an app cannot read) it falls back to a tinted translucent body.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = glassCard(),
    tint: Color? = null,
    elevated: Boolean = true,
    strongShadow: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val theme = LocalGlass.current
    val bd = LocalBackdrop.current
    val origin = remember { floatArrayOf(0f, 0f) }
    val canBlur = Build.VERSION.SDK_INT >= 31 && bd.kind != WallpaperKind.SYSTEM && theme.strength > 0
    val blurPx = 6f + 8f * theme.strength
    Box(
        modifier
            .drawBehind { if (elevated) drawSoftShadow(shape, strongShadow) }
            .onGloballyPositioned { val p = it.positionInWindow(); origin[0] = p.x; origin[1] = p.y }
            .clip(shape),
    ) {
        if (canBlur) {
            Canvas(
                Modifier.matchParentSize().graphicsLayer { renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp) },
            ) {
                translate(-origin[0], -origin[1]) { drawWallpaperArt(bd.kind, bd.image, bd.windowW, bd.windowH) }
            }
        }
        Box(Modifier.matchParentSize().drawBehind { drawGlassLayers(shape, theme.dark, theme.strength, canBlur, tint) })
        content()
    }
}

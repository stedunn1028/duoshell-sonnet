package com.duoshell.sonnet.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.duoshell.sonnet.core.WallpaperKind

data class GlassTheme(
    val dark: Boolean,
    val strength: Int, // 0 clear .. 3 frosted
    val panelText: Color,
    val panelSecondary: Color,
    val accent: Color,
)

data class BackdropInfo(
    val kind: WallpaperKind,
    val image: ImageBitmap?,
    val luma: Float,
    val windowW: Float,
    val windowH: Float,
)

val LocalGlass = staticCompositionLocalOf { GlassTheme(true, 2, Color.White, Color(0xB3FFFFFF), Color(0xFF0A84FF)) }
val LocalBackdrop = staticCompositionLocalOf { BackdropInfo(WallpaperKind.SYSTEM, null, 0.4f, 1f, 1f) }

fun glassTheme(dark: Boolean, strength: Int) = GlassTheme(
    dark = dark,
    strength = strength,
    panelText = if (dark) Color.White else Color(0xFF101319),
    panelSecondary = if (dark) Color(0xB3FFFFFF) else Color(0x99101319),
    accent = Color(0xFF0A84FF),
)

val LabelShadow = Shadow(Color(0x99000000), Offset(0f, 1.5f), 5f)

fun txt(size: TextUnit, color: Color, weight: FontWeight = FontWeight.Medium, shadow: Boolean = false) =
    TextStyle(
        color = color, fontSize = size, fontWeight = weight, fontFamily = FontFamily.SansSerif,
        shadow = if (shadow) LabelShadow else null,
    )

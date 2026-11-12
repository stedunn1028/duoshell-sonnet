package com.duoshell.sonnet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duoshell.sonnet.LauncherController

@Composable
fun AppIconImage(c: LauncherController, key: String, size: Dp, modifier: Modifier = Modifier) {
    val bmp = c.repo.icons[key]
    val shape = RoundedCornerShape(size * 0.2237f)
    Box(
        modifier
            .size(size)
            .drawBehind {
                drawRoundRect(
                    Color.Black.copy(alpha = 0.22f),
                    topLeft = Offset(0f, 2.5.dp.toPx()),
                    size = this.size,
                    cornerRadius = CornerRadius(size.toPx() * 0.2237f),
                )
            }
            .clip(shape),
    ) {
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.16f)))
        }
    }
}

@Composable
fun RemoveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(CircleShape).background(Color(0xE6E6E6EA)),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(10.dp, 2.5.dp).background(Color(0xFF202124), RoundedCornerShape(2.dp))) }
}

/**
 * Icon + label stack, centred inside its cell. Laid out at its FINAL size; the fold morph only
 * transforms it in the graphics layer.
 */
@Composable
fun AppCellBody(
    c: LauncherController,
    key: String,
    iconPx: Float,
    cellWpx: Float,
    showLabel: Boolean,
    editing: Boolean,
    hidden: Boolean,
    wobble: State<Float>?,
    onRemove: (() -> Unit)?,
) {
    val d = LocalDensity.current
    val entry = c.appsByKey[key]
    val iconDp = with(d) { iconPx.toDp() }
    val labelW = with(d) { cellWpx.toDp() } - 6.dp
    val sign = if (key.hashCode() % 2 == 0) 1f else -1f
    Column(
        Modifier.fillMaxSize().graphicsLayer { alpha = if (hidden) 0f else 1f },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.graphicsLayer { rotationZ = if (wobble != null) wobble.value * sign else 0f }) {
            AppIconImage(c, key, iconDp)
            if (editing && onRemove != null) {
                RemoveBadge(Modifier.align(Alignment.TopStart).offset((-7).dp, (-7).dp))
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            BasicText(
                entry?.label ?: "",
                modifier = Modifier.width(labelW),
                style = txt(11.sp, Color.White, FontWeight.Medium, shadow = true).copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun contentDesc(label: String, extra: String = "") = Modifier.semantics { contentDescription = if (extra.isEmpty()) label else "$label, $extra" }

package com.duoshell.sonnet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Click without ripple (we don't ship Material); gives a 48dp minimum touch target. */
@Composable
fun Modifier.tap(onClick: () -> Unit, label: String? = null): Modifier {
    val src = remember { MutableInteractionSource() }
    return this
        .semantics(mergeDescendants = true) { if (label != null) contentDescription = label; role = Role.Button }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** Swallows taps so an overlay does not let touches fall through to whatever is beneath it. */
fun Modifier.consumeTaps(): Modifier = this.pointerInput(Unit) { detectTapGestures { } }

@Composable
fun SectionTitle(text: String) {
    val t = LocalGlass.current
    BasicText(text.uppercase(), Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp), style = txt(12.sp, t.panelSecondary, FontWeight.SemiBold))
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalGlass.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (t.dark) Color(0x22FFFFFF) else Color(0x14000000)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).defaultMinSize(minHeight = 40.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (sel) (if (t.dark) Color(0x40FFFFFF) else Color.White) else Color.Transparent)
                    .semantics { role = Role.RadioButton; this.selected = sel; contentDescription = o }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(o, style = txt(13.sp, t.panelText, if (sel) FontWeight.SemiBold else FontWeight.Medium).copy(textAlign = TextAlign.Center), maxLines = 1)
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    val t = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
            .semantics(mergeDescendants = true) { role = Role.Switch; contentDescription = title; this.selected = checked }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = txt(15.sp, t.panelText))
            if (subtitle != null) BasicText(subtitle, style = txt(12.sp, t.panelSecondary, FontWeight.Normal))
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(width = 52.dp, height = 31.dp).clip(RoundedCornerShape(16.dp))
                .background(if (checked) Color(0xFF34C759) else if (t.dark) Color(0x44FFFFFF) else Color(0x33000000)).padding(2.dp),
        ) {
            Box(
                Modifier.size(27.dp).align(if (checked) Alignment.CenterEnd else Alignment.CenterStart).clip(CircleShape).background(Color.White),
            )
        }
    }
}

@Composable
fun ActionRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    val t = LocalGlass.current
    Column(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).tap(onClick, title).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(title, style = txt(15.sp, t.panelText))
        if (subtitle != null) BasicText(subtitle, style = txt(12.sp, t.panelSecondary, FontWeight.Normal))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Color? = null) {
    val t = LocalGlass.current
    val bg = when {
        selected -> accent ?: t.accent
        t.dark -> Color(0x26FFFFFF)
        else -> Color(0x14000000)
    }
    Box(
        modifier.defaultMinSize(minHeight = 40.dp).clip(RoundedCornerShape(20.dp)).background(bg)
            .semantics { role = Role.Button; this.selected = selected; contentDescription = text }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = txt(13.sp, if (selected) Color.White else t.panelText, FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
fun DividerLine() {
    val t = LocalGlass.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(if (t.dark) Color(0x1FFFFFFF) else Color(0x14000000)))
}

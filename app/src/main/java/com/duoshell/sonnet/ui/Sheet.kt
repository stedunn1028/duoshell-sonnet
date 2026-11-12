package com.duoshell.sonnet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Centered glass sheet used by Settings / Widgets / Scenes. Wide screens get a bounded card. */
@Composable
fun Sheet(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, scroll: Boolean = true, content: @Composable () -> Unit) {
    val t = LocalGlass.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (t.dark) 0.45f else 0.30f))
            .tap(onClose, "Close $title"),
    )
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(12.dp), contentAlignment = Alignment.Center) {
        GlassSurface(
            modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight().consumeTaps(),
            shape = RoundedCornerShape(32.dp), strongShadow = true,
        ) {
            Column(Modifier.fillMaxSize().background(if (t.dark) Color(0x990B0D12) else Color(0xB3F4F5F9))) {
                Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(title, Modifier.weight(1f), style = txt(22.sp, t.panelText, FontWeight.Bold))
                    Chip("Done", true, onClose)
                }
                Column(
                    Modifier.fillMaxSize().let { if (scroll) it.verticalScroll(rememberScrollState()) else it }.padding(horizontal = 18.dp, vertical = 6.dp),
                ) { content() }
            }
        }
    }
}

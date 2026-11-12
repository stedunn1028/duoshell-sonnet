package com.duoshell.sonnet.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duoshell.sonnet.LauncherController
import com.duoshell.sonnet.MenuState
import com.duoshell.sonnet.Panel
import com.duoshell.sonnet.core.Categorizer
import com.duoshell.sonnet.core.FRect
import com.duoshell.sonnet.core.SearchLogic

/** Right-hand home pane: "App Library" category cards. Tap a card to open the full category. */
@Composable
fun LibraryPane(c: LauncherController, modifier: Modifier = Modifier) {
    val t = LocalGlass.current
    val cats = remember(c.repo.apps) {
        val by = c.repo.apps.groupBy { it.category }
        Categorizer.order.filter { by.containsKey(it) }.map { it to by.getValue(it) }
    }
    Column(modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText("App Library", style = txt(26.sp, Color.White, FontWeight.Bold, shadow = true))
        GlassSurface(Modifier.fillMaxWidth().height(46.dp).tap({ c.panel = Panel.SEARCH }, "Search apps"), shape = GlassPill) {
            Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                BasicText("Search apps", style = txt(15.sp, t.panelSecondary, FontWeight.Normal))
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(cats, key = { it.first }) { (name, apps) -> CategoryCard(c, name, apps.map { it.key }) }
            item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(90.dp)) }
        }
    }
}

@Composable
private fun CategoryCard(c: LauncherController, name: String, keys: List<String>) {
    val t = LocalGlass.current
    Column(Modifier.fillMaxWidth().tap({ c.categoryName = name; c.panel = Panel.CATEGORY }, "$name, ${keys.size} apps")) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val pad = 12.dp
            val gap = 8.dp
            val icon = (maxWidth - pad * 2 - gap) / 2
            GlassSurface(Modifier.fillMaxSize(), shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (row in keys.take(4).chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (k in row) AppIconImage(c, k, icon)
                            if (row.size == 1) Spacer(Modifier.size(icon))
                        }
                    }
                }
            }
        }
        BasicText(
            name, Modifier.fillMaxWidth().padding(top = 6.dp),
            style = txt(12.sp, Color.White, FontWeight.Medium, shadow = true).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Full-screen Library: search (ranked), category detail, or A-Z list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPanel(c: LauncherController, modifier: Modifier = Modifier) {
    val t = LocalGlass.current
    val category = c.categoryName
    val searching = c.panel == Panel.SEARCH
    var query by remember(c.panel, category) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(c.panel) { if (searching) { runCatching { focus.requestFocus() }; kb?.show() } }

    val freq = remember(c.log) { c.log.groupingBy { it.key }.eachCount() }
    val results = remember(query, c.repo.apps, freq) {
        SearchLogic.rank(query, c.repo.apps.map { Triple(it.key, it.label, it.pkg) }, freq)
    }
    val all = c.repo.apps
    val shown: List<String> = when {
        searching && query.isNotBlank() -> results
        category != null -> all.filter { it.category == category }.map { it.key }
        else -> all.map { it.key }
    }

    Column(
        modifier.fillMaxSize()
            .background(if (t.dark) Color(0xCC0B0D12) else Color(0xCCF2F3F7))
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (searching) {
                GlassSurface(Modifier.weight(1f).height(48.dp), shape = GlassPill) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = txt(16.sp, t.panelText, FontWeight.Normal),
                            cursorBrush = SolidColor(t.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let { c.launch(it, null); c.closePanel() } }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                        if (query.isEmpty()) BasicText("Search apps", style = txt(16.sp, t.panelSecondary, FontWeight.Normal))
                    }
                }
            } else {
                BasicText(category ?: "App Library", Modifier.weight(1f).padding(start = 4.dp), style = txt(24.sp, t.panelText, FontWeight.Bold))
            }
            Spacer(Modifier.size(10.dp))
            Chip("Cancel", false, { c.closePanel() })
        }
        Spacer(Modifier.height(12.dp))
        if (searching && query.isNotBlank() && shown.isEmpty()) {
            BasicText("No apps match “$query”", Modifier.padding(8.dp), style = txt(15.sp, t.panelSecondary, FontWeight.Normal))
        }
        // Group A-Z when browsing everything, plain grid for category / results
        val grouped = !(searching && query.isNotBlank()) && category == null
        LazyVerticalGrid(
            columns = GridCells.Adaptive(84.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (grouped) {
                val groups = shown.mapNotNull { c.appsByKey[it] }.groupBy { it.label.firstOrNull()?.uppercaseChar()?.takeIf { ch -> ch.isLetter() } ?: '#' }.toSortedMap()
                for ((letter, apps) in groups) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h$letter") {
                        BasicText(letter.toString(), Modifier.padding(start = 4.dp, top = 6.dp), style = txt(13.sp, t.panelSecondary, FontWeight.Bold))
                    }
                    items(apps, key = { it.key }) { LibCell(c, it.key) }
                }
            } else {
                items(shown, key = { it }) { LibCell(c, it) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibCell(c: LauncherController, key: String) {
    val t = LocalGlass.current
    val e = c.appsByKey[key] ?: return
    var bounds by remember { mutableStateOf(FRect(0f, 0f, 0f, 0f)) }
    Column(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { val b = it.boundsInWindow(); bounds = FRect(b.left, b.top, b.right, b.bottom) }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClick = {
                    val b = android.graphics.Rect(bounds.l.toInt(), bounds.t.toInt(), bounds.r.toInt(), bounds.b.toInt())
                    c.launch(key, b); c.closePanel()
                },
                onLongClick = { c.menu = MenuState(key, "library", bounds) },
                onClickLabel = "Open ${e.label}", onLongClickLabel = "Show options",
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIconImage(c, key, 58.dp)
        Spacer(Modifier.height(5.dp))
        BasicText(e.label, style = txt(11.sp, t.panelText, FontWeight.Medium).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

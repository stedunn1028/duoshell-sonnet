package com.duoshell.sonnet

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.duoshell.sonnet.core.*
import com.duoshell.sonnet.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class DevViewport(val label: String) { AUTO("Real display"), FOLDED("Folded preview"), UNFOLDED("Unfolded preview") }
enum class Panel { NONE, SEARCH, SETTINGS, WIDGETS, SCENES, CATEGORY }

data class MenuState(val key: String, val source: String, val anchor: FRect)
data class IslandToast(val id: Int, val title: String, val detail: String = "")
data class FoldStats(val frames: Int, val avgMs: Float, val p95Ms: Float, val maxMs: Float, val slow: Int, val durationMs: Int, val label: String)
data class WallpaperImage(val image: ImageBitmap, val luma: Float)

class LauncherController(private val app: Application) {
    val store = Store(app)
    val repo = AppRepository(app)
    val widgets = WidgetHostManager(app)
    val media = MediaWatcher(app)
    val battery = BatteryWatcher(app) { now, old -> onBattery(now, old) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var settings by mutableStateOf(store.loadSettings()); private set
    var log by mutableStateOf(store.loadLog()); private set
    var nowMs by mutableLongStateOf(System.currentTimeMillis()); private set
    var hinge by mutableStateOf<Hinge?>(null)

    // ---- transient UI state
    var editing by mutableStateOf(false)
    var menu by mutableStateOf<MenuState?>(null)
    var panel by mutableStateOf(Panel.NONE)
    var categoryName by mutableStateOf<String?>(null)
    var sceneEditId by mutableStateOf<String?>(null)
    var islandOpen by mutableStateOf(false)
    var toast by mutableStateOf<IslandToast?>(null); private set
    var devViewport by mutableStateOf(DevViewport.AUTO)
    var sceneEpoch by mutableIntStateOf(0); private set
    var lastFoldStats by mutableStateOf<FoldStats?>(null)
    var wallpaperImage by mutableStateOf<WallpaperImage?>(null); private set
    var homePressTick by mutableIntStateOf(0); private set
    var devLoopRunning by mutableStateOf(false); private set

    // ---- hooks wired by MainActivity (things that need an Activity: pickers, widget binding)
    var onPickPhoto: () -> Unit = {}
    var onSystemWallpaper: () -> Unit = {}
    var onPickWidget: (AppWidgetProviderInfo) -> Unit = {}

    private var toastId = 0
    private var autoActivated = false
    private var lastScheduledId: String? = "\u0000"
    private var devLoopJob: Job? = null

    // ---- derived collections
    val appsByKey: Map<String, AppEntry> by derivedStateOf { repo.apps.associateBy { it.key } }
    val activeScene: Scene? get() = settings.scenes.firstOrNull { it.id == settings.activeSceneId }

    val homeKeys: List<String> by derivedStateOf {
        val base = if (repo.loaded) settings.homeApps.filter { appsByKey.containsKey(it) } else settings.homeApps
        SceneLogic.visibleHome(base, activeScene)
    }
    val dockKeys: List<String> by derivedStateOf {
        val scene = activeScene
        val src = if (scene != null && scene.dock.isNotEmpty()) scene.dock else settings.dockApps
        if (repo.loaded) src.filter { appsByKey.containsKey(it) } else src
    }

    // ---- lifecycle
    fun start() {
        repo.start(); battery.start(); widgets.start(); media.start()
        // first load seeds Home/Dock; later changes (installs/uninstalls) prune unavailable apps
        loadJob?.cancel()
        loadJob = scope.launch { snapshotFlow { repo.loaded to repo.apps }.collect { (loaded, _) -> if (loaded) onAppsLoaded() } }
        scope.launch {
            if (settings.wallpaper == WallpaperKind.CUSTOM) loadCustomWallpaper(settings.customWallpaperUri)
        }
        scope.launch { while (true) { tick(); delay(20_000) } }.also { tickJob?.cancel(); tickJob = it }
    }
    private var tickJob: Job? = null
    private var loadJob: Job? = null

    fun stop() {
        repo.stop(); battery.stop(); widgets.stop(); media.stop()
        tickJob?.cancel(); tickJob = null
        loadJob?.cancel(); loadJob = null
    }

    private fun tick() {
        nowMs = System.currentTimeMillis()
        if (!settings.autoScenes || settings.scenes.none { it.schedule.enabled }) return
        val cal = Calendar.getInstance()
        val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Calendar: Sunday=1 -> Monday=0
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val sched = SceneLogic.scheduled(settings.scenes, dow, minute)
        if (sched?.id != lastScheduledId) {
            val first = lastScheduledId == "\u0000"
            lastScheduledId = sched?.id
            if (sched != null) { autoActivated = true; activateScene(sched.id, announce = !first) }
            else if (autoActivated && settings.activeSceneId != null) { autoActivated = false; activateScene(null, announce = true) }
        }
    }

    // ---- settings
    fun update(block: (Settings) -> Settings) {
        settings = block(settings)
        store.saveSettings(settings)
    }

    fun setFocus(pane: Int) { if (pane != settings.focusPane) update { it.copy(focusPane = pane) } }

    // ---- apps
    fun onAppsLoaded() {
        if (!repo.loaded || repo.apps.isEmpty()) return
        if (!settings.seeded) { seed(); return }
        // prune apps that are no longer installed (safe handling of unavailable apps)
        val keys = appsByKey.keys
        val s = settings
        val home = s.homeApps.filter { it in keys }
        val dock = s.dockApps.filter { it in keys }
        if (home.size != s.homeApps.size || dock.size != s.dockApps.size) update { it.copy(homeApps = home, dockApps = dock) }
    }

    private fun pkgsFor(intent: Intent): List<String> = try {
        app.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
    } catch (t: Throwable) { emptyList() }

    private fun firstEntryFor(intent: Intent, taken: Set<String>): String? {
        val pkgs = pkgsFor(intent)
        for (p in pkgs) {
            val e = repo.apps.firstOrNull { it.pkg == p && it.key !in taken }
            if (e != null) return e.key
        }
        return null
    }

    private fun mainCat(cat: String) = Intent(Intent.ACTION_MAIN).addCategory(cat)

    private fun seed() {
        val taken = LinkedHashSet<String>()
        val dock = ArrayList<String>()
        val dockIntents = listOf(
            Intent(Intent.ACTION_DIAL), mainCat(Intent.CATEGORY_APP_MESSAGING),
            mainCat(Intent.CATEGORY_APP_BROWSER), Intent("android.media.action.STILL_IMAGE_CAMERA"),
        )
        for (i in dockIntents) firstEntryFor(i, taken)?.let { dock += it; taken += it }
        val starters = listOf(
            mainCat(Intent.CATEGORY_APP_GALLERY), mainCat(Intent.CATEGORY_APP_EMAIL), mainCat(Intent.CATEGORY_APP_MAPS),
            mainCat(Intent.CATEGORY_APP_CALENDAR), mainCat(Intent.CATEGORY_APP_MUSIC), mainCat(Intent.CATEGORY_APP_CONTACTS),
            mainCat(Intent.CATEGORY_APP_MARKET), Intent(AndroidSettings.ACTION_SETTINGS),
        )
        val home = ArrayList<String>()
        for (i in starters) firstEntryFor(i, taken)?.let { home += it; taken += it }
        val cap = settings.pageCapacity
        for (e in repo.apps) {
            if (home.size >= cap * 2) break
            if (e.key !in taken) { home += e.key; taken += e.key }
        }
        update { it.copy(seeded = true, homeApps = home, dockApps = dock) }
    }

    fun launch(key: String, bounds: android.graphics.Rect?) {
        val e = repo.find(key)
        if (e == null || !repo.launch(e, bounds)) {
            postToast("Can't open app", "It may have been removed or disabled")
            return
        }
        val cal = Calendar.getInstance()
        val ev = LaunchEvent(key, cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE), (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7, System.currentTimeMillis() / 60000)
        log = (log + ev).takeLast(600)
        store.saveLog(log)
    }

    fun frequentKeys(max: Int = 6): List<String> =
        SceneLogic.frequent(log, max + 4).filter { appsByKey.containsKey(it) }.take(max)

    fun removeFromHome(key: String) = update { it.copy(homeApps = it.homeApps - key) }
    fun addToHome(key: String) = update { if (key in it.homeApps) it else it.copy(homeApps = it.homeApps + key, dockApps = it.dockApps - key) }

    fun pinToDock(key: String, index: Int? = null) = update { s ->
        val dock = s.dockApps.filter { it != key }.toMutableList()
        if (dock.size >= 6) return@update s
        dock.add((index ?: dock.size).coerceIn(0, dock.size), key)
        s.copy(dockApps = dock, homeApps = s.homeApps - key)
    }

    fun unpinFromDock(key: String) = update { s -> s.copy(dockApps = s.dockApps - key, homeApps = if (key in s.homeApps) s.homeApps else s.homeApps + key) }

    fun moveInDock(key: String, delta: Int) = update { s ->
        val i = s.dockApps.indexOf(key)
        if (i < 0) s else s.copy(dockApps = Paging.move(s.dockApps, key, (i + delta).coerceIn(0, s.dockApps.size - 1)))
    }

    /** Reorders within the *visible* list (scene filter aware) by anchoring to the neighbour at [targetVisibleIndex]. */
    fun reorderHome(key: String, targetVisibleIndex: Int) = update { s ->
        val vis = homeKeys.filter { it != key }
        val before = vis.getOrNull(targetVisibleIndex.coerceAtLeast(0))
        val base = if (key in s.homeApps) s.homeApps else s.homeApps + key
        s.copy(homeApps = Paging.moveBefore(base, key, before), dockApps = s.dockApps - key)
    }

    // ---- scenes (flagship)
    fun activateScene(id: String?, announce: Boolean = true) {
        if (settings.activeSceneId == id) return
        update { it.copy(activeSceneId = id) }
        sceneEpoch++
        if (!id.isNullOrEmpty() && announce) postToast(activeScene?.name ?: "Scene", "Scene on")
        else if (id == null && announce) postToast("All apps", "Scene off")
    }

    fun upsertScene(scene: Scene) = update { s ->
        val exists = s.scenes.any { it.id == scene.id }
        s.copy(scenes = if (exists) s.scenes.map { if (it.id == scene.id) scene else it } else s.scenes + scene)
    }

    fun deleteScene(id: String) {
        if (settings.activeSceneId == id) activateScene(null, false)
        update { it.copy(scenes = it.scenes.filter { s -> s.id != id }) }
    }

    fun newSceneId() = "scene-" + System.currentTimeMillis().toString(36)

    /** Suggests apps from this user's own on-device launch log around the current time. */
    fun suggestedForNow(): List<String> {
        val cal = Calendar.getInstance()
        val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return SceneLogic.suggestApps(log, dow, minute).filter { appsByKey.containsKey(it) }
    }

    // ---- Today / widgets
    fun addToday(kind: String, arg: String = "") = update { it.copy(today = it.today + TodayItem(kind, arg)) }
    fun removeToday(index: Int) = update { s ->
        val item = s.today.getOrNull(index)
        if (item != null && item.kind == "appwidget") item.arg.toIntOrNull()?.let { widgets.delete(it) }
        s.copy(today = s.today.filterIndexed { i, _ -> i != index },
            appWidgetIds = if (item?.kind == "appwidget") s.appWidgetIds - (item.arg.toIntOrNull() ?: -1) else s.appWidgetIds)
    }
    fun moveToday(index: Int, delta: Int) = update { s ->
        val j = index + delta
        if (index !in s.today.indices || j !in s.today.indices) s
        else s.copy(today = s.today.toMutableList().also { val t = it.removeAt(index); it.add(j, t) })
    }
    fun commitAppWidget(id: Int) = update { it.copy(today = it.today + TodayItem("appwidget", id.toString()), appWidgetIds = it.appWidgetIds + id) }
    fun setNote(text: String) = update { it.copy(noteText = text) }

    // ---- wallpaper
    fun setWallpaper(kind: WallpaperKind) = update { it.copy(wallpaper = kind) }

    fun setCustomWallpaper(uri: Uri) {
        update { it.copy(wallpaper = WallpaperKind.CUSTOM, customWallpaperUri = uri.toString()) }
        scope.launch { loadCustomWallpaper(uri.toString()) }
    }

    private suspend fun loadCustomWallpaper(uriStr: String?) {
        if (uriStr == null) { wallpaperImage = null; return }
        val result = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriStr)
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                var sample = 1
                while (maxOf(opts.outWidth, opts.outHeight) / sample > 1800) sample *= 2
                val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
                if (bmp == null) null else {
                    val tiny = android.graphics.Bitmap.createScaledBitmap(bmp, 8, 8, true)
                    var sum = 0f
                    for (x in 0 until 8) for (y in 0 until 8) {
                        val p = tiny.getPixel(x, y)
                        sum += (0.2126f * ((p shr 16) and 255) + 0.7152f * ((p shr 8) and 255) + 0.0722f * (p and 255)) / 255f
                    }
                    WallpaperImage(bmp.asImageBitmap(), sum / 64f)
                }
            } catch (t: Throwable) { null }
        }
        wallpaperImage = result
        if (result == null) postToast("Couldn't load photo", "Using a built-in wallpaper")
    }

    // ---- island toasts
    fun postToast(title: String, detail: String = "") {
        val id = ++toastId
        toast = IslandToast(id, title, detail)
        scope.launch { delay(2600); if (toast?.id == id) toast = null }
    }

    private fun onBattery(now: BatteryInfo, old: BatteryInfo?) {
        if (old != null && now.charging != old.charging) {
            postToast(if (now.charging) "Charging" else "Unplugged", "${now.percent}%")
        }
    }

    // ---- navigation
    /** Back handling for a launcher: unwind overlays, then edit mode, then return to first page. */
    fun onBack() {
        when {
            menu != null -> menu = null
            panel != Panel.NONE -> closePanel()
            islandOpen -> islandOpen = false
            editing -> editing = false
            else -> setFocus(1)
        }
    }

    fun closePanel() { panel = Panel.NONE; categoryName = null; sceneEditId = null }

    fun onHomePressed() {
        menu = null; closePanel(); islandOpen = false; editing = false
        setFocus(1)
        homePressTick++
    }

    // ---- fold memo (recreation fallback) + dev preview
    fun saveMemo(input: LayoutInput, focus: Int) = store.saveMemo(LayoutMemo.encode(input, focus, System.currentTimeMillis()))
    fun loadMemo(): LayoutMemo.Memo? = LayoutMemo.decode(store.loadMemo())

    fun cycleViewport() {
        devViewport = when (devViewport) {
            DevViewport.AUTO -> DevViewport.FOLDED
            DevViewport.FOLDED -> DevViewport.UNFOLDED
            DevViewport.UNFOLDED -> DevViewport.FOLDED
        }
    }

    fun runFoldLoop(cycles: Int) {
        devLoopJob?.cancel()
        devLoopJob = scope.launch {
            devLoopRunning = true
            val start = devViewport
            devViewport = DevViewport.FOLDED
            delay(900)
            repeat(cycles) {
                devViewport = DevViewport.UNFOLDED; delay(1700)
                devViewport = DevViewport.FOLDED; delay(1700)
            }
            devViewport = start
            devLoopRunning = false
        }
    }

    fun stopFoldLoop() { devLoopJob?.cancel(); devLoopRunning = false }

    fun openUninstall(key: String) {
        val e = repo.find(key) ?: return
        try {
            app.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${e.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) { postToast("Can't uninstall", "Use system settings") }
    }

    fun openNotificationAccess() {
        try { app.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (t: Throwable) { postToast("Couldn't open settings") }
    }

    fun openDefaultHomeSettings() {
        try { app.startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (t: Throwable) {
            try { app.startActivity(Intent(AndroidSettings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
        }
    }

    fun setViewport(v: DevViewport) { devViewport = v }

    /** True when DuoShell currently holds the Home role (or is the resolved HOME activity on older Android). */
    fun isDefaultHome(): Boolean = try {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val rm = app.getSystemService(android.app.role.RoleManager::class.java)
            rm != null && rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
        } else {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            app.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == app.packageName
        }
    } catch (t: Throwable) { false }
}

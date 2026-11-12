package com.duoshell.sonnet.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.duoshell.sonnet.core.Categorizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppEntry(
    val key: String,
    val pkg: String,
    val cls: String,
    val label: String,
    val category: String,
    val component: ComponentName,
    val user: UserHandle,
)

/**
 * Installed-app discovery through the public LauncherApps API (no QUERY_ALL_PACKAGES needed),
 * with live refresh via LauncherApps.Callback.
 */
class AppRepository(private val ctx: Context) {
    private val launcherApps = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null
    private val iconPx = 192

    var apps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    val icons = mutableStateMapOf<String, ImageBitmap>()

    private val byKey: Map<String, AppEntry> get() = apps.associateBy { it.key }
    fun find(key: String): AppEntry? = apps.firstOrNull { it.key == key }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = scheduleRefresh(packageName, true)
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = scheduleRefresh(packageName, false)
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = scheduleRefresh(packageName, true)
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = scheduleRefresh(null, true)
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = scheduleRefresh(null, true)
    }

    fun start() {
        try { launcherApps.registerCallback(callback, Handler(Looper.getMainLooper())) } catch (_: Throwable) {}
        scheduleRefresh(null, false)
    }

    fun stop() {
        try { launcherApps.unregisterCallback(callback) } catch (_: Throwable) {}
    }

    private fun scheduleRefresh(pkg: String?, invalidateIcons: Boolean) {
        if (invalidateIcons) {
            if (pkg == null) icons.clear() else icons.keys.filter { it.startsWith("$pkg/") }.forEach { icons.remove(it) }
        }
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(120) // coalesce bursts of package broadcasts
            refreshNow()
        }
    }

    private suspend fun refreshNow() = withContext(Dispatchers.Default) {
        val infos = try { launcherApps.getActivityList(null, Process.myUserHandle()) } catch (t: Throwable) { emptyList() }
        val entries = infos.filter { it.componentName.packageName != ctx.packageName }.map { info ->
            val cn = info.componentName
            val label = try { info.label?.toString() ?: cn.packageName } catch (t: Throwable) { cn.packageName }
            val cat = try { info.applicationInfo.category } catch (t: Throwable) { ApplicationInfo.CATEGORY_UNDEFINED }
            AppEntry("${cn.packageName}/${cn.className}", cn.packageName, cn.className, label,
                Categorizer.categorize(cn.packageName, label, cat), cn, info.user)
        }.sortedBy { it.label.lowercase() }

        // render missing icons (first paint uses already-cached ones; the rest fill in)
        for (info in infos) {
            val cn = info.componentName
            val key = "${cn.packageName}/${cn.className}"
            if (icons.containsKey(key) || cn.packageName == ctx.packageName) continue
            try {
                val d = info.getIcon(0)
                if (d != null) icons[key] = IconRenderer.render(d, iconPx).asImageBitmap()
            } catch (_: Throwable) {}
        }
        apps = entries
        loaded = true
    }

    fun launch(entry: AppEntry, bounds: Rect?): Boolean = try {
        launcherApps.startMainActivity(entry.component, entry.user, bounds, null); true
    } catch (t: Throwable) { false }

    fun shortcuts(entry: AppEntry): List<ShortcutInfo> = try {
        val q = LauncherApps.ShortcutQuery()
            .setPackage(entry.pkg)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        (launcherApps.getShortcuts(q, entry.user) ?: emptyList()).filter { it.isEnabled }.take(4)
    } catch (t: Throwable) { emptyList() } // SecurityException when not the default Home app

    fun launchShortcut(s: ShortcutInfo): Boolean = try {
        launcherApps.startShortcut(s, null, null); true
    } catch (t: Throwable) { false }

    fun openAppInfo(entry: AppEntry): Boolean = try {
        launcherApps.startAppDetailsActivity(entry.component, entry.user, null, null); true
    } catch (t: Throwable) { false }
}

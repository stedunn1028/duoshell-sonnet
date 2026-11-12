package com.duoshell.sonnet.data

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent

/** Thin wrapper over the public AppWidgetHost APIs (user-approved binding, no special permission). */
class WidgetHostManager(private val ctx: Context) {
    companion object {
        const val HOST_ID = 0xD0051
        const val REQ_BIND = 4101
        const val REQ_CONFIGURE = 4102
    }

    private val manager = AppWidgetManager.getInstance(ctx)
    private val host = AppWidgetHost(ctx, HOST_ID)

    fun start() { try { host.startListening() } catch (_: Throwable) {} }
    fun stop() { try { host.stopListening() } catch (_: Throwable) {} }

    fun providers(): List<AppWidgetProviderInfo> = try {
        manager.installedProviders.filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            .sortedBy { it.loadLabel(ctx.packageManager)?.lowercase() ?: "" }
    } catch (t: Throwable) { emptyList() }

    fun info(id: Int): AppWidgetProviderInfo? = try { manager.getAppWidgetInfo(id) } catch (t: Throwable) { null }

    fun allocate(): Int = host.allocateAppWidgetId()
    fun delete(id: Int) { try { host.deleteAppWidgetId(id) } catch (_: Throwable) {} }

    /** True if bound immediately; false means the user must approve via [bindIntent]. */
    fun tryBind(id: Int, p: AppWidgetProviderInfo): Boolean = try {
        manager.bindAppWidgetIdIfAllowed(id, p.provider)
    } catch (t: Throwable) { false }

    fun bindIntent(id: Int, p: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider)

    fun configure(activity: Activity, id: Int) {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE, null)
    }

    fun createView(context: Context, id: Int, p: AppWidgetProviderInfo): AppWidgetHostView =
        host.createView(context, id, p)

    fun labelOf(p: AppWidgetProviderInfo): String = try { p.loadLabel(ctx.packageManager) ?: p.provider.packageName } catch (t: Throwable) { p.provider.packageName }

    fun sizeLabel(p: AppWidgetProviderInfo): String {
        val d = ctx.resources.displayMetrics.density
        return "${(p.minWidth / d).toInt()} \u00D7 ${(p.minHeight / d).toInt()} dp"
    }
}

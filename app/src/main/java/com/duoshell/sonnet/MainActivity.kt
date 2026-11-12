package com.duoshell.sonnet

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.duoshell.sonnet.core.FRect
import com.duoshell.sonnet.core.Hinge
import com.duoshell.sonnet.data.WidgetHostManager
import com.duoshell.sonnet.ui.Root
import kotlinx.coroutines.launch

/**
 * The Home activity. Declares HOME + LAUNCHER filters and handles every configuration change itself
 * (see manifest), so folding/unfolding never recreates it: Compose re-lays-out and the morph engine
 * animates between layouts. If the system does recreate us anyway, a persisted layout memo lets
 * the first frame still morph from the previous layout.
 */
class MainActivity : ComponentActivity() {
    private lateinit var controller: LauncherController
    private var pendingWidgetId = -1
    private var pendingProvider: AppWidgetProviderInfo? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
            controller.setCustomWallpaper(uri)
        }
    }

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingWidgetId
        val p = pendingProvider
        if (r.resultCode == Activity.RESULT_OK && id >= 0 && p != null) afterBind(id, p)
        else { if (id >= 0) controller.widgets.delete(id); resetPending() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = (application as DuoApp).controller
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        controller.onPickPhoto = { pickPhoto.launch(arrayOf("image/*")) }
        controller.onSystemWallpaper = {
            try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")) }
            catch (t: Throwable) { controller.postToast("Wallpaper picker unavailable") }
        }
        controller.onPickWidget = { p -> beginWidget(p) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collect { info ->
                    val f = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    controller.hinge = f?.let {
                        val b = it.bounds
                        Hinge(
                            FRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()),
                            vertical = it.orientation == FoldingFeature.Orientation.VERTICAL,
                            halfOpened = it.state == FoldingFeature.State.HALF_OPENED,
                            separating = it.isSeparating,
                        )
                    }
                }
            }
        }

        setContent { Root(controller) }
    }

    override fun onStart() { super.onStart(); controller.start() }
    override fun onStop() { controller.stop(); super.onStop() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Home button / gesture while we're already running: go to the first page and close overlays.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) controller.onHomePressed()
    }

    private fun beginWidget(p: AppWidgetProviderInfo) {
        val id = controller.widgets.allocate()
        pendingWidgetId = id
        pendingProvider = p
        if (controller.widgets.tryBind(id, p)) afterBind(id, p)
        else {
            try { bindWidget.launch(controller.widgets.bindIntent(id, p)) }
            catch (t: Throwable) { controller.widgets.delete(id); resetPending(); controller.postToast("Can't add widget") }
        }
    }

    private fun afterBind(id: Int, p: AppWidgetProviderInfo) {
        if (p.configure != null) {
            try { controller.widgets.configure(this, id); return } catch (_: Throwable) { /* fall through: add unconfigured */ }
        }
        commitWidget(id)
    }

    private fun commitWidget(id: Int) {
        controller.commitAppWidget(id)
        controller.closePanel()
        controller.setFocus(0)
        resetPending()
    }

    private fun resetPending() { pendingWidgetId = -1; pendingProvider = null }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WidgetHostManager.REQ_CONFIGURE) {
            val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
            if (resultCode == Activity.RESULT_OK && id >= 0) commitWidget(id)
            else { if (id >= 0) controller.widgets.delete(id); resetPending() }
        }
    }
}

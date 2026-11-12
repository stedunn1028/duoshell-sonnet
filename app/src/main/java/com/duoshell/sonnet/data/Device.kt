package com.duoshell.sonnet.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.duoshell.sonnet.MediaAccessService

data class BatteryInfo(val percent: Int, val charging: Boolean)
data class MediaInfo(val title: String, val artist: String, val playing: Boolean, val pkg: String)

/** Honest system info only: battery level and charging state from the public sticky broadcast. */
class BatteryWatcher(private val ctx: Context, private val onChange: (BatteryInfo, BatteryInfo?) -> Unit) {
    var info by mutableStateOf(BatteryInfo(100, false)); private set
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { if (i != null) update(i) }
    }

    private fun update(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val charging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else info.percent
        val old = info
        info = BatteryInfo(pct, charging)
        if (started) onChange(info, old)
    }

    fun start() {
        val sticky = ContextCompat.registerReceiver(ctx, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (sticky != null) update(sticky)
        started = true
    }

    fun stop() { try { ctx.unregisterReceiver(receiver) } catch (_: Throwable) {}; started = false }
}

/**
 * Optional now-playing info. Works only after the user enables Notification access for this app
 * in system settings; until then [enabled] is false and nothing is read.
 */
class MediaWatcher(private val ctx: Context) {
    var info by mutableStateOf<MediaInfo?>(null); private set
    var enabled by mutableStateOf(false); private set

    private var controller: MediaController? = null
    private val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val component = ComponentName(ctx, MediaAccessService::class.java)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    private val ctrlCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { publish() }
        override fun onPlaybackStateChanged(state: PlaybackState?) { publish() }
        override fun onSessionDestroyed() { pick(null) }
    }

    fun isAccessGranted(): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun start() {
        if (!isAccessGranted()) { enabled = false; info = null; return }
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component)
            pick(msm.getActiveSessions(component))
            enabled = true
        } catch (t: Throwable) { enabled = false }
    }

    fun stop() {
        try { msm.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Throwable) {}
        try { controller?.unregisterCallback(ctrlCallback) } catch (_: Throwable) {}
        controller = null
    }

    private fun pick(list: List<MediaController>?) {
        try { controller?.unregisterCallback(ctrlCallback) } catch (_: Throwable) {}
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        controller = best
        try { best?.registerCallback(ctrlCallback) } catch (_: Throwable) {}
        publish()
    }

    private fun publish() {
        val c = controller
        val md = c?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        info = if (c == null || title.isNullOrBlank()) null else MediaInfo(
            title, md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            c.playbackState?.state == PlaybackState.STATE_PLAYING, c.packageName ?: "",
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
}

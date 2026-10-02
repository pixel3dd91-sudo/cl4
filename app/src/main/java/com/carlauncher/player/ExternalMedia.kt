package com.carlauncher.player

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock

/** کنترل پخش‌کننده‌های دیگر (Spotify، YouTube Music، موزیک بلوتوث و ...) از طریق MediaSession */
class ExternalMedia(private val ctx: Context, private val onChange: () -> Unit) {

    private val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val cn = ComponentName(ctx, MediaListener::class.java)
    private var controllers: List<MediaController> = emptyList()
    private var listening = false

    var active: MediaController? = null
        private set
    var accessGranted = false
        private set

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> update(list) }

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { pick(); onChange() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { onChange() }
        override fun onSessionDestroyed() { update(try { msm.getActiveSessions(cn) } catch (e: Exception) { null }); onChange() }
    }

    fun start() {
        try {
            if (!listening) {
                msm.addOnActiveSessionsChangedListener(sessionListener, cn)
                listening = true
            }
            update(msm.getActiveSessions(cn))
            accessGranted = true
        } catch (e: SecurityException) {
            accessGranted = false
        } catch (e: Exception) {
            accessGranted = false
        }
    }

    fun stop() {
        try { if (listening) msm.removeOnActiveSessionsChangedListener(sessionListener) } catch (_: Exception) {}
        listening = false
        for (c in controllers) {
            try { c.unregisterCallback(cb) } catch (_: Exception) {}
        }
        controllers = emptyList()
        active = null
    }

    private fun update(list: List<MediaController>?) {
        for (c in controllers) {
            try { c.unregisterCallback(cb) } catch (_: Exception) {}
        }
        controllers = (list ?: emptyList()).filter { it.packageName != ctx.packageName }
        for (c in controllers) {
            try { c.registerCallback(cb) } catch (_: Exception) {}
        }
        pick()
        onChange()
    }

    private fun pick() {
        val playing = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        if (playing != null) {
            active = playing
            return
        }
        val cur = active
        if (cur != null && controllers.any { it.sessionToken == cur.sessionToken }) return
        active = controllers.firstOrNull()
    }

    val isPlaying: Boolean
        get() = active?.playbackState?.state == PlaybackState.STATE_PLAYING

    val title: String?
        get() = active?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)

    val artist: String?
        get() {
            val m = active?.metadata ?: return null
            return m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        }

    val art: Bitmap?
        get() {
            val m = active?.metadata ?: return null
            return m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }

    val duration: Int
        get() = (active?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).toInt()

    val position: Int
        get() {
            val st = active?.playbackState ?: return 0
            var p = st.position
            if (st.state == PlaybackState.STATE_PLAYING) {
                p += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
            }
            return p.toInt().coerceAtLeast(0)
        }

    val appLabel: String
        get() {
            val pkg = active?.packageName ?: return ""
            return try {
                val pm = ctx.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
        }
}

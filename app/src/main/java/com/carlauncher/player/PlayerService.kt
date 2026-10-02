package com.carlauncher.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import kotlin.random.Random

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val albumId: Long,
    val path: String?
)

class PlayerService : Service() {

    inner class LocalBinder : Binder() {
        val service: PlayerService get() = this@PlayerService
    }

    private val binder = LocalBinder()
    private val mp = MediaPlayer()
    private var prepared = false
    private lateinit var prefs: Prefs
    private var audioManager: AudioManager? = null
    private var resumeOnFocus = false
    private var session: MediaSession? = null
    private var art: Bitmap? = null

    var allTracks: List<Track> = emptyList()
        private set
    var queue: List<Track> = emptyList()
        private set
    var index = 0
        private set
    var listener: (() -> Unit)? = null
    var resumeDone = false

    var eq: Equalizer? = null
    var bass: BassBoost? = null
    var virt: Virtualizer? = null

    val currentTrack: Track? get() = queue.getOrNull(index)
    val isPlaying: Boolean get() = try { prepared && mp.isPlaying } catch (e: Exception) { false }
    val position: Int get() = if (prepared) try { mp.currentPosition } catch (e: Exception) { 0 } else 0
    val duration: Int get() = if (prepared) try { mp.duration } catch (e: Exception) { 0 } else 0
    val audioSessionId: Int get() = mp.audioSessionId

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        try {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> { resumeOnFocus = false; if (isPlaying) mp.pause() }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { if (isPlaying) { resumeOnFocus = true; mp.pause() } }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mp.setVolume(0.3f, 0.3f)
                AudioManager.AUDIOFOCUS_GAIN -> {
                    mp.setVolume(1f, 1f)
                    if (resumeOnFocus) { mp.start(); resumeOnFocus = false }
                }
            }
        } catch (_: Exception) {}
        changed()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        )
        mp.setOnCompletionListener { onEnd() }
        mp.setOnErrorListener { _, _, _ -> prepared = false; changed(); true }
        initSession()
        initEffects()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // ---------- MediaSession (دکمه‌های فرمان / پنل ماشین) ----------
    @Suppress("DEPRECATION")
    private fun initSession() {
        try {
            val s = MediaSession(this, "CarLauncher")
            s.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            s.setCallback(object : MediaSession.Callback() {
                override fun onPlay() { if (!isPlaying) toggle() }
                override fun onPause() { if (isPlaying) toggle() }
                override fun onSkipToNext() { next() }
                override fun onSkipToPrevious() { prev() }
                override fun onSeekTo(pos: Long) { seekTo(pos.toInt()) }
            })
            s.setActive(true)
            session = s
        } catch (_: Throwable) {}
    }

    private fun updateSession() {
        val s = session ?: return
        try {
            val st = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            s.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO
                    )
                    .setState(st, position.toLong(), 1f).build()
            )
            val t = currentTrack
            if (t != null) {
                val mb = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration.toLong())
                val a = art
                if (a != null) mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, a)
                s.setMetadata(mb.build())
            }
        } catch (_: Throwable) {}
    }

    fun setArt(b: Bitmap?) {
        art = b
        updateSession()
    }

    private fun changed() {
        updateSession()
        listener?.invoke()
    }

    // ---------- Effects ----------
    private fun initEffects() {
        val sid = mp.audioSessionId
        try { eq = Equalizer(0, sid) } catch (_: Throwable) { eq = null }
        try { bass = BassBoost(0, sid) } catch (_: Throwable) { bass = null }
        try { virt = Virtualizer(0, sid) } catch (_: Throwable) { virt = null }
        applyEffects()
    }

    fun applyEffects() {
        val on = prefs.eqOn
        try {
            val e = eq
            if (e != null) {
                e.setEnabled(on)
                val levels = prefs.eqBands.split(",").mapNotNull { it.trim().toIntOrNull() }
                val n = e.numberOfBands.toInt()
                for (b in 0 until n) {
                    if (b < levels.size) e.setBandLevel(b.toShort(), levels[b].toShort())
                }
            }
            val bb = bass
            if (bb != null && bb.strengthSupported) {
                bb.setEnabled(on && prefs.bass > 0)
                bb.setStrength(prefs.bass.toShort())
            }
            val vv = virt
            if (vv != null && vv.strengthSupported) {
                vv.setEnabled(on && prefs.virt > 0)
                vv.setStrength(prefs.virt.toShort())
            }
        } catch (_: Throwable) {}
    }

    // ---------- Notification ----------
    private fun showForeground() {
        val channel = "player"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(channel, "Player", NotificationManager.IMPORTANCE_LOW))
        }
        val flags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel) else Notification.Builder(this)
        b.setSmallIcon(R.drawable.ic_play)
            .setContentTitle(currentTrack?.title ?: "Car Launcher")
            .setContentText(currentTrack?.artist ?: "")
            .setContentIntent(pi)
        startForeground(1, b.build())
    }

    // ---------- Library ----------
    @Suppress("DEPRECATION")
    fun loadTracks() {
        val list = ArrayList<Track>()
        try {
            val dataCol = MediaStore.Audio.Media.DATA
            val proj = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID, dataCol
            )
            val c = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )
            if (c != null) {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val artist = c.getString(2)
                    val album = c.getString(3)
                    list.add(
                        Track(
                            id,
                            c.getString(1) ?: "Unknown",
                            if (artist == null || artist == "<unknown>") "" else artist,
                            if (album == null || album == "<unknown>") "" else album,
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                            c.getLong(4),
                            c.getString(5)
                        )
                    )
                }
                c.close()
            }
        } catch (_: Exception) {}
        val keepId = if (prepared) (currentTrack?.id ?: -1L) else prefs.lastTrackId
        allTracks = list
        queue = list
        val idx = list.indexOfFirst { it.id == keepId }
        index = if (idx >= 0) idx else 0
        changed()
    }

    fun playQueue(list: List<Track>, pos: Int) {
        if (list.isEmpty()) return
        queue = list
        play(pos)
    }

    @Suppress("DEPRECATION")
    private fun requestFocus() {
        audioManager?.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }

    fun play(i: Int) {
        if (queue.isEmpty()) return
        index = ((i % queue.size) + queue.size) % queue.size
        try {
            mp.reset()
            mp.setDataSource(this, queue[index].uri)
            mp.prepare()
            prepared = true
            requestFocus()
            mp.start()
        } catch (e: Exception) {
            prepared = false
        }
        saveState()
        showForeground()
        changed()
    }

    fun toggle() {
        if (queue.isEmpty()) return
        if (!prepared) { play(index); return }
        try {
            if (mp.isPlaying) mp.pause() else { requestFocus(); mp.start() }
        } catch (_: Exception) {}
        saveState()
        changed()
    }

    fun pause() {
        try { if (isPlaying) mp.pause() } catch (_: Exception) {}
        saveState()
        changed()
    }

    private fun randomIndex(): Int {
        if (queue.size <= 1) return 0
        var r = Random.nextInt(queue.size)
        while (r == index) r = Random.nextInt(queue.size)
        return r
    }

    fun next() {
        if (queue.isEmpty()) return
        if (prefs.shuffle) play(randomIndex()) else play(index + 1)
    }

    fun prev() {
        if (position > 3000) seekTo(0) else play(index - 1)
    }

    private fun onEnd() {
        when {
            queue.isEmpty() -> {}
            prefs.repeat == 2 -> play(index)
            prefs.shuffle -> play(randomIndex())
            index + 1 < queue.size -> play(index + 1)
            prefs.repeat == 1 -> play(0)
            else -> changed()
        }
    }

    fun seekTo(ms: Int) {
        if (prepared) try { mp.seekTo(ms) } catch (_: Exception) {}
    }

    fun saveState() {
        val t = currentTrack ?: return
        prefs.lastTrackId = t.id
        prefs.lastPos = position
    }

    /** ادامه‌ی آخرین آهنگ از همان ثانیه‌ای که قطع شده بود */
    fun resumeLast() {
        if (resumeDone) return
        resumeDone = true
        if (queue.isEmpty() || prepared) return
        val pos = prefs.lastPos
        play(index)
        if (pos > 0) seekTo(pos)
    }

    override fun onDestroy() {
        saveState()
        try { eq?.release() } catch (_: Throwable) {}
        try { bass?.release() } catch (_: Throwable) {}
        try { virt?.release() } catch (_: Throwable) {}
        try { session?.release() } catch (_: Throwable) {}
        try { mp.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}

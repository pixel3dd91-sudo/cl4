package com.carlauncher.player

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private var svc: PlayerService? = null
    private var ext: ExternalMedia? = null
    private var extMode = false
    private val handler = Handler(Looper.getMainLooper())
    private var builtVersion = -1

    private lateinit var root: FrameLayout
    private lateinit var bgBox: FrameLayout
    private lateinit var bgA: ImageView
    private lateinit var bgB: ImageView
    private lateinit var clockTv: TextView
    private lateinit var dateTv: TextView
    private lateinit var sourceTv: TextView
    private lateinit var speedTv: TextView
    private lateinit var weatherTv: TextView
    private lateinit var lyricTv: TextView
    private lateinit var titleTv: TextView
    private lateinit var artistTv: TextView
    private lateinit var infoTv: TextView
    private lateinit var cover: CoverView
    private lateinit var bars: BarsView
    private lateinit var progress: ProgressLine
    private lateinit var btnPlay: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var slotsRow: LinearLayout

    private var accent = 0xFFE53935.toInt()
    private var dynAccent = 0xFFE53935.toInt()
    private var accentAnim: ValueAnimator? = null
    private var bgZoom: ObjectAnimator? = null
    private var currentArt: Bitmap? = null
    private var bgBlur: Bitmap? = null
    private var shownKey = ""
    private var lyrics: List<Pair<Long, String>> = emptyList()
    private var attachedSession = -1

    private var locationManager: LocationManager? = null
    private var lastLoc: Location? = null
    private var weatherAt = 0L
    private var askedLoc = false

    private val locListener = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            lastLoc = l
            if (prefs.showSpeed) {
                val kmh = if (l.hasSpeed()) (l.speed * 3.6f).roundToInt() else 0
                speedTv.setFa("$kmh km/h", true)
            }
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName, b: IBinder) {
            val s = (b as PlayerService.LocalBinder).service
            svc = s
            s.listener = { runOnUiThread { refreshTrack() } }
            if (granted(audioPerm()) && s.allTracks.isEmpty()) s.loadTracks()
            maybeResume()
            attachVisualizer()
            refreshTrack()
        }
        override fun onServiceDisconnected(n: ComponentName) { svc = null }
    }

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 400)
        }
    }

    // =====================================================================
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs.get(this)
        accent = prefs.lastAccent
        dynAccent = accent
        buildUi()
        afterBuild()

        ext = ExternalMedia(this) { runOnUiThread { refreshTrack() } }

        val i = Intent(this, PlayerService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        bindService(i, conn, BIND_AUTO_CREATE)
        requestPerms()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.version != builtVersion) {
            buildUi()
            afterBuild()
        }
        buildSlots()
        ext?.start()
        svc?.let { if (it.allTracks.isEmpty() && granted(audioPerm())) it.loadTracks() }
        startLocation()
        if (prefs.bgAnim) startBgZoom() else stopBgZoom()
        handler.post(ticker)
        refreshTrack()
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        svc?.saveState()
        stopLocation()
        stopBgZoom()
        super.onPause()
    }

    override fun onDestroy() {
        ext?.stop()
        try { unbindService(conn) } catch (_: Exception) {}
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { /* لانچر بسته نمی‌شود */ }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> { doNext(); return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { doPrev(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> { doToggle(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if (!isPlayingNow()) doToggle(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (isPlayingNow()) doToggle(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    // =====================================================================
    // UI
    private fun buildUi() {
        val white = Color.WHITE
        root = FrameLayout(this)
        root.layoutDirection = View.LAYOUT_DIRECTION_LTR
        root.setBackgroundColor(Color.parseColor("#0B0B10"))

        bgBox = FrameLayout(this)
        bgA = ImageView(this)
        bgA.scaleType = ImageView.ScaleType.CENTER_CROP
        bgB = ImageView(this)
        bgB.scaleType = ImageView.ScaleType.CENTER_CROP
        bgB.alpha = 0f
        bgBox.addView(bgA, FrameLayout.LayoutParams(MATCH, MATCH))
        bgBox.addView(bgB, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(bgBox, FrameLayout.LayoutParams(MATCH, MATCH))

        val shade = View(this)
        shade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xB3000000.toInt(), 0xCC000000.toInt(), 0xF2000000.toInt())
        )
        root.addView(shade, FrameLayout.LayoutParams(MATCH, MATCH))

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(u(22), u(10), u(22), u(14))
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- نوار بالا ----
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL

        val clockCol = LinearLayout(this)
        clockCol.orientation = LinearLayout.VERTICAL
        clockTv = label(36f, white)
        dateTv = label(15f, 0xB3FFFFFF.toInt())
        clockCol.addView(clockTv)
        clockCol.addView(dateTv)
        top.addView(clockCol, lp(WRAP, WRAP))

        sourceTv = label(15f, 0x99FFFFFF.toInt())
        sourceTv.gravity = Gravity.CENTER
        sourceTv.setSingleLine(true)
        sourceTv.setOnClickListener { switchSource() }
        top.addView(sourceTv, lp(0, WRAP).also { it.weight = 1f })

        speedTv = label(24f, white)
        weatherTv = label(18f, 0xCCFFFFFF.toInt())
        top.addView(speedTv, lp(WRAP, WRAP).also { it.rightMargin = u(16) })
        top.addView(weatherTv, lp(WRAP, WRAP).also { it.rightMargin = u(16) })

        val navBtn = iconButton(R.drawable.ic_nav, 52, 12)
        navBtn.setOnClickListener {
            val p = prefs.navPkg
            if (p.isEmpty()) pickApp(200) else launchPkg(p)
        }
        navBtn.setOnLongClickListener { pickApp(200); true }
        val libBtn = iconButton(R.drawable.ic_list, 52, 12)
        libBtn.setOnClickListener { startActivity(Intent(this, LibraryActivity::class.java)) }
        val setBtn = iconButton(R.drawable.ic_settings, 52, 12)
        setBtn.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        val appsBtn = iconButton(R.drawable.ic_apps, 52, 12)
        appsBtn.setOnClickListener { startActivity(Intent(this, AppsActivity::class.java)) }
        for (btn in arrayOf(navBtn, libBtn, setBtn, appsBtn)) {
            top.addView(btn, lp(u(52), u(52)).also { it.leftMargin = u(4) })
        }
        content.addView(top, lp(MATCH, u(64)))

        // ---- بارها ----
        bars = BarsView(this)
        bars.setPadding(u(6), 0, u(6), 0)
        content.addView(bars, lp(MATCH, 0).also { it.weight = 1f })

        // ---- متن آهنگ ----
        lyricTv = label(22f, accent)
        lyricTv.gravity = Gravity.CENTER
        lyricTv.setSingleLine(true)
        lyricTv.ellipsize = TextUtils.TruncateAt.END
        content.addView(lyricTv, lp(MATCH, u(38)))

        // ---- تایم‌لاین ----
        progress = ProgressLine(this)
        progress.onSeek = { f -> doSeek(f) }
        content.addView(progress, lp(MATCH, u(28)))

        // ---- ردیف پخش ----
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        cover = CoverView(this)
        row.addView(cover, lp(u(112), u(112)))

        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        titleTv = label(30f, white)
        titleTv.setSingleLine(true)
        titleTv.ellipsize = TextUtils.TruncateAt.MARQUEE
        titleTv.marqueeRepeatLimit = -1
        titleTv.isSelected = true
        artistTv = label(20f, 0xFF9AA0A6.toInt())
        artistTv.setSingleLine(true)
        infoTv = label(15f, 0x99FFFFFF.toInt())
        infoTv.setSingleLine(true)
        info.addView(titleTv, lp(MATCH, WRAP))
        info.addView(artistTv, lp(MATCH, WRAP))
        info.addView(infoTv, lp(MATCH, WRAP).also { it.topMargin = u(4) })
        row.addView(info, lp(0, WRAP).also {
            it.weight = 1f
            it.leftMargin = u(22)
            it.rightMargin = u(14)
        })

        btnShuffle = iconButton(R.drawable.ic_shuffle, 46, 11)
        btnShuffle.setOnClickListener { prefs.shuffle = !prefs.shuffle; updateModeIcons() }
        btnPrev = iconButton(R.drawable.ic_prev, 68, 19, true)
        btnPrev.setOnClickListener { doPrev() }
        btnPlay = iconButton(R.drawable.ic_play, 90, 24)
        btnPlay.setOnClickListener { doToggle() }
        btnNext = iconButton(R.drawable.ic_next, 68, 19, true)
        btnNext.setOnClickListener { doNext() }
        btnRepeat = iconButton(R.drawable.ic_repeat, 46, 11)
        btnRepeat.setOnClickListener { prefs.repeat = (prefs.repeat + 1) % 3; updateModeIcons() }

        row.addView(btnShuffle, lp(u(46), u(46)))
        row.addView(btnPrev, lp(u(68), u(68)).also { it.leftMargin = u(8) })
        row.addView(btnPlay, lp(u(90), u(90)).also { it.leftMargin = u(14); it.rightMargin = u(14) })
        row.addView(btnNext, lp(u(68), u(68)).also { it.rightMargin = u(8) })
        row.addView(btnRepeat, lp(u(46), u(46)))
        content.addView(row, lp(MATCH, WRAP).also { it.topMargin = u(4) })

        // ---- میانبرها ----
        slotsRow = LinearLayout(this)
        slotsRow.orientation = LinearLayout.HORIZONTAL
        content.addView(slotsRow, lp(MATCH, u(80)).also { it.topMargin = u(12) })

        // ---- سوایپ چپ/راست برای تعویض آهنگ ----
        var downX = 0f
        var downY = 0f
        root.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN) {
                downX = ev.x
                downY = ev.y
            } else if (ev.action == MotionEvent.ACTION_UP) {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > u(120) && abs(dx) > abs(dy) * 1.5f) {
                    if (dx < 0) doNext() else doPrev()
                }
            }
            true
        }

        setContentView(root)
        builtVersion = prefs.version
        attachedSession = -1
    }

    /** اعمال تنظیمات و بازگرداندن وضعیت فعلی روی UI تازه ساخته‌شده */
    private fun afterBuild() {
        cover.vinyl = prefs.coverStyle == 1
        bars.style = prefs.barStyle
        speedTv.visibility = if (prefs.showSpeed) View.VISIBLE else View.GONE
        weatherTv.visibility = if (prefs.showWeather) View.VISIBLE else View.GONE
        if (prefs.showSpeed) speedTv.setFa("0 km/h", true)
        sourceTv.setFa("")
        titleTv.setFa("—", true)
        lyricTv.setFa("")
        applyAccent(accent)
        cover.bitmap = currentArt
        val bl = bgBlur
        if (bl != null) bgA.setImageBitmap(bl)
        if (prefs.bgAnim) startBgZoom()
        buildSlots()
        tick()
        attachVisualizer()
        refreshTrack()
    }

    private fun applyAccent(c: Int) {
        accent = c
        bars.accent = c
        progress.accent = c
        cover.accent = c
        lyricTv.setTextColor(c)
        btnPlay.background = rippleBg(c, 0, 0, true, 0f)
        updateModeIcons()
    }

    private fun updateModeIcons() {
        val sOn = prefs.shuffle
        btnShuffle.setColorFilter(if (sOn) accent else 0x66FFFFFF)
        val r = prefs.repeat
        btnRepeat.setImageResource(if (r == 2) R.drawable.ic_repeat_one else R.drawable.ic_repeat)
        btnRepeat.setColorFilter(if (r != 0) accent else 0x66FFFFFF)
    }

    private fun targetAccent(): Int {
        val m = prefs.accentMode
        return if (m == 0) dynAccent else ACCENT_COLORS[(m - 1).coerceIn(0, ACCENT_COLORS.size - 1)]
    }

    private fun animateAccent(to: Int) {
        val from = accent
        if (from == to) return
        accentAnim?.cancel()
        val a = ValueAnimator.ofObject(ArgbEvaluator(), from, to)
        a.duration = 700
        a.addUpdateListener { applyAccent(it.animatedValue as Int) }
        a.start()
        accentAnim = a
        prefs.lastAccent = to
    }

    private fun startBgZoom() {
        if (bgZoom != null) return
        val a = ObjectAnimator.ofPropertyValuesHolder(
            bgBox,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.09f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.09f)
        )
        a.duration = 24000
        a.repeatCount = ValueAnimator.INFINITE
        a.repeatMode = ValueAnimator.REVERSE
        a.interpolator = LinearInterpolator()
        a.start()
        bgZoom = a
    }

    private fun stopBgZoom() {
        bgZoom?.cancel()
        bgZoom = null
        bgBox.scaleX = 1f
        bgBox.scaleY = 1f
    }

    // =====================================================================
    // منبع پخش (حافظه داخلی / پلیر خارجی)
    private fun isPlayingNow(): Boolean = if (extMode) ext?.isPlaying == true else svc?.isPlaying == true

    private fun decideSource() {
        val e = ext
        val extPlaying = e?.isPlaying == true
        val locPlaying = svc?.isPlaying == true
        if (extPlaying) extMode = true
        else if (locPlaying) extMode = false
        else if (extMode && e?.active == null) extMode = false
    }

    private fun switchSource() {
        val e = ext
        if (extMode) {
            e?.active?.transportControls?.pause()
            extMode = false
        } else {
            if (e?.active == null) {
                toast("پخش‌کننده‌ی دیگری پیدا نشد. در تنظیمات «دسترسی به نوتیفیکیشن» را فعال کنید.")
                return
            }
            svc?.pause()
            extMode = true
        }
        shownKey = ""
        refreshTrack()
        attachVisualizer()
    }

    private fun toast(s: String) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun doToggle() {
        if (extMode) {
            val e = ext ?: return
            val tc = e.active?.transportControls ?: return
            if (e.isPlaying) tc.pause() else tc.play()
        } else {
            svc?.toggle()
        }
    }

    private fun doNext() {
        if (extMode) ext?.active?.transportControls?.skipToNext() else svc?.next()
    }

    private fun doPrev() {
        if (extMode) ext?.active?.transportControls?.skipToPrevious() else svc?.prev()
    }

    private fun doSeek(f: Float) {
        if (extMode) {
            val e = ext ?: return
            val d = e.duration
            if (d > 0) e.active?.transportControls?.seekTo((d * f).toLong())
        } else {
            val s = svc ?: return
            s.seekTo((f * s.duration).toInt())
        }
    }

    // =====================================================================
    // نمایش آهنگ
    private fun refreshTrack() {
        val before = extMode
        decideSource()
        if (before != extMode) {
            shownKey = ""
            attachVisualizer()
        }
        if (extMode) refreshExternal() else refreshLocal()
    }

    private fun refreshLocal() {
        val s = svc ?: return
        sourceTv.setFa("حافظه داخلی  ▾")
        val t = s.currentTrack
        if (t == null) {
            titleTv.setFa("موزیکی پیدا نشد", true)
            artistTv.setFa("مجوز دسترسی را بدهید یا فلش USB را وصل کنید")
            infoTv.setFa("")
            lyrics = emptyList()
            if (shownKey != "none") {
                shownKey = "none"
                setArt(null)
            }
            return
        }
        titleTv.setFa(t.title, true)
        artistTv.setFa(if (t.artist.isEmpty()) "—" else t.artist)
        val key = "L" + t.id
        if (key != shownKey) {
            shownKey = key
            lyrics = emptyList()
            Thread {
                val bmp = ArtLoader.load(this, t)
                val lrc = Lrc.loadFor(t)
                runOnUiThread {
                    if (shownKey == key) {
                        lyrics = lrc
                        setArt(bmp)
                        svc?.setArt(bmp)
                    }
                }
            }.start()
        }
    }

    private fun refreshExternal() {
        val e = ext ?: return
        if (e.active == null) return
        sourceTv.setFa(e.appLabel + "  ▾")
        titleTv.setFa(e.title ?: "—", true)
        artistTv.setFa(e.artist ?: "")
        val art = e.art
        val key = "E" + e.title + "|" + e.artist + "|" + (art != null)
        if (key != shownKey) {
            shownKey = key
            lyrics = emptyList()
            setArt(art)
        }
    }

    private fun setArt(b: Bitmap?) {
        currentArt = b
        cover.bitmap = b
        if (b == null) {
            bgBlur = null
            crossfade(null)
            dynAccent = prefs.lastAccent
        } else {
            val bl = ArtLoader.blur(b)
            bgBlur = bl
            crossfade(bl)
            dynAccent = ArtLoader.accent(b, 0xFFE53935.toInt())
        }
        animateAccent(targetAccent())
    }

    private fun crossfade(bmp: Bitmap?) {
        bgB.animate().cancel()
        bgB.setImageBitmap(bmp)
        bgB.alpha = 0f
        bgB.animate().alpha(1f).setDuration(700).withEndAction {
            bgA.setImageBitmap(bmp)
            bgB.alpha = 0f
        }.start()
    }

    // =====================================================================
    // تیک دوره‌ای: ساعت، پیشرفت، متن آهنگ
    private fun tick() {
        val now = Date()
        val fmt = SimpleDateFormat(if (prefs.clock24) "HH:mm" else "h:mm a", Locale.ENGLISH)
        clockTv.setFa(fmt.format(now), true)
        dateTv.setFa(dateText(prefs.dateMode))
        dateTv.visibility = if (prefs.dateMode == 0) View.GONE else View.VISIBLE

        val before = extMode
        decideSource()
        if (before != extMode) {
            shownKey = ""
            attachVisualizer()
            refreshTrack()
        }

        var playing = false
        var pos = 0
        var dur = 0
        if (extMode) {
            val e = ext
            if (e != null) {
                playing = e.isPlaying
                pos = e.position
                dur = e.duration
            }
            infoTv.setFa(if (dur > 0) formatTime(pos) + " / " + formatTime(dur) else "")
        } else {
            val s = svc
            if (s != null) {
                playing = s.isPlaying
                pos = s.position
                dur = s.duration
                if (s.currentTrack != null) {
                    infoTv.setFa("${s.index + 1}/${s.queue.size}   •   ${formatTime(pos)} / ${formatTime(dur)}")
                }
            }
        }
        bars.setPlaying(playing)
        cover.setPlaying(playing)
        btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        if (!progress.dragging) progress.fraction = if (dur > 0) pos.toFloat() / dur else 0f
        updateLyric(pos)
        maybeWeather()
    }

    private fun updateLyric(pos: Int) {
        if (lyrics.isEmpty()) {
            if (lyricTv.text.isNotEmpty()) lyricTv.text = ""
            return
        }
        var line = ""
        for (p in lyrics) {
            if (p.first <= pos) line = p.second else break
        }
        lyricTv.setFa(line)
    }

    // =====================================================================
    // میانبرها و ناوبری
    private fun launchPkg(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i)
    }

    private fun pickApp(req: Int) {
        startActivityForResult(Intent(this, AppsActivity::class.java).putExtra(AppsActivity.EXTRA_PICK, true), req)
    }

    private fun buildSlots() {
        slotsRow.removeAllViews()
        val pm = packageManager
        val n = prefs.slotCount.coerceIn(3, 8)
        for (i in 0 until n) {
            var pkg: String? = prefs.slot(i)
            val icon = ImageView(this)
            val lab = label(12f, Color.WHITE)
            lab.gravity = Gravity.CENTER
            lab.setSingleLine(true)
            lab.ellipsize = TextUtils.TruncateAt.END
            if (pkg != null) {
                try {
                    icon.setImageDrawable(pm.getApplicationIcon(pkg))
                    lab.setFa(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString())
                } catch (e: PackageManager.NameNotFoundException) {
                    pkg = null
                }
            }
            if (pkg == null) {
                icon.setImageResource(R.drawable.ic_add)
                lab.text = ""
            }
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.background = rippleBg(0x26FFFFFF, 0, 0, false, u(14).toFloat())
            cell.isClickable = true
            cell.isFocusable = true
            cell.addView(icon, lp(u(42), u(42)))
            cell.addView(lab, lp(MATCH, WRAP).also { it.topMargin = u(2) })
            val valid = pkg
            cell.setOnClickListener {
                if (valid != null) launchPkg(valid) else pickApp(100 + i)
            }
            cell.setOnLongClickListener { slotMenu(i); true }
            slotsRow.addView(cell, lp(0, MATCH).also {
                it.weight = 1f
                it.setMargins(u(5), 0, u(5), 0)
            })
        }
    }

    private fun slotMenu(slot: Int) {
        AlertDialog.Builder(this)
            .setItems(arrayOf("تغییر برنامه", "حذف میانبر")) { _, which ->
                if (which == 0) pickApp(100 + slot)
                else { prefs.setSlot(slot, null); buildSlots() }
            }.show()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (res != RESULT_OK) return
        val pkg = data?.getStringExtra(AppsActivity.EXTRA_PKG) ?: return
        if (req in 100..107) {
            prefs.setSlot(req - 100, pkg)
            buildSlots()
        } else if (req == 200) {
            prefs.navPkg = pkg
        }
    }

    // =====================================================================
    // مجوزها
    private fun audioPerm() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun granted(p: String) =
        Build.VERSION.SDK_INT < 23 || checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun requestPerms() {
        if (Build.VERSION.SDK_INT < 23) return
        val need = listOf(audioPerm(), Manifest.permission.RECORD_AUDIO).filter { !granted(it) }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        val s = svc
        if (s != null && granted(audioPerm())) {
            s.loadTracks()
            maybeResume()
        }
        attachedSession = -1
        attachVisualizer()
        startLocation()
    }

    private fun maybeResume() {
        val s = svc ?: return
        if (prefs.autoResume && s.allTracks.isNotEmpty() && !s.resumeDone && !extMode) s.resumeLast()
    }

    private fun attachVisualizer() {
        if (!granted(Manifest.permission.RECORD_AUDIO)) return
        val session = if (extMode) 0 else (svc?.audioSessionId ?: return)
        if (session == attachedSession) return
        attachedSession = session
        bars.attach(session)
    }

    // =====================================================================
    // GPS (سرعت) و آب‌وهوا
    private fun startLocation() {
        if (!(prefs.showSpeed || prefs.showWeather)) return
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            if (!askedLoc && Build.VERSION.SDK_INT >= 23) {
                askedLoc = true
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 2
                )
            }
            return
        }
        try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            locationManager = lm
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun stopLocation() {
        try { locationManager?.removeUpdates(locListener) } catch (_: Exception) {}
    }

    private fun lastKnown(): Location? {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) return null
        return try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            null
        }
    }

    private fun weatherDesc(code: Int): String = when (code) {
        0 -> "صاف"
        1, 2 -> "کمی ابری"
        3 -> "ابری"
        45, 48 -> "مه"
        in 51..57 -> "نم‌نم باران"
        in 61..67 -> "بارانی"
        in 71..77 -> "برفی"
        in 80..82 -> "رگبار"
        in 95..99 -> "رعد و برق"
        else -> ""
    }

    private fun maybeWeather() {
        if (!prefs.showWeather) return
        val now = SystemClock.elapsedRealtime()
        if (weatherAt != 0L && now - weatherAt < 30 * 60 * 1000L) return
        val loc = lastLoc ?: lastKnown() ?: return
        weatherAt = now
        Thread {
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}" +
                        "&current=temperature_2m,weather_code"
                )
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                val txt = c.inputStream.bufferedReader().readText()
                c.disconnect()
                val cur = JSONObject(txt).getJSONObject("current")
                val temp = cur.getDouble("temperature_2m").roundToInt()
                val desc = weatherDesc(cur.getInt("weather_code"))
                runOnUiThread { weatherTv.setFa("$temp°C  $desc") }
            } catch (e: Exception) {
                weatherAt = now - 25 * 60 * 1000L
            }
        }.start()
    }
}

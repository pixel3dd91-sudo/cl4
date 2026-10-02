package com.carlauncher.player

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/** اکولایزر، تقویت باس و صدای فراگیر */
class EqActivity : Activity() {

    private var svc: PlayerService? = null
    private lateinit var prefs: Prefs
    private lateinit var col: LinearLayout
    private var accent = 0xFFE53935.toInt()

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName, b: IBinder) {
            svc = (b as PlayerService.LocalBinder).service
            build()
        }
        override fun onServiceDisconnected(n: ComponentName) { svc = null }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs.get(this)
        accent = prefs.lastAccent
        val scroll = ScrollView(this)
        scroll.layoutDirection = View.LAYOUT_DIRECTION_LTR
        scroll.setBackgroundColor(Color.parseColor("#0B0B10"))
        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(u(30), u(14), u(30), u(20))
        scroll.addView(col)
        setContentView(scroll)
        bindService(Intent(this, PlayerService::class.java), conn, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        try { unbindService(conn) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun header() {
        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        val t = label(26f, Color.WHITE)
        t.setFa("اکولایزر و افکت صدا", true)
        bar.addView(t, lp(0, WRAP).also { it.weight = 1f })
        val close = label(30f, accent)
        close.text = "✕"
        close.setPadding(u(16), u(6), u(16), u(6))
        close.setOnClickListener { finish() }
        bar.addView(close)
        col.addView(bar, lp(MATCH, u(60)))
    }

    private fun simpleRow(title: String, value: String, onClick: () -> Unit): View {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(u(18), u(14), u(18), u(14))
        r.background = rippleBg(0x14FFFFFF, 0, 0, false, u(12).toFloat())
        r.isClickable = true
        val t = label(20f, Color.WHITE)
        t.setFa(title)
        val v = label(17f, accent)
        v.setFa(value)
        r.addView(t, lp(0, WRAP).also { it.weight = 1f })
        r.addView(v, lp(WRAP, WRAP))
        r.setOnClickListener { onClick() }
        col.addView(r, lp(MATCH, WRAP).also { it.topMargin = u(8) })
        return r
    }

    private fun slider(title: String, valueText: String, max: Int, progress: Int,
                       onChange: (Int) -> Unit, onDone: () -> Unit) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(u(18), u(8), u(18), u(8))
        val t = label(17f, 0xCCFFFFFF.toInt())
        t.setFa("$title   $valueText")
        val sb = SeekBar(this)
        sb.max = max
        sb.progress = progress
        sb.progressTintList = ColorStateList.valueOf(accent)
        sb.thumbTintList = ColorStateList.valueOf(accent)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) onChange(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { onDone() }
        })
        box.addView(t, lp(MATCH, WRAP))
        box.addView(sb, lp(MATCH, u(40)))
        col.addView(box, lp(MATCH, WRAP))
    }

    private fun build() {
        col.removeAllViews()
        header()
        val s = svc ?: return
        val e = s.eq
        if (e == null) {
            val t = label(20f, 0xCCFFFFFF.toInt())
            t.setFa("اکولایزر در این دستگاه پشتیبانی نمی‌شود.")
            col.addView(t, lp(WRAP, WRAP).also { it.topMargin = u(20) })
            return
        }

        simpleRow("اکولایزر و افکت‌ها", if (prefs.eqOn) "روشن" else "خاموش") {
            prefs.eqOn = !prefs.eqOn
            s.applyEffects()
            build()
        }

        try {
            val n = e.numberOfBands.toInt()
            val range = e.bandLevelRange
            val min = range[0].toInt()
            val max = range[1].toInt()

            simpleRow("پریست آماده", "انتخاب") {
                val names = ArrayList<String>()
                for (i in 0 until e.numberOfPresets.toInt()) names.add(e.getPresetName(i.toShort()))
                AlertDialog.Builder(this)
                    .setItems(names.toTypedArray()) { _, which ->
                        try {
                            e.usePreset(which.toShort())
                            val lv = ArrayList<String>()
                            for (b in 0 until n) lv.add("" + e.getBandLevel(b.toShort()))
                            prefs.eqBands = lv.joinToString(",")
                            build()
                        } catch (_: Throwable) {}
                    }.show()
            }

            val saved = prefs.eqBands.split(",").mapNotNull { it.trim().toIntOrNull() }
            val levels = IntArray(n)
            for (b in 0 until n) levels[b] = if (b < saved.size) saved[b] else e.getBandLevel(b.toShort()).toInt()

            for (b in 0 until n) {
                val hz = e.getCenterFreq(b.toShort()) / 1000
                val name = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
                slider(name, "${levels[b] / 100} dB", max - min, levels[b] - min, { p ->
                    levels[b] = p + min
                    try { e.setBandLevel(b.toShort(), levels[b].toShort()) } catch (_: Throwable) {}
                }, {
                    prefs.eqBands = levels.joinToString(",")
                })
            }
        } catch (_: Throwable) {}

        val bb = s.bass
        if (bb != null && bb.strengthSupported) {
            slider("تقویت باس", "${prefs.bass / 10}%", 1000, prefs.bass, { p ->
                prefs.bass = p
                s.applyEffects()
            }, { build() })
        }
        val vv = s.virt
        if (vv != null && vv.strengthSupported) {
            slider("صدای فراگیر", "${prefs.virt / 10}%", 1000, prefs.virt, { p ->
                prefs.virt = p
                s.applyEffects()
            }, { build() })
        }

        simpleRow("بازنشانی همه", "") {
            prefs.eqBands = ""
            prefs.bass = 0
            prefs.virt = 0
            try {
                for (b in 0 until e.numberOfBands.toInt()) e.setBandLevel(b.toShort(), 0)
            } catch (_: Throwable) {}
            s.applyEffects()
            build()
        }
    }
}

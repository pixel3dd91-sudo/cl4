package com.carlauncher.player

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** لیست آهنگ‌ها، هنرمندان و آلبوم‌ها با جستجو */
class LibraryActivity : Activity() {

    private class Row(val title: String, val sub: String, val track: Track?, val group: String?)

    private var svc: PlayerService? = null
    private var mode = 0            // 0 آهنگ‌ها، 1 هنرمندان، 2 آلبوم‌ها
    private var filterKind = 0      // 1 هنرمند، 2 آلبوم
    private var filterValue: String? = null
    private var query = ""
    private var accent = 0xFFE53935.toInt()
    private val rows = ArrayList<Row>()

    private lateinit var tabs: List<TextView>
    private lateinit var chip: TextView
    private lateinit var empty: TextView
    private lateinit var list: ListView

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName, b: IBinder) {
            svc = (b as PlayerService.LocalBinder).service
            rebuild()
        }
        override fun onServiceDisconnected(n: ComponentName) { svc = null }
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(p: Int) = rows[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
            val v: LinearLayout
            if (cv is LinearLayout) {
                v = cv
            } else {
                v = LinearLayout(this@LibraryActivity)
                v.orientation = LinearLayout.VERTICAL
                v.setPadding(u(16), u(11), u(16), u(11))
                val t = label(22f, Color.WHITE)
                t.setSingleLine(true)
                t.ellipsize = TextUtils.TruncateAt.END
                val s = label(15f, 0x99FFFFFF.toInt())
                s.setSingleLine(true)
                v.addView(t, lp(MATCH, WRAP))
                v.addView(s, lp(MATCH, WRAP))
            }
            val r = rows[p]
            val playing = r.track != null && r.track.id == svc?.currentTrack?.id
            v.background = rippleBg(if (playing) 0x22FFFFFF else 0, 0, 0, false, u(10).toFloat())
            val t = v.getChildAt(0) as TextView
            t.setFa(r.title, playing)
            t.setTextColor(if (playing) accent else Color.WHITE)
            (v.getChildAt(1) as TextView).setFa(r.sub)
            return v
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        accent = Prefs.get(this).lastAccent

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_LTR
        root.setBackgroundColor(Color.parseColor("#0B0B10"))
        root.setPadding(u(26), u(12), u(26), u(12))

        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        val names = arrayOf("آهنگ‌ها", "هنرمندان", "آلبوم‌ها")
        val tl = ArrayList<TextView>()
        for (i in 0 until 3) {
            val t = label(20f, Color.WHITE)
            t.setFa(names[i], true)
            t.gravity = Gravity.CENTER
            t.setPadding(u(18), u(8), u(18), u(8))
            t.setOnClickListener {
                mode = i
                filterValue = null
                rebuild()
            }
            tl.add(t)
            bar.addView(t, lp(WRAP, WRAP).also { it.rightMargin = u(8) })
        }
        tabs = tl

        val spacer = View(this)
        bar.addView(spacer, lp(0, 1).also { it.weight = 1f })

        val search = EditText(this)
        search.hint = "جستجو..."
        search.setTextColor(Color.WHITE)
        search.setHintTextColor(0x66FFFFFF)
        search.setSingleLine(true)
        search.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        search.setPadding(u(16), u(8), u(16), u(8))
        search.background = rippleBg(0x1FFFFFFF, 0, 0, false, u(20).toFloat())
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; rebuild() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, bf: Int, c: Int) {}
        })
        bar.addView(search, lp(u(240), u(44)).also { it.rightMargin = u(10) })

        val close = label(30f, accent)
        close.text = "✕"
        close.gravity = Gravity.CENTER
        close.setPadding(u(16), u(6), u(16), u(6))
        close.setOnClickListener { finish() }
        bar.addView(close)
        root.addView(bar, lp(MATCH, u(60)))

        chip = label(17f, accent)
        chip.setPadding(u(16), u(6), u(16), u(6))
        chip.visibility = View.GONE
        chip.setOnClickListener { filterValue = null; mode = filterKind; rebuild() }
        root.addView(chip, lp(WRAP, WRAP))

        empty = label(20f, 0x99FFFFFF.toInt())
        empty.gravity = Gravity.CENTER
        empty.setFa("موزیکی پیدا نشد")
        empty.visibility = View.GONE

        list = ListView(this)
        list.divider = null
        list.dividerHeight = 0
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> onRow(rows[pos]) }
        root.addView(list, lp(MATCH, 0).also { it.weight = 1f })
        root.addView(empty, lp(MATCH, WRAP))
        setContentView(root)

        bindService(Intent(this, PlayerService::class.java), conn, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        try { unbindService(conn) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun onRow(r: Row) {
        val s = svc ?: return
        val t = r.track
        if (t != null) {
            val songs = rows.mapNotNull { it.track }
            val pos = songs.indexOfFirst { it.id == t.id }
            s.playQueue(songs, if (pos >= 0) pos else 0)
            finish()
        } else if (r.group != null) {
            filterKind = mode
            filterValue = r.group
            mode = 0
            rebuild()
        }
    }

    private fun artistOf(t: Track) = if (t.artist.isEmpty()) "—" else t.artist
    private fun albumOf(t: Track) = if (t.album.isEmpty()) "—" else t.album

    private fun rebuild() {
        val s = svc ?: return
        val q = query.trim().lowercase()
        val all = s.allTracks
        rows.clear()

        if (mode == 0) {
            var src: List<Track> = all
            val fv = filterValue
            if (fv != null) {
                src = if (filterKind == 1) src.filter { artistOf(it) == fv } else src.filter { albumOf(it) == fv }
            }
            for (t in src) {
                if (q.isEmpty() || t.title.lowercase().contains(q) || t.artist.lowercase().contains(q)) {
                    rows.add(Row(t.title, t.artist, t, null))
                }
            }
        } else {
            val groups = if (mode == 1) all.groupBy { artistOf(it) } else all.groupBy { albumOf(it) }
            for (e in groups.entries.sortedBy { it.key.lowercase() }) {
                if (q.isEmpty() || e.key.lowercase().contains(q)) {
                    rows.add(Row(e.key, "${e.value.size} آهنگ", null, e.key))
                }
            }
        }

        for (i in 0 until tabs.size) {
            val on = (i == mode) || (mode == 0 && filterValue != null && i == filterKind)
            tabs[i].setTextColor(if (on) accent else Color.WHITE)
            tabs[i].background = rippleBg(if (on) 0x22FFFFFF else 0, 0, 0, false, u(18).toFloat())
        }
        val fv = filterValue
        if (fv != null) {
            chip.visibility = View.VISIBLE
            chip.setFa("✕  $fv")
        } else {
            chip.visibility = View.GONE
        }
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }
}

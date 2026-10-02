package com.carlauncher.player

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class AppsActivity : Activity() {

    companion object {
        const val EXTRA_PICK = "pick"
        const val EXTRA_PKG = "pkg"
    }

    private class AppItem(val label: String, val pkg: String, val icon: Drawable)

    private var pick = false
    private val all = ArrayList<AppItem>()
    private val shown = ArrayList<AppItem>()
    private var query = ""

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
            val v: LinearLayout
            if (cv is LinearLayout) {
                v = cv
            } else {
                v = LinearLayout(this@AppsActivity)
                v.orientation = LinearLayout.VERTICAL
                v.gravity = Gravity.CENTER
                v.setPadding(u(6), u(8), u(6), u(8))
                v.addView(ImageView(this@AppsActivity), lp(u(68), u(68)))
                val t = label(15f, Color.WHITE)
                t.gravity = Gravity.CENTER
                t.setSingleLine(true)
                t.ellipsize = TextUtils.TruncateAt.END
                v.addView(t, lp(MATCH, WRAP).also { it.topMargin = u(6) })
            }
            val item = shown[p]
            (v.getChildAt(0) as ImageView).setImageDrawable(item.icon)
            (v.getChildAt(1) as TextView).setFa(item.label)
            return v
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        pick = intent.getBooleanExtra(EXTRA_PICK, false)
        val accent = Prefs.get(this).lastAccent

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_LTR
        root.setBackgroundColor(Color.parseColor("#0B0B10"))
        root.setPadding(u(26), u(12), u(26), u(12))

        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        val title = label(24f, Color.WHITE)
        title.setFa(if (pick) "یک برنامه انتخاب کنید" else "همه برنامه‌ها", true)
        bar.addView(title, lp(0, WRAP).also { it.weight = 1f })

        val search = EditText(this)
        search.hint = "جستجو..."
        search.setTextColor(Color.WHITE)
        search.setHintTextColor(0x66FFFFFF)
        search.setSingleLine(true)
        search.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        search.setPadding(u(16), u(8), u(16), u(8))
        search.background = rippleBg(0x1FFFFFFF, 0, 0, false, u(20).toFloat())
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; applyFilter() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, bf: Int, c: Int) {}
        })
        bar.addView(search, lp(u(260), u(44)).also { it.rightMargin = u(10) })

        val close = label(30f, accent)
        close.text = "✕"
        close.gravity = Gravity.CENTER
        close.setPadding(u(16), u(6), u(16), u(6))
        close.setOnClickListener { finish() }
        bar.addView(close)
        root.addView(bar, lp(MATCH, u(60)))

        val grid = GridView(this)
        grid.numColumns = GridView.AUTO_FIT
        grid.columnWidth = u(132)
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.verticalSpacing = u(12)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ ->
            val app = shown[pos]
            if (pick) {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_PKG, app.pkg))
                finish()
            } else {
                val i = packageManager.getLaunchIntentForPackage(app.pkg)
                if (i != null) startActivity(i)
            }
        }
        root.addView(grid, lp(MATCH, 0).also { it.weight = 1f })
        setContentView(root)

        Thread {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(q, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                all.clear()
                all.addAll(list)
                applyFilter()
            }
        }.start()
    }

    private fun applyFilter() {
        val q = query.trim().lowercase()
        shown.clear()
        for (a in all) if (q.isEmpty() || a.label.lowercase().contains(q)) shown.add(a)
        adapter.notifyDataSetChanged()
    }
}

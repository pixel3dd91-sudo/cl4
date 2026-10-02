package com.carlauncher.player

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var col: LinearLayout
    private var accent = 0xFFE53935.toInt()
    private var previewFa: TextView? = null
    private var previewEn: TextView? = null
    private var navValue: TextView? = null
    private var listenerValue: TextView? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs.get(this)
        accent = prefs.lastAccent

        val scroll = ScrollView(this)
        scroll.layoutDirection = View.LAYOUT_DIRECTION_LTR
        scroll.setBackgroundColor(Color.parseColor("#0B0B10"))
        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(u(30), u(14), u(30), u(24))
        scroll.addView(col)

        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        val title = label(28f, Color.WHITE)
        title.setFa("تنظیمات", true)
        bar.addView(title, lp(0, WRAP).also { it.weight = 1f })
        val close = label(30f, accent)
        close.text = "✕"
        close.setPadding(u(16), u(6), u(16), u(6))
        close.setOnClickListener { finish() }
        bar.addView(close)
        col.addView(bar, lp(MATCH, u(60)))

        // ---------------- ظاهر ----------------
        section("ظاهر")
        choice("رنگ تم", listOf("پویا (از روی کاور)", "قرمز", "آبی", "سبز", "بنفش", "نارنجی", "فیروزه‌ای", "صورتی"),
            { prefs.accentMode }, { prefs.accentMode = it })
        choice("سبک کاور", listOf("مربع گرد", "صفحه گرامافون (چرخان)"),
            { prefs.coverStyle }, { prefs.coverStyle = it })
        choice("سبک بارهای موزیک", listOf("ساده", "گرد", "LED"),
            { prefs.barStyle }, { prefs.barStyle = it })
        toggle("پس‌زمینه‌ی متحرک (زوم آرام)", { prefs.bgAnim }, { prefs.bgAnim = it })

        // ---------------- فونت ----------------
        section("فونت")
        val faNames = Fonts.FA.map { it.label } + "سیستم"
        val enNames = Fonts.EN.map { it.label } + "System"
        choice("فونت فارسی", faNames, { prefs.faFont }, { prefs.faFont = it; refreshPreview() })
        choice("فونت انگلیسی", enNames, { prefs.enFont }, { prefs.enFont = it; refreshPreview() })
        val pFa = label(24f, Color.WHITE)
        val pEn = label(24f, Color.WHITE)
        previewFa = pFa
        previewEn = pEn
        col.addView(pFa, lp(MATCH, WRAP).also { it.topMargin = u(10); it.leftMargin = u(18) })
        col.addView(pEn, lp(MATCH, WRAP).also { it.topMargin = u(4); it.leftMargin = u(18) })
        refreshPreview()
        action("وضعیت فونت‌ها", "بررسی") { showFontStatus() }

        // ---------------- ساعت و اطلاعات ----------------
        section("ساعت و اطلاعات")
        toggle("ساعت ۲۴ ساعته", { prefs.clock24 }, { prefs.clock24 = it })
        choice("تاریخ", listOf("خاموش", "شمسی", "میلادی"), { prefs.dateMode }, { prefs.dateMode = it })
        toggle("نمایش سرعت (GPS)", { prefs.showSpeed }, { prefs.showSpeed = it; if (it) askLocation() })
        toggle("نمایش دما و آب‌وهوا (نیاز به اینترنت)", { prefs.showWeather }, { prefs.showWeather = it; if (it) askLocation() })

        // ---------------- میانبرها و ناوبری ----------------
        section("میانبرها و ناوبری")
        choice("تعداد میانبرها", listOf("۵", "۶", "۷", "۸"), { prefs.slotCount - 5 }, { prefs.slotCount = it + 5 })
        navValue = action("برنامه‌ی مسیریاب (دکمه‌ی بالای صفحه)", navLabel()) {
            startActivityForResult(Intent(this, AppsActivity::class.java).putExtra(AppsActivity.EXTRA_PICK, true), 300)
        }

        // ---------------- پخش ----------------
        section("پخش و صدا")
        toggle("ادامه‌ی خودکار آخرین آهنگ هنگام روشن شدن", { prefs.autoResume }, { prefs.autoResume = it })
        action("اکولایزر و تقویت باس", "باز کردن") { startActivity(Intent(this, EqActivity::class.java)) }
        listenerValue = action("کنترل پخش‌کننده‌های دیگر (Spotify و ...)", listenerLabel()) { openListenerSettings() }

        section("درباره")
        val about = label(16f, 0x99FFFFFF.toInt())
        about.setFa("Car Launcher نسخه ۲.۰  •  فونت‌ها: Vazirmatn، Sahel، Shabnam، Poppins، Rajdhani (مجوز OFL)")
        col.addView(about, lp(MATCH, WRAP).also { it.topMargin = u(8); it.leftMargin = u(18) })

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        listenerValue?.setFa(listenerLabel())
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 300 && res == RESULT_OK) {
            val pkg = data?.getStringExtra(AppsActivity.EXTRA_PKG) ?: return
            prefs.navPkg = pkg
            prefs.bump()
            navValue?.setFa(navLabel())
        }
    }

    // ---------------- ابزارهای ساخت ردیف ----------------
    private fun section(name: String) {
        val t = label(19f, accent)
        t.setFa(name, true)
        col.addView(t, lp(WRAP, WRAP).also { it.topMargin = u(22); it.leftMargin = u(6) })
    }

    private fun baseRow(title: String, value: String): Pair<LinearLayout, TextView> {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(u(18), u(14), u(18), u(14))
        r.background = rippleBg(0x14FFFFFF, 0, 0, false, u(12).toFloat())
        r.isClickable = true
        r.isFocusable = true
        val t = label(20f, Color.WHITE)
        t.setFa(title)
        val v = label(17f, accent)
        v.setFa(value)
        r.addView(t, lp(0, WRAP).also { it.weight = 1f; it.rightMargin = u(12) })
        r.addView(v, lp(WRAP, WRAP))
        col.addView(r, lp(MATCH, WRAP).also { it.topMargin = u(8) })
        return Pair(r, v)
    }

    private fun choice(title: String, names: List<String>, get: () -> Int, set: (Int) -> Unit) {
        fun cur() = names[get().coerceIn(0, names.size - 1)]
        val row = baseRow(title, cur())
        row.first.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(names.toTypedArray()) { _, which ->
                    set(which)
                    prefs.bump()
                    row.second.setFa(cur())
                }.show()
        }
    }

    private fun toggle(title: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        fun cur() = if (get()) "روشن" else "خاموش"
        val row = baseRow(title, cur())
        row.first.setOnClickListener {
            set(!get())
            prefs.bump()
            row.second.setFa(cur())
        }
    }

    private fun action(title: String, value: String, onClick: () -> Unit): TextView {
        val row = baseRow(title, value)
        row.first.setOnClickListener { onClick() }
        return row.second
    }

    // ---------------- منطق‌ها ----------------
    private fun refreshPreview() {
        previewFa?.setFa("نمونه متن فارسی ۱۲۳۴۵  —  آهنگ جدید", true)
        previewEn?.setFa("Sample English Text 12345 — New Song", true)
    }

    private fun showFontStatus() {
        val sb = StringBuilder()
        for (p in Fonts.status(this)) sb.append(if (p.second) "✓  " else "✗  ").append(p.first).append("\n")
        sb.append("\nاگر فونتی ✗ است، هنگام ساخت برنامه دانلود نشده و از فونت سیستم استفاده می‌شود.")
        AlertDialog.Builder(this).setTitle("وضعیت فونت‌ها").setMessage(sb.toString()).show()
    }

    private fun navLabel(): String {
        val p = prefs.navPkg
        if (p.isEmpty()) return "انتخاب نشده"
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString()
        } catch (e: Exception) {
            "انتخاب نشده"
        }
    }

    private fun listenerEnabled(): Boolean {
        val s = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return s != null && s.contains(packageName)
    }

    private fun listenerLabel(): String = if (listenerEnabled()) "فعال ✓" else "غیرفعال — برای فعال‌سازی بزنید"

    private fun openListenerSettings() {
        try {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "این مانیتور صفحه‌ی دسترسی به نوتیفیکیشن را ندارد.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "باز کردن تنظیمات ممکن نشد.", Toast.LENGTH_LONG).show()
        }
    }

    private fun askLocation() {
        if (Build.VERSION.SDK_INT < 23) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 5)
        }
    }
}

package com.carlauncher.player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** مرجع طراحی: صفحه 1024x600. همه‌ی اندازه‌ها متناسب با صفحه‌ی مانیتور مقیاس می‌شوند. */
fun Context.uiScale(): Float {
    val m = resources.displayMetrics
    val w = max(m.widthPixels, m.heightPixels).toFloat()
    val h = min(m.widthPixels, m.heightPixels).toFloat()
    return min(w / 1024f, h / 600f).coerceIn(0.5f, 3f)
}

fun Context.u(v: Float): Int = (v * uiScale() + 0.5f).toInt()
fun Context.u(v: Int): Int = u(v.toFloat())

fun formatTime(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun blendColors(a: Int, b: Int, t: Float): Int {
    val r = (Color.red(a) * (1 - t) + Color.red(b) * t).toInt()
    val g = (Color.green(a) * (1 - t) + Color.green(b) * t).toInt()
    val bl = (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
    return Color.rgb(r, g, bl)
}

fun rippleBg(fill: Int, stroke: Int, strokeW: Int, oval: Boolean, radius: Float): RippleDrawable {
    val content = GradientDrawable()
    content.setShape(if (oval) GradientDrawable.OVAL else GradientDrawable.RECTANGLE)
    if (!oval) content.setCornerRadius(radius)
    content.setColor(fill)
    if (strokeW > 0) content.setStroke(strokeW, stroke)
    val mask = GradientDrawable()
    mask.setShape(if (oval) GradientDrawable.OVAL else GradientDrawable.RECTANGLE)
    if (!oval) mask.setCornerRadius(radius)
    mask.setColor(Color.WHITE)
    return RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), content, mask)
}

val ACCENT_COLORS = intArrayOf(
    Color.parseColor("#E53935"), Color.parseColor("#2196F3"), Color.parseColor("#43A047"),
    Color.parseColor("#AB47BC"), Color.parseColor("#FF9800"), Color.parseColor("#00BCD4"),
    Color.parseColor("#EC407A")
)

// ---------- تاریخ شمسی ----------
private val FA_MONTHS = arrayOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)
private val FA_DAYS = arrayOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")

fun faDigits(s: String): String {
    val sb = StringBuilder()
    for (c in s) sb.append(if (c in '0'..'9') ('۰' + (c - '0')) else c)
    return sb.toString()
}

fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
    val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
    var jy = -1595 + (33 * (days / 12053))
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm: Int
    val jd: Int
    if (days < 186) {
        jm = 1 + days / 31
        jd = 1 + days % 31
    } else {
        jm = 7 + (days - 186) / 30
        jd = 1 + (days - 186) % 30
    }
    return intArrayOf(jy, jm, jd)
}

fun dateText(mode: Int): String {
    if (mode == 0) return ""
    val cal = Calendar.getInstance()
    if (mode == 1) {
        val dow = cal.get(Calendar.DAY_OF_WEEK) - 1
        val j = gregorianToJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        return FA_DAYS[dow] + "  " + faDigits("" + j[2]) + " " + FA_MONTHS[j[1] - 1] + " " + faDigits("" + j[0])
    }
    return SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(cal.time)
}

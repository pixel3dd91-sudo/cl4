package com.carlauncher.player

import android.content.Context
import android.content.SharedPreferences
import kotlin.reflect.KProperty

class IntPref(private val sp: SharedPreferences, private val key: String, private val def: Int) {
    operator fun getValue(t: Any?, p: KProperty<*>): Int = sp.getInt(key, def)
    operator fun setValue(t: Any?, p: KProperty<*>, v: Int) { sp.edit().putInt(key, v).apply() }
}

class BoolPref(private val sp: SharedPreferences, private val key: String, private val def: Boolean) {
    operator fun getValue(t: Any?, p: KProperty<*>): Boolean = sp.getBoolean(key, def)
    operator fun setValue(t: Any?, p: KProperty<*>, v: Boolean) { sp.edit().putBoolean(key, v).apply() }
}

class LongPref(private val sp: SharedPreferences, private val key: String, private val def: Long) {
    operator fun getValue(t: Any?, p: KProperty<*>): Long = sp.getLong(key, def)
    operator fun setValue(t: Any?, p: KProperty<*>, v: Long) { sp.edit().putLong(key, v).apply() }
}

class StrPref(private val sp: SharedPreferences, private val key: String, private val def: String) {
    operator fun getValue(t: Any?, p: KProperty<*>): String = sp.getString(key, def) ?: def
    operator fun setValue(t: Any?, p: KProperty<*>, v: String) { sp.edit().putString(key, v).apply() }
}

class Prefs private constructor(ctx: Context) {

    companion object {
        @Volatile private var inst: Prefs? = null
        fun get(ctx: Context): Prefs {
            val i = inst
            if (i != null) return i
            return synchronized(this) {
                val j = inst
                if (j != null) j else {
                    val n = Prefs(ctx.applicationContext)
                    inst = n
                    n
                }
            }
        }
    }

    val sp: SharedPreferences = ctx.getSharedPreferences("launcher2", Context.MODE_PRIVATE)

    // ظاهر
    var accentMode by IntPref(sp, "accentMode", 0)      // 0 = پویا از کاور
    var coverStyle by IntPref(sp, "coverStyle", 0)      // 0 مربع گرد، 1 صفحه گرامافون
    var barStyle by IntPref(sp, "barStyle", 0)          // 0 ساده، 1 گرد، 2 LED
    var bgAnim by BoolPref(sp, "bgAnim", true)
    var faFont by IntPref(sp, "faFont", 0)
    var enFont by IntPref(sp, "enFont", 0)
    var clock24 by BoolPref(sp, "clock24", true)
    var dateMode by IntPref(sp, "dateMode", 1)          // 0 خاموش، 1 شمسی، 2 میلادی
    var showSpeed by BoolPref(sp, "showSpeed", false)
    var showWeather by BoolPref(sp, "showWeather", false)
    var slotCount by IntPref(sp, "slotCount", 5)
    var navPkg by StrPref(sp, "navPkg", "")

    // پخش
    var autoResume by BoolPref(sp, "autoResume", true)
    var shuffle by BoolPref(sp, "shuffle", false)
    var repeat by IntPref(sp, "repeat", 0)              // 0 خاموش، 1 همه، 2 یک آهنگ
    var lastTrackId by LongPref(sp, "lastTrackId", -1L)
    var lastPos by IntPref(sp, "lastPos", 0)
    var lastAccent by IntPref(sp, "lastAccent", 0xFFE53935.toInt())

    // اکولایزر
    var eqOn by BoolPref(sp, "eqOn", true)
    var eqBands by StrPref(sp, "eqBands", "")
    var bass by IntPref(sp, "bass", 0)
    var virt by IntPref(sp, "virt", 0)

    var version by IntPref(sp, "version", 0)
    fun bump() { version = version + 1 }

    fun slot(i: Int): String? = sp.getString("slot$i", null)
    fun setSlot(i: Int, pkg: String?) {
        val e = sp.edit()
        if (pkg == null) e.remove("slot$i") else e.putString("slot$i", pkg)
        e.apply()
    }
}

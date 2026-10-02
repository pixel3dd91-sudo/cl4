package com.carlauncher.player

import android.content.Context
import android.graphics.Typeface

/**
 * فونت‌ها از پوشه assets/fonts خوانده می‌شوند. اگر فایلی نباشد، فونت پیش‌فرض سیستم استفاده می‌شود.
 * متن فارسی با فونت فارسی و متن انگلیسی با فونت انگلیسی نمایش داده می‌شود.
 */
object Fonts {
    class Fam(val label: String, val regular: String, val bold: String)

    val FA = listOf(
        Fam("Vazirmatn", "Vazirmatn-Regular.ttf", "Vazirmatn-Bold.ttf"),
        Fam("Sahel", "Sahel.ttf", "Sahel-Bold.ttf"),
        Fam("Shabnam", "Shabnam.ttf", "Shabnam-Bold.ttf")
    )
    val EN = listOf(
        Fam("Poppins", "Poppins-Regular.ttf", "Poppins-Bold.ttf"),
        Fam("Rajdhani", "Rajdhani-Medium.ttf", "Rajdhani-Bold.ttf")
    )

    private val cache = HashMap<String, Typeface?>()

    fun load(ctx: Context, file: String): Typeface? {
        if (cache.containsKey(file)) return cache[file]
        val tf: Typeface? = try {
            Typeface.createFromAsset(ctx.applicationContext.assets, "fonts/$file")
        } catch (e: Exception) {
            null
        }
        cache[file] = tf
        return tf
    }

    fun isPersian(s: String): Boolean {
        for (ch in s) {
            val c = ch.code
            if ((c in 0x0600..0x06FF) || (c in 0xFB50..0xFDFF) || (c in 0xFE70..0xFEFF)) return true
        }
        return false
    }

    fun forText(ctx: Context, text: String, bold: Boolean): Typeface {
        val p = Prefs.get(ctx)
        val fam = if (isPersian(text)) FA.getOrNull(p.faFont) else EN.getOrNull(p.enFont)
        val tf = if (fam == null) null else load(ctx, if (bold) fam.bold else fam.regular)
        return tf ?: (if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
    }

    fun status(ctx: Context): List<Pair<String, Boolean>> =
        (FA + EN).map { Pair(it.label, load(ctx, it.regular) != null) }
}

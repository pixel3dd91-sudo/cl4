package com.carlauncher.player

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size

object ArtLoader {

    fun load(ctx: Context, t: Track): Bitmap? {
        // 1) کاور داخل فایل
        try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, t.uri)
                val d = r.embeddedPicture
                if (d != null) {
                    val b = decode(d)
                    if (b != null) return b
                }
            } finally {
                try { r.release() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        // 2) تامبنیل مدیااستور (اندروید 10+)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                return ctx.contentResolver.loadThumbnail(t.uri, Size(512, 512), null)
            }
        } catch (_: Throwable) {}

        // 3) albumart قدیمی
        try {
            val u = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), t.albumId)
            val s = ctx.contentResolver.openInputStream(u)
            if (s != null) {
                val b = BitmapFactory.decodeStream(s)
                s.close()
                return b
            }
        } catch (_: Throwable) {}
        return null
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / sample > 800) sample *= 2
        val o2 = BitmapFactory.Options()
        o2.inSampleSize = sample
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
    }

    /** تاری ملایم: کوچک کردن شدید و بزرگ کردن چندمرحله‌ای با فیلتر */
    fun blur(src: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(src, 24, 24, true)
        val mid = Bitmap.createScaledBitmap(small, 96, 96, true)
        return Bitmap.createScaledBitmap(mid, 192, 192, true)
    }

    /** رنگ غالب و شاداب کاور برای استفاده به‌عنوان رنگ تم */
    fun accent(src: Bitmap, fallback: Int): Int {
        val b = Bitmap.createScaledBitmap(src, 16, 16, true)
        var x = 0.0
        var y = 0.0
        var w = 0.0
        var sSum = 0.0
        val hsv = FloatArray(3)
        for (i in 0 until 16) {
            for (j in 0 until 16) {
                Color.colorToHSV(b.getPixel(i, j), hsv)
                if (hsv[1] < 0.25f || hsv[2] < 0.25f) continue
                val wt = (hsv[1] * hsv[2]).toDouble()
                val rad = Math.toRadians(hsv[0].toDouble())
                x += Math.cos(rad) * wt
                y += Math.sin(rad) * wt
                w += wt
                sSum += hsv[1] * wt
            }
        }
        if (w < 1.0) return fallback
        var hue = Math.toDegrees(Math.atan2(y, x)).toFloat()
        if (hue < 0) hue += 360f
        val s = (sSum / w).toFloat().coerceIn(0.55f, 0.95f)
        return Color.HSVToColor(floatArrayOf(hue, s, 1f))
    }
}

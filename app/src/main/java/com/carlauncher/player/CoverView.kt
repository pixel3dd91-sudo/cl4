package com.carlauncher.player

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.max
import kotlin.math.min

/** کاور آلبوم: مربع گرد یا صفحه‌ی گرامافون چرخان */
class CoverView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var bitmap: Bitmap? = null
        set(v) { field = v; shader = null; invalidate() }

    var vinyl: Boolean = false
        set(v) {
            field = v
            if (!v) {
                spin?.cancel()
                rotation = 0f
            }
            invalidate()
        }

    var accent: Int = 0xFFE53935.toInt()
        set(v) { field = v; invalidate() }

    private var shader: BitmapShader? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private var spin: ObjectAnimator? = null

    init {
        fill.color = 0x22FFFFFF
        ring.style = Paint.Style.STROKE
    }

    fun setPlaying(p: Boolean) {
        if (!vinyl) return
        val s: ObjectAnimator = spin ?: createSpin()
        if (p) {
            if (!s.isStarted) s.start() else if (s.isPaused) s.resume()
        } else {
            if (s.isRunning) s.pause()
        }
    }

    private fun createSpin(): ObjectAnimator {
        val a = ObjectAnimator.ofFloat(this, View.ROTATION, 0f, 360f)
        a.duration = 16000
        a.repeatCount = ValueAnimator.INFINITE
        a.interpolator = LinearInterpolator()
        spin = a
        return a
    }

    private fun buildShader(b: Bitmap) {
        if (width == 0 || height == 0) return
        val sh = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = max(width / b.width.toFloat(), height / b.height.toFloat())
        val m = Matrix()
        m.setScale(scale, scale)
        m.postTranslate((width - b.width * scale) / 2f, (height - b.height * scale) / 2f)
        sh.setLocalMatrix(m)
        shader = sh
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        pivotX = w / 2f
        pivotY = h / 2f
        shader = null
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f - context.u(2)
        val corner = context.u(16).toFloat()
        val bm = bitmap

        if (bm == null) {
            if (vinyl) canvas.drawCircle(cx, cy, r, fill)
            else canvas.drawRoundRect(0f, 0f, w, h, corner, corner, fill)
            val d = context.getDrawable(R.drawable.art_placeholder)
            if (d != null) {
                val q = (min(w, h) * 0.3f).toInt()
                d.setBounds((cx - q).toInt(), (cy - q).toInt(), (cx + q).toInt(), (cy + q).toInt())
                d.draw(canvas)
            }
            return
        }

        if (shader == null) buildShader(bm)
        paint.shader = shader
        if (vinyl) {
            canvas.drawCircle(cx, cy, r, paint)
            ring.color = accent
            ring.strokeWidth = context.u(3).toFloat()
            canvas.drawCircle(cx, cy, r, ring)
            fill.color = 0xFF101010.toInt()
            canvas.drawCircle(cx, cy, r * 0.16f, fill)
            ring.strokeWidth = context.u(2).toFloat()
            canvas.drawCircle(cx, cy, r * 0.16f, ring)
            fill.color = 0x22FFFFFF
        } else {
            canvas.drawRoundRect(0f, 0f, w, h, corner, corner, paint)
        }
    }
}

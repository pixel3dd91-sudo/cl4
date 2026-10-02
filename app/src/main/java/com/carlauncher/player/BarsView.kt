package com.carlauncher.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** بارهای موزیک با سه سبک، نوک‌های افتان و رنگ تم. اگر Visualizer واقعی کار نکند، انیمیشن شبیه‌سازی می‌شود. */
class BarsView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private val count = 48
    private val levels = FloatArray(count)
    private val peaks = FloatArray(count)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var playing = false
    private var visualizer: Visualizer? = null
    private var visualizerOk = false
    @Volatile private var lastFftTime = 0L
    private var lastSim = 0L

    var style: Int = 0
        set(v) { field = v; invalidate() }

    var accent: Int = 0xFFE53935.toInt()
        set(v) { field = v; rebuildShader(); invalidate() }

    init {
        peakPaint.color = 0xCCFFFFFF.toInt()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        rebuildShader()
    }

    private fun rebuildShader() {
        if (height == 0) return
        val top = blendColors(accent, Color.WHITE, 0.55f)
        paint.shader = LinearGradient(0f, height.toFloat(), 0f, 0f, accent, top, Shader.TileMode.CLAMP)
    }

    fun setPlaying(p: Boolean) {
        if (playing != p) {
            playing = p
            invalidate()
        }
    }

    fun attach(sessionId: Int) {
        release()
        try {
            val v = Visualizer(sessionId)
            val range = Visualizer.getCaptureSizeRange()
            v.setCaptureSize(if (range[1] >= 512) 512 else range[1])
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, w: ByteArray?, r: Int) {}
                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, r: Int) {
                    if (fft == null) return
                    val bins = fft.size / 2
                    var any = false
                    for (i in 0 until count) {
                        val f0 = i.toFloat() / count
                        val f1 = (i + 1f) / count
                        val lo = 1 + ((bins * 0.5f - 1) * f0 * f0).toInt()
                        val hi = max(lo + 1, 1 + ((bins * 0.5f - 1) * f1 * f1).toInt())
                        var sum = 0.0
                        var n = 0
                        for (k in lo until hi) {
                            if (2 * k + 1 >= fft.size) break
                            sum += hypot(fft[2 * k].toDouble(), fft[2 * k + 1].toDouble())
                            n++
                        }
                        val mag = if (n > 0) sum / n else 0.0
                        if (mag > 1.0) any = true
                        val lv = sqrt(mag / 128.0).toFloat().coerceIn(0f, 1f)
                        levels[i] = max(lv, levels[i] * 0.8f)
                    }
                    if (any) lastFftTime = SystemClock.uptimeMillis()
                    postInvalidate()
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            v.setEnabled(true)
            visualizer = v
            visualizerOk = true
        } catch (t: Throwable) {
            visualizerOk = false
        }
    }

    fun release() {
        try {
            visualizer?.setEnabled(false)
            visualizer?.release()
        } catch (_: Throwable) {}
        visualizer = null
        visualizerOk = false
    }

    private fun simulate(now: Long) {
        val tt = now / 1000.0
        val beat = (0.5 + 0.5 * sin(tt * 7.0)).toFloat()
        for (i in 0 until count) {
            val wave = abs(sin(tt * (1.2 + i * 0.17) + i)).toFloat()
            val target = (0.22f + 0.58f * wave) * (1f - 0.55f * i / count) * (0.65f + 0.35f * beat) +
                Random.nextFloat() * 0.12f
            levels[i] = levels[i] * 0.6f + target * 0.4f
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val sim = playing && (!visualizerOk || now - lastFftTime > 1500)
        if (sim) {
            if (now - lastSim > 45) {
                lastSim = now
                simulate(now)
            }
        } else if (!playing) {
            for (i in 0 until count) levels[i] *= 0.9f
        }

        val pl = paddingLeft.toFloat()
        val w = (width - paddingLeft - paddingRight).toFloat()
        val h = (height - paddingTop - paddingBottom).toFloat()
        val slot = w / count
        val barW = slot * 0.62f
        val minH = context.u(4).toFloat()
        val capH = context.u(3).toFloat()
        val bottom = paddingTop + h
        val usable = h - capH * 3
        var active = false

        for (i in 0 until count) {
            peaks[i] = max(levels[i], peaks[i] - 0.010f)
            if (levels[i] > 0.01f || peaks[i] > 0.01f) active = true
            val left = pl + i * slot + (slot - barW) / 2f
            val right = left + barW
            val bh = max(minH, levels[i] * usable)
            when (style) {
                2 -> {
                    val seg = context.u(7).toFloat()
                    val gap = context.u(3).toFloat()
                    val top = bottom - bh
                    var y = bottom
                    var first = true
                    while (first || y - seg >= top) {
                        canvas.drawRect(left, y - seg, right, y, paint)
                        y -= seg + gap
                        first = false
                    }
                }
                1 -> canvas.drawRoundRect(left, bottom - bh, right, bottom, barW / 2f, barW / 2f, paint)
                else -> canvas.drawRect(left, bottom - bh, right, bottom, paint)
            }
            if (style != 2) {
                val py = bottom - max(minH, peaks[i] * usable) - capH * 1.8f
                canvas.drawRect(left, py - capH, right, py, peakPaint)
            }
        }
        if (playing || active) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }
}

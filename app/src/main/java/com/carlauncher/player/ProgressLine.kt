package com.carlauncher.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ProgressLine @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var fraction = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var accent: Int = 0xFFE53935.toInt()
        set(v) { field = v; invalidate() }
    var dragging = false
        private set
    var onSeek: ((Float) -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        bgPaint.color = Color.parseColor("#55FFFFFF")
    }

    override fun onDraw(canvas: Canvas) {
        val th = context.u(5).toFloat()
        val cy = height / 2f
        val pad = context.u(8).toFloat()
        val w = width - 2 * pad
        canvas.drawRoundRect(pad, cy - th / 2, pad + w, cy + th / 2, th / 2, th / 2, bgPaint)
        fgPaint.color = accent
        canvas.drawRoundRect(pad, cy - th / 2, pad + w * fraction, cy + th / 2, th / 2, th / 2, fgPaint)
        fgPaint.color = Color.WHITE
        canvas.drawCircle(pad + w * fraction, cy, context.u(if (dragging) 10 else 7).toFloat(), fgPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val pad = context.u(8).toFloat()
        val f = ((e.x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
        when (e.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                fraction = f
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                fraction = f
                onSeek?.invoke(f)
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                invalidate()
            }
        }
        return true
    }
}

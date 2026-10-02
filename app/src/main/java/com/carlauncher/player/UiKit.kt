package com.carlauncher.player

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

fun Context.label(sizeU: Float, color: Int): TextView {
    val t = TextView(this)
    t.setTextSize(TypedValue.COMPLEX_UNIT_PX, u(sizeU).toFloat())
    t.setTextColor(color)
    t.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    return t
}

fun TextView.setFa(value: CharSequence, bold: Boolean = false) {
    val s = value.toString()
    if (this.text.toString() != s) this.text = s
    val tf = Fonts.forText(context, s, bold)
    if (this.typeface !== tf) this.typeface = tf
}

fun Context.iconButton(res: Int, sizeU: Int, padU: Int, ring: Boolean = false): ImageButton {
    val b = ImageButton(this)
    b.setImageResource(res)
    b.scaleType = ImageView.ScaleType.FIT_CENTER
    b.setPadding(u(padU), u(padU), u(padU), u(padU))
    b.background = if (ring) rippleBg(0, 0x99FFFFFF.toInt(), u(3), true, 0f) else rippleBg(0, 0, 0, true, 0f)
    return b
}

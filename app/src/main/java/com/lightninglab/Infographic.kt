package com.lightninglab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/** Renders a shareable image: sky photo + title + analysis lines (StaticLayout handles Persian/Arabic shaping and RTL). */
object Infographic {
    fun render(photo: Bitmap, title: String, lines: List<String>, footer: String): Bitmap {
        val w = 1080; val pad = 48; val inner = w - 2 * pad
        val ph = (photo.height.toFloat() / photo.width * inner).toInt()
        val tpTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4FC3F7.toInt(); textSize = 56f; isFakeBoldText = true }
        val tpBody = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE6EDF7.toInt(); textSize = 40f }
        val tpFoot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A97AD.toInt(); textSize = 30f }
        fun layout(t: String, p: TextPaint) = StaticLayout.Builder.obtain(t, 0, t.length, p, inner)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.15f).build()
        val lt = layout(title, tpTitle)
        val lb = layout(lines.joinToString("\n"), tpBody)
        val lf = layout(footer, tpFoot)
        val total = pad + lt.height + 24 + ph + 32 + lb.height + 32 + lf.height + pad
        val out = Bitmap.createBitmap(w, total, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFF0B0F17.toInt())
        var y = pad
        c.save(); c.translate(pad.toFloat(), y.toFloat()); lt.draw(c); c.restore(); y += lt.height + 24
        c.drawBitmap(photo, null, Rect(pad, y, pad + inner, y + ph), Paint(Paint.FILTER_BITMAP_FLAG)); y += ph + 32
        c.save(); c.translate(pad.toFloat(), y.toFloat()); lb.draw(c); c.restore(); y += lb.height + 32
        c.save(); c.translate(pad.toFloat(), y.toFloat()); lf.draw(c); c.restore()
        return out
    }
}

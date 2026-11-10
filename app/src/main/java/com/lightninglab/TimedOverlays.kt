package com.lightninglab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings
import kotlin.math.max
import kotlin.math.min

/** Renders overlay text/emoji to a bitmap sized in VIDEO pixels (the overlay effect maps bitmap pixels 1:1 onto the frame). */
object OverlayRender {
    val sizeFactor = floatArrayOf(0.05f, 0.075f, 0.11f)

    fun render(text: String, color: Int, videoW: Int, videoH: Int, sizeIdx: Int, bg: Boolean, outline: Boolean,
               typeface: Typeface?, scale: Float = 1f): Bitmap {
        val base = min(videoW, videoH).coerceAtLeast(64)
        val px = base * sizeFactor[sizeIdx.coerceIn(0, 2)] * scale
        val maxW = (videoW * 0.9f).toInt().coerceAtLeast(64)
        val fill = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = px; if (typeface != null) this.typeface = typeface }
        val stroke = TextPaint(fill).apply { this.color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = px * 0.14f; strokeJoin = Paint.Join.ROUND }
        fun layout(p: TextPaint) = StaticLayout.Builder.obtain(text, 0, text.length, p, maxW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        val l = layout(fill)
        var w = 1
        for (i in 0 until l.lineCount) w = max(w, Math.ceil(l.getLineWidth(i).toDouble()).toInt())
        val pad = (px * 0.3f).toInt()
        val bmp = Bitmap.createBitmap(w + 2 * pad, l.height + 2 * pad, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (bg) c.drawColor(0xAA000000.toInt())
        c.translate(pad.toFloat(), pad.toFloat())
        if (outline && !bg) layout(stroke).draw(c)
        l.draw(c)
        return bmp
    }
}

/** A bitmap shown only between startUs and endUs of the OUTPUT timeline (use as a composition-level effect). pos: 0 top, 1 centre, 2 bottom. */
class TimedOverlay(private val bmp: Bitmap, private val startUs: Long, private val endUs: Long, pos: Int) : BitmapOverlay() {
    private val shown: OverlaySettings
    private val hidden: OverlaySettings
    init {
        val ay = when (pos) { 0 -> 0.92f; 2 -> -0.92f; else -> 0f }
        val oy = when (pos) { 0 -> 1f; 2 -> -1f; else -> 0f }
        shown = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, ay).setOverlayFrameAnchor(0f, oy).setAlphaScale(1f).build()
        hidden = OverlaySettings.Builder().setBackgroundFrameAnchor(0f, ay).setOverlayFrameAnchor(0f, oy).setAlphaScale(0f).build()
    }
    override fun getBitmap(presentationTimeUs: Long): Bitmap = bmp
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        if (presentationTimeUs in startUs until endUs) shown else hidden
}

package com.lightninglab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.pow

/** Non-destructive edit parameters; the source bitmap is never modified. aspect = width/height, 0 = original. */
data class PhotoParams(
    val exposure: Float = 0f,    // -2..2 stops
    val brightness: Float = 0f,  // -1..1
    val contrast: Float = 0f,    // -1..1
    val saturation: Float = 0f,  // -1..1
    val warmth: Float = 0f,      // -1..1
    val rotate: Int = 0,         // 0/90/180/270
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val aspect: Float = 0f
)

object PhotoEdit {
    fun apply(src: Bitmap, p: PhotoParams): Bitmap {
        var b = src
        val m = Matrix()
        if (p.rotate != 0) m.postRotate(p.rotate.toFloat())
        if (p.flipH) m.postScale(-1f, 1f)
        if (p.flipV) m.postScale(1f, -1f)
        if (!m.isIdentity) b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        if (p.aspect > 0f) {
            val cur = b.width.toFloat() / b.height
            var cw = b.width; var ch = b.height
            if (cur > p.aspect) cw = (b.height * p.aspect).toInt() else ch = (b.width / p.aspect).toInt()
            cw = cw.coerceAtLeast(1); ch = ch.coerceAtLeast(1)
            b = Bitmap.createBitmap(b, (b.width - cw) / 2, (b.height - ch) / 2, cw, ch)
        }
        val gain = 2f.pow(p.exposure); val k = 1f + p.contrast
        val tr = 128f - 128f * k + 255f * p.brightness
        val rs = gain * k * (1f + 0.15f * p.warmth); val bs = gain * k * (1f - 0.15f * p.warmth); val gs = gain * k
        val cm = ColorMatrix(floatArrayOf(
            rs, 0f, 0f, 0f, tr,
            0f, gs, 0f, 0f, tr,
            0f, 0f, bs, 0f, tr,
            0f, 0f, 0f, 1f, 0f))
        cm.postConcat(ColorMatrix().apply { setSaturation(1f + p.saturation) })
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }
}

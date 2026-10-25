package com.lightninglab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min
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
    val aspect: Float = 0f,
    val highlights: Float = 0f,  // -1..1 (negative = recover highlights)
    val shadows: Float = 0f,     // -1..1 (positive = lift shadows)
    val sharpness: Float = 0f    // 0..1 unsharp mask
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
        if (p.highlights == 0f && p.shadows == 0f && p.sharpness <= 0f) return out
        val res = tonePass(out, p)
        out.recycle()
        return res
    }

    /**
     * Local tone + sharpening, processed in horizontal bands (with overlap) so memory stays small even at 16 MP.
     * highlights/shadows: additive correction weighted by a heavily blurred luma mask (no halo from per-pixel masks).
     * sharpness: unsharp mask on luma (pixel - 5x5 box blur).
     * Reads from `src` and writes to a NEW bitmap, so overlapping bands never see already-modified rows.
     */
    private fun tonePass(src: Bitmap, p: PhotoParams): Bitmap {
        val w = src.width; val h = src.height
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val needTone = p.highlights != 0f || p.shadows != 0f
        val rBig = max(8, w / 60); val rSmall = 2
        val band = 512; val m = if (needTone) rBig else rSmall
        var y0 = 0
        while (y0 < h) {
            val y1 = min(h, y0 + band)
            val ya = max(0, y0 - m); val yb = min(h, y1 + m); val bh = yb - ya
            val px = IntArray(w * bh); src.getPixels(px, 0, w, 0, ya, w, bh)
            val l = FloatArray(w * bh) { ImgUtil.luma(px[it]) }
            val big = if (needTone) ImgUtil.boxBlur(l, w, bh, rBig) else l
            val small = if (p.sharpness > 0f) ImgUtil.boxBlur(l, w, bh, rSmall) else l
            val off = (y0 - ya) * w; val n = (y1 - y0) * w
            val out = IntArray(n)
            for (i in 0 until n) {
                val j = off + i; val c = px[j]
                val bl = big[j] / 255f
                val d = p.shadows * 70f * (1f - bl) * (1f - bl) - p.highlights * 70f * bl * bl +
                    p.sharpness * 1.5f * (l[j] - small[j])
                val r = (((c shr 16) and 255) + d).toInt().coerceIn(0, 255)
                val g = (((c shr 8) and 255) + d).toInt().coerceIn(0, 255)
                val b = ((c and 255) + d).toInt().coerceIn(0, 255)
                out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            dst.setPixels(out, 0, w, 0, y0, w, y1 - y0)
            y0 = y1
        }
        return dst
    }
}

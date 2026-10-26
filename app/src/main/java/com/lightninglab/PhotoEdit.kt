package com.lightninglab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

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
    val sharpness: Float = 0f,   // 0..1 unsharp mask
    val scurve: Float = 0f,      // -1..1 S-curve contrast (tone curve)
    val denoise: Float = 0f,     // 0..1 luminance noise reduction
    val vibrance: Float = 0f     // -1..1 boosts weak colours more than strong ones
)

/** One removal mark: x,y normalised to image width/height, r normalised to image WIDTH. */
data class Heal(val x: Float, val y: Float, val r: Float)

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
        if (p.highlights == 0f && p.shadows == 0f && p.sharpness <= 0f && p.scurve == 0f && p.denoise <= 0f && p.vibrance == 0f) return out
        val res = tonePass(out, p)
        out.recycle()
        return res
    }

    /**
     * Local tone, denoise, sharpening, vibrance and S-curve, processed in horizontal bands (with overlap) so memory
     * stays small even at 16 MP. Reads from `src`, writes a NEW bitmap, so overlapping bands never see modified rows.
     * highlights/shadows: additive correction weighted by a heavily blurred luma mask.
     * denoise: luminance only, pulls a pixel toward its 3x3 mean where the difference is small (edges preserved).
     * sharpness: unsharp mask on luma. vibrance: saturation boost weighted toward low-saturation pixels.
     * scurve: per-channel tone curve v - k*sin(2*pi*v)/(2*pi).
     */
    private fun tonePass(src: Bitmap, p: PhotoParams): Bitmap {
        val w = src.width; val h = src.height
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val needTone = p.highlights != 0f || p.shadows != 0f
        val rBig = max(8, w / 60); val rSmall = 2
        val band = 512; val m = if (needTone) rBig else rSmall
        val twoPi = 2.0 * PI
        val lut = IntArray(256) { i ->
            val v = i / 255.0
            (255.0 * (v - p.scurve * sin(twoPi * v) / twoPi)).roundToInt().coerceIn(0, 255)
        }
        var y0 = 0
        while (y0 < h) {
            val y1 = min(h, y0 + band)
            val ya = max(0, y0 - m); val yb = min(h, y1 + m); val bh = yb - ya
            val px = IntArray(w * bh); src.getPixels(px, 0, w, 0, ya, w, bh)
            val l = FloatArray(w * bh) { ImgUtil.luma(px[it]) }
            val big = if (needTone) ImgUtil.boxBlur(l, w, bh, rBig) else l
            val small = if (p.sharpness > 0f) ImgUtil.boxBlur(l, w, bh, rSmall) else l
            val dn = if (p.denoise > 0f) ImgUtil.boxBlur(l, w, bh, 1) else l
            val off = (y0 - ya) * w; val n = (y1 - y0) * w
            val out = IntArray(n)
            for (i in 0 until n) {
                val j = off + i; val c = px[j]
                val bl = big[j] / 255f
                var d = p.shadows * 70f * (1f - bl) * (1f - bl) - p.highlights * 70f * bl * bl +
                    p.sharpness * 1.5f * (l[j] - small[j])
                if (p.denoise > 0f) {
                    val dl = dn[j] - l[j]
                    d += p.denoise * max(0f, 1f - abs(dl) / (10f + 40f * p.denoise)) * dl
                }
                var r = (((c shr 16) and 255) + d).toInt().coerceIn(0, 255)
                var g = (((c shr 8) and 255) + d).toInt().coerceIn(0, 255)
                var b = ((c and 255) + d).toInt().coerceIn(0, 255)
                if (p.vibrance != 0f) {
                    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
                    val sat = if (mx > 0) (mx - mn) / mx.toFloat() else 0f
                    val f = (1f + p.vibrance * (1f - sat)).coerceAtLeast(0f)
                    val lum = 0.299f * r + 0.587f * g + 0.114f * b
                    r = (lum + (r - lum) * f).toInt().coerceIn(0, 255)
                    g = (lum + (g - lum) * f).toInt().coerceIn(0, 255)
                    b = (lum + (b - lum) * f).toInt().coerceIn(0, 255)
                }
                if (p.scurve != 0f) { r = lut[r]; g = lut[g]; b = lut[b] }
                out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            dst.setPixels(out, 0, w, 0, y0, w, y1 - y0)
            y0 = y1
        }
        return dst
    }

    /** Object removal: fills each mark with [Inpaint] (smooth fill). Returns a new bitmap; src is untouched. */
    fun heal(src: Bitmap, circles: List<Heal>): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width; val h = out.height
        for (c in circles) {
            val cx = c.x * w; val cy = c.y * h; val r = max(2f, c.r * w)
            val x0 = max(0, (cx - r - 3).toInt()); val y0 = max(0, (cy - r - 3).toInt())
            val x1 = min(w, (cx + r + 4).toInt()); val y1 = min(h, (cy + r + 4).toInt())
            val bw = x1 - x0; val bh = y1 - y0
            if (bw < 3 || bh < 3) continue
            val px = IntArray(bw * bh); out.getPixels(px, 0, bw, x0, y0, bw, bh)
            val unk = BooleanArray(bw * bh)
            for (y in 0 until bh) for (x in 0 until bw) {
                val dx = x + x0 - cx; val dy = y + y0 - cy
                if (dx * dx + dy * dy <= r * r) unk[y * bw + x] = true
            }
            Inpaint.fill(px, bw, bh, unk)
            out.setPixels(px, 0, bw, x0, y0, bw, bh)
        }
        return out
    }
}

package com.lightninglab

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Lightning stacking. Frame #1 (base) is the background and is never changed except where lightning is added.
 * For every other frame a LIGHTNING MASK is built, so only the bolt survives and the rest of that frame is dropped:
 *   d   = luma(frame) - luma(base)            (what became brighter)
 *   top = d - boxBlur(d)                      (top-hat: removes global brightening, keeps thin bright lines)
 *   mask = clamp((top - t) / t, 0, 1)
 * Then the masked pixels are combined with the chosen blend: LIGHTEN (max), SCREEN, or ADVANCED
 * (lighten + highlight protection that fades the contribution where the result is already near white).
 * Frames whose mask covers >20% of the image (misalignment / whole-sky flash) are skipped.
 * A final highlight roll-off keeps the top end from clipping. No alignment yet: assumes a static camera.
 */
object Composer {
    enum class Method { LIGHTEN, SCREEN, ADVANCED }
    class Result(val bitmap: Bitmap, val used: Int, val skipped: Int, val clippedPct: Float)

    private fun blend(c: Int, o: Int, m: Float, method: Method): Int = when (method) {
        Method.SCREEN -> (255f - (255f - c) * (255f - o * m) / 255f).toInt()
        else -> if (o > c) (c + (o - c) * m).toInt() else c
    }

    fun compose(base: Bitmap, others: Sequence<Bitmap?>, method: Method, intensity: Float, sensitivity: Float): Result {
        val w = base.width; val h = base.height; val n = w * h
        val bp = IntArray(n); base.getPixels(bp, 0, w, 0, 0, w, h)
        val bl = FloatArray(n) { ImgUtil.luma(bp[it]) }
        val out = bp.copyOf(); val op = IntArray(n); val d = FloatArray(n)
        val t = 8f + 16f * (1f - sensitivity.coerceIn(0f, 1f))
        val r = max(6, w / 150)
        var used = 1; var skipped = 0
        for (f in others) {
            if (f == null || f.width != w || f.height != h) { skipped++; f?.recycle(); continue }
            f.getPixels(op, 0, w, 0, 0, w, h); f.recycle()
            for (i in 0 until n) d[i] = ImgUtil.luma(op[i]) - bl[i]
            val blur = ImgUtil.boxBlur(d, w, h, r)
            var cover = 0
            for (i in 0 until n) {
                val m = ((d[i] - blur[i] - t) / t).coerceIn(0f, 1f)
                d[i] = m; if (m > 0.5f) cover++
            }
            if (cover > 0.2f * n) { skipped++; continue }
            for (i in 0 until n) {
                var m = min(1f, d[i] * intensity)
                if (m <= 0f) continue
                val c = out[i]; val o = op[i]
                if (method == Method.ADVANCED) {
                    val lum = ImgUtil.luma(c)
                    m *= 1f - ((lum - 225f) / 30f).coerceIn(0f, 1f)
                    if (m <= 0f) continue
                }
                var res = 0xFF shl 24
                for (sh in intArrayOf(16, 8, 0)) {
                    val v = blend((c shr sh) and 255, (o shr sh) and 255, m, method)
                    res = res or (v.coerceIn(0, 255) shl sh)
                }
                out[i] = res
            }
            used++
        }
        var clipped = 0
        for (i in 0 until n) {
            var res = 0xFF shl 24
            for (sh in intArrayOf(16, 8, 0)) {
                var v = ((out[i] shr sh) and 255).toFloat()
                if (v > 220f) v = 220f + 35f * (1f - exp(-(v - 220f) / 35f))
                res = res or (v.toInt().coerceIn(0, 255) shl sh)
            }
            out[i] = res
            if (ImgUtil.luma(res) >= 250f) clipped++
        }
        return Result(Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888), used, skipped, 100f * clipped / n)
    }
}

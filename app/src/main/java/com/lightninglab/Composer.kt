package com.lightninglab

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.min

/**
 * Controlled lightning stacking.
 * Background comes from the base frame only. For every other frame, pixels that are clearly brighter
 * than the base (luma difference > thr) are blended in with a soft weight, so only lightning
 * structure is added. Frames where a large part of the image brightens (whole sky flash) are skipped,
 * which prevents washed-out results. A highlight roll-off keeps the top end from clipping hard.
 * No alignment yet: assumes a tripod / static camera.
 */
object Composer {
    class Result(val bitmap: Bitmap, val used: Int, val skipped: Int)

    private fun luma(p: Int) =
        0.299f * ((p shr 16) and 255) + 0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)

    fun compose(base: Bitmap, others: Sequence<Bitmap?>, intensity: Float = 1f, thr: Float = 18f): Result {
        val w = base.width; val h = base.height; val n = w * h
        val bp = IntArray(n); base.getPixels(bp, 0, w, 0, 0, w, h)
        val bl = FloatArray(n) { luma(bp[it]) }
        val out = bp.copyOf()
        val op = IntArray(n)
        var used = 1; var skipped = 0
        for (f in others) {
            if (f == null || f.width != w || f.height != h) { skipped++; f?.recycle(); continue }
            f.getPixels(op, 0, w, 0, 0, w, h); f.recycle()
            var over = 0
            for (i in 0 until n) if (luma(op[i]) - bl[i] > thr) over++
            if (over > 0.35f * n) { skipped++; continue }
            for (i in 0 until n) {
                val d = luma(op[i]) - bl[i]
                if (d <= thr) continue
                val wgt = min(1f, (d - thr) / thr) * intensity
                val c = out[i]; val o = op[i]
                var res = 0xFF shl 24
                for (sh in intArrayOf(16, 8, 0)) {
                    val cc = (c shr sh) and 255; val oc = (o shr sh) and 255
                    val v = if (oc > cc) (cc + (oc - cc) * wgt).toInt() else cc
                    res = res or (v.coerceIn(0, 255) shl sh)
                }
                out[i] = res
            }
            used++
        }
        // highlight roll-off above 220
        for (i in 0 until n) {
            var res = 0xFF shl 24
            for (sh in intArrayOf(16, 8, 0)) {
                var v = ((out[i] shr sh) and 255).toFloat()
                if (v > 220f) v = 220f + 35f * (1f - exp(-(v - 220f) / 35f))
                res = res or (v.toInt().coerceIn(0, 255) shl sh)
            }
            out[i] = res
        }
        return Result(Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888), used, skipped)
    }
}

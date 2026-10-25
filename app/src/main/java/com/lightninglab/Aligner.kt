package com.lightninglab

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Translation alignment for Lightning Stack (original implementation; no third-party code).
 * Normalised cross-correlation of GRADIENT images (static edges: horizon, trees, buildings), coarse search on a
 * ~256 px image then a full-resolution refinement on a central crop. Lightning/clouds change brightness but
 * not the edge layout, so NCC on edges is more robust than on raw luma.
 * If the frame has no usable structure (pure night sky) or the peak is ambiguous, the result is NOT confident
 * and the caller must leave the frame unshifted. Convention: aligned(x,y) = mov(x+dx, y+dy).
 */
object Aligner {
    class Shift(val dx: Int, val dy: Int, val confident: Boolean, val score: Float)
    private class Peak(val dx: Int, val dy: Int, val s: Float, val second: Float)

    private class Small(val a: FloatArray, val w: Int, val h: Int)

    private fun down(src: FloatArray, w: Int, h: Int, f: Int): Small {
        if (f <= 1) return Small(src, w, h)
        val nw = w / f; val nh = h / f; val out = FloatArray(nw * nh); val inv = 1f / (f * f)
        for (y in 0 until nh) for (x in 0 until nw) {
            var s = 0f
            for (yy in 0 until f) { val o = (y * f + yy) * w + x * f; for (xx in 0 until f) s += src[o + xx] }
            out[y * nw + x] = s * inv
        }
        return Small(out, nw, nh)
    }

    private fun grad(l: FloatArray, w: Int, h: Int): FloatArray {
        val g = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            g[i] = abs(l[i + 1] - l[i - 1]) + abs(l[i + w] - l[i - w])
        }
        return g
    }

    private fun ncc(a: FloatArray, b: FloatArray, w: Int, h: Int, dx: Int, dy: Int,
                    x0: Int, y0: Int, x1: Int, y1: Int): Float {
        val xs = max(x0, max(0, -dx)); val xe = min(x1, min(w, w - dx))
        val ys = max(y0, max(0, -dy)); val ye = min(y1, min(h, h - dy))
        if (xe <= xs || ye <= ys) return -1f
        val n = (xe - xs).toLong() * (ye - ys)
        if (n < 64) return -1f
        var sa = 0.0; var sb = 0.0; var saa = 0.0; var sbb = 0.0; var sab = 0.0
        for (y in ys until ye) {
            val ra = y * w; val rb = (y + dy) * w + dx
            for (x in xs until xe) {
                val va = a[ra + x].toDouble(); val vb = b[rb + x].toDouble()
                sa += va; sb += vb; saa += va * va; sbb += vb * vb; sab += va * vb
            }
        }
        val cov = sab - sa * sb / n; val vA = saa - sa * sa / n; val vB = sbb - sb * sb / n
        if (vA < 1e-6 || vB < 1e-6) return -1f
        return (cov / sqrt(vA * vB)).toFloat()
    }

    private fun search(a: FloatArray, b: FloatArray, w: Int, h: Int, cdx: Int, cdy: Int, r: Int,
                       x0: Int, y0: Int, x1: Int, y1: Int): Peak {
        val side = 2 * r + 1
        val sc = FloatArray(side * side)
        var best = -2f; var bx = cdx; var by = cdy
        for (j in 0 until side) for (i in 0 until side) {
            val dx = cdx + i - r; val dy = cdy + j - r
            val s = ncc(a, b, w, h, dx, dy, x0, y0, x1, y1)
            sc[j * side + i] = s
            if (s > best) { best = s; bx = dx; by = dy }
        }
        var second = -2f
        for (j in 0 until side) for (i in 0 until side) {
            val dx = cdx + i - r; val dy = cdy + j - r
            if (max(abs(dx - bx), abs(dy - by)) >= 2 && sc[j * side + i] > second) second = sc[j * side + i]
        }
        return Peak(bx, by, best, second)
    }

    /** base/mov: luma arrays (w*h). maxFrac = largest expected shift as a fraction of the width. */
    fun estimate(base: FloatArray, mov: FloatArray, w: Int, h: Int, maxFrac: Float = 0.03f): Shift {
        if (w < 64 || h < 64) return Shift(0, 0, false, 0f)
        val f = max(1, w / 256)
        val b = down(base, w, h, f); val m = down(mov, w, h, f)
        val gb = grad(b.a, b.w, b.h); val gm = grad(m.a, m.w, m.h)
        val r = max(2, (maxFrac * b.w).roundToInt())
        val p = search(gb, gm, b.w, b.h, 0, 0, r, 2, 2, b.w - 2, b.h - 2)
        if (p.s < MIN_SCORE || p.s - p.second < MIN_MARGIN) return Shift(0, 0, false, p.s)
        var dx = p.dx * f; var dy = p.dy * f
        if (f > 1) {
            val gbf = grad(ImgUtil.boxBlur(base, w, h, 1), w, h); val gmf = grad(ImgUtil.boxBlur(mov, w, h, 1), w, h)
            val cw = min(w / 2, 640); val ch = min(h / 2, 640)
            val x0 = (w - cw) / 2; val y0 = (h - ch) / 2
            val q = search(gbf, gmf, w, h, dx, dy, f, x0, y0, x0 + cw, y0 + ch)
            if (q.s >= 0.5f * p.s) { dx = q.dx; dy = q.dy }
        }
        return Shift(dx, dy, true, p.s)
    }

    /** Writes the aligned frame into `dst`; pixels that fall outside the source take the base pixel (no contribution). */
    fun shiftInto(src: IntArray, dst: IntArray, fallback: IntArray, w: Int, h: Int, dx: Int, dy: Int) {
        for (y in 0 until h) {
            val sy = y + dy
            for (x in 0 until w) {
                val sx = x + dx; val i = y * w + x
                dst[i] = if (sx in 0 until w && sy in 0 until h) src[sy * w + sx] else fallback[i]
            }
        }
    }

    const val MIN_SCORE = 0.2f
    const val MIN_MARGIN = 0.02f
}

package com.lightninglab

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Motion-compensated "what appeared and vanished" detector (original code; algorithm validated in Python on four real
 * clips, see docs/REAL_CLIP_FINDINGS.md).
 *
 * Streaming: frames (luma, ~320 px wide) are pushed in presentation order. For each frame:
 *  1. global translation to the previous frame is tracked (NCC on half-resolution gradients, +-5 px, sub-pixel),
 *     giving every frame a cumulative position, so any two nearby frames can be aligned;
 *  2. frames within +-[winMs] are warped onto the current frame (bilinear); per side the pixel-wise MINIMUM is the
 *     "background", and d = cur - max(minBefore, minAfter): only pixels brighter than BOTH sides survive.
 *     A short window (100 ms) is deliberate: a moving object (truck, bird) is present in at least one side's window
 *     at the same pixels, so it cancels out, whereas a flash exists only in the current frame(s);
 *  3. d > [T1] marks "positive" pixels. Misaligned edges produce positive pixels right next to NEGATIVE ones, so
 *     positive pixels within 3 px of a negative pixel (cur - mean of neighbours < -T1) are discarded. What remains
 *     ("unpaired") is lightning: thin bolts and rolling-shutter glow bands.
 * Per frame we report: area of positive pixels, unpaired count (normalised to 320x180) and the longest side of the
 * largest connected unpaired component.
 */
class DayLine(private val w: Int, private val h: Int, private val winMs: Long = 100L) {
    class Stat(val tMs: Long, val areaFrac: Float, val unpaired: Float, val longest: Int)

    private class Fr(val t: Long, val l: FloatArray, val g: FloatArray) { var px = 0f; var py = 0f }

    private val n = w * h
    private val hw = w / 2
    private val hh = h / 2
    private val q = ArrayList<Fr>()
    private var evalPtr = 0
    private var prev: Fr? = null
    val stats = ArrayList<Stat>()

    private val pool = Array(2 * MAX_SIDE) { FloatArray(n) }
    private val minB = FloatArray(n); private val minA = FloatArray(n)
    private val medB = FloatArray(n); private val medA = FloatArray(n)
    private val posM = BooleanArray(n); private val negM = BooleanArray(n); private val negD = BooleanArray(n)
    private val tmpD = BooleanArray(n)
    private val stack = IntArray(n)

    companion object {
        const val T1 = 20f
        const val MAX_SIDE = 6
        const val BORDER = 8
    }

    private fun halfGrad(l: FloatArray): FloatArray {
        val sm = FloatArray(hw * hh)
        for (y in 0 until hh) for (x in 0 until hw) {
            val i = 2 * y * w + 2 * x
            sm[y * hw + x] = 0.25f * (l[i] + l[i + 1] + l[i + w] + l[i + w + 1])
        }
        val g = FloatArray(hw * hh)
        for (y in 1 until hh - 1) for (x in 1 until hw - 1) {
            val i = y * hw + x
            g[i] = abs(sm[i + 1] - sm[i - 1]) + abs(sm[i + hw] - sm[i - hw])
        }
        return g
    }

    /** Returns [dx, dy] in half-resolution pixels such that cur(x+dx, y+dy) ~ prev(x, y). */
    private fun trackShift(a: FloatArray, b: FloatArray): FloatArray {
        val r = 5; val side = 2 * r + 1
        val x0 = r; val x1 = hw - r; val y0 = r; val y1 = hh - r
        val cnt = ((x1 - x0) * (y1 - y0)).toDouble()
        if (cnt < 64) return floatArrayOf(0f, 0f)
        var sa = 0.0; var saa = 0.0
        for (y in y0 until y1) for (x in x0 until x1) { val v = a[y * hw + x].toDouble(); sa += v; saa += v * v }
        val varA = saa - sa * sa / cnt
        if (varA < 1e-6) return floatArrayOf(0f, 0f)
        val sc = DoubleArray(side * side)
        for (dy in -r..r) for (dx in -r..r) {
            var sb = 0.0; var sbb = 0.0; var sab = 0.0
            for (y in y0 until y1) {
                val ra = y * hw; val rb = (y + dy) * hw + dx
                for (x in x0 until x1) {
                    val va = a[ra + x].toDouble(); val vb = b[rb + x].toDouble()
                    sb += vb; sbb += vb * vb; sab += va * vb
                }
            }
            val varB = sbb - sb * sb / cnt
            val den = sqrt(varA * varB)
            sc[(dy + r) * side + dx + r] = if (den > 1e-6) (sab - sa * sb / cnt) / den else -1.0
        }
        var best = -2.0; var bi = 0
        for (i in sc.indices) if (sc[i] > best) { best = sc[i]; bi = i }
        val iy = bi / side; val ix = bi % side
        fun sub(m: Double, c: Double, p: Double): Float {
            val den = m - 2 * c + p
            return if (den != 0.0) (0.5 * (m - p) / den).toFloat() else 0f
        }
        var fx = 0f; var fy = 0f
        if (ix in 1 until 2 * r) fx = sub(sc[iy * side + ix - 1], sc[iy * side + ix], sc[iy * side + ix + 1])
        if (iy in 1 until 2 * r) fy = sub(sc[(iy - 1) * side + ix], sc[iy * side + ix], sc[(iy + 1) * side + ix])
        return floatArrayOf(ix - r + fx, iy - r + fy)
    }

    /** out(x,y) = src(x+dx, y+dy), bilinear, border replicated. */
    private fun warp(src: FloatArray, dx: Float, dy: Float, out: FloatArray) {
        val ix = floor(dx).toInt(); val fx = dx - ix
        val iy = floor(dy).toInt(); val fy = dy - iy
        for (y in 0 until h) {
            val y0 = (y + iy).coerceIn(0, h - 1); val y1 = (y + iy + 1).coerceIn(0, h - 1)
            for (x in 0 until w) {
                val x0 = (x + ix).coerceIn(0, w - 1); val x1 = (x + ix + 1).coerceIn(0, w - 1)
                val a = src[y0 * w + x0]; val b = src[y0 * w + x1]; val c = src[y1 * w + x0]; val d = src[y1 * w + x1]
                out[y * w + x] = (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
            }
        }
    }

    fun push(tMs: Long, luma: FloatArray) {
        if (luma.size != n) return
        val f = Fr(tMs, luma, halfGrad(luma))
        prev?.let { p ->
            val s = trackShift(p.g, f.g)
            f.px = p.px + 2f * s[0]; f.py = p.py + 2f * s[1]
        }
        q.add(f); prev = f
        while (evalPtr < q.size && q[q.size - 1].t - q[evalPtr].t >= winMs) { evaluate(evalPtr); evalPtr++ }
        if (evalPtr < q.size) {
            val keepFrom = q[evalPtr].t - winMs
            var drop = 0
            while (drop < evalPtr && q[drop].t < keepFrom) drop++
            if (drop > 0) { repeat(drop) { q.removeAt(0) }; evalPtr -= drop }
        }
    }

    /** Call once after the last frame; evaluates frames whose "after" window is incomplete. */
    fun finish() {
        while (evalPtr < q.size) { evaluate(evalPtr); evalPtr++ }
        q.clear(); evalPtr = 0
    }

    private fun pick(l: List<Fr>): List<Fr> {
        if (l.size <= MAX_SIDE) return l
        return List(MAX_SIDE) { l[Math.round(it * (l.size - 1) / (MAX_SIDE - 1).toFloat())] }
    }

    private fun side(frs: List<Fr>, c: Fr, offset: Int, outMin: FloatArray, outMed: FloatArray) {
        val k = frs.size
        for (m in 0 until k) warp(frs[m].l, frs[m].px - c.px, frs[m].py - c.py, pool[offset + m])
        val tmp = FloatArray(k)
        for (i in 0 until n) {
            for (m in 0 until k) tmp[m] = pool[offset + m][i]
            for (a in 1 until k) { val v = tmp[a]; var b = a - 1; while (b >= 0 && tmp[b] > v) { tmp[b + 1] = tmp[b]; b-- }; tmp[b + 1] = v }
            outMin[i] = tmp[0]; outMed[i] = tmp[k / 2]
        }
    }

    private fun evaluate(i: Int) {
        val c = q[i]
        val before = ArrayList<Fr>(); val after = ArrayList<Fr>()
        var j = i - 1
        while (j >= 0 && c.t - q[j].t <= winMs) { before.add(0, q[j]); j-- }
        j = i + 1
        while (j < q.size && q[j].t - c.t <= winMs) { after.add(q[j]); j++ }
        val b = pick(before); val a = pick(after)
        if (b.isEmpty() && a.isEmpty()) { stats.add(Stat(c.t, 0f, 0f, 0)); return }
        if (b.isNotEmpty()) side(b, c, 0, minB, medB)
        if (a.isNotEmpty()) side(a, c, MAX_SIDE, minA, medA)
        val both = b.isNotEmpty() && a.isNotEmpty()
        var posCount = 0
        val l = c.l
        for (y in 0 until h) for (x in 0 until w) {
            val p = y * w + x
            val base = if (both) max(minB[p], minA[p]) else if (b.isNotEmpty()) minB[p] else minA[p]
            val ref = if (both) 0.5f * (medB[p] + medA[p]) else if (b.isNotEmpty()) medB[p] else medA[p]
            val inside = x >= BORDER && x < w - BORDER && y >= BORDER && y < h - BORDER
            val pos = inside && (l[p] - base) > T1
            posM[p] = pos; if (pos) posCount++
            negM[p] = (l[p] - ref) < -T1
        }
        val area = posCount.toFloat() / n
        if (posCount < 12) { stats.add(Stat(c.t, area, 0f, 0)); return }
        dilate7(negM, negD)
        var unp = 0
        for (p in 0 until n) { val u = posM[p] && !negD[p]; tmpD[p] = u; if (u) unp++ }
        var longest = 0
        if (unp >= 12) longest = longestComponent(tmpD)
        stats.add(Stat(c.t, area, unp * 57600f / n, longest))
    }

    /** 7x7 binary dilation (separable). */
    private fun dilate7(src: BooleanArray, dst: BooleanArray) {
        val tmp = tmpD
        for (y in 0 until h) {
            val r = y * w
            for (x in 0 until w) {
                var v = false
                var xx = max(0, x - 3)
                val xe = min(w - 1, x + 3)
                while (xx <= xe) { if (src[r + xx]) { v = true; break }; xx++ }
                dst[r + x] = v
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var v = false
                var yy = max(0, y - 3)
                val ye = min(h - 1, y + 3)
                while (yy <= ye) { if (dst[yy * w + x]) { v = true; break }; yy++ }
                tmp[y * w + x] = v
            }
        }
        System.arraycopy(tmp, 0, dst, 0, n)
    }

    /** Longest bounding-box side among 8-connected components with >= 6 pixels. Consumes (clears) the mask. */
    private fun longestComponent(mask: BooleanArray): Int {
        var longest = 0
        for (s in 0 until n) {
            if (!mask[s]) continue
            var sp = 0; stack[sp++] = s; mask[s] = false
            var minX = w; var maxX = 0; var minY = h; var maxY = 0; var area = 0
            while (sp > 0) {
                val p = stack[--sp]; val x = p % w; val y = p / w
                area++
                if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue
                    val pp = yy * w + xx
                    if (mask[pp]) { mask[pp] = false; stack[sp++] = pp }
                }
            }
            if (area >= 6) longest = max(longest, max(maxX - minX + 1, maxY - minY + 1))
        }
        return longest
    }
}

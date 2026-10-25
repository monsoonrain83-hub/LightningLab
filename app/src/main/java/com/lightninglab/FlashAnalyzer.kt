package com.lightninglab

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class LightningEvent(val id: Int, val startMs: Long, val endMs: Long, val bestMs: Long, val score: Float)

/** One analysed frame: mean luma, GX*GY cell luma grid, and "thin bright line" fraction. No Android types. */
class Sample(val tMs: Long, val mean: Float, val cells: FloatArray, val structure: Float)

/**
 * Transient-positive flash analysis (pure Kotlin, unit-testable).
 *
 * A flash is a change that is (1) TRANSIENT - the cell differs from the median of the same cell both
 * 0.15-1.0 s BEFORE and 0.15-1.0 s AFTER, in the same direction; a permanent change (scene cut, exposure
 * step) differs only from one side and is rejected; and (2) POSITIVE-DOMINANT - camera shake produces
 * positive and negative changes together, so P/(P+N) must be >= [POS_RATIO].
 * Thresholds are FIXED functions of sensitivity (not derived from whole-video noise: that made the old
 * auto-threshold explode on flashy/shaky clips).
 * The first/last [EDGE_MS] of the video are ignored (title cards, fades).
 */
object FlashAnalyzer {
    const val GX = 16
    const val GY = 9
    const val EDGE_MS = 150L
    const val POS_RATIO = 0.7f
    const val WEAK_CELLS = 6
    const val DEADBAND = 0.25f
    private const val NEAR_MS = 150L
    private const val FAR_MS = 1000L
    private const val MAX_PICK = 12

    /** Strong single-cell threshold (luma units, 0..255) and weak threshold for the many-cell (faint) path. */
    fun strongThreshold(sens: Float) = 6f + 14f * (1f - sens.coerceIn(0f, 1f))
    fun weakThreshold(sens: Float) = 0.45f * strongThreshold(sens)

    /** Median of `cell` over up to MAX_PICK evenly spaced samples in [lo, hi] (inclusive indices). */
    private fun medianOf(ms: List<Sample>, lo: Int, hi: Int, cell: Int, buf: FloatArray): Float {
        val cnt = hi - lo + 1
        val k = min(MAX_PICK, cnt)
        for (j in 0 until k) buf[j] = ms[lo + (j.toLong() * (cnt - 1) / max(1, k - 1)).toInt()].cells[cell]
        java.util.Arrays.sort(buf, 0, k)
        return buf[k / 2]
    }

    /** Per-frame transient score. Returns flagged flag array and `rise` (strongest same-sign transient cell). */
    class Frames(val flagged: BooleanArray, val rise: FloatArray, val meanRise: FloatArray)

    fun scoreFrames(ms: List<Sample>, sens: Float): Frames {
        val n = ms.size
        val flagged = BooleanArray(n); val rise = FloatArray(n); val meanRise = FloatArray(n)
        if (n < 5) return Frames(flagged, rise, meanRise)
        val strong = strongThreshold(sens); val weak = weakThreshold(sens)
        val dead = DEADBAND * weak // sensor noise below this does not vote in the P/(P+N) ratio
        val cellN = GX * GY
        val buf = FloatArray(MAX_PICK)
        val t0 = ms.first().tMs; val t1 = ms.last().tMs
        var bLo = 0; var bHi = -1; var aLo = 0; var aHi = -1
        for (i in 0 until n) {
            val t = ms[i].tMs
            if (t - t0 < EDGE_MS || t1 - t < EDGE_MS) continue
            // before window [t-FAR, t-NEAR]
            while (bLo < n && ms[bLo].tMs < t - FAR_MS) bLo++
            while (bHi + 1 < i && ms[bHi + 1].tMs <= t - NEAR_MS) bHi++
            // after window [t+NEAR, t+FAR]
            if (aLo < i) aLo = i
            while (aLo < n && ms[aLo].tMs < t + NEAR_MS) aLo++
            if (aHi < aLo - 1) aHi = aLo - 1
            while (aHi + 1 < n && ms[aHi + 1].tMs <= t + FAR_MS) aHi++
            val hasB = bHi >= bLo && bHi >= 0
            val hasA = aHi >= aLo && aLo < n
            if (!hasB || !hasA) continue // need both sides to prove "transient"
            var best = 0f; var pos = 0f; var neg = 0f; var weakCells = 0
            for (c in 0 until cellN) {
                val v = ms[i].cells[c]
                val dB = v - medianOf(ms, bLo, bHi, c, buf)
                val dA = v - medianOf(ms, aLo, aHi, c, buf)
                val d = when {
                    dB > 0f && dA > 0f -> min(dB, dA)
                    dB < 0f && dA < 0f -> max(dB, dA)
                    else -> 0f
                }
                if (d > 0f) { if (d > best) best = d; if (d > weak) weakCells++ }
                if (d > dead) pos += d else if (d < -dead) neg -= d
            }
            rise[i] = best; meanRise[i] = pos / cellN
            val ratio = if (pos + neg > 0f) pos / (pos + neg) else 0f
            flagged[i] = ratio >= POS_RATIO && (best > strong || weakCells >= WEAK_CELLS)
        }
        return Frames(flagged, rise, meanRise)
    }

    fun analyze(ms: List<Sample>, sens: Float, minSepMs: Long): List<LightningEvent> {
        val n = ms.size
        if (n < 5) return emptyList()
        val f = scoreFrames(ms, sens)
        val idx = (0 until n).filter { f.flagged[it] }
        val out = ArrayList<LightningEvent>()
        var gi = 0
        while (gi < idx.size) {
            val s0 = idx[gi]; var e0 = s0; var j = gi + 1
            while (j < idx.size && ms[idx[j]].tMs - ms[e0].tMs <= minSepMs) { e0 = idx[j]; j++ }
            gi = j
            var bestI = s0; var bestS = -1f
            for (i in max(0, s0 - 1)..min(n - 1, e0 + 1)) {
                val sc = 0.6f * min(1f, ms[i].structure * 40f) +
                    0.4f * min(1f, max(0f, f.rise[i] - f.meanRise[i]) / 40f)
                if (sc > bestS) { bestS = sc; bestI = i }
            }
            out += LightningEvent(out.size + 1, ms[s0].tMs, ms[e0].tMs, ms[bestI].tMs, max(0f, bestS))
        }
        return out
    }
}

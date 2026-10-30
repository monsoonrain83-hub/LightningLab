package com.lightninglab

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class LightningEvent(val id: Int, val startMs: Long, val endMs: Long, val bestMs: Long, val score: Float)

/** One analysed frame: mean luma, GX*GY cell luma grid, and "thin bright line" fraction. No Android types. */
class Sample(val tMs: Long, val mean: Float, val cells: FloatArray, val structure: Float)

/**
 * Flash analysis (pure Kotlin, unit-testable). Findings from four REAL clips (day + night, handheld) drove this design:
 *
 *  CELL PATH (night flashes, cloud illumination, rolling-shutter glow):
 *   - Baseline per cell = LOW percentile (25th) of the same cell 0.1-1.0 s before and after (not the median: in a
 *     dense flash cluster the median is itself bright and real flashes were missed - 53/87 bright frames before,
 *     67/87 after on a real night clip). A flash must exceed BOTH sides' baselines.
 *   - Shake guard: residual vs the same-sign minimum of the two medians must be positive-dominant (P/(P+N) >= 0.7).
 *   - At the very start/end of the video only one side exists; that side is used with an extra margin (a flash
 *     already in progress when recording starts was previously ignored).
 *   - VERIFICATION with [DayLine]: handheld shake still fools the cell statistics. An episode is kept only if some
 *     frame has a motion-compensated positive area >= [MIN_AREA_FRAC] (real flashes: >= 2.8 %; shake: <= 0.06 %),
 *     unless it touches the first/last [EDGE_KEEP_MS] where no window exists.
 *   - Episodes are split into strokes at energy peaks separated by a dip (< 60 % of the lower peak).
 *  DAY LINE PATH: frames whose unpaired motion-compensated residual forms a long thin structure ([DayLine]) become
 *   events, so faint daytime bolts that barely change any cell mean are found.
 * Thresholds are fixed functions of sensitivity (never derived from whole-video noise).
 */
object FlashAnalyzer {
    const val GX = 16
    const val GY = 9
    const val POS_RATIO = 0.7f
    const val WEAK_CELLS = 6
    const val DEADBAND = 0.25f
    const val MIN_AREA_FRAC = 0.003f
    const val EDGE_KEEP_MS = 120L
    const val DAY_GAP_MS = 50L
    const val DUP_MARGIN_MS = 150L
    const val PEAK_MS = 66L
    const val DIP = 0.6f
    private const val NEAR_MS = 100L
    private const val FAR_MS = 1000L
    private const val MAX_PICK = 12

    /** Strong single-cell threshold (luma units, 0..255) and weak threshold for the many-cell (faint) path. */
    fun strongThreshold(sens: Float) = 6f + 14f * (1f - sens.coerceIn(0f, 1f))
    fun weakThreshold(sens: Float) = 0.45f * strongThreshold(sens)
    /** Day-line thresholds: longest unpaired thin structure (px at 320 wide) or unpaired pixel count. */
    fun dayLongest(sens: Float) = 16f + 24f * (1f - sens.coerceIn(0f, 1f))
    fun dayUnpaired(sens: Float) = 120f + 300f * (1f - sens.coerceIn(0f, 1f))

    class Frames(val flagged: BooleanArray, val best: FloatArray, val energy: FloatArray)

    /** Sorted copy of up to MAX_PICK evenly spaced samples of `cell` in [lo, hi]; returns count (sorted in buf[0 until k]). */
    private fun sortedPick(ms: List<Sample>, lo: Int, hi: Int, cell: Int, buf: FloatArray): Int {
        val cnt = hi - lo + 1
        val k = min(MAX_PICK, cnt)
        for (j in 0 until k) buf[j] = ms[lo + (j.toLong() * (cnt - 1) / max(1, k - 1)).toInt()].cells[cell]
        java.util.Arrays.sort(buf, 0, k)
        return k
    }

    fun scoreFrames(ms: List<Sample>, sens: Float): Frames {
        val n = ms.size
        val flagged = BooleanArray(n); val best = FloatArray(n); val energy = FloatArray(n)
        if (n < 5) return Frames(flagged, best, energy)
        val strong = strongThreshold(sens); val weak = weakThreshold(sens)
        val dead = DEADBAND * weak
        val cellN = GX * GY
        val buf = FloatArray(MAX_PICK)
        var bLo = 0; var bHi = -1; var aLo = 0; var aHi = -1
        for (i in 0 until n) {
            val t = ms[i].tMs
            while (bLo < n && ms[bLo].tMs < t - FAR_MS) bLo++
            while (bHi + 1 < i && ms[bHi + 1].tMs <= t - NEAR_MS) bHi++
            if (aLo < i) aLo = i
            while (aLo < n && ms[aLo].tMs < t + NEAR_MS) aLo++
            if (aHi < aLo - 1) aHi = aLo - 1
            while (aHi + 1 < n && ms[aHi + 1].tMs <= t + FAR_MS) aHi++
            val hasB = bHi >= bLo && bHi >= 0
            val hasA = aHi >= aLo && aLo < n
            if (!hasB && !hasA) continue
            var bestV = -1e9f; var pos = 0f; var neg = 0f; var weakCells = 0; var e = 0f
            for (c in 0 until cellN) {
                val v = ms[i].cells[c]
                var lB = 0f; var mB = 0f; var lA = 0f; var mA = 0f
                if (hasB) { val k = sortedPick(ms, bLo, bHi, c, buf); lB = buf[((k - 1) * 0.25f).toInt()]; mB = buf[k / 2] }
                if (hasA) { val k = sortedPick(ms, aLo, aHi, c, buf); lA = buf[((k - 1) * 0.25f).toInt()]; mA = buf[k / 2] }
                val low: Float; val rm: Float
                if (hasB && hasA) {
                    low = max(lB, lA)
                    val dB = v - mB; val dA = v - mA
                    rm = if (dB > 0f && dA > 0f) min(dB, dA) else if (dB < 0f && dA < 0f) max(dB, dA) else 0f
                } else {
                    low = (if (hasB) lB else lA) + 0.5f * strong
                    rm = v - (if (hasB) mB else mA)
                }
                val dpos = v - low
                if (dpos > bestV) bestV = dpos
                if (dpos > weak) weakCells++
                if (dpos > 0f) e += dpos
                if (rm > dead) pos += rm else if (rm < -dead) neg -= rm
            }
            val ratio = if (pos + neg > 0f) pos / (pos + neg) else 1f
            best[i] = bestV; energy[i] = e / cellN
            flagged[i] = ratio >= POS_RATIO && (bestV > strong || weakCells >= WEAK_CELLS)
        }
        return Frames(flagged, best, energy)
    }

    private fun frameScore(ms: List<Sample>, f: Frames, i: Int): Float =
        0.6f * min(1f, ms[i].structure * 40f) + 0.4f * min(1f, max(0f, f.best[i] - f.energy[i]) / 40f)

    /** Splits one confirmed episode [s0, e0] into strokes at energy peaks separated by a clear dip. */
    private fun strokes(ms: List<Sample>, f: Frames, s0: Int, e0: Int, out: MutableList<LightningEvent>) {
        val fl = (s0..e0).filter { f.flagged[it] }
        val peaks = fl.filter { i ->
            f.energy[i] > 0f && (s0..e0).all { j -> abs(ms[j].tMs - ms[i].tMs) > PEAK_MS || f.energy[j] <= f.energy[i] }
        }
        val kept = ArrayList<Int>()
        for (pk in peaks) {
            if (kept.isEmpty()) { kept.add(pk); continue }
            val q = kept[kept.size - 1]
            var valley = Float.MAX_VALUE
            for (j in q..pk) valley = min(valley, f.energy[j])
            if (valley < DIP * min(f.energy[q], f.energy[pk])) kept.add(pk)
            else if (f.energy[pk] > f.energy[q]) kept[kept.size - 1] = pk
        }
        if (kept.isEmpty()) kept.add(fl.maxByOrNull { f.energy[it] } ?: s0)
        val cuts = ArrayList<Int>()
        for (k in 1 until kept.size) {
            var vi = kept[k - 1]
            for (j in kept[k - 1]..kept[k]) if (f.energy[j] < f.energy[vi]) vi = j
            cuts.add(vi)
        }
        for (k in kept.indices) {
            val st = if (k == 0) s0 else cuts[k - 1]
            val en = max(st, if (k == kept.size - 1) e0 else cuts[k] - 1)
            var bi = st; var bs = -1f
            for (i in max(0, st - 1)..min(ms.size - 1, en + 1)) {
                val sc = frameScore(ms, f, i)
                if (sc > bs) { bs = sc; bi = i }
            }
            out.add(LightningEvent(0, ms[st].tMs, ms[en].tMs, ms[bi].tMs, max(0f, bs)))
        }
    }

    fun analyze(ms: List<Sample>, sens: Float, minSepMs: Long, day: List<DayLine.Stat>? = null): List<LightningEvent> {
        val n = ms.size
        if (n < 5) return emptyList()
        val f = scoreFrames(ms, sens)
        val useDay = day != null && day.size == n
        val out = ArrayList<LightningEvent>()
        val t0 = ms.first().tMs; val t1 = ms.last().tMs
        val covered = ArrayList<LongArray>()
        val idx = (0 until n).filter { f.flagged[it] }
        var gi = 0
        while (gi < idx.size) {
            val s0 = idx[gi]; var e0 = s0; var j = gi + 1
            while (j < idx.size && ms[idx[j]].tMs - ms[e0].tMs <= minSepMs) { e0 = idx[j]; j++ }
            gi = j
            val edge = ms[s0].tMs - t0 <= EDGE_KEEP_MS || t1 - ms[e0].tMs <= EDGE_KEEP_MS
            val confirmed = !useDay || edge || (s0..e0).any { day!![it].areaFrac >= MIN_AREA_FRAC }
            if (!confirmed) continue
            covered.add(longArrayOf(ms[s0].tMs, ms[e0].tMs))
            strokes(ms, f, s0, e0, out)
        }
        if (useDay) {
            val ln = dayLongest(sens); val un = dayUnpaired(sens)
            val di = (0 until n).filter { i ->
                val s = day!![i]; val t = ms[i].tMs
                (s.longest >= ln || s.unpaired >= un) &&
                    covered.none { r -> t >= r[0] - DUP_MARGIN_MS && t <= r[1] + DUP_MARGIN_MS }
            }
            var k = 0
            while (k < di.size) {
                val s0 = di[k]; var e0 = s0; var m = k + 1
                while (m < di.size && ms[di[m]].tMs - ms[e0].tMs <= DAY_GAP_MS) { e0 = di[m]; m++ }
                k = m
                var bi = s0; var bv = -1f
                for (i in s0..e0) {
                    val v = day!![i].longest + day[i].unpaired / 20f
                    if (v > bv) { bv = v; bi = i }
                }
                val sc = min(1f, day!![bi].longest / 80f) * 0.8f + 0.2f * min(1f, ms[bi].structure * 40f)
                out.add(LightningEvent(0, ms[s0].tMs, ms[e0].tMs, ms[bi].tMs, sc))
            }
        }
        return out.sortedBy { it.startMs }.mapIndexed { i, e -> e.copy(id = i + 1) }
    }
}

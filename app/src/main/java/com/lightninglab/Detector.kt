package com.lightninglab

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.OPTION_CLOSEST
import android.net.Uri
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class LightningEvent(val id: Int, val startMs: Long, val endMs: Long, val bestMs: Long, val score: Float)

/**
 * Offline lightning detector (foundation version).
 *
 * Stage A: sequential low-res scan; per-frame 6x4 grid of cell luminance.
 * Stage B: a cell that jumps above the median of the preceding ~1 s window flags a localized flash
 *          (adaptive to slow exposure drift). Flagged samples are grouped into events.
 * Cut rejection: a real flash returns to baseline within ~0.5 s; a scene cut stays changed.
 * Stage C: each event is re-scanned at a finer step and the frame with the best structure score
 *          (bright thin edges, penalized by clipping) is chosen. Score is HEURISTIC, not a probability.
 *
 * Known limits: MediaMetadataRetriever seeking is slow on long videos; no camera-motion compensation.
 */
class Detector(private val ctx: Context) {
    private companion object { const val GX = 6; const val GY = 4 }

    private class Metrics(val mean: Float, val cells: FloatArray, val score: Float)

    private fun open(uri: Uri): MediaMetadataRetriever =
        MediaMetadataRetriever().apply { setDataSource(ctx, uri) }

    private fun dims(r: MediaMetadataRetriever, maxSide: Int): Pair<Int, Int> {
        var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
        var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
        val s = min(1f, maxSide.toFloat() / max(w, h))
        return max(2, (w * s).toInt()) to max(2, (h * s).toInt())
    }

    private fun metrics(b: Bitmap): Metrics {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        val luma = FloatArray(w * h) {
            val p = px[it]
            0.299f * ((p shr 16) and 255) + 0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)
        }
        var sum = 0f; for (v in luma) sum += v
        val mean = sum / luma.size
        val cells = FloatArray(GX * GY); val cnt = IntArray(GX * GY)
        var edges = 0; var clipped = 0
        for (y in 0 until h) for (x in 0 until w) {
            val l = luma[y * w + x]
            val c = (y * GY / h) * GX + (x * GX / w)
            cells[c] += l; cnt[c]++
            if (l > 250f) clipped++
            if (l > mean + 40f && x < w - 1 && y < h - 1) {
                val g = abs(l - luma[y * w + x + 1]) + abs(l - luma[(y + 1) * w + x])
                if (g > 40f) edges++
            }
        }
        for (i in cells.indices) {
            val d = max(1, cnt[i]).toFloat()
            cells[i] = cells[i] / d
        }
        val n = luma.size.toFloat()
        return Metrics(mean, cells, (edges / n) * (1f - clipped / n))
    }

    private fun median(v: List<Float>): Float { val s = v.sorted(); return s[s.size / 2] }

    private fun sample(r: MediaMetadataRetriever, tMs: Long, w: Int, h: Int): Metrics? {
        val b = r.getScaledFrameAtTime(tMs * 1000, OPTION_CLOSEST, w, h) ?: return null
        val m = metrics(b); b.recycle(); return m
    }

    fun detect(
        uri: Uri, sensitivity: Float, stepMs: Long = 100L, minSepMs: Long = 1500L,
        onProgress: (Float) -> Unit, onStage: (Int) -> Unit, cancelled: () -> Boolean
    ): List<LightningEvent> {
        val r = open(uri)
        try {
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return emptyList()
            val (w, h) = dims(r, 320)
            val times = ArrayList<Long>(); val ms = ArrayList<Metrics>()
            var t = 0L
            while (t < dur) {
                if (cancelled()) return emptyList()
                sample(r, t, w, h)?.let { times += t; ms += it }
                t += stepMs
                onProgress(min(0.8f, t.toFloat() / dur * 0.8f))
            }
            val n = ms.size
            val thr = 25f - 20f * sensitivity.coerceIn(0f, 1f)
            val jump = FloatArray(n)
            for (i in 0 until n) {
                val a = max(0, i - 10); val b = i - 2
                if (b < a) continue
                var best = 0f
                for (c in 0 until GX * GY) {
                    val base = median((a..b).map { ms[it].cells[c] })
                    best = max(best, ms[i].cells[c] - base)
                }
                jump[i] = best
            }
            val gap = max(1, (minSepMs / stepMs).toInt())
            val groups = ArrayList<IntArray>() // [start, end]
            var i = 0
            while (i < n) {
                if (jump[i] > thr) {
                    val s = i; var e = i; var j = i + 1
                    while (j < n && j - e <= gap) { if (jump[j] > thr) e = j; j++ }
                    groups += intArrayOf(s, e); i = e + 1
                } else i++
            }
            onStage(R.string.stage_refine)
            val out = ArrayList<LightningEvent>()
            val after = max(1, (500 / stepMs).toInt())
            for ((gi, g) in groups.withIndex()) {
                if (cancelled()) return emptyList()
                val (s, e) = g[0] to g[1]
                // scene-cut rejection
                if (s - 10 >= 0 && e + after < n) {
                    val baseMean = median((max(0, s - 10)..(s - 2).coerceAtLeast(0)).map { ms[it].mean })
                    val peakMean = (s..e).maxOf { ms[it].mean }
                    if (peakMean - baseMean > 0 && ms[e + after].mean - baseMean > 0.7f * (peakMean - baseMean)) continue
                }
                // fine re-scan around the event
                val from = max(0L, times[s] - stepMs); val to = min(dur, times[e] + stepMs)
                val fine = max(25L, (to - from) / 40)
                var bestT = times[(s..e).maxByOrNull { jump[it] }!!]; var bestS = -1f
                var tt = from
                while (tt <= to) {
                    sample(r, tt, w, h)?.let { if (it.score > bestS) { bestS = it.score; bestT = tt } }
                    tt += fine
                }
                out += LightningEvent(out.size + 1, times[s], times[e], bestT, max(0f, bestS))
                onProgress(0.8f + 0.2f * (gi + 1) / groups.size)
            }
            return out
        } finally { r.release() }
    }

    /** Decode one frame; scaled so the longer side is at most maxSide. */
    fun frame(uri: Uri, tMs: Long, maxSide: Int): Bitmap? {
        val r = open(uri)
        try {
            val (w, h) = dims(r, maxSide)
            return r.getScaledFrameAtTime(tMs * 1000, OPTION_CLOSEST, w, h)
        } finally { r.release() }
    }
}

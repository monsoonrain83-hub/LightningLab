package com.lightninglab

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.OPTION_CLOSEST
import android.net.Uri
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class LightningEvent(val id: Int, val startMs: Long, val endMs: Long, val bestMs: Long, val score: Float)

/**
 * Offline lightning detector.
 *
 * Scan: every frame is decoded sequentially with MediaCodec (fast; no per-frame seeking). Only the luma (Y)
 *       plane is read, block-averaged to ~320 px wide. Per frame we keep a 16x9 grid of cell luminance and a
 *       "structure" value = fraction of pixels brighter than their local 7x7 neighbourhood (thin bright lines).
 *       If MediaCodec fails, a slower MediaMetadataRetriever scan (100 ms step) is used.
 * Detect: each cell is compared with the median of the same cell ~0.15-1.0 s earlier (adaptive to slow exposure
 *       drift). A localized rise above a noise-adaptive threshold flags the frame. This works for faint daytime
 *       flashes because the threshold is derived from the video's own noise level, not from a fixed brightness.
 * Group: flagged frames closer than minSep are one event. Scene cuts are rejected (a flash returns to baseline,
 *       a cut does not).
 * Rank: best frame in an event = structure + localized-vs-global rise. HEURISTIC score, not a probability.
 */
class Detector(private val ctx: Context) {
    private companion object { const val GX = 16; const val GY = 9; const val TW = 320 }

    class Sample(val tMs: Long, val mean: Float, val cells: FloatArray, val structure: Float)

    private fun sampleFromLuma(t: Long, l: FloatArray, w: Int, h: Int): Sample {
        var sum = 0f
        for (v in l) sum += v
        val mean = sum / l.size
        val cells = FloatArray(GX * GY); val cnt = IntArray(GX * GY)
        for (y in 0 until h) for (x in 0 until w) {
            val c = (y * GY / h) * GX + (x * GX / w)
            cells[c] += l[y * w + x]; cnt[c]++
        }
        for (i in cells.indices) cells[i] = cells[i] / max(1, cnt[i]).toFloat()
        val blur = ImgUtil.boxBlur(l, w, h, 3)
        var edges = 0
        for (i in l.indices) if (l[i] - blur[i] > 18f) edges++
        return Sample(t, mean, cells, edges.toFloat() / l.size)
    }

    private fun lumaGrid(img: Image, tw: Int, th: Int): FloatArray {
        val p = img.planes[0]; val buf = p.buffer; val rs = p.rowStride; val ps = p.pixelStride
        val bigW = img.width; val bigH = img.height
        val bw = max(1, bigW / tw); val bh = max(1, bigH / th)
        val off = if (ps == 2) 1 else 0 // 10-bit: take the high byte
        val limit = buf.limit()
        val out = FloatArray(tw * th)
        for (y in 0 until th) for (x in 0 until tw) {
            var s = 0; var c = 0
            var yy = y * bh
            val ye = min(bigH, yy + bh); val xe = min(bigW, (x + 1) * bw)
            while (yy < ye) {
                var xx = x * bw
                while (xx < xe) {
                    val idx = yy * rs + xx * ps + off
                    if (idx < limit) { s += buf.get(idx).toInt() and 255; c++ }
                    xx += 2
                }
                yy += 2
            }
            out[y * tw + x] = if (c > 0) s.toFloat() / c else 0f
        }
        return out
    }

    private fun scanCodec(uri: Uri, onProgress: (Float) -> Unit, cancelled: () -> Boolean): ArrayList<Sample>? {
        val ex = MediaExtractor(); var codec: MediaCodec? = null
        try {
            ex.setDataSource(ctx, uri, null)
            var track = -1
            for (i in 0 until ex.trackCount) {
                if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { track = i; break }
            }
            if (track < 0) return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
            val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 1L
            val c = MediaCodec.createDecoderByType(mime); codec = c
            c.configure(fmt, null, null, 0); c.start()
            val info = MediaCodec.BufferInfo()
            val out = ArrayList<Sample>()
            var inDone = false; var outDone = false; var lastT = -100L
            while (!outDone) {
                if (cancelled()) return out
                if (!inDone) {
                    val ii = c.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val b = c.getInputBuffer(ii)!!
                        val sz = ex.readSampleData(b, 0)
                        if (sz < 0) { c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { c.queueInputBuffer(ii, 0, sz, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = c.dequeueOutputBuffer(info, 5000)
                if (oi >= 0) {
                    val tMs = info.presentationTimeUs / 1000
                    if (info.size > 0 && tMs - lastT >= 8) {
                        val img = c.getOutputImage(oi) ?: return null
                        val tw = min(TW, img.width); val th = max(2, tw * img.height / img.width)
                        out += sampleFromLuma(tMs, lumaGrid(img, tw, th), tw, th)
                        img.close(); lastT = tMs
                        onProgress(min(0.9f, info.presentationTimeUs.toFloat() / durUs * 0.9f))
                    }
                    c.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
            }
            return out
        } catch (e: Exception) {
            return null
        } finally {
            try { codec?.stop() } catch (e: Exception) { }
            codec?.release(); ex.release()
        }
    }

    private fun dims(r: MediaMetadataRetriever, maxSide: Int): Pair<Int, Int> {
        var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
        var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
        val s = min(1f, maxSide.toFloat() / max(w, h))
        return max(2, (w * s).toInt()) to max(2, (h * s).toInt())
    }

    private fun scanRetriever(uri: Uri, onProgress: (Float) -> Unit, cancelled: () -> Boolean): ArrayList<Sample> {
        val r = MediaMetadataRetriever(); val out = ArrayList<Sample>()
        try {
            r.setDataSource(ctx, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return out
            val (w, h) = dims(r, TW)
            var t = 0L
            while (t < dur) {
                if (cancelled()) break
                val b = r.getScaledFrameAtTime(t * 1000, OPTION_CLOSEST, w, h)
                if (b != null) {
                    val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h); b.recycle()
                    out += sampleFromLuma(t, FloatArray(w * h) { ImgUtil.luma(px[it]) }, w, h)
                }
                t += 100; onProgress(min(0.9f, t.toFloat() / dur * 0.9f))
            }
        } finally { r.release() }
        return out
    }

    fun detect(
        uri: Uri, sensitivity: Float, minSepMs: Long = 1000L,
        onProgress: (Float) -> Unit, onStage: (Int) -> Unit, cancelled: () -> Boolean
    ): List<LightningEvent> {
        var s = scanCodec(uri, onProgress, cancelled)
        if (cancelled()) return emptyList()
        if (s == null || s.size < 5) s = scanRetriever(uri, onProgress, cancelled)
        if (cancelled()) return emptyList()
        onStage(R.string.stage_refine)
        return analyze(s, sensitivity, minSepMs)
    }

    private fun analyze(ms: List<Sample>, sens: Float, minSepMs: Long): List<LightningEvent> {
        val n = ms.size
        if (n < 5) return emptyList()
        val sg = sens.coerceIn(0f, 1f)
        val jump = FloatArray(n); val meanJump = FloatArray(n)
        var a = 0; var b = -1
        val cellN = GX * GY
        for (i in 0 until n) {
            val t = ms[i].tMs
            while (a < n && ms[a].tMs < t - 1000) a++
            while (b + 1 < i && ms[b + 1].tMs <= t - 150) b++
            if (b < a || b < 0) continue
            val cnt = b - a + 1; val k = min(12, cnt)
            val idx = IntArray(k) { a + (it.toLong() * (cnt - 1) / max(1, k - 1)).toInt() }
            var best = 0f; var tot = 0f
            val vals = FloatArray(k)
            for (c in 0 until cellN) {
                for (j in 0 until k) vals[j] = ms[idx[j]].cells[c]
                vals.sort()
                val d = ms[i].cells[c] - vals[k / 2]
                if (d > best) best = d
                tot += d
            }
            jump[i] = best; meanJump[i] = tot / cellN
        }
        val sorted = jump.sortedArray(); val med = sorted[n / 2]
        val dev = FloatArray(n) { abs(jump[it] - med) }.sortedArray()
        val sigma = 1.4826f * dev[n / 2]
        val k = 10f - 7f * sg
        val floor = 2.5f + 8f * (1f - sg)
        val thr = max(floor, med + k * sigma)

        val flagged = (0 until n).filter { jump[it] > thr }
        val groups = ArrayList<IntArray>()
        var gi = 0
        while (gi < flagged.size) {
            val s0 = flagged[gi]; var e0 = s0; var j = gi + 1
            while (j < flagged.size && ms[flagged[j]].tMs - ms[e0].tMs <= minSepMs) { e0 = flagged[j]; j++ }
            groups += intArrayOf(s0, e0); gi = j
        }
        val out = ArrayList<LightningEvent>()
        for (g in groups) {
            val s0 = g[0]; val e0 = g[1]
            // scene-cut rejection: after a real flash the brightness returns to baseline
            val t0 = ms[s0].tMs
            val bi = (0 until s0).filter { ms[it].tMs in (t0 - 1000)..(t0 - 150) }
            val ai = (e0 until n).firstOrNull { ms[it].tMs >= ms[e0].tMs + 500 }
            if (bi.isNotEmpty() && ai != null) {
                val baseMean = bi.map { ms[it].mean }.sorted()[bi.size / 2]
                val peakMean = (s0..e0).maxOf { ms[it].mean }
                if (peakMean - baseMean > 0 && ms[ai].mean - baseMean > 0.7f * (peakMean - baseMean)) continue
            }
            var bestI = (s0..e0).maxByOrNull { jump[it] }!!; var bestS = -1f
            for (i in max(0, s0 - 1)..min(n - 1, e0 + 1)) {
                val sc = 0.6f * min(1f, ms[i].structure * 40f) + 0.4f * min(1f, max(0f, jump[i] - meanJump[i]) / 40f)
                if (sc > bestS) { bestS = sc; bestI = i }
            }
            out += LightningEvent(out.size + 1, ms[s0].tMs, ms[e0].tMs, ms[bestI].tMs, max(0f, bestS))
        }
        return out
    }

    /** Decode one frame; scaled so the longer side is at most maxSide (use a huge value for full resolution). */
    fun frame(uri: Uri, tMs: Long, maxSide: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val (w, h) = dims(r, maxSide)
            return r.getScaledFrameAtTime(tMs * 1000, OPTION_CLOSEST, w, h)
        } finally { r.release() }
    }
}

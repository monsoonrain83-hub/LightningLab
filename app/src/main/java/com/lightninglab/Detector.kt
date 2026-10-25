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

/**
 * Offline lightning detector.
 *
 * Scan: every frame is decoded sequentially with MediaCodec (fast; no per-frame seeking). Only the luma (Y)
 *       plane is read, block-averaged to ~320 px wide. Per frame we keep a 16x9 grid of cell luminance and a
 *       "structure" value = fraction of pixels brighter than their local 7x7 neighbourhood (thin bright lines).
 *       If MediaCodec fails, a slower MediaMetadataRetriever scan (100 ms step) is used.
 * Detect: see [FlashAnalyzer] - a flash is a TRANSIENT, POSITIVE-dominant change versus the median of the
 *       same cell 0.15-1.0 s before AND after. Permanent changes (cuts, exposure steps) and camera shake
 *       (mixed-sign changes) are rejected; thresholds are fixed functions of sensitivity.
 * Group: flagged frames closer than minSep (default 250 ms) are one event.
 * Rank: best frame in an event = structure + localized-vs-global rise. HEURISTIC score, not a probability.
 */
class Detector(private val ctx: Context) {
    private companion object { const val GX = FlashAnalyzer.GX; const val GY = FlashAnalyzer.GY; const val TW = 320 }

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
        uri: Uri, sensitivity: Float, minSepMs: Long = 250L,
        onProgress: (Float) -> Unit, onStage: (Int) -> Unit, cancelled: () -> Boolean
    ): List<LightningEvent> {
        var s = scanCodec(uri, onProgress, cancelled)
        if (cancelled()) return emptyList()
        if (s == null || s.size < 5) s = scanRetriever(uri, onProgress, cancelled)
        if (cancelled()) return emptyList()
        onStage(R.string.stage_refine)
        return analyze(s, sensitivity, minSepMs)
    }

    private fun analyze(ms: List<Sample>, sens: Float, minSepMs: Long): List<LightningEvent> =
        FlashAnalyzer.analyze(ms, sens, minSepMs)

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

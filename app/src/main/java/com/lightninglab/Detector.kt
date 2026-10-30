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
 * Detect: see [FlashAnalyzer] (cell statistics, motion-compensated verification via [DayLine], stroke splitting).
 *       Every decoded frame is used (native frame rate); the optional range limits the analysed part of the clip.
 * Group: flagged frames closer than minSep (default 250 ms) form an episode, split into strokes at energy dips.
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

    private class Scan(val s: ArrayList<Sample>, val day: List<DayLine.Stat>?)

    /** startMs/endMs restrict the analysed range (seek to the previous key frame, decode, skip frames before startMs). */
    private fun scanCodec(uri: Uri, startMs: Long, endMs: Long, onProgress: (Float) -> Unit, cancelled: () -> Boolean): Scan? {
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
            val spanUs = max(1L, min(durUs, if (endMs == Long.MAX_VALUE) durUs else endMs * 1000) - startMs * 1000)
            if (startMs > 0) ex.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val c = MediaCodec.createDecoderByType(mime); codec = c
            c.configure(fmt, null, null, 0); c.start()
            val info = MediaCodec.BufferInfo()
            val out = ArrayList<Sample>()
            var day: DayLine? = null
            var inDone = false; var outDone = false; var lastT = -100L
            while (!outDone) {
                if (cancelled()) return Scan(out, null)
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
                    if (info.size > 0 && tMs >= startMs && tMs <= endMs && tMs - lastT >= 8) {
                        val img = c.getOutputImage(oi) ?: return null
                        val tw = min(TW, img.width); val th = max(2, tw * img.height / img.width)
                        val luma = lumaGrid(img, tw, th)
                        if (day == null) day = DayLine(tw, th)
                        day.push(tMs, luma)
                        out += sampleFromLuma(tMs, luma, tw, th)
                        img.close(); lastT = tMs
                        onProgress(min(0.9f, (info.presentationTimeUs - startMs * 1000).toFloat() / spanUs * 0.9f))
                    }
                    c.releaseOutputBuffer(oi, false)
                    if (tMs > endMs) outDone = true
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
            }
            day?.finish()
            return Scan(out, if (day != null && day.stats.size == out.size) day.stats else null)
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

    private fun scanRetriever(uri: Uri, startMs: Long, endMs: Long, onProgress: (Float) -> Unit, cancelled: () -> Boolean): ArrayList<Sample> {
        val r = MediaMetadataRetriever(); val out = ArrayList<Sample>()
        try {
            r.setDataSource(ctx, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return out
            val (w, h) = dims(r, TW)
            val end = min(dur, endMs)
            var t = startMs
            while (t < end) {
                if (cancelled()) break
                val b = r.getScaledFrameAtTime(t * 1000, OPTION_CLOSEST, w, h)
                if (b != null) {
                    val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h); b.recycle()
                    out += sampleFromLuma(t, FloatArray(w * h) { ImgUtil.luma(px[it]) }, w, h)
                }
                t += 100; onProgress(min(0.9f, (t - startMs).toFloat() / max(1L, end - startMs) * 0.9f))
            }
        } finally { r.release() }
        return out
    }

    /** Analyses [startMs, endMs] of the video. The coarse retriever fallback has no motion-compensated verification. */
    fun detect(
        uri: Uri, sensitivity: Float, minSepMs: Long = 250L, startMs: Long = 0L, endMs: Long = Long.MAX_VALUE,
        onProgress: (Float) -> Unit, onStage: (Int) -> Unit, cancelled: () -> Boolean
    ): List<LightningEvent> {
        var sc = scanCodec(uri, startMs, endMs, onProgress, cancelled)
        if (cancelled()) return emptyList()
        if (sc == null || sc.s.size < 5) sc = Scan(scanRetriever(uri, startMs, endMs, onProgress, cancelled), null)
        if (cancelled()) return emptyList()
        onStage(R.string.stage_refine)
        return FlashAnalyzer.analyze(sc.s, sensitivity, minSepMs, sc.day)
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

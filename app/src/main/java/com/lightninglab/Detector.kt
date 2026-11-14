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
        val off = if (ps == 2) 1 else 0 // 10-bit: take the high byte
        val limit = buf.limit()
        val out = FloatArray(tw * th)
        for (y in 0 until th) {
            val yr = LumaGridGeometry.span(y, th, bigH)
            for (x in 0 until tw) {
                val xr = LumaGridGeometry.span(x, tw, bigW)
                var sum = 0; var count = 0
                var yy = yr.start
                while (yy < yr.endExclusive) {
                    var xx = xr.start
                    while (xx < xr.endExclusive) {
                        val idx = yy.toLong() * rs + xx.toLong() * ps + off
                        if (idx >= 0 && idx < limit) {
                            sum += buf.get(idx.toInt()).toInt() and 255
                            count++
                        }
                        xx += 2
                    }
                    yy += 2
                }
                out[y * tw + x] = if (count > 0) sum.toFloat() / count else 0f
            }
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
            val durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else -1L
            val window = LightningScanWindow.of(durationUs, startMs, endMs)
                ?: return Scan(ArrayList(), null)
            if (window.startUs > 0L)
                ex.seekTo(window.startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
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
                        val (tw, th) = LumaGridGeometry.targetDims(img.width, img.height, TW)
                        try {
                            val luma = lumaGrid(img, tw, th)
                            if (day == null) day = DayLine(tw, th)
                            day.push(tMs, luma)
                            out += sampleFromLuma(tMs, luma, tw, th)
                            lastT = tMs
                            onProgress(min(0.9f, window.progress(info.presentationTimeUs) * 0.9f))
                        } finally { img.close() }
                    }
                    c.releaseOutputBuffer(oi, false)
                    if (window.knownEnd && info.presentationTimeUs > window.endUs) outDone = true
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

    private fun scanRetriever(uri: Uri, startMs: Long, endMs: Long, onProgress: (Float) -> Unit, cancelled: () -> Boolean): Scan {
        val r = MediaMetadataRetriever(); val out = ArrayList<Sample>()
        var day: DayLine? = null
        try {
            r.setDataSource(ctx, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return Scan(out, null)
            val window = LightningScanWindow.of(LightningScanWindow.microseconds(dur), startMs, endMs)
                ?: return Scan(out, null)
            val (w, h) = dims(r, TW)
            if (LightningFallbackSampling.supportsMotionVerification(w, h))
                day = DayLine(w, h)
            val end = window.endUs / 1000L
            var t = window.startUs / 1000L
            val step = LightningFallbackSampling.intervalMs(end - t)
            while (t < end) {
                if (cancelled()) break
                val bitmap = r.getScaledFrameAtTime(
                    LightningScanWindow.microseconds(t), OPTION_CLOSEST, w, h)
                if (bitmap != null) {
                    val luma = try {
                        val px = IntArray(w * h)
                        bitmap.getPixels(px, 0, w, 0, 0, w, h)
                        FloatArray(w * h) { ImgUtil.luma(px[it]) }
                    } finally { bitmap.recycle() }
                    day?.push(t, luma)
                    out += sampleFromLuma(t, luma, w, h)
                }
                t = LightningFallbackSampling.next(t, end, step)
                onProgress(min(0.9f, window.progress(LightningScanWindow.microseconds(t)) * 0.9f))
            }
            day?.finish()
        } finally { r.release() }
        return Scan(out, day?.stats?.takeIf { it.size == out.size })
    }

    /** Analyses [startMs, endMs] of the video. The retriever fallback uses bounded, denser sampling and motion-compensated verification when dimensions permit. */
    fun detect(
        uri: Uri, sensitivity: Float, minSepMs: Long = 250L, startMs: Long = 0L, endMs: Long = Long.MAX_VALUE,
        onProgress: (Float) -> Unit, onStage: (Int) -> Unit, cancelled: () -> Boolean
    ): List<LightningEvent> {
        var sc = scanCodec(uri, startMs, endMs, onProgress, cancelled)
        if (cancelled()) return emptyList()
        if (sc == null || sc.s.size < 5) sc = scanRetriever(uri, startMs, endMs, onProgress, cancelled)
        if (cancelled()) return emptyList()
        onStage(R.string.stage_refine)
        return FlashAnalyzer.analyze(sc.s, sensitivity, minSepMs, sc.day)
    }

    /**
     * Decode a whole temporal window with one retriever instance rather than reopening the
     * same video for each frame. Ownership of returned bitmaps belongs to the caller.
     * On error, recycle everything to avoid leaking large direct allocations.
     */
    fun temporalFrames(uri: Uri, timesMs: LongArray, maxSide: Int, cancelled: () -> Boolean): List<Bitmap> {
        require(timesMs.size in 1..32 && maxSide in 1..2048)
        val frames = ArrayList<Bitmap>(timesMs.size)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(ctx, uri)
            val (w, h) = dims(retriever, maxSide)
            for (time in timesMs) {
                if (cancelled()) return emptyList<Bitmap>().also {
                    frames.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
                }
                val micros = Math.multiplyExact(time.coerceAtLeast(0L), 1000L)
                val bitmap = retriever.getScaledFrameAtTime(micros, OPTION_CLOSEST, w, h)
                    ?: return emptyList<Bitmap>().also {
                        frames.forEach { b -> if (!b.isRecycled) b.recycle() }
                    }
                frames.add(bitmap)
            }
            return frames
        } catch (_: Exception) {
            frames.forEach { if (!it.isRecycled) it.recycle() }
            return emptyList()
        } finally {
            retriever.release()
        }
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

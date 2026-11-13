package com.lightninglab

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/** Small synchronized LRU for decoded PCM peak envelopes. */
internal class WaveformCache(private val capacity: Int = 12) {
    private val values = LinkedHashMap<String, List<Float>>(16, 0.75f, true)
    @Synchronized fun get(key: String): List<Float>? = values[key]
    @Synchronized fun put(key: String, samples: List<Float>) {
        values[key] = samples.toList()
        while (values.size > capacity.coerceAtLeast(1)) values.remove(values.keys.first())
    }
    @Synchronized fun clear() = values.clear()
}

/**
 * Pure, JVM-testable bucket math for waveform binning.
 *
 * When [expectedFrames] is known from container metadata, frames are mapped
 * proportionally over the whole envelope. When it is unknown (<= 0), a live
 * monotonic estimate based on frames decoded so far is used instead, so peaks
 * spread across the envelope rather than collapsing into the last bucket.
 */
internal object WaveformBins {
    fun index(frame: Long, totalFrames: Long, expectedFrames: Long, bins: Int): Int {
        val safeBins = bins.coerceAtLeast(1)
        val divisor = if (expectedFrames > 0) expectedFrames else max(1L, totalFrames)
        val scaled = frame.toDouble() * safeBins.toDouble() / divisor.toDouble()
        return scaled.toInt().coerceIn(0, safeBins - 1)
    }

    /**
     * Sanity helper for regression tests: verify that a frame sequence covering
     * [frames] total frames spreads over at least [minSpread] distinct buckets.
     */
    fun spread(frames: Long, expectedFrames: Long, bins: Int): Int {
        if (frames <= 0L || bins <= 0) return 0
        val seen = HashSet<Int>()
        for (f in 0L until frames) seen.add(index(f, frames, expectedFrames, bins))
        return seen.size
    }
}

/**
 * One-pass bounded PCM peak capture for streams with no reliable duration.
 * Unlike a live bin estimate, a frame gets its FINAL horizontal position only
 * when the total number of frames is known at EOF.
 */
internal class WindowedPeaks(private val maxWindows: Int = 16384, initialFramesPerWindow: Int = 512) {
    init { require(maxWindows >= 2 && initialFramesPerWindow > 0) }
    private val windows = ArrayList<Float>()
    private var framesPerWindow = initialFramesPerWindow.toLong()
    private var buffered = 0L
    private var bufferedMax = 0f

    fun add(value: Float) {
        if (value.isFinite()) bufferedMax = max(bufferedMax, abs(value))
        buffered++
        if (buffered >= framesPerWindow) {
            windows.add(bufferedMax)
            buffered = 0L
            bufferedMax = 0f
            if (windows.size >= maxWindows) {
                val merged = ArrayList<Float>((windows.size + 1) / 2)
                var i = 0
                while (i < windows.size) {
                    merged.add(if (i + 1 < windows.size) max(windows[i], windows[i + 1]) else windows[i])
                    i += 2
                }
                windows.clear()
                windows.addAll(merged)
                framesPerWindow *= 2L
            }
        }
    }

    fun finish(bins: Int): FloatArray {
        val count = bins.coerceAtLeast(1)
        val data = ArrayList<Float>(windows)
        if (buffered > 0L) data.add(bufferedMax)
        val result = FloatArray(count)
        if (data.isEmpty()) return result
        for (i in data.indices) {
            val index = (i.toLong() * count / data.size).toInt().coerceIn(0, count - 1)
            result[index] = max(result[index], data[i])
        }
        return result
    }
}

/** Decodes an audio URI on a worker thread and returns normalized peak samples for a real waveform. */
object AudioWaveform {
    private val cache = WaveformCache(12)

    fun decode(context: Context, uri: Uri, bins: Int = 420): List<Float> {
        val safeBins = bins.coerceIn(32, 2048)
        val fileStamp = if (uri.scheme == "file") try {
            java.io.File(uri.path!!).let { "|" + it.length() + "|" + it.lastModified() }
        } catch (_: Exception) { "" } else ""
        val key = uri.toString() + fileStamp + "|" + safeBins
        cache.get(key)?.let { return it }
        val decoded = decodeUncached(context, uri, safeBins)
        cache.put(key, decoded)
        return decoded
    }

    private fun decodeUncached(context: Context, uri: Uri, bins: Int): List<Float> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; format = f; break }
            }
            if (track < 0 || format == null) return emptyList()
            extractor.selectTrack(track)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            val peaks = FloatArray(bins)
            val unknownDurationPeaks = WindowedPeaks()
            val durationUs = (format.getLongOrNull(MediaFormat.KEY_DURATION) ?: 0L)
            var inputDone = false
            var outputDone = false
            var sampleRate = format.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 44100
            var channels = format.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 2
            var totalFrames = 0L
            val expectedFrames = if (durationUs > 0L) durationUs * sampleRate / 1_000_000L else 0L
            var iterations = 0
            val info = MediaCodec.BufferInfo()
            while (!outputDone && iterations++ < 200000) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outputIndex >= 0) {
                    val out = codec.getOutputBuffer(outputIndex)
                    val outFormat = codec.outputFormat
                    sampleRate = outFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: sampleRate
                    channels = outFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: channels
                    if (out != null) {
                        out.order(ByteOrder.LITTLE_ENDIAN)
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val encoding = outFormat.getIntegerOrNull(MediaFormat.KEY_PCM_ENCODING) ?: 2
                        val bytesPerSample = if (encoding == 4) 4 else if (encoding == 3) 1 else 2
                        val count = out.remaining() / bytesPerSample
                        val frames = count / channels.coerceAtLeast(1)
                        for (frame in 0 until frames) {
                            var peak = 0f
                            for (ch in 0 until channels.coerceAtLeast(1)) {
                                val index = frame * channels + ch
                                val value = when (encoding) {
                                    4 -> out.getFloat(out.position() + index * 4)
                                    3 -> ((out.get(out.position() + index).toInt() and 0xff) - 128) / 128f
                                    else -> out.getShort(out.position() + index * 2) / 32768f
                                }
                                peak = max(peak, abs(value))
                            }
                            if (expectedFrames > 0L) {
                                val bucket = WaveformBins.index(totalFrames + frame, totalFrames + frames, expectedFrames, bins)
                                if (peak > peaks[bucket]) peaks[bucket] = peak
                            } else unknownDurationPeaks.add(peak)
                        }
                        totalFrames += frames
                    }
                    outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outFormat = codec.outputFormat
                    sampleRate = outFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: sampleRate
                    channels = outFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: channels
                }
            }
            val finalPeaks = if (expectedFrames > 0L) peaks else unknownDurationPeaks.finish(bins)
            val maxPeak = finalPeaks.maxOrNull() ?: 0f
            if (maxPeak <= 0f) return finalPeaks.map { 0.02f }
            return finalPeaks.map { (it / maxPeak).coerceIn(0.025f, 1f) }
        } finally {
            try { codec?.stop() } catch (_: Exception) { }
            try { codec?.release() } catch (_: Exception) { }
            try { extractor.release() } catch (_: Exception) { }
        }
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? = try { getInteger(key) } catch (_: Exception) { null }
    private fun MediaFormat.getLongOrNull(key: String): Long? = try { getLong(key) } catch (_: Exception) { null }
}

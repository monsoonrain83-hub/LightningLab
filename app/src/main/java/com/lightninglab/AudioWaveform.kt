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
            val durationUs = (format.getLongOrNull(MediaFormat.KEY_DURATION) ?: 0L).coerceAtLeast(1L)
            var inputDone = false
            var outputDone = false
            var sampleRate = format.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 44100
            var channels = format.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 2
            var totalFrames = 0L
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
                            val bucket = ((totalFrames + frame) * bins / (durationUs * sampleRate / 1_000_000L).coerceAtLeast(1L)).toInt().coerceIn(0, bins - 1)
                            if (peak > peaks[bucket]) peaks[bucket] = peak
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
            val maxPeak = peaks.maxOrNull() ?: 0f
            if (maxPeak <= 0f) return peaks.map { 0.02f }
            return peaks.map { (it / maxPeak).coerceIn(0.025f, 1f) }
        } finally {
            try { codec?.stop() } catch (_: Exception) { }
            try { codec?.release() } catch (_: Exception) { }
            try { extractor.release() } catch (_: Exception) { }
        }
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? = try { getInteger(key) } catch (_: Exception) { null }
    private fun MediaFormat.getLongOrNull(key: String): Long? = try { getLong(key) } catch (_: Exception) { null }
}

package com.lightninglab

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure sample and envelope maths (JVM-testable). */
object Gain {
    fun scale(s: Short, g: Float): Short = (s * g).roundToInt().coerceIn(-32768, 32767).toShort()

    /** Linear fades are multiplied with the track gain; overlapping fades never exceed either envelope. */
    fun envelope(baseGain: Float, positionMs: Long, durationMs: Long, fadeInMs: Long, fadeOutMs: Long): Float {
        val time = positionMs.coerceAtLeast(0L)
        val duration = durationMs.coerceAtLeast(0L)
        val inGain = if (fadeInMs <= 0L) 1f else (time.toFloat() / fadeInMs).coerceIn(0f, 1f)
        val remaining = (duration - time).coerceAtLeast(0L)
        val outGain = if (fadeOutMs <= 0L) 1f else (remaining.toFloat() / fadeOutMs).coerceIn(0f, 1f)
        return baseGain.coerceIn(0f, 2f) * min(inGain, outGain)
    }
}

/** Constant gain on 16-bit PCM audio. */
@UnstableApi
class VolumeProcessor(private val gain: Float) : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val out = replaceOutputBuffer(inputBuffer.remaining())
        while (inputBuffer.remaining() >= 2) out.putShort(Gain.scale(inputBuffer.short, gain))
        out.flip()
    }
}

/** Time-based linear fade-in/fade-out for an independent music or voice track. */
@UnstableApi
class FadeVolumeProcessor(
    private val gain: Float,
    private val durationMs: Long,
    private val fadeInMs: Long,
    private val fadeOutMs: Long
) : BaseAudioProcessor() {
    private var sampleRate = 44_100
    private var channels = 2
    private var processedFrames = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        return inputAudioFormat
    }

    override fun onFlush() { processedFrames = 0L }

    override fun onReset() {
        processedFrames = 0L
        sampleRate = 44_100
        channels = 2
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val out = replaceOutputBuffer(inputBuffer.remaining())
        val bytesPerFrame = channels * 2
        while (inputBuffer.remaining() >= bytesPerFrame) {
            val atMs = processedFrames * 1_000L / sampleRate
            val frameGain = Gain.envelope(gain, atMs, durationMs, fadeInMs, fadeOutMs)
            repeat(channels) { out.putShort(Gain.scale(inputBuffer.short, frameGain)) }
            processedFrames++
        }
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }
}

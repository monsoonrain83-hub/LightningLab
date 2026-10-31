package com.lightninglab

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** Pure sample maths (JVM-testable). */
object Gain {
    fun scale(s: Short, g: Float): Short = (s * g).roundToInt().coerceIn(-32768, 32767).toShort()
}

/** Constant gain on 16-bit PCM audio (original code on Media3's BaseAudioProcessor). Clips hard at +/- full scale. */
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

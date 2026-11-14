package com.lightninglab

import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Optional local verifier for a candidate lightning event.
 *
 * Exactly eight RGB float32 frames (0..255), temporal chronological order.
 * The model MUST have a real negative class (index 0) and a positive lightning
 * class (index 1); the earlier seven-class Brixton model is incompatible.
 *
 * No weights are bundled or auto-downloaded. Its scores never suppress events.
 */
internal class LightningBinaryVerifier(private val file: File) {
    private var interpreter: Interpreter? = null

    @Synchronized fun load(): Boolean {
        close()
        if (!file.isFile || file.length() <= 0L) return false
        var candidate: Interpreter? = null
        return try {
            candidate = Interpreter(file, Interpreter.Options().setNumThreads(2))
            val inTensor = candidate.getInputTensor(0)
            val outTensor = candidate.getOutputTensor(0)
            val valid = candidate.inputTensorCount == 1 &&
                candidate.outputTensorCount == 1 &&
                inTensor.shape().contentEquals(intArrayOf(1, 8, 224, 224, 3)) &&
                outTensor.shape().contentEquals(intArrayOf(1, 2)) &&
                inTensor.dataType() == DataType.FLOAT32 &&
                outTensor.dataType() == DataType.FLOAT32
            if (valid) {
                interpreter = candidate
                candidate = null
                true
            } else {
                candidate.close()
                candidate = null
                false
            }
        } catch (_: Throwable) {
            try { candidate?.close() } catch (_: Throwable) {}
            interpreter = null
            false
        }
    }

    @Synchronized fun close() {
        try { interpreter?.close() } catch (_: Throwable) {}
        interpreter = null
    }

    @Synchronized fun classify(frames: List<Bitmap>): Float? {
        val model = interpreter ?: return null
        if (frames.size != 8 || frames.any { it.isRecycled || it.width <= 0 || it.height <= 0 })
            return null
        return try {
            val input = ByteBuffer.allocateDirect(8 * 224 * 224 * 3 * 4)
                .order(ByteOrder.nativeOrder())
            val pixels = IntArray(224 * 224)
            for (bitmap in frames) {
                val scaled = if (bitmap.width == 224 && bitmap.height == 224) bitmap
                    else Bitmap.createScaledBitmap(bitmap, 224, 224, true)
                try {
                    scaled.getPixels(pixels, 0, 224, 0, 0, 224, 224)
                    for (pixel in pixels) {
                        input.putFloat(((pixel shr 16) and 255).toFloat())
                        input.putFloat(((pixel shr 8) and 255).toFloat())
                        input.putFloat((pixel and 255).toFloat())
                    }
                } finally {
                    if (scaled !== bitmap) scaled.recycle()
                }
            }
            input.rewind()
            val result = Array(1) { FloatArray(2) }
            model.run(input, result)
            LightningBinaryScores.decode(result[0])
        } catch (_: Throwable) {
            null
        }
    }
}

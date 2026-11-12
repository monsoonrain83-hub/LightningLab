package com.lightninglab

import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Optional on-device temporal classifier. No weights are bundled. */
class LightningTemporalModel(private val file: File) {
    companion object {
        const val FRAMES = 8
        const val SIDE = 224
        val LABELS = listOf("towerupward", "towerdownward", "close", "far",
            "unclear", "unclearbehind", "CC")

        /** Softmax rankings are model scores; not calibrated real-world confidence. */
        fun decode(scores: FloatArray): List<Pair<String, Float>>? {
            if (scores.size != LABELS.size || scores.any { !it.isFinite() || it < 0f || it > 1f })
                return null
            val total = scores.sum()
            if (!total.isFinite() || total !in 0.90f..1.10f) return null
            return LABELS.indices.map { LABELS[it] to scores[it] }.sortedByDescending { it.second }
        }

        fun sampleTimes(startMs: Long, endMs: Long, durationMs: Long): LongArray {
            require(startMs >= 0L && endMs >= startMs && durationMs >= 0L)
            val start = (startMs - 500L).coerceAtLeast(0L)
            val end = (endMs + 500L).coerceAtMost(durationMs).coerceAtLeast(start)
            return LongArray(FRAMES) { i -> start + (end - start) * i / (FRAMES - 1) }
        }
    }

    private var interpreter: Interpreter? = null

    @Synchronized fun load(): Boolean {
        close()
        if (!file.isFile || file.length() <= 0L) return false
        var candidate: Interpreter? = null
        return try {
            candidate = Interpreter(file, Interpreter.Options().setNumThreads(2))
            val input = candidate.getInputTensor(0)
            val output = candidate.getOutputTensor(0)
            val valid = input.shape().contentEquals(intArrayOf(1, FRAMES, SIDE, SIDE, 3)) &&
                input.dataType() == DataType.FLOAT32 &&
                output.shape().contentEquals(intArrayOf(1, LABELS.size)) &&
                output.dataType() == DataType.FLOAT32
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

    @Synchronized fun classify(frames: List<Bitmap>): List<Pair<String, Float>>? {
        val model = interpreter ?: return null
        if (frames.size != FRAMES || frames.any { it.isRecycled || it.width <= 0 || it.height <= 0 })
            return null
        return try {
            val input = ByteBuffer.allocateDirect(FRAMES * SIDE * SIDE * 3 * 4)
                .order(ByteOrder.nativeOrder())
            val pixels = IntArray(SIDE * SIDE)
            for (bitmap in frames) {
                // The Brixton training pipeline uses direct RGB resizing, no padding/normalization.
                val scaled = if (bitmap.width == SIDE && bitmap.height == SIDE) bitmap
                else Bitmap.createScaledBitmap(bitmap, SIDE, SIDE, true)
                try {
                    scaled.getPixels(pixels, 0, SIDE, 0, 0, SIDE, SIDE)
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
            val result = Array(1) { FloatArray(LABELS.size) }
            model.run(input, result)
            decode(result[0])
        } catch (_: Throwable) {
            null
        }
    }
}

package com.lightninglab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Optional multi-label morphology model. It proposes visible features, never confirms them. */
class CloudFeatureClassifier(private val modelFile: File) {
    companion object {
        const val SIZE = 224
        /** Output order must exactly match tools/cloud_ai/FEATURE_LABELS. */
        val LABELS = listOf("incus", "mamma", "virga", "praecipitatio", "arcus", "tuba", "asperitas", "murus", "cauda", "fluctus", "cavum")
    }
    private var interpreter: Interpreter? = null

    @Synchronized fun load(): Boolean {
        close()
        if (!modelFile.isFile || modelFile.length() <= 0L) return false
        return try {
            val candidate = Interpreter(modelFile)
            val input = candidate.getInputTensor(0)
            val output = candidate.getOutputTensor(0)
            val validInput = input.shape().contentEquals(intArrayOf(1, SIZE, SIZE, 3)) && input.dataType() == DataType.FLOAT32
            val validOutput = output.shape().contentEquals(intArrayOf(1, LABELS.size)) && output.dataType() == DataType.FLOAT32
            if (!validInput || !validOutput) { candidate.close(); false }
            else { interpreter = candidate; true }
        } catch (_: Throwable) { interpreter = null; false }
    }

    @Synchronized fun close() { interpreter?.close(); interpreter = null }

    /** Returns independent sigmoid scores, not mutually-exclusive classes. */
    @Synchronized fun classify(bitmap: Bitmap): List<Pair<String, Float>>? {
        val model = interpreter ?: return null
        return try {
            val scale = minOf(SIZE.toFloat() / bitmap.width, SIZE.toFloat() / bitmap.height)
            val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            val padded = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(padded)
            canvas.drawColor(Color.rgb(128, 128, 128))
            canvas.drawBitmap(scaled, ((SIZE - width) / 2f), ((SIZE - height) / 2f), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            if (scaled !== bitmap) scaled.recycle()
            val pixels = IntArray(SIZE * SIZE)
            padded.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            padded.recycle()
            val input = ByteBuffer.allocateDirect(4 * SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
            pixels.forEach { p ->
                input.putFloat(((p shr 16) and 255).toFloat())
                input.putFloat(((p shr 8) and 255).toFloat())
                input.putFloat((p and 255).toFloat())
            }
            input.rewind()
            val output = Array(1) { FloatArray(LABELS.size) }
            model.run(input, output)
            val scores = output[0]
            if (scores.any { !it.isFinite() || it !in 0f..1f }) null
            else LABELS.indices.map { LABELS[it] to scores[it] }.sortedByDescending { it.second }
        } catch (_: Throwable) { null }
    }
}

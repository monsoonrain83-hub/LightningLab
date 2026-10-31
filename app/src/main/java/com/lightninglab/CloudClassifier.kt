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
import kotlin.math.exp

/**
 * Optional on-device CCSN cloud-genus classifier.
 *
 * Contract for the shipped training pipeline: NHWC float32 RGB, values 0..255,
 * with MobileNetV3 preprocessing embedded in the model; output is 11 classes
 * in [LABELS] order. Input aspect ratio is preserved with neutral letterboxing.
 * Scores are model scores, not calibrated meteorological probabilities.
 */
class CloudClassifier(private val modelFile: File) {
    companion object {
        /** Alphabetical CCSN folder order; must match tools/train_cloud_model.py. */
        val LABELS = listOf("Ac", "As", "Cb", "Cc", "Ci", "Cs", "Ct", "Cu", "Ns", "Sc", "St")
        const val SIZE = 224
    }

    private var interp: Interpreter? = null
    private var inputWidth = SIZE
    private var inputHeight = SIZE
    private var inputType = DataType.FLOAT32

    /** True only when tensor contract is compatible with this classifier. */
    @Synchronized
    fun load(): Boolean {
        close()
        if (!modelFile.isFile || modelFile.length() < 1024L) return false
        var candidate: Interpreter? = null
        return try {
            val created = Interpreter(modelFile, Interpreter.Options().apply { setNumThreads(2) })
            candidate = created
            val input = created.getInputTensor(0)
            val output = created.getOutputTensor(0)
            val shape = input.shape()
            val outShape = output.shape()
            val validInput = shape.size == 4 && shape[0] == 1 && shape[1] > 0 && shape[2] > 0 && shape[3] == 3
            val validOutput = outShape.isNotEmpty() && outShape.last() == LABELS.size
            val validTypes = input.dataType() == DataType.FLOAT32 && output.dataType() == DataType.FLOAT32
            if (!validInput || !validOutput || !validTypes) {
                created.close()
                candidate = null
                false
            } else {
                inputHeight = shape[1]
                inputWidth = shape[2]
                inputType = input.dataType()
                interp = created
                candidate = null
                true
            }
        } catch (_: Throwable) {
            try { candidate?.close() } catch (_: Throwable) { }
            interp = null
            false
        }
    }

    @Synchronized
    fun close() {
        try { interp?.close() } catch (_: Throwable) { }
        interp = null
    }

    /** Returns the three highest normalized model scores, or null on unavailable/invalid inference. */
    @Synchronized
    fun classify(bmp: Bitmap): List<Pair<String, Float>>? {
        val model = interp ?: return null
        if (bmp.width <= 0 || bmp.height <= 0) return null
        var prepared: Bitmap? = null
        return try {
            prepared = letterbox(bmp, inputWidth, inputHeight)
            val pixels = IntArray(inputWidth * inputHeight)
            prepared.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
            val bytesPerValue = if (inputType == DataType.FLOAT32) 4 else return null
            val buffer = ByteBuffer.allocateDirect(bytesPerValue * inputWidth * inputHeight * 3)
                .order(ByteOrder.nativeOrder())
            for (pixel in pixels) {
                buffer.putFloat(((pixel shr 16) and 255).toFloat())
                buffer.putFloat(((pixel shr 8) and 255).toFloat())
                buffer.putFloat((pixel and 255).toFloat())
            }
            buffer.rewind()
            val raw = Array(1) { FloatArray(LABELS.size) }
            model.run(buffer, raw)
            val values = raw[0]
            if (values.any { it.isNaN() || it.isInfinite() }) return null
            val normalized = normalizeScores(values)
            LABELS.indices.map { LABELS[it] to normalized[it] }.sortedByDescending { it.second }.take(3)
        } catch (_: Throwable) {
            null
        } finally {
            try { prepared?.recycle() } catch (_: Throwable) { }
        }
    }

    private fun letterbox(source: Bitmap, width: Int, height: Int): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.rgb(127, 127, 127))
        val scale = minOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val drawWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val drawHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(source, null, android.graphics.RectF(left, top, left + drawWidth, top + drawHeight), paint)
        return output
    }

    /** Keep valid softmax outputs; apply stable softmax if the model emits logits. */
    private fun normalizeScores(values: FloatArray): FloatArray {
        val sum = values.sum()
        if (values.all { it in 0f..1f } && sum in 0.98f..1.02f) {
            return FloatArray(values.size) { values[it].coerceIn(0f, 1f) }
        }
        val maxValue = values.maxOrNull() ?: 0f
        val exps = DoubleArray(values.size) { exp((values[it] - maxValue).toDouble()) }
        val total = exps.sum().takeIf { it > 0.0 && it.isFinite() } ?: return FloatArray(values.size)
        return FloatArray(values.size) { (exps[it] / total).toFloat() }
    }
}

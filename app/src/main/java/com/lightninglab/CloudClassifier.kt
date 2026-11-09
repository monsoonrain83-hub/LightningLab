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

/**
 * Offline TFLite cloud-genus classifier.
 *
 * Supports:
 *  - 10-class LightningLab models: aspect-preserving letterbox, RGB float 0..255.
 *  - 11-class CCSN models: direct 224x224 resize, RGB float 0..1.
 *
 * CCSN class order follows Keras flow_from_directory's alphabetical folder order:
 * Ac, As, Cb, Cc, Ci, Cs, Ct, Cu, Ns, Sc, St.
 */
class CloudClassifier(private val modelFile: File) {
    companion object {
        val LABELS = listOf("Ac", "As", "Cb", "Cc", "Ci", "Cs", "Cu", "Ns", "Sc", "St")
        val LEGACY_LABELS = listOf("Ac", "As", "Cb", "Cc", "Ci", "Cs", "Ct", "Cu", "Ns", "Sc", "St")
        const val SIZE = 224
    }

    private var interp: Interpreter? = null
    private var activeLabels: List<String> = LABELS
    private var ccsnInput = false

    @Synchronized
    fun load(): Boolean {
        close()
        if (!modelFile.exists()) return false
        return try {
            val options = Interpreter.Options().apply { setNumThreads(4) }
            val it = Interpreter(modelFile, options)
            val input = it.getInputTensor(0)
            val inputShape = input.shape()
            val inputOk = inputShape.contentEquals(intArrayOf(1, SIZE, SIZE, 3)) &&
                input.dataType() == DataType.FLOAT32
            val output = it.getOutputTensor(0)
            val outputShape = output.shape()
            val outputCount = outputShape.getOrNull(1)
            val labels = when (outputCount) {
                LABELS.size -> LABELS
                LEGACY_LABELS.size -> LEGACY_LABELS
                else -> null
            }
            if (!inputOk || outputShape.size != 2 || outputShape[0] != 1 ||
                labels == null || output.dataType() != DataType.FLOAT32) {
                it.close()
                false
            } else {
                activeLabels = labels
                ccsnInput = outputCount == LEGACY_LABELS.size
                interp = it
                true
            }
        } catch (_: Throwable) {
            interp = null
            false
        }
    }

    @Synchronized
    fun close() {
        interp?.close()
        interp = null
    }

    @Synchronized
    fun classify(bmp: Bitmap): List<Pair<String, Float>>? {
        val model = interp ?: return null
        return try {
            // The published CCSN trainer uses Keras ImageDataGenerator:
            // RGB, 224x224 stretch resize and rescale=1/255. LightningLab's
            // 10-class pipeline instead uses aspect-preserving letterbox and 0..255.
            val prepared = if (ccsnInput) {
                Bitmap.createScaledBitmap(bmp, SIZE, SIZE, true)
            } else {
                val scale = minOf(SIZE.toFloat() / bmp.width, SIZE.toFloat() / bmp.height)
                val dw = (bmp.width * scale).toInt().coerceAtLeast(1)
                val dh = (bmp.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bmp, dw, dh, true)
                val canvasBitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
                Canvas(canvasBitmap).apply {
                    drawColor(Color.rgb(128, 128, 128))
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                    drawBitmap(scaled, ((SIZE - dw) / 2).toFloat(), ((SIZE - dh) / 2).toFloat(), paint)
                }
                if (scaled !== bmp) scaled.recycle()
                canvasBitmap
            }

            val pixels = IntArray(SIZE * SIZE)
            prepared.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            if (prepared !== bmp) prepared.recycle()

            val buffer = ByteBuffer.allocateDirect(4 * SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
            val divisor = if (ccsnInput) 255f else 1f
            for (pixel in pixels) {
                buffer.putFloat(((pixel shr 16) and 255) / divisor)
                buffer.putFloat(((pixel shr 8) and 255) / divisor)
                buffer.putFloat((pixel and 255) / divisor)
            }
            buffer.rewind()

            val output = Array(1) { FloatArray(activeLabels.size) }
            model.run(buffer, output)
            val scores = output[0]
            if (scores.any { !it.isFinite() || it < 0f }) return null
            val sum = scores.sum()
            if (!sum.isFinite() || sum <= 0f) return null
            activeLabels.indices
                .map { activeLabels[it] to (scores[it] / sum) }
                .sortedByDescending { it.second }
        } catch (_: Throwable) {
            null
        }
    }
}

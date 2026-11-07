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
 * Optional offline cloud-vs-background segmentation model.
 * Contract: float32 RGB input [1,256,256,3] in 0..255; float32 sigmoid mask [1,256,256,1] in 0..1.
 * The model is deliberately not bundled: a trained, contract-compatible model must be imported.
 */
data class CloudSegmentationResult(
    val overlay: Bitmap,
    val cloudPixelPercent: Int,
    val meanCloudScore: Float,
    val threshold: Float
)

class CloudSegmentationModel(private val modelFile: File) {
    companion object {
        const val SIZE = 256
        const val DEFAULT_THRESHOLD = 0.5f
    }

    private var interpreter: Interpreter? = null

    @Synchronized fun load(): Boolean {
        close()
        if (!modelFile.isFile || modelFile.length() <= 0L) return false
        return try {
            val candidate = Interpreter(modelFile)
            val input = candidate.getInputTensor(0)
            val output = candidate.getOutputTensor(0)
            val inputOk = input.shape().contentEquals(intArrayOf(1, SIZE, SIZE, 3)) && input.dataType() == DataType.FLOAT32
            val outputOk = output.shape().contentEquals(intArrayOf(1, SIZE, SIZE, 1)) && output.dataType() == DataType.FLOAT32
            if (!inputOk || !outputOk) { candidate.close(); false }
            else { interpreter = candidate; true }
        } catch (_: Throwable) { interpreter = null; false }
    }

    @Synchronized fun close() { interpreter?.close(); interpreter = null }

    @Synchronized fun segment(source: Bitmap, threshold: Float = DEFAULT_THRESHOLD): CloudSegmentationResult? {
        val model = interpreter ?: return null
        if (source.width <= 0 || source.height <= 0) return null
        return try {
            val scale = minOf(SIZE.toFloat() / source.width, SIZE.toFloat() / source.height)
            val resizedWidth = (source.width * scale).toInt().coerceIn(1, SIZE)
            val resizedHeight = (source.height * scale).toInt().coerceIn(1, SIZE)
            val left = (SIZE - resizedWidth) / 2
            val top = (SIZE - resizedHeight) / 2
            val resized = Bitmap.createScaledBitmap(source, resizedWidth, resizedHeight, true)
            val inputBitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(inputBitmap)
                canvas.drawColor(Color.rgb(128, 128, 128))
                canvas.drawBitmap(resized, left.toFloat(), top.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            } finally {
                if (resized !== source) resized.recycle()
            }

            val pixels = IntArray(SIZE * SIZE)
            inputBitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            inputBitmap.recycle()
            val input = ByteBuffer.allocateDirect(4 * SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
            for (pixel in pixels) {
                input.putFloat(((pixel shr 16) and 255).toFloat())
                input.putFloat(((pixel shr 8) and 255).toFloat())
                input.putFloat((pixel and 255).toFloat())
            }
            input.rewind()
            val output = Array(1) { Array(SIZE) { Array(SIZE) { FloatArray(1) } } }
            model.run(input, output)

            var sum = 0.0
            var count = 0
            var cloudCount = 0
            for (y in 0 until source.height) {
                val modelY = (top + (y + 0.5f) * resizedHeight / source.height).toInt().coerceIn(top, top + resizedHeight - 1)
                for (x in 0 until source.width) {
                    val modelX = (left + (x + 0.5f) * resizedWidth / source.width).toInt().coerceIn(left, left + resizedWidth - 1)
                    val score = output[0][modelY][modelX][0]
                    if (!score.isFinite() || score !in 0f..1f) return null
                    if (score >= threshold) cloudCount++
                    sum += score
                    count++
                }
            }

            val overlay = source.copy(Bitmap.Config.ARGB_8888, true)
            val srcPixels = IntArray(source.width * source.height)
            source.getPixels(srcPixels, 0, source.width, 0, 0, source.width, source.height)
            for (y in 0 until source.height) {
                val modelY = (top + (y + 0.5f) * resizedHeight / source.height).toInt().coerceIn(top, top + resizedHeight - 1)
                for (x in 0 until source.width) {
                    val modelX = (left + (x + 0.5f) * resizedWidth / source.width).toInt().coerceIn(left, left + resizedWidth - 1)
                    if (output[0][modelY][modelX][0] >= threshold) {
                        val idx = y * source.width + x
                        val base = srcPixels[idx]
                        val red = (((base shr 16) and 255) * 0.55f + 255f * 0.45f).toInt().coerceIn(0, 255)
                        val green = (((base shr 8) and 255) * 0.55f).toInt().coerceIn(0, 255)
                        val blue = ((base and 255) * 0.55f).toInt().coerceIn(0, 255)
                        srcPixels[idx] = Color.argb(255, red, green, blue)
                    }
                }
            }
            overlay.setPixels(srcPixels, 0, source.width, 0, 0, source.width, source.height)
            CloudSegmentationResult(overlay, (cloudCount * 100f / count).toInt(), (sum / count).toFloat(), threshold)
        } catch (_: Throwable) { null }
    }
}

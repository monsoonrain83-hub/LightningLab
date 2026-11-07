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

/** Seven-class offline scene segmentation. Output channels must follow CLASS_NAMES exactly. */
data class AtmosphericClassStat(val id: Int, val code: String, val labelFa: String, val percent: Int, val colorArgb: Int)
data class AtmosphericSegmentationResult(
    val overlay: Bitmap,
    val stats: List<AtmosphericClassStat>,
    val pixelCount: Int,
    val disclaimerFa: String
)

class AtmosphericSegmentationModel(private val modelFile: File) {
    companion object {
        const val SIZE = 256
        const val CLASS_COUNT = 7
        val CLASS_NAMES = listOf("clear_sky", "cloud", "fog", "dust_haze", "smoke", "snow_ice", "other")
        private val LABELS_FA = listOf("آسمان صاف", "ابر", "مه", "غبار و گردوخاک", "دود", "برف و یخ", "سایر پس‌زمینه‌ها")
        private val COLORS = intArrayOf(
            Color.rgb(40, 145, 255), Color.rgb(255, 70, 70), Color.rgb(190, 190, 205),
            Color.rgb(224, 165, 65), Color.rgb(125, 125, 135), Color.rgb(90, 220, 235), Color.rgb(90, 190, 125)
        )
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
            val outputOk = output.shape().contentEquals(intArrayOf(1, SIZE, SIZE, CLASS_COUNT)) && output.dataType() == DataType.FLOAT32
            if (!inputOk || !outputOk) { candidate.close(); false }
            else { interpreter = candidate; true }
        } catch (_: Throwable) { interpreter = null; false }
    }

    @Synchronized fun close() { interpreter?.close(); interpreter = null }

    @Synchronized fun segment(source: Bitmap, overlayAlpha: Int = 112): AtmosphericSegmentationResult? {
        val model = interpreter ?: return null
        if (source.width <= 0 || source.height <= 0) return null
        var inputBitmap: Bitmap? = null
        var resized: Bitmap? = null
        return try {
            val scale = minOf(SIZE.toFloat() / source.width, SIZE.toFloat() / source.height)
            val rw = (source.width * scale).toInt().coerceIn(1, SIZE)
            val rh = (source.height * scale).toInt().coerceIn(1, SIZE)
            val left = (SIZE - rw) / 2
            val top = (SIZE - rh) / 2
            resized = Bitmap.createScaledBitmap(source, rw, rh, true)
            inputBitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(inputBitmap!!)
            canvas.drawColor(Color.rgb(127, 127, 127))
            canvas.drawBitmap(resized!!, left.toFloat(), top.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            val pixels = IntArray(SIZE * SIZE)
            inputBitmap!!.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            val input = ByteBuffer.allocateDirect(4 * SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
            for (p in pixels) {
                input.putFloat(((p shr 16) and 255).toFloat())
                input.putFloat(((p shr 8) and 255).toFloat())
                input.putFloat((p and 255).toFloat())
            }
            input.rewind()
            val output = Array(1) { Array(SIZE) { Array(SIZE) { FloatArray(CLASS_COUNT) } } }
            model.run(input, output)

            val counts = LongArray(CLASS_COUNT)
            val srcPixels = IntArray(source.width * source.height)
            source.getPixels(srcPixels, 0, source.width, 0, 0, source.width, source.height)
            val tinted = IntArray(srcPixels.size)
            for (y in 0 until source.height) {
                val my = (top + (y + 0.5f) * rh / source.height).toInt().coerceIn(top, top + rh - 1)
                for (x in 0 until source.width) {
                    val mx = (left + (x + 0.5f) * rw / source.width).toInt().coerceIn(left, left + rw - 1)
                    val scores = output[0][my][mx]
                    var best = 0
                    var bestScore = Float.NEGATIVE_INFINITY
                    var total = 0f
                    for (c in 0 until CLASS_COUNT) {
                        val score = scores[c]
                        if (!score.isFinite() || score !in 0f..1f) return null
                        total += score
                        if (score > bestScore) { bestScore = score; best = c }
                    }
                    if (!total.isFinite() || total !in 0.90f..1.10f) return null
                    counts[best]++
                    val index = y * source.width + x
                    val base = srcPixels[index]
                    val color = COLORS[best]
                    val a = overlayAlpha.coerceIn(0, 255) / 255f
                    val r = (((base shr 16) and 255) * (1f - a) + Color.red(color) * a).toInt()
                    val g = (((base shr 8) and 255) * (1f - a) + Color.green(color) * a).toInt()
                    val b = ((base and 255) * (1f - a) + Color.blue(color) * a).toInt()
                    tinted[index] = Color.rgb(r, g, b)
                }
            }
            val overlay = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            overlay.setPixels(tinted, 0, source.width, 0, 0, source.width, source.height)
            val totalPixels = source.width.toLong() * source.height.toLong()
            val stats = CLASS_NAMES.indices.map { id ->
                AtmosphericClassStat(id, CLASS_NAMES[id], LABELS_FA[id], (counts[id] * 100.0 / totalPixels).toInt(), COLORS[id])
            }.sortedByDescending { it.percent }
            AtmosphericSegmentationResult(
                overlay, stats, totalPixels.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                "رنگ‌ها خروجی آزمایشی مدل‌اند؛ امتیازها و درصدها اندازه‌گیری رسمی هواشناسی نیستند. تفکیک مه، غبار و دود به تنوع و کیفیت داده‌های آموزش وابسته است."
            )
        } catch (_: Throwable) { null }
        finally {
            inputBitmap?.recycle()
            if (resized != null && resized !== source && !resized.isRecycled) resized.recycle()
        }
    }
}

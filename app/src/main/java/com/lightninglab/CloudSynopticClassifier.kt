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

data class CloudSynopticPredictions(
    val cl: List<Pair<String, Float>>,
    val cm: List<Pair<String, Float>>,
    val ch: List<Pair<String, Float>>
)

/** Optional three-head WMO synoptic classifier: one 11-way output each for CL, CM and CH. */
class CloudSynopticClassifier(private val modelFile: File) {
    companion object {
        const val SIZE = 224
        const val HEADS = 3
        const val CLASSES = 11
        val CODES = (0..9).map { it.toString() } + "/"

        internal fun decodeOutput(values: FloatArray): CloudSynopticPredictions? {
            if (values.size != HEADS * CLASSES || values.any { !it.isFinite() || it !in 0f..1f }) return null
            val ranked = ArrayList<List<Pair<String, Float>>>(HEADS)
            for (head in 0 until HEADS) {
                val scores = values.copyOfRange(head * CLASSES, (head + 1) * CLASSES)
                val total = scores.sum()
                if (!total.isFinite() || total !in 0.90f..1.10f) return null
                ranked.add(CODES.indices.map { CODES[it] to scores[it] }.sortedByDescending { it.second })
            }
            return CloudSynopticPredictions(ranked[0], ranked[1], ranked[2])
        }
    }

    private var interpreter: Interpreter? = null

    @Synchronized fun load(): Boolean {
        close()
        if (!modelFile.isFile || modelFile.length() <= 0L) return false
        var candidate: Interpreter? = null
        return try {
            val model = Interpreter(modelFile, Interpreter.Options().setNumThreads(2))
            candidate = model
            val input = model.getInputTensor(0)
            val output = model.getOutputTensor(0)
            val valid = input.shape().contentEquals(intArrayOf(1, SIZE, SIZE, 3)) &&
                input.dataType() == DataType.FLOAT32 &&
                output.shape().contentEquals(intArrayOf(1, HEADS, CLASSES)) &&
                output.dataType() == DataType.FLOAT32
            if (!valid) {
                model.close(); candidate = null; false
            } else {
                interpreter = model; candidate = null; true
            }
        } catch (_: Throwable) {
            try { candidate?.close() } catch (_: Throwable) { }
            interpreter = null
            false
        }
    }

    @Synchronized fun close() {
        try { interpreter?.close() } catch (_: Throwable) { }
        interpreter = null
    }

    @Synchronized fun classify(source: Bitmap): CloudSynopticPredictions? {
        val model = interpreter ?: return null
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
        var scaled: Bitmap? = null
        var padded: Bitmap? = null
        return try {
            val scale = minOf(SIZE.toFloat() / source.width, SIZE.toFloat() / source.height)
            val width = (source.width * scale).toInt().coerceIn(1, SIZE)
            val height = (source.height * scale).toInt().coerceIn(1, SIZE)
            scaled = Bitmap.createScaledBitmap(source, width, height, true)
            padded = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            Canvas(padded!!).apply {
                drawColor(Color.rgb(128, 128, 128))
                drawBitmap(scaled!!, (SIZE - width) / 2f, (SIZE - height) / 2f,
                    Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            }
            val pixels = IntArray(SIZE * SIZE)
            padded!!.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            val input = ByteBuffer.allocateDirect(4 * SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
            for (pixel in pixels) {
                input.putFloat(((pixel shr 16) and 255).toFloat())
                input.putFloat(((pixel shr 8) and 255).toFloat())
                input.putFloat((pixel and 255).toFloat())
            }
            input.rewind()
            val output = Array(1) { Array(HEADS) { FloatArray(CLASSES) } }
            model.run(input, output)
            decodeOutput(FloatArray(HEADS * CLASSES) { i -> output[0][i / CLASSES][i % CLASSES] })
        } catch (_: Throwable) {
            null
        } finally {
            if (scaled != null && scaled !== source && !scaled!!.isRecycled) scaled!!.recycle()
            if (padded != null && !padded!!.isRecycled) padded!!.recycle()
        }
    }
}

object CloudSynopticInfo {
    private val labels = mapOf(
        "CL" to listOf("بدون ابر کم‌ارتفاع", "کومه‌ای کم‌ارتفاع/پاره", "کومه‌ای متوسط یا پررشد", "کومولونیمبوس بدون سندان",
            "استراتوکومولوس حاصل از گسترش کومه‌ای", "استراتوکومولوس", "استراتوس پیوسته", "استراتوس/کومه‌ای پاره در هوای بد",
            "کومه‌ای همراه استراتوکومولوس", "کومولونیمبوس با سندان", "قابل تعیین نیست"),
        "CM" to listOf("بدون ابر میان‌ارتفاع", "آلتواستراتوس نیمه‌شفاف", "آلتواستراتوس کدر یا نیمبواستراتوس",
            "آلتوکومولوس نیمه‌شفاف", "آلتوکومولوس عدسی", "باندهای آلتوکومولوس", "آلتوکومولوس حاصل از گسترش کومه‌ای",
            "آلتوکومولوس چندلایه یا همراه آلتواستراتوس", "آلتوکومولوس کاستلاناتوس", "آلتوکومولوس آشفته", "قابل تعیین نیست"),
        "CH" to listOf("بدون ابر پرارتفاع", "سیروس فیبراتوس", "سیروس متراکم", "سیروس متراکم با منشأ سندان",
            "سیروس اونچینوس/فیبراتوس", "باندهای سیروس/سیروستراتوس تا ۴۵ درجه", "باندهای سیروس/سیروستراتوس بالاتر از ۴۵ درجه",
            "سیروستراتوس تمام‌آسمان", "سیروستراتوس در حال گسترش", "سیروکومولوس", "قابل تعیین نیست")
    )
    fun label(level: String, code: String): String {
        val index = if (code == "/") 10 else code.toIntOrNull() ?: return "نامشخص"
        return labels[level]?.getOrNull(index) ?: "نامشخص"
    }
}

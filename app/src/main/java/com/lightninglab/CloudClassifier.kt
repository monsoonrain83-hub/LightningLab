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
 * Optional on-device cloud-genus classifier (TensorFlow Lite, Apache-2.0 runtime).
 * The app ships WITHOUT a model: you train one with tools/train_cloud_model.py (CCAiM dataset, prepared with tools/cloud_ai/prepare_ccaim.py) and import the
 * resulting cloud_model.tflite from the Weather tab. If no valid model is loaded, callers use the rule-based
 * [CloudAnalysis] result instead. Supports the 10-class in-app contract and the specifically identified CCSN 11-class
 * export; each has its own resize/normalization contract.
 */
class CloudClassifier(private val modelFile: File) {
    companion object {
        /** Ten WMO cloud genera in the exact order emitted by tools/train_cloud_model.py. */
        val LABELS = listOf("Ac", "As", "Cb", "Cc", "Ci", "Cs", "Cu", "Ns", "Sc", "St")
        /** Backward compatibility for CCSN's 11 classes, including contrails. */
        val LEGACY_LABELS = listOf("Ac", "As", "Cb", "Cc", "Ci", "Cs", "Ct", "Cu", "Ns", "Sc", "St")
        const val SIZE = 224

        /** The legacy CCSN export uses external RGB /255 preprocessing and a stretched resize. */
        internal fun labelsForOutputCount(count: Int): List<String>? = when (count) {
            LABELS.size -> LABELS
            LEGACY_LABELS.size -> LEGACY_LABELS
            else -> null
        }
        internal fun usesCcsnUnitInput(count: Int, inputTensorName: String): Boolean =
            count == LEGACY_LABELS.size && inputTensorName.contains("input_layer_6", ignoreCase = true)
    }

    private var interp: Interpreter? = null
    private var activeLabels: List<String> = LABELS
    private var activeCcsnInput: Boolean = false

    /** True when a supported ten-genus or legacy eleven-class model was loaded. */
    @Synchronized
    fun load(): Boolean {
        close()
        if (!modelFile.exists()) return false
        var candidate: Interpreter? = null
        return try {
            val it = Interpreter(modelFile)
            candidate = it
            val input = it.getInputTensor(0)
            val inputShape = input.shape()
            val inputOk = inputShape.contentEquals(intArrayOf(1, SIZE, SIZE, 3)) && input.dataType() == DataType.FLOAT32
            val output = it.getOutputTensor(0)
            val outputShape = output.shape()
            // Accept only [1,10] or [1,11] float softmax outputs; reject ambiguous tensor ranks.
            val outputCount = outputShape.getOrNull(1)
            val labels = labelsForOutputCount(outputCount ?: -1)
            if (!inputOk || outputShape.size != 2 || outputShape[0] != 1 || labels == null || output.dataType() != DataType.FLOAT32) {
                it.close()
                candidate = null
                false
            } else {
                activeLabels = labels
                activeCcsnInput = usesCcsnUnitInput(outputCount ?: -1, input.name())
                interp = it
                candidate = null
                true
            }
        } catch (e: Throwable) {
            try { candidate?.close() } catch (_: Throwable) { }
            interp = null
            false
        }
    }

    @Synchronized
    fun close() { interp?.close(); interp = null; activeLabels = LABELS; activeCcsnInput = false }

    /** Returns (genus code, probability) sorted descending, or null if no model is loaded or inference fails. */
    @Synchronized
    fun classify(bmp: Bitmap): List<Pair<String, Float>>? {
        val m = interp ?: return null
        if (bmp.width <= 0 || bmp.height <= 0) return null
        var scaled: Bitmap? = null
        var padded: Bitmap? = null
        return try {
            // CCSN's published trainer uses cv2.resize to a square and RGB /255.
            // The newer in-app trainer uses aspect-preserving grey padding and raw 0..255;
            // keep both preprocessing contracts explicit and tied to the detected output shape.
            if (activeCcsnInput) {
                padded = Bitmap.createScaledBitmap(bmp, SIZE, SIZE, true)
            } else {
                val scale = minOf(SIZE.toFloat() / bmp.width, SIZE.toFloat() / bmp.height)
                val dw = (bmp.width * scale).toInt().coerceIn(1, SIZE)
                val dh = (bmp.height * scale).toInt().coerceIn(1, SIZE)
                scaled = Bitmap.createScaledBitmap(bmp, dw, dh, true)
                padded = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(padded!!)
                canvas.drawColor(Color.rgb(128, 128, 128))
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(scaled!!, ((SIZE - dw) / 2f), ((SIZE - dh) / 2f), paint)
            }

            val px = IntArray(SIZE * SIZE)
            padded!!.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
            val buf = ByteBuffer.allocateDirect(4 * SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
            for (p in px) {
                val red = ((p shr 16) and 255).toFloat()
                val green = ((p shr 8) and 255).toFloat()
                val blue = (p and 255).toFloat()
                if (activeCcsnInput) {
                    buf.putFloat(red / 255f); buf.putFloat(green / 255f); buf.putFloat(blue / 255f)
                } else {
                    buf.putFloat(red); buf.putFloat(green); buf.putFloat(blue)
                }
            }
            buf.rewind()
            val out = Array(1) { FloatArray(activeLabels.size) }
            m.run(buf, out)
            val raw = out[0]
            // The exported model contract is a softmax probability vector. Do not
            // normalize arbitrary positive logits or malformed tensors into plausible
            // looking percentages: that would conceal an incompatible model export.
            if (raw.any { !it.isFinite() || it !in 0f..1f }) return null
            val total = raw.sum()
            if (!total.isFinite() || total !in 0.90f..1.10f) return null
            activeLabels.indices.map { activeLabels[it] to raw[it] }.sortedByDescending { it.second }
        } catch (_: Throwable) {
            null
        } finally {
            // The source bitmap belongs to the caller; only temporary images are recycled.
            if (scaled != null && scaled !== bmp && !scaled!!.isRecycled) scaled!!.recycle()
            if (padded != null && padded !== scaled && padded !== bmp && !padded!!.isRecycled) padded!!.recycle()
        }
    }
}

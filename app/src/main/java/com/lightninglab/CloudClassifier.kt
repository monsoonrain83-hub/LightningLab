package com.lightninglab

import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Optional on-device cloud-genus classifier (TensorFlow Lite, Apache-2.0 runtime).
 * The app ships WITHOUT a model: you train one with tools/train_cloud_model.py (CCSN dataset, CC0) and import the
 * resulting cloud_model.tflite from the Weather tab. If no valid model is loaded, callers use the rule-based
 * [CloudAnalysis] result instead. Contract: input 224x224x3 float32 RGB in 0..255 (preprocessing is INSIDE the model),
 * output 11 softmax scores in the order of [LABELS].
 */
class CloudClassifier(private val modelFile: File) {
    companion object {
        /** Alphabetical CCSN folder order; the training script asserts the same order. */
        val LABELS = listOf("Ac", "As", "Cb", "Cc", "Ci", "Cs", "Ct", "Cu", "Ns", "Sc", "St")
        const val SIZE = 224
    }

    private var interp: Interpreter? = null

    /** True when a valid model with 11 outputs was loaded. */
    fun load(): Boolean {
        close()
        if (!modelFile.exists()) return false
        return try {
            val it = Interpreter(modelFile)
            val shape = it.getOutputTensor(0).shape()
            if (shape.isEmpty() || shape.last() != LABELS.size) { it.close(); false } else { interp = it; true }
        } catch (e: Throwable) { interp = null; false }
    }

    fun close() { interp?.close(); interp = null }

    /** Returns (label, probability) sorted descending, or null if no model is loaded or inference fails. */
    fun classify(bmp: Bitmap): List<Pair<String, Float>>? {
        val m = interp ?: return null
        return try {
            val s = Bitmap.createScaledBitmap(bmp, SIZE, SIZE, true)
            val px = IntArray(SIZE * SIZE); s.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
            val buf = ByteBuffer.allocateDirect(4 * SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
            for (p in px) {
                buf.putFloat(((p shr 16) and 255).toFloat()); buf.putFloat(((p shr 8) and 255).toFloat()); buf.putFloat((p and 255).toFloat())
            }
            buf.rewind()
            val out = Array(1) { FloatArray(LABELS.size) }
            m.run(buf, out)
            LABELS.indices.map { LABELS[it] to out[0][it] }.sortedByDescending { it.second }
        } catch (e: Throwable) { null }
    }
}

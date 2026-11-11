package com.lightninglab

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Device-tolerant H.264 export presets. Height is a maximum; never upscale the source. */
enum class VideoExportProfile(val labelFa: String, val targetHeight: Int?) {
    SOURCE("کیفیت منبع", null),
    FULL_HD("1080p", 1080),
    HD("720p", 720),
    SD("480p", 480);

    fun effectiveHeight(sourceWidth: Int, sourceHeight: Int): Int {
        val h = sourceHeight.coerceAtLeast(2)
        return min(targetHeight ?: h, h)
    }

    fun outputSize(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
        val w = sourceWidth.coerceAtLeast(2)
        val h = sourceHeight.coerceAtLeast(2)
        val targetH = effectiveHeight(w, h)
        val scale = targetH.toFloat() / h
        fun even(value: Float) = (value.roundToInt().coerceAtLeast(2) / 2) * 2
        return even(w * scale) to even(h * scale)
    }

    /** Approximate H.264 target in bits/sec; hardware encoders may choose a supported nearby value. */
    fun targetBitrate(sourceWidth: Int, sourceHeight: Int): Int {
        val (w, h) = outputSize(sourceWidth, sourceHeight)
        return (w.toLong() * h.toLong() * 3.86f)
            .toLong().coerceIn(1_500_000L, 40_000_000L).toInt()
    }

    companion object {
        fun fromId(id: String?): VideoExportProfile = values().firstOrNull { it.name == id } ?: SOURCE
    }
}

package com.lightninglab

import kotlin.math.ceil

/** Conservative planning estimate, NOT a promise about hardware-encoder output bytes. */
object VideoExportBudget {
    const val AUDIO_BITRATE_BPS = 192_000L

    fun durationMs(segments: List<Seg>, sourceDurationMs: Long, speed: Float): Long {
        val ranges = if (segments.isEmpty()) listOf(Seg(0L, sourceDurationMs)) else segments
        val sum = ranges.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        val divisor = speed.takeIf { it.isFinite() && it > 0f } ?: 1f
        return (sum / divisor.toDouble()).toLong().coerceAtLeast(0L)
    }

    fun estimatedBytes(durationMs: Long, videoBitrateBps: Long, audioBitrateBps: Long = AUDIO_BITRATE_BPS): Long {
        if (durationMs <= 0L) return 0L
        val bitsPerSecond = videoBitrateBps.coerceAtLeast(0L) + audioBitrateBps.coerceAtLeast(0L)
        val bytes = durationMs.toDouble() * bitsPerSecond.toDouble() / 8000.0
        return ceil(bytes * 1.10).toLong().coerceAtLeast(0L) // 10% container/encoder margin
    }

    /** Separate scratch and finished output can coexist until MediaStore copy completes. */
    fun minimumFreeBytes(estimatedOutputBytes: Long): Long =
        maxOf(150L * 1024L * 1024L, estimatedOutputBytes.coerceAtLeast(0L) * 2L)
}

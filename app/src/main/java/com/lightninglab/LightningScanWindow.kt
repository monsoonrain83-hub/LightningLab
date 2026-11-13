package com.lightninglab

import kotlin.math.max
import kotlin.math.min

/** Saturating millisecond-to-microsecond conversion for MediaExtractor timestamps. */
internal object LightningScanWindow {
    fun microseconds(milliseconds: Long): Long =
        when {
            milliseconds <= 0L -> 0L
            milliseconds > Long.MAX_VALUE / 1000L -> Long.MAX_VALUE
            else -> milliseconds * 1000L
        }

    data class Range(val startUs: Long, val endUs: Long, val knownEnd: Boolean) {
        val spanUs: Long get() = max(1L, endUs - startUs)
        fun progress(timestampUs: Long): Float {
            if (!knownEnd) return 0f
            val passed = if (timestampUs <= startUs) 0L else timestampUs - startUs
            return (passed.toDouble() / spanUs.toDouble()).toFloat().coerceIn(0f, 1f)
        }
    }

    /** Returns null for empty ranges, never an overflowing or reversed window. */
    fun of(durationUs: Long, startMs: Long, endMs: Long): Range? {
        if (startMs < 0L || endMs < 0L || (endMs != Long.MAX_VALUE && endMs <= startMs)) return null
        val start = microseconds(startMs)
        if (start == Long.MAX_VALUE) return null
        val knownDuration = durationUs > 0L
        val explicitEnd = endMs != Long.MAX_VALUE
        val end = when {
            knownDuration && explicitEnd -> min(durationUs, microseconds(endMs))
            knownDuration -> durationUs
            explicitEnd -> microseconds(endMs)
            else -> Long.MAX_VALUE
        }
        if (end <= start) return null
        return Range(start, end, knownDuration || explicitEnd)
    }
}

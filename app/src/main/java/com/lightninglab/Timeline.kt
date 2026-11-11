package com.lightninglab

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Zoom marker used by timeline navigation and future animated crop controls. */
data class ZoomKey(val id: Int, val timeMs: Long, val scale: Float)

/** Pure timeline math shared by editing UI and unit tests. All public times are milliseconds. */
object TimelineMath {
    const val MIN_CLIP_MS = 200L

    fun zoomAt(keys: List<ZoomKey>, tMs: Long): Float {
        if (keys.isEmpty()) return 1f
        val sorted = keys.sortedBy { it.timeMs }
        if (tMs <= sorted.first().timeMs) return sorted.first().scale
        if (tMs >= sorted.last().timeMs) return sorted.last().scale
        for (i in 1 until sorted.size) if (tMs <= sorted[i].timeMs) {
            val a = sorted[i - 1]; val b = sorted[i]
            val span = max(1L, b.timeMs - a.timeMs)
            val f = ((tMs - a.timeMs).toFloat() / span).coerceIn(0f, 1f)
            val smooth = f * f * (3f - 2f * f)
            return a.scale + (b.scale - a.scale) * smooth
        }
        return sorted.last().scale
    }

    /** Converts a normalized ruler coordinate into a safe output-timeline timestamp. */
    fun timeAtFraction(fraction: Float, durationMs: Long): Long {
        val duration = durationMs.coerceAtLeast(0L)
        return (fraction.coerceIn(0f, 1f) * duration.toDouble()).toLong().coerceIn(0L, duration)
    }

    /** Snap to the closest edit boundary only when it is within the touch-friendly tolerance. */
    fun snapTime(value: Long, anchors: List<Long>, toleranceMs: Long = 90L): Long {
        if (anchors.isEmpty() || toleranceMs < 0L) return value
        val nearest = anchors.minByOrNull { abs(it - value) } ?: return value
        return if (abs(nearest - value) <= toleranceMs) nearest else value
    }

    /** Clamps [start,end) into the timeline while preserving a usable minimum duration. */
    fun clampRange(start: Long, end: Long, total: Long): LongArray {
        val t = max(total, 1L)
        val len = (end - start).coerceAtLeast(min(MIN_CLIP_MS, t)).coerceAtMost(t)
        val s = start.coerceIn(0L, max(0L, t - len))
        return longArrayOf(s, min(t, s + len))
    }

    /** Output timeline position -> (segment index, source offset in that segment). */
    fun locate(segMs: List<Long>, speed: Float, outMs: Long): Pair<Int, Long> {
        if (segMs.isEmpty()) return 0 to 0L
        var remaining = (outMs.coerceAtLeast(0L) * speed.coerceAtLeast(0.01f)).toLong()
        for (i in segMs.indices) {
            val length = segMs[i].coerceAtLeast(0L)
            if (remaining < length || i == segMs.lastIndex) return i to remaining.coerceIn(0L, length)
            remaining -= length
        }
        return segMs.lastIndex to segMs.last().coerceAtLeast(0L)
    }

    /** Inverse mapping of [locate]. */
    fun outPosition(segMs: List<Long>, speed: Float, index: Int, offsetMs: Long): Long {
        val safeIndex = index.coerceIn(0, segMs.size)
        val source = segMs.take(safeIndex).sumOf { it.coerceAtLeast(0L) } + offsetMs.coerceAtLeast(0L)
        return (source / speed.coerceAtLeast(0.01f)).toLong()
    }

    /** Trim an existing source clip while preserving a minimum usable duration. */
    fun trimSegmentRange(requestedStart: Long, requestedEnd: Long, sourceDuration: Long, minimumMs: Long = MIN_CLIP_MS): LongArray? {
        val minimum = minimumMs.coerceAtLeast(1L)
        if (sourceDuration < minimum) return null
        val start = requestedStart.coerceIn(0L, sourceDuration - minimum)
        val end = requestedEnd.coerceIn(start + minimum, sourceDuration)
        return longArrayOf(start, end)
    }

    /** Map an output time after a ripple deletion; times inside the removed gap collapse to its start. */
    fun rippleShift(timeMs: Long, cutStartMs: Long, cutDurationMs: Long): Long {
        if (cutDurationMs <= 0L) return timeMs
        val cutEnd = cutStartMs + cutDurationMs
        return when {
            timeMs <= cutStartMs -> timeMs
            timeMs >= cutEnd -> (timeMs - cutDurationMs).coerceAtLeast(cutStartMs)
            else -> cutStartMs
        }
    }

    /** Return the destination index for a valid clip reorder, or null when no move is possible. */
    fun reorderIndex(size: Int, from: Int, to: Int): Int? =
        if (size > 0 && from in 0 until size && to in 0 until size && from != to) to else null

    /** Move a contiguous selection one slot left/right without changing its internal order. */
    fun <T> reorderContiguousGroup(items: List<T>, selectedIndices: Set<Int>, direction: Int): List<T>? {
        if (direction != -1 && direction != 1) return null
        if (selectedIndices.isEmpty() || selectedIndices.any { it !in items.indices }) return null
        val indices = selectedIndices.sorted()
        val first = indices.first()
        val last = indices.last()
        if (last - first + 1 != indices.size) return null
        val result = items.toMutableList()
        return when (direction) {
            -1 -> {
                if (first == 0) return null
                val previous = result.removeAt(first - 1)
                result.add(last, previous)
                result
            }
            else -> {
                if (last == items.lastIndex) return null
                val next = result.removeAt(last + 1)
                result.add(first, next)
                result
            }
        }
    }

    fun split(start: Long, end: Long, at: Long): Pair<LongArray, LongArray>? {
        if (at - start < MIN_CLIP_MS || end - at < MIN_CLIP_MS) return null
        return longArrayOf(start, at) to longArrayOf(at, end)
    }
}

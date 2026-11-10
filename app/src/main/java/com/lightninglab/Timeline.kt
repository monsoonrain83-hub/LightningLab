package com.lightninglab

import kotlin.math.max
import kotlin.math.min

/**
 * Timeline model of the video editor. ALL times are milliseconds on the OUTPUT timeline (after trimming/joining and
 * speed change), i.e. exactly the time axis the user sees and the exported file will have. Pure Kotlin (unit-testable).
 */
data class TextClip(
    val id: Int, val text: String, val color: Int, val size: Int, val pos: Int, val bg: Boolean,
    val startMs: Long, val endMs: Long, val font: String? = null
)
data class StickerClip(val id: Int, val emoji: String, val size: Int, val pos: Int, val startMs: Long, val endMs: Long)
/** kind: 0 = music/imported audio, 1 = built-in sound effect, 2 = voice recording. `uri` is a string so the model stays pure. */
data class AudioClip(val id: Int, val uri: String, val label: String, val kind: Int, val startMs: Long, val durMs: Long, val vol: Float = 1f)
/** Zoom marker: the picture is scaled to `scale` (1 = normal) at `timeMs`; between markers the scale is interpolated. */
data class ZoomKey(val id: Int, val timeMs: Long, val scale: Float)

object TimelineMath {
    const val MIN_CLIP_MS = 200L

    /** Zoom factor at time t: linear (smoothstep) between markers, held before the first and after the last. 1 without markers. */
    fun zoomAt(keys: List<ZoomKey>, tMs: Long): Float {
        if (keys.isEmpty()) return 1f
        val k = keys.sortedBy { it.timeMs }
        if (tMs <= k.first().timeMs) return k.first().scale
        if (tMs >= k.last().timeMs) return k.last().scale
        for (i in 1 until k.size) {
            if (tMs <= k[i].timeMs) {
                val a = k[i - 1]; val b = k[i]
                val span = max(1L, b.timeMs - a.timeMs)
                val f = (tMs - a.timeMs).toFloat() / span
                val s = f * f * (3f - 2f * f)
                return a.scale + (b.scale - a.scale) * s
            }
        }
        return k.last().scale
    }

    /** Clamps [start, end) into [0, total] keeping at least MIN_CLIP_MS (or the whole total when it is shorter). */
    fun clampRange(start: Long, end: Long, total: Long): LongArray {
        val t = max(total, 1L)
        val len = (end - start).coerceAtLeast(min(MIN_CLIP_MS, t))
        val s = start.coerceIn(0L, max(0L, t - min(len, t)))
        return longArrayOf(s, min(t, s + min(len, t)))
    }

    /** Output-timeline position -> (segment index, offset inside that segment in SOURCE ms). `segMs` = source durations. */
    fun locate(segMs: List<Long>, speed: Float, outMs: Long): Pair<Int, Long> {
        if (segMs.isEmpty()) return 0 to 0L
        var rest = (outMs * speed).toLong().coerceAtLeast(0L)
        for (i in segMs.indices) {
            if (rest < segMs[i] || i == segMs.size - 1) return i to rest.coerceIn(0L, segMs[i])
            rest -= segMs[i]
        }
        return segMs.size - 1 to segMs.last()
    }

    /** Inverse of [locate]. */
    fun outPosition(segMs: List<Long>, speed: Float, index: Int, offsetMs: Long): Long {
        var sum = 0L
        for (i in 0 until min(index, segMs.size)) sum += segMs[i]
        return ((sum + offsetMs) / speed).toLong()
    }

    /** Splits segment [start,end) at source offset `at` (strictly inside, leaving >= MIN_CLIP_MS each side); null if impossible. */
    fun split(start: Long, end: Long, at: Long): Pair<LongArray, LongArray>? {
        if (at - start < MIN_CLIP_MS || end - at < MIN_CLIP_MS) return null
        return longArrayOf(start, at) to longArrayOf(at, end)
    }
}

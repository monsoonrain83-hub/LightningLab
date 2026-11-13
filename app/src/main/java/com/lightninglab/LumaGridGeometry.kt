package com.lightninglab

/**
 * Evenly partitions every source axis, including rightmost/bottom pixels,
 * without dropping remainder columns when dimensions aren't divisible.
 */
internal object LumaGridGeometry {
    data class Span(val start: Int, val endExclusive: Int) {
        val width: Int get() = endExclusive - start
    }

    fun span(index: Int, buckets: Int, pixels: Int): Span {
        require(pixels > 0 && buckets in 1..pixels && index in 0 until buckets)
        val start = (index.toLong() * pixels / buckets).toInt()
        val end = ((index.toLong() + 1L) * pixels / buckets).toInt()
        return Span(start, end)
    }

    data class Dims(val width: Int, val height: Int)

    /** Bound BOTH source axes, preserving the approximate aspect ratio. */
    fun targetDims(sourceWidth: Int, sourceHeight: Int, maxSide: Int): Dims {
        require(sourceWidth > 0 && sourceHeight > 0 && maxSide > 0)
        val longest = maxOf(sourceWidth, sourceHeight).toLong()
        val tw = (sourceWidth.toLong() * maxSide / longest)
            .coerceIn(1L, sourceWidth.toLong()).toInt()
        val th = (sourceHeight.toLong() * maxSide / longest)
            .coerceIn(1L, sourceHeight.toLong()).toInt()
        return Dims(tw, th)
    }

    fun targetHeight(sourceWidth: Int, sourceHeight: Int, targetWidth: Int): Int {
        require(sourceWidth > 0 && sourceHeight > 0 && targetWidth in 1..sourceWidth)
        return ((targetWidth.toLong() * sourceHeight / sourceWidth).coerceAtLeast(1L)
            .coerceAtMost(sourceHeight.toLong())).toInt()
    }
}

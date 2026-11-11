package com.lightninglab

/** Pure geometry helpers for the non-destructive before/after photo comparison wipe. */
object PhotoCompareMath {
    fun normalize(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0.02f, 0.98f) else 0.5f

    fun splitX(widthPx: Float, position: Float): Float =
        (widthPx.coerceAtLeast(0f) * normalize(position)).coerceIn(0f, widthPx.coerceAtLeast(0f))
}

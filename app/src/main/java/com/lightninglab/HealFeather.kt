package com.lightninglab

/**
 * Pure boundary-feathering math for object removal (JVM-testable, no Bitmap).
 *
 * Inpaint.fill clears the unknown mask while filling, so the caller must keep a
 * copy of the original mask and pass it here; otherwise feathering silently
 * becomes dead code and the repair shows a hard circular seam.
 */
internal object HealFeather {
    /** Smoothstep weight for a pixel at [distance] from the mark centre inside radius [r] with [feather] width. */
    fun weight(distance: Float, r: Float, feather: Float): Float {
        val f = feather.coerceAtLeast(1f)
        val a = ((r - distance) / f).coerceIn(0f, 1f)
        return a * a * (3f - 2f * a)
    }

    /**
     * Blend the [original] and [repaired] colours with weight [a] (0..1).
     * a = 1 keeps the repair, a = 0 restores the original untouched pixel.
     */
    fun blend(original: Int, repaired: Int, a: Float): Int {
        if (a >= 0.999f) return repaired
        if (a <= 0.001f) return original
        val aa = (a * 255f).toInt()
        val inv = 255 - aa
        val rr = ((((repaired shr 16) and 255) * aa + ((original shr 16) and 255) * inv) / 255)
        val gg = ((((repaired shr 8) and 255) * aa + ((original shr 8) and 255) * inv) / 255)
        val bb = (((repaired and 255) * aa + (original and 255) * inv) / 255)
        return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    /**
     * In-place feathering over pixels that were unknown BEFORE the fill.
     * [px] holds the repaired pixels, [original] the pre-repair colours,
     * [wasUnknown] the pre-fill mask copy and [distances] each pixel's distance
     * from the mark centre.
     */
    fun apply(px: IntArray, original: IntArray, wasUnknown: BooleanArray, distances: FloatArray, r: Float, feather: Float) {
        for (i in px.indices) if (wasUnknown[i]) {
            px[i] = blend(original[i], px[i], weight(distances[i], r, feather))
        }
    }
}

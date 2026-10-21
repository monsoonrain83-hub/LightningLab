package com.lightninglab

object ImgUtil {
    fun luma(p: Int): Float =
        0.299f * ((p shr 16) and 255) + 0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)

    /** Separable box blur with running sums, O(w*h). Edges are clamped. */
    fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(w * h); val out = FloatArray(w * h)
        val inv = 1f / (2 * r + 1)
        for (y in 0 until h) {
            val o = y * w; var s = 0f
            for (k in -r..r) s += src[o + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[o + x] = s * inv
                s += src[o + (x + r + 1).coerceAtMost(w - 1)] - src[o + (x - r).coerceAtLeast(0)]
            }
        }
        for (x in 0 until w) {
            var s = 0f
            for (k in -r..r) s += tmp[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = s * inv
                s += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
            }
        }
        return out
    }
}

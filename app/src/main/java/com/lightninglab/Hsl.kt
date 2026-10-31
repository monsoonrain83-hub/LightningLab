package com.lightninglab

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Per-colour HSL adjustment (original code). Seven bands: red, orange, yellow, green, aqua, blue, purple.
 * A pixel's hue is blended between neighbouring bands (triangular weights, 60 degrees wide). Grey pixels (low saturation)
 * are barely affected. hue: +-1 = +-30 degrees; sat: -1 = grey, +1 = double; lum: +-1 = +-50 % brightness of that colour.
 */
object Hsl {
    const val BANDS = 7
    val BAND_HUES = floatArrayOf(0f, 30f, 60f, 120f, 180f, 240f, 300f)

    fun adjust(c: Int, hue: FloatArray, sat: FloatArray, lum: FloatArray): Int {
        val r = ((c shr 16) and 255) / 255f; val g = ((c shr 8) and 255) / 255f; val b = (c and 255) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        if (d < 1e-4f) return c
        var h = when (mx) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        if (h < 0f) h += 360f
        val s = d / mx
        var wSum = 0f
        val w = FloatArray(BANDS)
        for (i in 0 until BANDS) {
            var dist = abs(h - BAND_HUES[i]); if (dist > 180f) dist = 360f - dist
            w[i] = max(0f, 1f - dist / 60f); wSum += w[i]
        }
        if (wSum <= 0f) return c
        val norm = if (wSum > 1f) 1f / wSum else 1f
        var dh = 0f; var ds = 0f; var dl = 0f
        for (i in 0 until BANDS) { val k = w[i] * norm; dh += k * hue[i]; ds += k * sat[i]; dl += k * lum[i] }
        if (dh == 0f && ds == 0f && dl == 0f) return c
        h = (h + dh * 30f * min(1f, s * 2f) + 360f) % 360f
        val s2 = (s * (1f + ds)).coerceIn(0f, 1f)
        val v2 = (mx * (1f + 0.5f * dl * min(1f, s * 2f))).coerceIn(0f, 1f)
        // HSV -> RGB
        val cc = v2 * s2; val hp = h / 60f; val x = cc * (1f - abs(hp % 2f - 1f)); val m = v2 - cc
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(cc, x, 0f); hp < 2f -> Triple(x, cc, 0f); hp < 3f -> Triple(0f, cc, x)
            hp < 4f -> Triple(0f, x, cc); hp < 5f -> Triple(x, 0f, cc); else -> Triple(cc, 0f, x)
        }
        val ri = ((r1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val gi = ((g1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        val bi = ((b1 + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
}

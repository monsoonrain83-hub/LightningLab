package com.lightninglab

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Fast, deterministic on-device image diagnostics.
 * This is a sky-photo quality/cover estimator, NOT a cloud-genus AI classifier.
 * It deliberately reports frame-quality warnings because ground, buildings,
 * vegetation, sunset colour and direct solar glare can bias RGB heuristics.
 */
object CloudAnalysis {
    enum class Kind { CLEAR, THIN_STREAKS, CUMULIFORM, UNIFORM_LAYER, DARK_OVERCAST, MIXED }
    enum class FrameQuality { GOOD, CHECK_FRAME, LOW_SKY_CONTENT }

    class Result(
        val cover: Float,
        val oktas: Int,
        val cloudLuma: Float,
        val texture: Float,
        val contrast: Float,
        val kind: Kind,
        val skyLikeFraction: Float = 1f,
        val frameQuality: FrameQuality = FrameQuality.GOOD,
        val qualityFlags: List<String> = emptyList()
    )

    const val NBR_CLOUD = 0.2f

    fun oktas(cover: Float): Int = when {
        cover < 0.02f -> 0
        cover > 0.98f -> 8
        else -> (cover * 8f).roundToInt().coerceIn(1, 7)
    }

    fun analyze(px: IntArray, w: Int, h: Int): Result {
        require(w > 0 && h > 0 && px.size >= w * h) { "Invalid image dimensions" }
        val n = w * h
        val luma = FloatArray(n) { ImgUtil.luma(px[it]) }
        val cloud = BooleanArray(n)
        var valid = 0
        var cloudCount = 0
        var skyLike = 0
        var warmCast = 0
        var greenOrEarth = 0

        for (i in 0 until n) {
            val c = px[i]
            val r = (c shr 16) and 255
            val g = (c shr 8) and 255
            val b = c and 255
            val hi = max(r, max(g, b))
            val lo = min(r, min(g, b))
            if (r + b < 20) continue
            valid++

            val blueSky = b > r * 1.08f && b > g * 0.98f && b > 55
            val neutralCloud = hi > 65 && (hi - lo) < 78
            if (blueSky || neutralCloud) skyLike++
            if (r > b * 1.35f && r > g * 1.12f && hi > 80) warmCast++
            if (g > r * 1.12f && g > b * 1.08f && hi > 55) greenOrEarth++

            val nbr = (b - r).toFloat() / (b + r).coerceAtLeast(1)
            // Neutral/white cloud pixels, with a brightness floor to avoid counting dark objects as cloud.
            val isCloud = (nbr < NBR_CLOUD && neutralCloud) || lo >= 225
            if (isCloud) { cloud[i] = true; cloudCount++ }
        }

        if (valid == 0) return Result(0f, 0, 0f, 0f, 0f, Kind.CLEAR,
            0f, FrameQuality.LOW_SKY_CONTENT, listOf("no_valid_pixels"))

        val cover = cloudCount.toFloat() / valid
        val skyFraction = skyLike.toFloat() / valid
        var count = 0L
        var sum = 0.0
        var sum2 = 0.0
        var gradientSum = 0.0
        for (y in 0 until h - 1) for (x in 0 until w - 1) {
            val i = y * w + x
            if (!cloud[i]) continue
            val value = luma[i].toDouble()
            sum += value
            sum2 += value * value
            count++
            gradientSum += abs(luma[i] - luma[i + 1]) + abs(luma[i] - luma[i + w])
        }
        val mean = if (count > 0) (sum / count).toFloat() else 0f
        val contrast = if (count > 0) sqrt(max(0.0, sum2 / count - (sum / count) * (sum / count))).toFloat() else 0f
        val texture = if (count > 0) (gradientSum / (2.0 * count)).toFloat() else 0f
        val kind = when {
            cover < 0.02f -> Kind.CLEAR
            cover > 0.8f && mean < 100f -> Kind.DARK_OVERCAST
            cover > 0.85f && texture < 3f -> Kind.UNIFORM_LAYER
            cover in 0.05f..0.7f && contrast > 45f && texture > 5f -> Kind.CUMULIFORM
            cover < 0.6f && mean > 150f && contrast < 35f -> Kind.THIN_STREAKS
            else -> Kind.MIXED
        }

        val flags = mutableListOf<String>()
        if (skyFraction < 0.45f) flags += "possible_non_sky_content"
        if (warmCast.toFloat() / valid > 0.28f) flags += "strong_warm_colour_cast"
        if (greenOrEarth.toFloat() / valid > 0.18f) flags += "possible_vegetation_or_ground"
        val quality = when {
            skyFraction < 0.30f || greenOrEarth.toFloat() / valid > 0.35f -> FrameQuality.LOW_SKY_CONTENT
            flags.isNotEmpty() -> FrameQuality.CHECK_FRAME
            else -> FrameQuality.GOOD
        }
        return Result(cover.coerceIn(0f, 1f), oktas(cover), mean, texture, contrast, kind,
            skyFraction.coerceIn(0f, 1f), quality, flags)
    }
}

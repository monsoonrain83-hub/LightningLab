package com.lightninglab

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Sky-photo cloud analysis (pure Kotlin, original code).
 *  - Cloud cover: classic red/blue-ratio idea. Clear sky scatters blue, cloud is grey/white, so a pixel is cloud when
 *    the normalised difference (B-R)/(B+R) is small, or when it is near-white. Cover -> oktas (0 = clear, 8 = overcast).
 *  - Texture / contrast are measured on cloud pixels only.
 *  - Type is a ROUGH HEURISTIC guess from cover + texture + brightness. It is NOT a WMO genus classifier and has
 *    NOT been validated against labelled data. The UI says so.
 * Assumes the photo is (mostly) sky; ground, sun glare and red sunset skies will bias the result.
 */
object CloudAnalysis {
    enum class Kind { CLEAR, THIN_STREAKS, CUMULIFORM, UNIFORM_LAYER, DARK_OVERCAST, MIXED }
    class Result(val cover: Float, val oktas: Int, val cloudLuma: Float, val texture: Float, val contrast: Float, val kind: Kind)

    const val NBR_CLOUD = 0.2f

    fun oktas(cover: Float): Int = when {
        cover < 0.02f -> 0
        cover > 0.98f -> 8
        else -> (cover * 8f).roundToInt().coerceIn(1, 7)
    }

    fun analyze(px: IntArray, w: Int, h: Int): Result {
        val n = w * h
        val l = FloatArray(n) { ImgUtil.luma(px[it]) }
        val cloud = BooleanArray(n)
        var valid = 0; var cl = 0
        for (i in 0 until n) {
            val c = px[i]; val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            if (r + b < 20) continue
            valid++
            val nbr = (b - r).toFloat() / (b + r)
            if (nbr < NBR_CLOUD || min(r, min(g, b)) >= 225) { cloud[i] = true; cl++ }
        }
        if (valid == 0) return Result(0f, 0, 0f, 0f, 0f, Kind.CLEAR)
        val cover = cl.toFloat() / valid
        var cnt = 0L; var sum = 0.0; var sum2 = 0.0; var gs = 0.0
        for (y in 0 until h - 1) for (x in 0 until w - 1) {
            val i = y * w + x
            if (!cloud[i]) continue
            val v = l[i].toDouble()
            sum += v; sum2 += v * v; cnt++
            gs += abs(l[i] - l[i + 1]) + abs(l[i] - l[i + w])
        }
        val mean = if (cnt > 0) (sum / cnt).toFloat() else 0f
        val contrast = if (cnt > 0) sqrt(max(0.0, sum2 / cnt - (sum / cnt) * (sum / cnt))).toFloat() else 0f
        val texture = if (cnt > 0) (gs / (2.0 * cnt)).toFloat() else 0f
        val kind = when {
            cover < 0.02f -> Kind.CLEAR
            cover > 0.8f && mean < 100f -> Kind.DARK_OVERCAST
            cover > 0.85f && texture < 3f -> Kind.UNIFORM_LAYER
            cover in 0.05f..0.7f && contrast > 45f && texture > 5f -> Kind.CUMULIFORM
            cover < 0.6f && mean > 150f && contrast < 35f -> Kind.THIN_STREAKS
            else -> Kind.MIXED
        }
        return Result(cover, oktas(cover), mean, texture, contrast, kind)
    }
}

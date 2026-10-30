package com.lightninglab

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.media3.common.Effect
import androidx.media3.effect.Contrast
import androidx.media3.effect.RgbAdjustment
import kotlin.math.abs

/** Pure helpers for the video editor tools (crop maths, colour filters, styled overlay text). */
object VideoFx {
    class Filter(val nameRes: Int, val r: Float, val g: Float, val b: Float, val contrast: Float)

    /** Colour filters = per-channel gain + contrast (real shaders/LUTs are not used). Index 0 = none. */
    val filters = listOf(
        Filter(R.string.filter_none, 1f, 1f, 1f, 0f),
        Filter(R.string.filter_warm, 1.08f, 1.0f, 0.88f, 0.05f),
        Filter(R.string.filter_cool, 0.90f, 1.0f, 1.10f, 0.05f),
        Filter(R.string.filter_storm, 0.88f, 0.97f, 1.08f, 0.18f),
        Filter(R.string.filter_vivid, 1f, 1f, 1f, 0.25f),
        Filter(R.string.filter_dusk, 1.10f, 0.94f, 0.88f, -0.08f))

    fun filterEffects(i: Int): List<Effect> {
        if (i <= 0) return emptyList()
        val f = filters.getOrNull(i) ?: return emptyList()
        return listOf(RgbAdjustment.Builder().setRedScale(f.r).setGreenScale(f.g).setBlueScale(f.b).build(), Contrast(f.contrast))
    }

    /** Centered crop to `aspect` (= width/height) in NDC: [left, right, bottom, top]; null when no crop is needed. */
    fun cropBounds(srcW: Int, srcH: Int, aspect: Float): FloatArray? {
        if (srcW <= 0 || srcH <= 0 || aspect <= 0f) return null
        val s = srcW.toFloat() / srcH
        if (abs(aspect - s) < 0.01f) return null
        return if (aspect < s) { val hw = aspect / s; floatArrayOf(-hw, hw, -1f, 1f) }
        else { val hh = s / aspect; floatArrayOf(-1f, 1f, -hh, hh) }
    }

    /**
     * Styled overlay text. The overlay is centred by the library, so top/bottom placement is done by padding the text
     * with blank lines (pos: 0 = top, 1 = centre, 2 = bottom). base scales the whole thing (stickers use > 1).
     * Verify placement on a device: it is not unit-testable.
     */
    fun styled(text: String, color: Int, sizeIdx: Int, pos: Int, bg: Boolean, base: Float = 1f): SpannableString {
        val rel = base * when (sizeIdx) { 0 -> 0.6f; 2 -> 1.6f; else -> 1f }
        val pad = "\n".repeat(5)
        val prefix = if (pos == 2) pad else ""
        val suffix = if (pos == 0) pad else ""
        val sp = SpannableString(prefix + text + suffix)
        val a = prefix.length; val b = a + text.length
        sp.setSpan(ForegroundColorSpan(color), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(RelativeSizeSpan(rel), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(StyleSpan(Typeface.BOLD), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (bg) sp.setSpan(BackgroundColorSpan(0x99000000.toInt()), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sp
    }
}

package com.lightninglab

/**
 * Lightweight, fully offline RGB quality control for sky photo/video evidence.
 * These are observable pixel statistics, NOT proof of sun glare, foreground,
 * a specific cloud genus, or a validated meteorological measurement.
 */
object SkyFrameQuality {
    enum class Flag(val labelFa: String) {
        CLIPPED_HIGHLIGHTS("نواحی بیش‌ازحد روشن یا اشباع"),
        VERY_DARK("نواحی بسیار تاریک"),
        WARM_RED_BIAS("غلبهٔ رنگ گرم/قرمز؛ احتمال سوگیری روش RGB"),
        TOO_FEW_PIXELS("نمونهٔ پیکسلی ناکافی")
    }

    data class Result(
        val clippedPercent: Int,
        val darkPercent: Int,
        val warmRedPercent: Int,
        val validSamplePixels: Int,
        val flags: List<Flag>
    ) {
        val noteFa: String
            get() = if (flags.isEmpty()) "هشدار کیفیت RGB در آستانه‌های ساده دیده نشد؛ اعتبارسنجی علمی همچنان لازم است."
            else flags.joinToString("؛ ") { it.labelFa } +
                "؛ این هشدارها فقط از رنگ پیکسل‌ها به دست آمده‌اند و عامل فیزیکی را تعیین نمی‌کنند."
    }

    fun assess(pixels: IntArray, width: Int, height: Int): Result {
        require(width > 0 && height > 0 && width.toLong() * height.toLong() == pixels.size.toLong())
        val stride = (pixels.size / 65_536).coerceAtLeast(1)
        var total = 0
        var clipped = 0
        var dark = 0
        var warm = 0
        for (i in pixels.indices step stride) {
            val argb = pixels[i]
            if (((argb ushr 24) and 255) < 128) continue
            val red = (argb ushr 16) and 255
            val green = (argb ushr 8) and 255
            val blue = argb and 255
            total++
            if (minOf(red, green, blue) >= 248) clipped++
            if (maxOf(red, green, blue) <= 20) dark++
            if (red - blue >= 45 && red - green >= 20) warm++
        }
        fun percent(value: Int): Int = if (total == 0) 0 else ((value.toLong() * 100L + total / 2) / total).toInt().coerceIn(0, 100)
        val clipPct = percent(clipped)
        val darkPct = percent(dark)
        val warmPct = percent(warm)
        val flags = buildList {
            if (clipPct >= 10) add(Flag.CLIPPED_HIGHLIGHTS)
            if (darkPct >= 30) add(Flag.VERY_DARK)
            if (warmPct >= 30) add(Flag.WARM_RED_BIAS)
            if (total < 128) add(Flag.TOO_FEW_PIXELS)
        }
        return Result(clipPct, darkPct, warmPct, total, flags)
    }
}

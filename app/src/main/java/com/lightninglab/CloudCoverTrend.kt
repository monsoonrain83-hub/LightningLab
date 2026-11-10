package com.lightninglab

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Describes change in image-derived cloud-cover estimates across sampled video frames.
 * This is a visual proxy only: camera motion, exposure and non-sky pixels can change it.
 */
data class CloudCoverTrend(
    val firstMeanPercent: Int,
    val lastMeanPercent: Int,
    val changePercentagePoints: Int,
    val minimumPercent: Int,
    val maximumPercent: Int,
    val direction: Direction,
    val labelPersian: String,
    val notePersian: String
) {
    enum class Direction { INCREASING, DECREASING, STABLE, INSUFFICIENT }
}

object CloudCoverTrendAnalyzer {
    fun analyze(frameResults: List<CloudVideoFrameResult>): CloudCoverTrend {
        val values = frameResults.sortedBy { it.elapsedMillis }.map { it.coverPercent.coerceIn(0, 100) }
        if (values.size < 3) {
            val mean = if (values.isEmpty()) 0 else values.average().roundToInt()
            return CloudCoverTrend(mean, mean, 0, values.minOrNull() ?: 0, values.maxOrNull() ?: 0,
                CloudCoverTrend.Direction.INSUFFICIENT, "دادهٔ کافی نیست",
                "برای برآورد روند پوشش، دست‌کم سه فریم قابل تحلیل لازم است.")
        }
        val third = (values.size / 3).coerceAtLeast(1)
        val first = values.take(third).average().roundToInt()
        val last = values.takeLast(third).average().roundToInt()
        val delta = last - first
        val direction = when {
            delta >= 8 -> CloudCoverTrend.Direction.INCREASING
            delta <= -8 -> CloudCoverTrend.Direction.DECREASING
            else -> CloudCoverTrend.Direction.STABLE
        }
        val label = when (direction) {
            CloudCoverTrend.Direction.INCREASING -> "افزایش پوشش ابریِ برآوردشده"
            CloudCoverTrend.Direction.DECREASING -> "کاهش پوشش ابریِ برآوردشده"
            CloudCoverTrend.Direction.STABLE -> "پوشش ابری تقریباً پایدار"
            CloudCoverTrend.Direction.INSUFFICIENT -> "دادهٔ کافی نیست"
        }
        val note = when (direction) {
            CloudCoverTrend.Direction.INCREASING -> "میانگین بخش پایانی ویدئو از بخش آغازین بیشتر است؛ این تغییر به‌تنهایی به معنی افزایش واقعی ابرناکی منطقه نیست."
            CloudCoverTrend.Direction.DECREASING -> "میانگین بخش پایانی ویدئو از بخش آغازین کمتر است؛ تغییر زاویه یا نور دوربین نیز می‌تواند مؤثر باشد."
            else -> "اختلاف میانگین ابتدا و انتهای ویدئو کمتر از آستانهٔ ۸ واحد درصد است؛ تغییرات کوتاه‌مدت ممکن است همچنان وجود داشته باشد."
        }
        return CloudCoverTrend(first, last, delta, values.minOrNull() ?: 0, values.maxOrNull() ?: 0,
            direction, label, note)
    }
}

package com.lightninglab

/**
 * Aggregates optional multi-label structural-feature model outputs over sampled video frames.
 * Scores are raw sigmoid outputs, not calibrated probabilities or meteorological confirmation.
 */
data class CloudFeatureTemporalCandidate(
    val code: String,
    val framesDetected: Int,
    val frameSharePercent: Int,
    val meanRawScore: Float,
    val peakRawScore: Float,
    val firstThirdMean: Float?,
    val lastThirdMean: Float?,
    val trendPersian: String
)

data class CloudFeatureTemporalReport(
    val modelOutputFrames: Int,
    val sampledFrames: Int,
    val candidates: List<CloudFeatureTemporalCandidate>,
    val notePersian: String
) {
    val modelWasAvailable: Boolean get() = modelOutputFrames > 0
}

object CloudFeatureTemporalAnalyzer {
    private const val DETECTION_THRESHOLD = 0.50f

    private val names = mapOf(
        "incus" to "سندان", "mamma" to "مامّا", "virga" to "ویرگا",
        "praecipitatio" to "بارش قابل مشاهده", "arcus" to "آرکوس / قوس",
        "tuba" to "قیف ابری", "asperitas" to "آسپریتاس",
        "murus" to "دیواره ابری", "cauda" to "دنباله ابری",
        "fluctus" to "فلاکتوس / موج شکسته", "cavum" to "کاووم / حفره"
    )

    fun displayName(code: String): String = names[code] ?: code

    fun analyze(frames: List<CloudVideoFrameResult>): CloudFeatureTemporalReport {
        val validFrames = frames.filter { it.structuralFeatures != null }.sortedBy { it.elapsedMillis }
        if (validFrames.isEmpty()) return CloudFeatureTemporalReport(
            modelOutputFrames = 0,
            sampledFrames = frames.size,
            candidates = emptyList(),
            notePersian = "مدل ویژگی‌های ساختاری در دسترس نیست یا برای فریم‌های نمونه خروجی معتبر نداده است. برای فعال‌سازی، فایل cloud_features.tflite سازگار را از بخش مدل‌ها وارد کنید."
        )

        val allCodes = validFrames.flatMap { it.structuralFeatures.orEmpty().map { item -> item.first } }.distinct()
        val firstCount = (validFrames.size / 3).coerceAtLeast(1)
        val lastStart = (validFrames.size - firstCount).coerceAtLeast(0)
        val candidates = allCodes.mapNotNull { code ->
            val perFrame = validFrames.mapNotNull { frame ->
                frame.structuralFeatures.orEmpty().firstOrNull { it.first == code }?.second
                    ?.takeIf { it.isFinite() && it in 0f..1f }
            }
            val detections = validFrames.count { frame ->
                frame.structuralFeatures.orEmpty().any { it.first == code && it.second.isFinite() && it.second >= DETECTION_THRESHOLD }
            }
            if (detections == 0 || perFrame.isEmpty()) return@mapNotNull null
            val first = validFrames.take(firstCount).mapNotNull { frame -> frame.structuralFeatures.orEmpty().firstOrNull { it.first == code }?.second?.takeIf { it.isFinite() } }
            val last = validFrames.drop(lastStart).mapNotNull { frame -> frame.structuralFeatures.orEmpty().firstOrNull { it.first == code }?.second?.takeIf { it.isFinite() } }
            val firstMean = first.takeIf { it.isNotEmpty() }?.average()?.toFloat()
            val lastMean = last.takeIf { it.isNotEmpty() }?.average()?.toFloat()
            val delta = if (firstMean != null && lastMean != null) lastMean - firstMean else 0f
            val trend = when {
                firstMean == null || lastMean == null -> "روند نامشخص"
                delta >= 0.12f -> "افزایش امتیاز در بخش پایانی"
                delta <= -0.12f -> "کاهش امتیاز در بخش پایانی"
                else -> "تقریباً پایدار"
            }
            CloudFeatureTemporalCandidate(
                code = code,
                framesDetected = detections,
                frameSharePercent = (detections * 100f / validFrames.size).toInt(),
                meanRawScore = perFrame.average().toFloat(),
                peakRawScore = perFrame.maxOrNull() ?: 0f,
                firstThirdMean = firstMean,
                lastThirdMean = lastMean,
                trendPersian = trend
            )
        }.sortedWith(compareByDescending<CloudFeatureTemporalCandidate> { it.framesDetected }.thenByDescending { it.meanRawScore })

        return CloudFeatureTemporalReport(
            modelOutputFrames = validFrames.size,
            sampledFrames = frames.size,
            candidates = candidates.take(8),
            notePersian = "ویژگی‌ها فقط نامزدهای دیداری مدل چندبرچسبی‌اند. امتیازها احتمال کالیبره‌شده نیستند؛ زاویه، فاصله، نور و کیفیت تصویر می‌توانند نتیجه را تغییر دهند. تکرار در فریم‌ها پایداری خروجی مدل را نشان می‌دهد، نه تأیید قطعی پدیده در جو."
        )
    }
}

package com.lightninglab

import android.graphics.Bitmap

/**
 * Multi-region candidate analysis for a single sky photograph.
 * Regions are image-relative bands, NOT meteorological cloud layers or measured altitudes.
 * Scores are raw model ranking scores and are not calibrated probabilities.
 */
data class CloudRegionResult(
    val regionCode: String,
    val regionLabelPersian: String,
    val genusCandidates: List<Pair<String, Float>>,
    val featureCandidates: List<Pair<String, Float>>
)

data class CloudMultiScaleReport(
    val regions: List<CloudRegionResult>,
    val disclaimerPersian: String = "بالا، میانه و پایین فقط ناحیه‌های مکانی تصویر هستند؛ این تقسیم‌بندی معادل تراز ارتفاعی جو نیست. امتیازها نامزدهای مدل‌اند، نه احتمال کالیبره‌شده یا تشخیص قطعی."
)

class CloudMultiScaleAnalyzer(
    private val genusClassifier: CloudClassifier,
    private val featureClassifier: CloudFeatureClassifier
) {
    fun analyze(bitmap: Bitmap): CloudMultiScaleReport {
        if (bitmap.width < 2 || bitmap.height < 3) return CloudMultiScaleReport(emptyList())
        val bands = listOf(
            Triple("upper", "بخش بالایی تصویر", 0f to 0.42f),
            Triple("middle", "بخش میانی تصویر", 0.29f to 0.71f),
            Triple("lower", "بخش پایینی تصویر", 0.58f to 1f)
        )
        val results = bands.map { (code, label, range) ->
            val top = (bitmap.height * range.first).toInt().coerceIn(0, bitmap.height - 1)
            val bottom = (bitmap.height * range.second).toInt().coerceIn(top + 1, bitmap.height)
            val crop = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, bottom - top)
            try {
                CloudRegionResult(
                    code, label,
                    genusClassifier.classify(crop)?.take(3) ?: emptyList(),
                    featureClassifier.classify(crop)?.filter { it.second >= 0.50f }?.take(5) ?: emptyList()
                )
            } finally { crop.recycle() }
        }
        return CloudMultiScaleReport(results)
    }
}

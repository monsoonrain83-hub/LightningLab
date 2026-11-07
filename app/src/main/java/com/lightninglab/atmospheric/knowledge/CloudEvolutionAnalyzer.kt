package com.lightninglab.atmospheric.knowledge

/**
 * Time-series assessment for observations extracted from successive video frames.
 * It does not inspect pixels and does not claim that model scores are calibrated.
 * The caller must provide frame observations in elapsed-time order (or this class
 * sorts them). Outputs describe visible change, not storm severity or forecasting.
 */
enum class CloudGrowthStage { UNKNOWN, SHALLOW, MODERATE, STRONG }

data class CloudFrameObservation(
    val elapsedMillis: Long,
    val genusCode: String? = null,
    val genusLabelPersian: String? = null,
    val genusScore: Float? = null,
    val growthStage: CloudGrowthStage = CloudGrowthStage.UNKNOWN,
    val distinctAnvil: Boolean = false,
    val fibrousOrIcyTop: Boolean = false,
    val precipitationStreaks: Boolean = false,
    val precipitationReachesGround: Boolean = false,
    val raggedFragmentsUnderCloud: Boolean = false
)

enum class CloudEvolutionTrend { INCREASING, DECREASING, MOSTLY_STABLE, MIXED_OR_UNCERTAIN }

data class CloudEvolutionEvent(
    val elapsedMillis: Long,
    val titlePersian: String,
    val detailPersian: String
)

data class CloudEvolutionReport(
    val trend: CloudEvolutionTrend,
    val trendLabelPersian: String,
    val durationMillis: Long,
    val frameCount: Int,
    val events: List<CloudEvolutionEvent>,
    val genusSequence: List<String>,
    val cautionsPersian: List<String>
)

class CloudEvolutionAnalyzer {
    fun analyze(input: List<CloudFrameObservation>): CloudEvolutionReport {
        require(input.size >= 2) { "At least two frame observations are required" }
        val frames = input.sortedBy { it.elapsedMillis }
        require(frames.first().elapsedMillis >= 0L) { "Elapsed time cannot be negative" }
        require(frames.zipWithNext().all { it.first.elapsedMillis < it.second.elapsedMillis }) {
            "Frame timestamps must be unique"
        }
        val knownStages = frames.map { it.growthStage }.filter { it != CloudGrowthStage.UNKNOWN }
        val firstStage = knownStages.firstOrNull()
        val lastStage = knownStages.lastOrNull()
        val trend = when {
            knownStages.size < 2 -> CloudEvolutionTrend.MIXED_OR_UNCERTAIN
            firstStage == CloudGrowthStage.SHALLOW && lastStage in setOf(CloudGrowthStage.MODERATE, CloudGrowthStage.STRONG) -> CloudEvolutionTrend.INCREASING
            firstStage == CloudGrowthStage.MODERATE && lastStage == CloudGrowthStage.STRONG -> CloudEvolutionTrend.INCREASING
            firstStage == CloudGrowthStage.STRONG && lastStage in setOf(CloudGrowthStage.MODERATE, CloudGrowthStage.SHALLOW) -> CloudEvolutionTrend.DECREASING
            firstStage == CloudGrowthStage.MODERATE && lastStage == CloudGrowthStage.SHALLOW -> CloudEvolutionTrend.DECREASING
            knownStages.distinct().size == 1 -> CloudEvolutionTrend.MOSTLY_STABLE
            else -> CloudEvolutionTrend.MIXED_OR_UNCERTAIN
        }
        val events = mutableListOf<CloudEvolutionEvent>()
        frames.forEachIndexed { index, frame ->
            val previous = frames.getOrNull(index - 1)
            if (frame.distinctAnvil && previous?.distinctAnvil != true) {
                events += CloudEvolutionEvent(frame.elapsedMillis, "ظهور ساختار سندانی", "گسترش افقی بالای ابر در این فریم قابل مشاهده شده است.")
            }
            if (frame.fibrousOrIcyTop && previous?.fibrousOrIcyTop != true) {
                events += CloudEvolutionEvent(frame.elapsedMillis, "آشکارشدن بخش رشته‌ای یا یخی", "بخش بالایی ابر ظاهر رشته‌ای یا یخی پیدا کرده است؛ این نشانه به‌تنهایی مرحله دقیق یخ‌زایی را تعیین نمی‌کند.")
            }
            if (frame.precipitationStreaks && previous?.precipitationStreaks != true) {
                events += CloudEvolutionEvent(frame.elapsedMillis, "ظهور رگه‌های بارش", "رگه‌های بارش در تصویر قابل مشاهده شده‌اند.")
            }
            if (frame.precipitationReachesGround && previous?.precipitationReachesGround != true) {
                events += CloudEvolutionEvent(frame.elapsedMillis, "بارش تا سطح زمین", "در تصویر شواهدی از رسیدن بارش به سطح دیده می‌شود؛ مقدار و شدت بارش از این ویدئو به‌تنهایی قابل محاسبه نیست.")
            }
            if (frame.raggedFragmentsUnderCloud && previous?.raggedFragmentsUnderCloud != true) {
                events += CloudEvolutionEvent(frame.elapsedMillis, "ظهور قطعات پاره‌پاره زیر ابر", "قطعات ابری کم‌ارتفاع زیر ابر اصلی آشکار شده‌اند؛ برای نام‌گذاری pannus بررسی دقیق‌تر لازم است.")
            }
            val oldGenus = previous?.genusCode?.uppercase()
            val newGenus = frame.genusCode?.uppercase()
            if (!oldGenus.isNullOrBlank() && !newGenus.isNullOrBlank() && oldGenus != newGenus) {
                events += CloudEvolutionEvent(frame.elapsedMillis, "تغییر نامزد جنس ابر", "نامزد مدل از ${previous.genusLabelPersian ?: oldGenus} به ${frame.genusLabelPersian ?: newGenus} تغییر کرده است؛ این تغییر می‌تواند ناشی از زاویه دید، پوشیدگی یا خطای مدل نیز باشد.")
            }
        }
        val label = when (trend) {
            CloudEvolutionTrend.INCREASING -> "نشانه‌های رشد قائم افزایشی"
            CloudEvolutionTrend.DECREASING -> "نشانه‌های کاهش رشد قائم"
            CloudEvolutionTrend.MOSTLY_STABLE -> "رشد قائم تقریباً پایدار"
            CloudEvolutionTrend.MIXED_OR_UNCERTAIN -> "روند نامشخص یا متغیر"
        }
        val genusSequence = frames.mapNotNull { it.genusLabelPersian ?: it.genusCode?.uppercase() }.distinct()
        return CloudEvolutionReport(
            trend = trend,
            trendLabelPersian = label,
            durationMillis = frames.last().elapsedMillis - frames.first().elapsedMillis,
            frameCount = frames.size,
            events = events,
            genusSequence = genusSequence,
            cautionsPersian = listOf(
                "این تحلیل بر پایه مشاهدات ثبت‌شده از فریم‌هاست و به‌تنهایی تحلیل مستقیم پیکسل‌های ویدئو نیست.",
                "امتیاز مدل در این گزارش احتمال کالیبره‌شده محسوب نمی‌شود.",
                "روند ظاهری رشد ابر، شدت طوفان، وقوع تگرگ، گردباد یا زمان و مکان بارش را اثبات نمی‌کند.",
                "برای مقایسه زمانی معتبر، دوربین تا حد امکان ثابت باشد و فریم‌ها با فاصله زمانی مناسب انتخاب شوند."
            )
        )
    }
}

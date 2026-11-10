package com.lightninglab

import kotlin.math.ceil

/**
 * Temporal vote summary for frame-level cloud-genus model candidates.
 * Scores are raw model ranking values, not calibrated probabilities.
 */
data class CloudTemporalCandidate(
    val genusCode: String,
    val frameCount: Int,
    val frameSharePercent: Int,
    val meanRawScore: Float,
    val peakRawScore: Float
)

data class CloudTemporalConsensus(
    val analyzedFrames: Int,
    val framesWithModelOutput: Int,
    val candidates: List<CloudTemporalCandidate>,
    val stabilityPercent: Int,
    val notePersian: String = "این جمع‌بندی از رأی فریم‌های نمونه‌برداری‌شده ساخته شده است؛ امتیاز مدل احتمال کالیبره‌شده نیست و روند زمانی الزاماً بیانگر تحول واقعی ابر در جو نیست."
)

object CloudTemporalConsensusAnalyzer {
    fun analyze(frameResults: List<CloudVideoFrameResult>): CloudTemporalConsensus {
        val valid = frameResults.filter { it.genusCode != null && it.score != null && it.score.isFinite() && it.score >= 0f }
        if (valid.isEmpty()) {
            return CloudTemporalConsensus(
                analyzedFrames = frameResults.size,
                framesWithModelOutput = 0,
                candidates = emptyList(),
                stabilityPercent = 0
            )
        }

        val grouped = valid.groupBy { it.genusCode!! }
        val candidates = grouped.map { (code, frames) ->
            val scores = frames.mapNotNull { it.score }
            CloudTemporalCandidate(
                genusCode = code,
                frameCount = frames.size,
                frameSharePercent = (frames.size * 100f / valid.size).toInt(),
                meanRawScore = scores.average().toFloat(),
                peakRawScore = scores.maxOrNull() ?: 0f
            )
        }.sortedWith(compareByDescending<CloudTemporalCandidate> { it.frameCount }
            .thenByDescending { it.meanRawScore })

        val minimumSupport = maxOf(2, ceil(valid.size * 0.25).toInt())
        val supported = candidates.filter { it.frameCount >= minimumSupport }
        return CloudTemporalConsensus(
            analyzedFrames = frameResults.size,
            framesWithModelOutput = valid.size,
            candidates = candidates.take(3),
            stabilityPercent = candidates.firstOrNull()?.frameSharePercent ?: 0,
            notePersian = if (supported.isEmpty())
                "بین فریم‌ها توافق کافی برای یک نامزد پایدار دیده نشد؛ تغییر رتبه‌ها می‌تواند ناشی از زاویه، نور، پوشش یا محدودیت مدل باشد."
            else
                "نامزد پرتکرار در دست‌کم بخشی از فریم‌ها تکرار شده است؛ این فقط پایداری خروجی مدل در ویدئو است، نه تأیید قطعی جنس ابر."
        )
    }
}

package com.lightninglab.atmospheric.knowledge

/**
 * Deterministic sampling plan for offline video inspection.
 * Keeps samples spread across the clip, includes the final timestamp when possible,
 * and never returns duplicate or out-of-range timestamps.
 */
object CloudVideoSamplingPlan {
    fun timestamps(durationMillis: Long, maxFrames: Int = 30, minimumIntervalMillis: Long = 1_000L): List<Long> {
        require(durationMillis > 0L) { "Video duration must be positive" }
        require(maxFrames >= 2) { "At least two frames must be requested" }
        require(minimumIntervalMillis > 0L) { "Minimum interval must be positive" }
        val count = minOf(maxFrames, ((durationMillis / minimumIntervalMillis) + 1L).coerceAtLeast(2L).toInt())
        if (count == 2) return listOf(0L, durationMillis)
        val step = durationMillis.toDouble() / (count - 1).toDouble()
        val samples = (0 until count).map { index ->
            (index * step).toLong().coerceIn(0L, durationMillis)
        }.distinct()
        return if (samples.lastOrNull() == durationMillis) samples else (samples + durationMillis).take(maxFrames)
    }
}

package com.lightninglab

/**
 * Frame-sampling schedule for MediaMetadataRetriever when the sequential
 * MediaCodec path is unavailable. Short clips use denser sampling to reduce
 * missed short flashes; long clips obey a strict sample budget.
 *
 * Sampling still cannot guarantee detection between decoded timestamps.
 * The MediaCodec path continues to decode native frames whenever available.
 */
internal object LightningFallbackSampling {
    const val MAX_SAMPLES = 30_000L

    fun intervalMs(spanMs: Long): Long {
        require(spanMs >= 0L)
        val preferred = when {
            spanMs <= 2L * 60_000L -> 33L
            spanMs <= 10L * 60_000L -> 50L
            else -> 100L
        }
        // ceil(span / MAX_SAMPLES) without overflowing Long.
        val budget = (spanMs / MAX_SAMPLES) +
            if (spanMs % MAX_SAMPLES != 0L) 1L else 0L
        return maxOf(preferred, budget)
    }

    /** Return end for the last short interval; never overflow a timestamp. */
    fun next(tMs: Long, endMs: Long, stepMs: Long): Long {
        require(tMs >= 0L && endMs >= tMs && stepMs > 0L)
        return if (endMs - tMs <= stepMs) endMs else tMs + stepMs
    }

    /** Motion-compensated DayLine needs an adequate spatial region. */
    fun supportsMotionVerification(width: Int, height: Int): Boolean =
        width >= 48 && height >= 48
}

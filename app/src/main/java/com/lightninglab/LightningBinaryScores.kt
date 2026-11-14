package com.lightninglab

/**
 * Output contract for the optional binary lightning verifier.
 * Index 0 = NON_LIGHTNING, index 1 = LIGHTNING.
 * These are uncalibrated model scores, not scientific probabilities.
 */
internal object LightningBinaryScores {
    const val NEGATIVE = "non_lightning"
    const val POSITIVE = "lightning"

    fun decode(scores: FloatArray): Float? {
        if (scores.size != 2 || scores.any { !it.isFinite() || it < 0f || it > 1f })
            return null
        val total = scores[0] + scores[1]
        if (!total.isFinite() || kotlin.math.abs(total - 1f) > 0.01f) return null
        return scores[1]
    }
}

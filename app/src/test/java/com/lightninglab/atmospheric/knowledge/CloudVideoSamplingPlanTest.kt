package com.lightninglab.atmospheric.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudVideoSamplingPlanTest {
    @Test fun includesStartAndEndAndStaysWithinFrameLimit() {
        val samples = CloudVideoSamplingPlan.timestamps(600_000L, maxFrames = 30)
        assertEquals(0L, samples.first())
        assertEquals(600_000L, samples.last())
        assertTrue(samples.size <= 30)
        assertEquals(samples.size, samples.distinct().size)
        assertTrue(samples.all { it in 0L..600_000L })
    }

    @Test fun shortClipStillGetsTwoDistinctSamples() {
        assertEquals(listOf(0L, 500L), CloudVideoSamplingPlan.timestamps(500L))
    }

    @Test fun longClipIsSampledAcrossItsWholeDuration() {
        val samples = CloudVideoSamplingPlan.timestamps(3_600_000L, maxFrames = 24)
        assertEquals(24, samples.size)
        assertEquals(0L, samples.first())
        assertEquals(3_600_000L, samples.last())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroDuration() {
        CloudVideoSamplingPlan.timestamps(0L)
    }
}

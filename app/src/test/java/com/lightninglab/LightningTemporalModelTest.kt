package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class LightningTemporalModelTest {
    @Test fun contractHasCorrectLabelsAndFrames() {
        assertEquals(8, LightningTemporalModel.FRAMES)
        assertEquals(224, LightningTemporalModel.SIDE)
        assertEquals(listOf("towerupward", "towerdownward", "close", "far",
            "unclear", "unclearbehind", "CC"), LightningTemporalModel.LABELS)
    }

    @Test fun sampleTimesAreMonotonicAndBounded() {
        val times = LightningTemporalModel.sampleTimes(1000L, 1200L, 2500L)
        assertEquals(8, times.size)
        assertEquals(500L, times.first())
        assertEquals(1700L, times.last())
        assertTrue(times.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test fun sampleTimesClampAtVideoBoundaries() {
        val times = LightningTemporalModel.sampleTimes(0L, 100L, 200L)
        assertEquals(0L, times.first())
        assertEquals(200L, times.last())
    }

    @Test fun decodeRejectsMalformedAndUncalibratedValues() {
        assertNull(LightningTemporalModel.decode(floatArrayOf(1f)))
        assertNull(LightningTemporalModel.decode(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, Float.NaN)))
        assertNull(LightningTemporalModel.decode(FloatArray(7) { 0.01f }))
        assertNull(LightningTemporalModel.decode(FloatArray(7) { -0.1f }))
    }

    @Test fun decodeRetainsModelScoreAndRanking() {
        val ranked = LightningTemporalModel.decode(floatArrayOf(0.05f, 0.05f, 0.55f, 0.1f, 0.1f, 0.1f, 0.05f))
        assertNotNull(ranked)
        assertEquals("close", ranked!![0].first)
        assertEquals(0.55f, ranked[0].second, 1e-6f)
    }
}

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
        assertTrue(times.asList().zipWithNext().all { (a: Long, b: Long) -> a <= b })
    }

    @Test fun sampleTimesClampAtVideoBoundaries() {
        val times = LightningTemporalModel.sampleTimes(0L, 100L, 200L)
        assertEquals(0L, times.first())
        assertEquals(200L, times.last())
    }

    @Test fun binaryTrainingContractUsesSameEventCenteredTimes() {
        assertArrayEquals(
            longArrayOf(500, 671, 842, 1014, 1185, 1357, 1528, 1700),
            LightningTemporalModel.sampleTimes(1000, 1200, 2500)
        )
    }

    @Test fun sampleTimesAreOverflowSafeNearLongMax() {
        val times = LightningTemporalModel.sampleTimes(
            Long.MAX_VALUE - 150, Long.MAX_VALUE - 120, Long.MAX_VALUE - 1
        )
        assertEquals(8, times.size)
        assertEquals(Long.MAX_VALUE - 1, times.last())
        assertTrue(times.asList().zipWithNext().all { (first, next) -> next >= first })
    }

    @Test fun sampleTimesDoNotOverflowOnVeryLongVideo() {
        val times = LightningTemporalModel.sampleTimes(
            0, Long.MAX_VALUE - 1000, Long.MAX_VALUE - 500
        )
        assertEquals(8, times.size)
        assertEquals(0L, times.first())
        assertTrue(times.asList().zipWithNext().all { (first, next) -> next >= first })
        assertEquals(Long.MAX_VALUE - 500, times.last())
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

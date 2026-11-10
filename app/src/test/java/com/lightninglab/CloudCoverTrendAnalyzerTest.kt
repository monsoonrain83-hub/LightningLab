package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCoverTrendAnalyzerTest {
    private fun frame(t: Long, cover: Int) = CloudVideoFrameResult(t, null, null, cover, 0, 0f, "test")

    @Test fun increasingCloudCoverIsReported() {
        val r = CloudCoverTrendAnalyzer.analyze(listOf(frame(0, 10), frame(1, 20), frame(2, 30), frame(3, 55), frame(4, 65), frame(5, 75)))
        assertEquals(CloudCoverTrend.Direction.INCREASING, r.direction)
        assertEquals(55, r.changePercentagePoints)
    }

    @Test fun decreasingCloudCoverIsReported() {
        val r = CloudCoverTrendAnalyzer.analyze(listOf(frame(0, 90), frame(1, 80), frame(2, 70), frame(3, 40), frame(4, 30), frame(5, 20)))
        assertEquals(CloudCoverTrend.Direction.DECREASING, r.direction)
    }

    @Test fun smallChangeIsStable() {
        val r = CloudCoverTrendAnalyzer.analyze(listOf(frame(0, 40), frame(1, 42), frame(2, 41), frame(3, 43), frame(4, 44), frame(5, 42)))
        assertEquals(CloudCoverTrend.Direction.STABLE, r.direction)
    }

    @Test fun fewerThanThreeFramesIsInsufficient() {
        val r = CloudCoverTrendAnalyzer.analyze(listOf(frame(0, 50), frame(1, 60)))
        assertEquals(CloudCoverTrend.Direction.INSUFFICIENT, r.direction)
        assertTrue(r.notePersian.isNotBlank())
    }
}

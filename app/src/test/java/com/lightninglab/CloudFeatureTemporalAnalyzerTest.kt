package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudFeatureTemporalAnalyzerTest {
    private fun frame(time: Long, features: List<Pair<String, Float>>?) = CloudVideoFrameResult(
        elapsedMillis = time,
        genusCode = null,
        score = null,
        coverPercent = 50,
        oktas = 4,
        texture = 0.5f,
        kindLabel = "test",
        structuralFeatures = features
    )

    @Test fun repeatedFeatureRanksAndShareAreComputed() {
        val report = CloudFeatureTemporalAnalyzer.analyze(listOf(
            frame(0, listOf("incus" to .65f, "mamma" to .2f)),
            frame(1000, listOf("incus" to .75f)),
            frame(2000, listOf("incus" to .80f)),
            frame(3000, listOf("mamma" to .9f))
        ))
        assertTrue(report.modelWasAvailable)
        assertEquals("incus", report.candidates.first().code)
        assertEquals(3, report.candidates.first().framesDetected)
        assertEquals(75, report.candidates.first().frameSharePercent)
    }

    @Test fun risingFeatureScoreIsFlagged() {
        val report = CloudFeatureTemporalAnalyzer.analyze(listOf(
            frame(0, listOf("virga" to .1f)),
            frame(1000, listOf("virga" to .2f)),
            frame(2000, listOf("virga" to .7f)),
            frame(3000, listOf("virga" to .9f))
        ))
        assertTrue(report.candidates.first().trendPersian.contains("افزایش"))
    }

    @Test fun missingFeatureModelIsReportedWithoutInventingCandidates() {
        val report = CloudFeatureTemporalAnalyzer.analyze(listOf(frame(0, null), frame(1000, null)))
        assertFalse(report.modelWasAvailable)
        assertTrue(report.candidates.isEmpty())
        assertTrue(report.notePersian.isNotBlank())
    }

    @Test fun subthresholdScoresDoNotBecomeDetections() {
        val report = CloudFeatureTemporalAnalyzer.analyze(listOf(
            frame(0, listOf("arcus" to .2f)), frame(1000, listOf("arcus" to .49f))
        ))
        assertTrue(report.candidates.isEmpty())
    }
}

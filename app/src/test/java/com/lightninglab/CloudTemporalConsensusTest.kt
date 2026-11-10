package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudTemporalConsensusTest {
    private fun frame(code: String?, score: Float?, time: Long) = CloudVideoFrameResult(
        elapsedMillis = time,
        genusCode = code,
        score = score,
        coverPercent = 50,
        oktas = 4,
        texture = 0.5f,
        kindLabel = "test"
    )

    @Test fun mostFrequentGenusRanksFirstAndShareIsComputed() {
        val result = CloudTemporalConsensusAnalyzer.analyze(listOf(
            frame("Cb", .8f, 0), frame("Cu", .7f, 1000),
            frame("Cb", .6f, 2000), frame("Cb", .9f, 3000)
        ))
        assertEquals("Cb", result.candidates.first().genusCode)
        assertEquals(3, result.candidates.first().frameCount)
        assertEquals(75, result.stabilityPercent)
    }

    @Test fun invalidAndMissingScoresAreIgnored() {
        val result = CloudTemporalConsensusAnalyzer.analyze(listOf(
            frame("Cb", .8f, 0), frame("Cu", Float.NaN, 1000),
            frame(null, null, 2000)
        ))
        assertEquals(1, result.framesWithModelOutput)
        assertEquals(100, result.stabilityPercent)
    }

    @Test fun emptyModelOutputProducesNoCandidates() {
        val result = CloudTemporalConsensusAnalyzer.analyze(listOf(frame(null, null, 0)))
        assertTrue(result.candidates.isEmpty())
        assertEquals(0, result.framesWithModelOutput)
    }
}

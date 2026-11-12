package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class SkyPhotoEvidenceTest {
    @Test fun imageOpticalProxyRemainsSeparateFromModelCandidate() {
        val result = CloudAnalysis.Result(0.625f, 5, 140f, 2.5f, 15.7f, CloudAnalysis.Kind.MIXED)
        val csv = SkyPhotoEvidence.csv(result, listOf("Cu" to 0.3f, "Sc" to 0.6f))
        val rows = csv.trim().lines()
        assertEquals(2, rows.size)
        assertTrue(rows.first().contains("model_softmax_uncalibrated"))
        assertTrue(rows.last().contains("0.62500,5,140.00000"))
        assertTrue(rows.last().contains("\"Sc\",0.60000"))
        assertTrue(rows.last().contains("not WMO-confirmed"))
    }

    @Test fun noModelIsAnExplicitEmptyCandidate() {
        val result = CloudAnalysis.Result(0f, 0, 0f, 0f, 0f, CloudAnalysis.Kind.CLEAR)
        val row = SkyPhotoEvidence.csv(result, emptyList()).trim().lines()[1]
        assertTrue(row.contains("\"CLEAR\",\"\",,"))
    }
}

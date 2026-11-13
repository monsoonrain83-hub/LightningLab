package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class SkyVideoEvidenceTest {
    private val rows = listOf(
        CloudVideoFrameResult(2500L, "Ci", 0.35f, 80, 7, 0.75f, "Thin clouds"),
        CloudVideoFrameResult(500L, null, null, 20, 2, 0.15f, "Clear sky")
    )

    @Test fun csvSortsFramesAndKeepsCandidateUncalibrated() {
        val csv = SkyVideoEvidence.csvFrames(rows)
        val lines = csv.lines().filter { it.isNotEmpty() }
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("model_softmax_uncalibrated"))
        assertTrue(lines[1].startsWith("2,500,20,2,0.15000"))
        assertTrue(lines[2].startsWith("2,2500,80,7,0.75000"))
        assertTrue(lines[2].contains("\"Ci\",0.35000"))
        assertTrue(lines[1].endsWith("\"\",,,,\"\""))
    }

    @Test fun emptyReportRetainsHeader() {
        assertEquals(1, SkyVideoEvidence.csvFrames(emptyList()).lines().count { it.isNotEmpty() })
    }
}

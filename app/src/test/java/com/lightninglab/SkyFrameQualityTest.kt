package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class SkyFrameQualityTest {
    private fun pixels(color: Int, count: Int = 256) = IntArray(count) { color }

    @Test fun whiteClippingIsFlaggedButNotCalledSunGlare() {
        val quality = SkyFrameQuality.assess(pixels(0xFFFFFFFF.toInt()), 16, 16)
        assertEquals(100, quality.clippedPercent)
        assertTrue(quality.flags.contains(SkyFrameQuality.Flag.CLIPPED_HIGHLIGHTS))
        assertFalse(quality.noteFa.contains("قطعاً"))
    }

    @Test fun darkAndWarmPixelBiasAreIndependent() {
        val pixels = IntArray(400) { index ->
            when {
                index < 140 -> 0xFFFA4020.toInt()
                index < 300 -> 0xFF060809.toInt()
                else -> 0xFF6080A0.toInt()
            }
        }
        val result = SkyFrameQuality.assess(pixels, 20, 20)
        assertEquals(35, result.warmRedPercent)
        assertEquals(40, result.darkPercent)
        assertTrue(result.flags.contains(SkyFrameQuality.Flag.WARM_RED_BIAS))
        assertTrue(result.flags.contains(SkyFrameQuality.Flag.VERY_DARK))
    }

    @Test fun transparentPixelsCannotCreateFalseWarning() {
        val pixels = IntArray(256) { if (it < 200) 0x00FFFFFF else 0xFF7088AB.toInt() }
        val result = SkyFrameQuality.assess(pixels, 16, 16)
        assertEquals(0, result.clippedPercent)
        assertTrue(result.flags.contains(SkyFrameQuality.Flag.TOO_FEW_PIXELS))
    }

    @Test fun invalidDimensionsCannotSilentlyMisreportSky() {
        try {
            SkyFrameQuality.assess(IntArray(200), 10, 30)
            fail("Dimension mismatch must be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun photoEvidenceContainsAuditableOpticalWarnings() {
        val quality = SkyFrameQuality.assess(pixels(0xFFFFFFFF.toInt()), 16, 16)
        val observation = CloudAnalysis.Result(1f, 8, 255f, 0f, 0f, CloudAnalysis.Kind.UNIFORM_LAYER)
        val csv = SkyPhotoEvidence.csv(observation, emptyList(), quality)
        assertTrue(csv.lines()[0].contains("rgb_quality_flags"))
        assertTrue(csv.lines()[1].contains("CLIPPED_HIGHLIGHTS"))
    }
}

package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class LightningFallbackSamplingTest {
    @Test fun shortClipsUseNearThirtyFpsSamples() {
        assertEquals(33L, LightningFallbackSampling.intervalMs(30_000L))
        assertEquals(33L, LightningFallbackSampling.intervalMs(120_000L))
    }
    @Test fun mediumClipsAreDenserThanThePreviousHundredMsFallback() {
        assertEquals(50L, LightningFallbackSampling.intervalMs(360_000L))
    }
    @Test fun longClipsRetainBudgetAndAvoidUnboundedFrameExtraction() {
        val hours = 12L * 60L * 60_000L
        val step = LightningFallbackSampling.intervalMs(hours)
        assertTrue(step >= 100L)
        assertTrue((hours / step) <= LightningFallbackSampling.MAX_SAMPLES)
    }
    @Test fun stepDoesNotOverflowOrSkipFinalBoundary() {
        assertEquals(1000L, LightningFallbackSampling.next(967, 1000, 33))
        assertEquals(33L, LightningFallbackSampling.next(0, 1000, 33))
        assertEquals(Long.MAX_VALUE, LightningFallbackSampling.next(
            Long.MAX_VALUE - 9, Long.MAX_VALUE, 30L))
    }
    @Test fun lowResolutionSkipsMotionVerifierSafely() {
        assertFalse(LightningFallbackSampling.supportsMotionVerification(20, 180))
        assertFalse(LightningFallbackSampling.supportsMotionVerification(180, 20))
        assertTrue(LightningFallbackSampling.supportsMotionVerification(320, 180))
    }
}

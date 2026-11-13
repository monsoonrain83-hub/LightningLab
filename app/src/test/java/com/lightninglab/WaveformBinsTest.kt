package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests (r10.70) for waveform binning when container duration metadata
 * is missing. Previously every peak collapsed into the last bucket because the
 * divisor degraded to max(1, 0); peaks must now spread across the envelope.
 */
class WaveformBinsTest {
    @Test fun knownExpectedFramesDistributeProportionally() {
        // 1000 frames over 10 bins: first frame -> bin 0, last frame -> bin 9.
        assertEquals(0, WaveformBins.index(0L, 1000L, 1000L, 10))
        assertEquals(9, WaveformBins.index(999L, 1000L, 1000L, 10))
        assertEquals(4, WaveformBins.index(499L, 1000L, 1000L, 10))
    }

    @Test fun unknownExpectedFramesDoNotCollapseIntoLastBucket() {
        // No container duration: a 1000-frame stream must not map everything to the end.
        val spread = WaveformBins.spread(1000L, 0L, 10)
        assertEquals(10, spread)
        assertEquals(0, WaveformBins.index(0L, 1000L, 0L, 10))
        assertTrue(WaveformBins.index(500L, 1000L, 0L, 10) in 0..9)
    }

    @Test fun unknownExpectedFramesSpreadWithLargeStream() {
        // Live-estimate fallback: a real decode maps per-buffer totals; with many
        // frames the spread must approach all buckets, not one spike.
        val spread = WaveformBins.spread(100_000L, 0L, 420)
        assertTrue("spread was only $spread buckets", spread >= 400)
    }

    @Test fun bucketIndexIsAlwaysWithinBounds() {
        // Defensive: negative/zero/large inputs never escape the bucket range.
        assertEquals(0, WaveformBins.index(0L, 0L, 0L, 32))
        assertEquals(31, WaveformBins.index(Long.MAX_VALUE, 1L, 1L, 32))
        assertEquals(0, WaveformBins.index(0L, 1L, -5L, 32))
    }

    @Test fun partialChunkStaysMonotonicWithLiveEstimate() {
        // During decoding, a chunk covering frames [900, 1000) of an unknown-length
        // stream maps into higher buckets than frames [0, 100) — preserving order.
        val early = WaveformBins.index(50L, 1000L, 0L, 10)
        val late = WaveformBins.index(950L, 1000L, 0L, 10)
        assertTrue(early < late)
    }
}

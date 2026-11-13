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
    @Test fun unknownDurationPeaksKeepEarlyAndLateTimePositions() {
        val collector = WindowedPeaks(initialFramesPerWindow = 1)
        for (i in 0 until 1000) collector.add(when (i) {
            100 -> 0.75f
            900 -> 1f
            else -> 0f
        })
        val result = collector.finish(10)
        assertEquals(0.75f, result[1], 0f)
        assertEquals(1f, result[9], 0f)
        assertEquals(0f, result[5], 0f)
    }

    @Test fun noDurationDataIsIndependentOfDecoderBufferBoundaries() {
        fun envelope(chunkSize: Int): List<Float> {
            val peaks = WindowedPeaks(initialFramesPerWindow = 16)
            for (start in 0 until 1024 step chunkSize) {
                for (i in start until minOf(1024, start + chunkSize)) peaks.add(if (i in 200..250) 0.8f else 0.1f)
            }
            return peaks.finish(32).toList()
        }
        assertEquals(envelope(1), envelope(37))
        assertEquals(envelope(37), envelope(256))
    }

    @Test fun dynamicWindowCoarseningKeepsMemoryBoundedAndEndPeaks() {
        val collector = WindowedPeaks(maxWindows = 4, initialFramesPerWindow = 1)
        repeat(100) { collector.add(if (it == 99) 1f else 0.1f) }
        val result = collector.finish(4)
        assertEquals(1f, result.last(), 0f)
        assertTrue(result.any { it > 0f })
    }

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

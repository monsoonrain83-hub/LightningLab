package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class VideoExportBudgetTest {
    @Test fun durationAccountsForSpeedAndEdits() {
        val segments = listOf(Seg(100L, 2100L), Seg(0L, 3000L))
        assertEquals(2500L, VideoExportBudget.durationMs(segments, 9999L, 2f))
        assertEquals(6000L, VideoExportBudget.durationMs(emptyList(), 3000L, 0.5f))
    }

    @Test fun emptyAndInvalidRangesNeverCreateNegativeSize() {
        assertEquals(0L, VideoExportBudget.durationMs(listOf(Seg(500L, 200L)), 1000L, 1f))
        assertEquals(0L, VideoExportBudget.estimatedBytes(0L, 5_000_000L))
    }

    @Test fun estimateAllowsContainerMarginAndDoubleScratchSpace() {
        val bytes = VideoExportBudget.estimatedBytes(10_000L, 8_000_000L, 192_000L)
        assertTrue(bytes in 10_000_000L..12_000_000L)
        assertTrue(VideoExportBudget.minimumFreeBytes(bytes) >= 150L * 1024L * 1024L)
        assertEquals(0L, VideoExportBudget.estimatedBytes(-5L, 1_000_000L))
    }
}

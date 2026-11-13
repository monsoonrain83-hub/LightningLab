package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class LightningScanWindowTest {
    @Test fun millisecondsCannotOverflowWhenConvertedToMicroseconds() {
        assertEquals(0L, LightningScanWindow.microseconds(-1L))
        assertEquals(123_000L, LightningScanWindow.microseconds(123L))
        assertEquals(Long.MAX_VALUE, LightningScanWindow.microseconds(Long.MAX_VALUE))
    }

    @Test fun noDurationUsesExplicitRangeWithoutOverflow() {
        val r = LightningScanWindow.of(-1L, 1_000L, 4_000L)!!
        assertEquals(1_000_000L, r.startUs)
        assertEquals(4_000_000L, r.endUs)
        assertTrue(r.knownEnd)
        assertEquals(0.5f, r.progress(2_500_000L), 0.0001f)
    }

    @Test fun unknownDurationWithoutEndDoesNotFabricatePercentage() {
        val r = LightningScanWindow.of(-1L, 0L, Long.MAX_VALUE)!!
        assertFalse(r.knownEnd)
        assertEquals(0f, r.progress(50_000_000L), 0f)
    }

    @Test fun durationClipsRequestedRange() {
        val r = LightningScanWindow.of(5_000_000L, 2_000L, Long.MAX_VALUE)!!
        assertEquals(5_000_000L, r.endUs)
        assertEquals(1f, r.progress(10_000_000L), 0f)
    }

    @Test fun impossibleOrEmptyWindowsAreRejected() {
        assertNull(LightningScanWindow.of(1_000_000L, -1L, 900L))
        assertNull(LightningScanWindow.of(1_000_000L, 500L, 300L))
        assertNull(LightningScanWindow.of(1_000_000L, 1_500L, Long.MAX_VALUE))
        assertNull(LightningScanWindow.of(1_000_000L, Long.MAX_VALUE, Long.MAX_VALUE))
    }
}

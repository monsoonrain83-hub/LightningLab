package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoHistogramTest {
    @Test fun buildsNormalizedRgbAndLumaBins() {
        val histogram = PhotoHistogram.fromPixels(
            intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFF808080.toInt()), 64
        )
        assertEquals(64, histogram.red.size)
        assertEquals(1f, histogram.red.maxOrNull() ?: 0f, 0.0001f)
        assertEquals(1f, histogram.green.maxOrNull() ?: 0f, 0.0001f)
        assertEquals(1f, histogram.blue.maxOrNull() ?: 0f, 0.0001f)
        assertEquals(1f, histogram.luma.maxOrNull() ?: 0f, 0.0001f)
    }

    @Test fun emptyImageProducesZeroHistogram() {
        val histogram = PhotoHistogram.fromPixels(intArrayOf(), 64)
        assertTrue(histogram.red.all { it == 0f })
        assertTrue(histogram.luma.all { it == 0f })
    }
}

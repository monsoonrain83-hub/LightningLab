package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoCompareMathTest {
    @Test fun normalizeClampsHandleAwayFromEdges() {
        assertEquals(0.02f, PhotoCompareMath.normalize(-2f), 0.0001f)
        assertEquals(0.98f, PhotoCompareMath.normalize(2f), 0.0001f)
        assertEquals(0.5f, PhotoCompareMath.normalize(Float.NaN), 0.0001f)
    }

    @Test fun splitXMapsFractionToPixelWidth() {
        assertEquals(250f, PhotoCompareMath.splitX(1000f, 0.25f), 0.0001f)
        assertEquals(0f, PhotoCompareMath.splitX(-10f, 0.5f), 0.0001f)
    }
}

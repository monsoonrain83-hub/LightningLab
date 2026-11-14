package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class LightningBinaryScoresTest {
    @Test fun decodesPositiveClassAtIndexOne() {
        assertEquals(0.8f, LightningBinaryScores.decode(floatArrayOf(0.2f, 0.8f))!!, 0.0001f)
        assertEquals(0f, LightningBinaryScores.decode(floatArrayOf(1f, 0f))!!, 0.0001f)
    }
    @Test fun rejectsSevenClassModelScoresAndBadShape() {
        assertNull(LightningBinaryScores.decode(floatArrayOf(0.1f, 0.5f, 0.4f)))
        assertNull(LightningBinaryScores.decode(floatArrayOf(0.9f)))
    }
    @Test fun rejectsNonfiniteNegativeAndUnnormalizedOutputs() {
        assertNull(LightningBinaryScores.decode(floatArrayOf(Float.NaN, 1f)))
        assertNull(LightningBinaryScores.decode(floatArrayOf(0.2f, -0.2f)))
        assertNull(LightningBinaryScores.decode(floatArrayOf(0.1f, 0.3f)))
    }
}

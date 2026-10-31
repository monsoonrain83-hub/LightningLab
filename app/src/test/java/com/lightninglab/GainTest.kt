package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Test

class GainTest {
    @Test fun halfAndDoubleAndSilence() {
        assertEquals(500.toShort(), Gain.scale(1000, 0.5f))
        assertEquals(2000.toShort(), Gain.scale(1000, 2f))
        assertEquals(0.toShort(), Gain.scale(1000, 0f))
        assertEquals((-500).toShort(), Gain.scale(-1000, 0.5f))
    }

    @Test fun clipsInsteadOfWrapping() {
        assertEquals(32767.toShort(), Gain.scale(30000, 2f))
        assertEquals((-32768).toShort(), Gain.scale(-30000, 2f))
    }
}

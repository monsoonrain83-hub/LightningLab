package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HslTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun z() = FloatArray(Hsl.BANDS)

    @Test fun zeroParametersChangeNothing() {
        for (c in listOf(rgb(200, 30, 40), rgb(10, 200, 90), rgb(120, 120, 120), rgb(0, 0, 0))) assertEquals(c, Hsl.adjust(c, z(), z(), z()))
    }

    @Test fun greyIsNeverTouched() {
        val sat = FloatArray(Hsl.BANDS) { 1f }
        assertEquals(rgb(128, 128, 128), Hsl.adjust(rgb(128, 128, 128), sat, sat, sat))
    }

    @Test fun desaturatingTheRedBandWashesPureRedOut() {
        // neighbouring bands blend (orange overlaps red), so pure red loses two thirds of its saturation, not all of it
        val sat = z(); sat[0] = -1f
        val o = Hsl.adjust(rgb(255, 0, 0), z(), sat, z())
        val r = (o shr 16) and 255; val g = (o shr 8) and 255; val b = o and 255
        assertTrue("r=$r g=$g b=$b", r == 255 && g == b && g in 120..220)
    }

    @Test fun otherBandsAreUnaffected() {
        val sat = z(); sat[0] = -1f
        val blue = rgb(20, 40, 230)
        assertEquals(blue, Hsl.adjust(blue, z(), sat, z()))
    }

    @Test fun hueShiftOfBlueBandMovesBlueTowardPurple() {
        val hue = z(); hue[5] = 1f   // +30 degrees: 240 -> 270
        val o = Hsl.adjust(rgb(0, 0, 255), hue, z(), z())
        val r = (o shr 16) and 255; val b = o and 255
        assertTrue("red channel should rise, r=$r", r > 60)
        assertTrue(b > 200)
    }
}

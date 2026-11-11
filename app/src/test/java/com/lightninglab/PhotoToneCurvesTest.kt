package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoToneCurvesTest {
    @Test fun identityCurvesProduceIdentityLuts() {
        val curves = PhotoToneCurves.identity()
        assertTrue(PhotoToneCurves.isIdentity(curves))
        for (channel in 0..2) {
            val lut = PhotoToneCurves.lut(curves, channel)
            for (value in 0..255) assertEquals(value, lut[value])
        }
    }

    @Test fun channelCurveChangesOnlyItsOwnLookupTable() {
        val curves = PhotoToneCurves.identity().toMutableList()
        curves[5 + 2] = 0.8f
        assertFalse(PhotoToneCurves.isIdentity(curves))
        assertEquals(128, PhotoToneCurves.lut(curves, 0)[128])
        assertTrue(PhotoToneCurves.lut(curves, 1)[128] > 128)
        assertEquals(128, PhotoToneCurves.lut(curves, 2)[128])
    }

    @Test fun curveOutputIsBoundedAndEndpointsAreHonoured() {
        val curves = PhotoToneCurves.identity().toMutableList()
        curves[0] = -2f
        curves[4] = 2f
        val lut = PhotoToneCurves.lut(curves, 0)
        assertEquals(0, lut[0])
        assertEquals(255, lut[255])
        assertTrue(lut.all { it in 0..255 })
    }
}

package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests (r10.70) for the heal boundary feathering.
 * Inpaint.fill clears the unknown mask while filling, so heal() must copy the
 * mask first or the feather pass silently becomes dead code and the repair
 * shows a hard circular seam. Only exact, arithmetic-certain assertions are
 * used; the simulation pretends Inpaint filled the whole patch, so pixels
 * outside the pre-fill mask keep that value (feathering never touches them).
 */
class HealFeatherTest {
    @Test fun weightEndpoints() {
        // Centre of the mark: fully repaired (weight 1). At/outside the radius: original stays (weight 0).
        assertEquals(1f, HealFeather.weight(0f, 10f, 6f), 0f)
        assertEquals(0f, HealFeather.weight(10f, 10f, 6f), 0f)
        assertEquals(0f, HealFeather.weight(15f, 10f, 6f), 0f)
    }

    @Test fun transparentPNGHealingPreservesAlphaAndColour() {
        val transparent = 0x00000000
        val repair = 0xFFFF8040.toInt()
        val midpoint = HealFeather.blend(transparent, repair, 0.5f)
        val alpha = (midpoint ushr 24) and 255
        assertTrue(alpha in 126..129)
        assertEquals(255, (midpoint ushr 16) and 255)
        assertEquals(128, (midpoint ushr 8) and 255)
        assertEquals(64, midpoint and 255)
        assertEquals(transparent, HealFeather.blend(transparent, repair, 0f))
        assertEquals(repair, HealFeather.blend(transparent, repair, 1f))
    }

    @Test fun blendKeepsEndpoints() {
        val original = 0xFF102030.toInt()
        val repaired = 0xFFAABBCC.toInt()
        assertEquals(repaired, HealFeather.blend(original, repaired, 1f))
        assertEquals(original, HealFeather.blend(original, repaired, 0f))
    }

    /**
     * The real regression: with a copy of the pre-fill mask, feathering actually
     * runs — the centre keeps the repair, the boundary ring is restored to the
     * original, and pixels outside the mask are never modified. Without the
     * mask copy the boundary stayed fully repaired and showed a hard seam.
     */
    @Test fun applyFeathersTheBoundaryAndKeepsTheCentre() {
        val bw = 41; val bh = 41
        val cx = 20f; val cy = 20f; val r = 10f; val feather = 6f
        val original = IntArray(bw * bh) { 0xFF102030.toInt() }
        val px = IntArray(bw * bh) { 0xFFF0E0D0.toInt() } // pretend Inpaint filled everything
        val wasUnknown = BooleanArray(bw * bh)
        val distances = FloatArray(bw * bh)
        for (y in 0 until bh) for (x in 0 until bw) {
            val dx = x - cx; val dy = y - cy
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            distances[y * bw + x] = d
            wasUnknown[y * bw + x] = d <= r
        }
        HealFeather.apply(px, original, wasUnknown, distances, r, feather)
        // Centre (weight 1): repaired colour kept.
        assertEquals(0xFFF0E0D0.toInt(), px[(cy.toInt()) * bw + cx.toInt()])
        // Outside the mark: feathering never touches it, so the filled value stays.
        assertEquals(0xFFF0E0D0.toInt(), px[0])
        // Exactly on the boundary (weight 0, inside the mask): original restored —
        // this is what the missing mask copy used to break.
        val onEdge = (cy.toInt()) * bw + (cx.toInt() + 10)
        assertEquals(0xFF102030.toInt(), px[onEdge])
    }
}

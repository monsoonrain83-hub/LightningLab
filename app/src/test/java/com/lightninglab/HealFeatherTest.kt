package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Regression tests (r10.70) for the heal boundary feathering.
 * Inpaint.fill clears the unknown mask while filling, so heal() must copy the
 * mask first or the feather pass silently becomes dead code and the repair
 * shows a hard circular seam.
 */
class HealFeatherTest {
    @Test fun weightIsOneAtCentreAndZeroAtBoundary() {
        // Centre of the mark: fully repaired (weight 1). At/outside the radius: original stays (weight 0).
        assertEquals(1f, HealFeather.weight(0f, 10f, 6f), 1e-4f)
        assertEquals(0f, HealFeather.weight(10f, 10f, 6f), 1e-4f)
        assertEquals(0f, HealFeather.weight(15f, 10f, 6f), 1e-4f)
    }

    @Test fun weightDecreasesMonotonicallyWithDistance() {
        // Farther from the centre means closer to the original: weight never increases with distance.
        var prev = Float.MAX_VALUE
        for (i in 0..20) {
            val d = i * 0.5f // 0..10 inside radius 10
            val w = HealFeather.weight(d, 10f, 6f)
            assertTrue("weight increased at d=$d ($w > $prev)", w <= prev + 1e-6f)
            prev = w
        }
    }

    @Test fun blendKeepsEndpoints() {
        val original = 0xFF102030.toInt()
        val repaired = 0xFFAABBCC.toInt()
        assertEquals(repaired, HealFeather.blend(original, repaired, 1f))
        assertEquals(original, HealFeather.blend(original, repaired, 0f))
    }

    @Test fun blendIsOrderedAndInsideTheInputRange() {
        val o = 0xFF000030.toInt(); val r = 0xFF0000D0.toInt()
        val ob = o and 0xFF; val rb = r and 0xFF
        var prev = ob
        for (i in 1..9) {
            val a = i / 10f
            val b = HealFeather.blend(o, r, a) and 0xFF
            // A higher weight keeps more of the repaired colour, and never leaves [original, repaired].
            assertTrue("blend out of order at a=$a ($b)", b >= prev)
            assertTrue("blend below original at a=$a ($b)", b >= ob)
            assertTrue("blend above repaired at a=$a ($b)", b <= rb)
            prev = b
        }
    }

    /**
     * The real regression: with a copy of the pre-fill mask, a circular mark is
     * blended with smooth weights — the centre keeps the repair, the boundary
     * keeps the original, and in-between pixels land strictly between the two.
     * Without the mask copy the feather loop never ran.
     */
    @Test fun applyFeathersTheBoundaryAndKeepsTheCentre() {
        val bw = 41; val bh = 41
        val cx = 20f; val cy = 20f; val r = 10f; val feather = 6f
        val original = IntArray(bw * bh) { 0xFF102030.toInt() }
        val px = IntArray(bw * bh) { 0xFFF0E0D0.toInt() } // pretend Inpaint filled everything
        val wasUnknown = BooleanArray(bw * bh)
        val distances = FloatArray(bw * bh)
        for (y in 0 until bh) for (x in 0 until bw) {
            val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
            distances[y * bw + x] = d
            wasUnknown[y * bw + x] = d <= r
        }
        HealFeather.apply(px, original, wasUnknown, distances, r, feather)
        // Centre (weight 1): repaired colour kept.
        assertEquals(0xFFF0E0D0.toInt(), px[(cy.toInt()) * bw + cx.toInt()])
        // Outside the mark: untouched original.
        assertEquals(0xFF102030.toInt(), px[0])
        // Exactly on the boundary (weight 0): original restored.
        val onEdge = (cy.toInt()) * bw + (cx.toInt() + 10)
        assertEquals(0xFF102030.toInt(), px[onEdge])
        // Half-way inside (weight ~0.93): strictly between original and repaired.
        val mid = (cy.toInt()) * bw + (cx.toInt() + 5)
        val midBlue = px[mid] and 0xFF
        assertTrue("mid pixel not between original and repaired, blue=$midBlue", midBlue in 49..207)
        // A pixel closer to the centre keeps strictly more of the repair.
        val inner = (cy.toInt()) * bw + (cx.toInt() + 2)
        assertTrue((px[inner] and 0xFF) > midBlue)
    }
}

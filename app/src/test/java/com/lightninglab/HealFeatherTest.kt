package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
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

    @Test fun weightIsMonotonicInsideTheFeather() {
        var prev = -1f
        for (i in 0..20) {
            val d = i * 0.5f // 0..10 inside radius 10
            val w = HealFeather.weight(d, 10f, 6f)
            assertTrue("weight not monotonic at d=$d", w >= prev - 1e-6f)
            prev = w
        }
    }

    @Test fun blendKeepsEndpoints() {
        val original = 0xFF102030.toInt()
        val repaired = 0xFFAABBCC.toInt()
        assertEquals(repaired, HealFeather.blend(original, repaired, 1f))
        assertEquals(original, HealFeather.blend(original, repaired, 0f))
    }

    @Test fun blendIsSymmetricAroundHalfWeight() {
        val o = 0xFF000000.toInt(); val r = 0xFF0000FF.toInt()
        val a = HealFeather.blend(o, r, 0.25f)
        val b = HealFeather.blend(o, r, 0.75f)
        val mid = (0xFF shl 24) or (0x7F)
        assertTrue(abs(a - b) < 12)
        assertTrue(abs((a and 0xFF) - 0x3F) < 4 && abs((b and 0xFF) - 0xBF) < 4)
    }

    /**
     * The real regression: with a copy of the pre-fill mask, a circular mark is
     * blended with smooth weights, so repaired pixels far from the boundary
     * (centre) differ maximally from the original while boundary pixels stay
     * close to it. Without the mask copy the feather loop never ran.
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
        // Centre: repaired colour kept.
        assertEquals(0xFFF0E0D0.toInt(), px[(cy.toInt()) * bw + cx.toInt()])
        // Outside the mark: untouched original.
        assertEquals(0xFF102030.toInt(), px[0])
        // Just inside the boundary: mostly original (weight near 0).
        val edge = (cy.toInt()) * bw + (cx.toInt() + 9)
        assertTrue("boundary not feathered", abs((px[edge] and 0xFF) - 0x30) <= 2)
    }
}

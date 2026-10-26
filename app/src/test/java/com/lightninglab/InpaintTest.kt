package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InpaintTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun holeInFlatColourBecomesThatColour() {
        val w = 20; val h = 20
        val px = IntArray(w * h) { rgb(10, 20, 30) }
        val unk = BooleanArray(w * h)
        for (y in 6..13) for (x in 6..13) { unk[y * w + x] = true; px[y * w + x] = rgb(255, 255, 255) }
        Inpaint.fill(px, w, h, unk)
        for (p in px) assertEquals(rgb(10, 20, 30), p)
        assertTrue(unk.none { it })
    }

    @Test fun holeInHorizontalGradientStaysBetweenNeighbours() {
        val w = 30; val h = 10
        val px = IntArray(w * h) { rgb((it % w) * 8, 0, 0) }
        val unk = BooleanArray(w * h)
        for (y in 3..6) for (x in 12..17) unk[y * w + x] = true
        Inpaint.fill(px, w, h, unk)
        for (y in 3..6) for (x in 12..17) {
            val r = (px[y * w + x] shr 16) and 255
            assertTrue(r in 8 * 11..8 * 18)
        }
    }
}

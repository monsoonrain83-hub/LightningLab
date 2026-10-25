package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Mirrors docs/aligner_port.py (validated scenario: exact recovery of shifts up to 14 px at 1024x576). */
class AlignerTest {
    private val w = 1024; private val h = 576
    private val rnd = Random(3)

    private fun scene(): FloatArray {
        val a = FloatArray(w * h)
        val blocks = FloatArray((w / 8) * (h / 8)) { 10f * rnd.nextFloat() }
        for (y in 0 until h) for (x in 0 until w) {
            a[y * w + x] = if (y > 420) 60f + 30f * Math.sin(x / 9.0).toFloat() * Math.cos(y / 7.0).toFloat()
            else 20f + blocks[(y / 8) * (w / 8) + x / 8]
        }
        repeat(40) { val x = rnd.nextInt(w - 3); for (y in 300 until 420) for (k in 0 until 3) a[y * w + x + k] += 50f }
        for (i in a.indices) a[i] += rnd.nextGaussian().toFloat()
        return a
    }

    private fun moved(src: FloatArray, dx: Int, dy: Int): FloatArray {
        val m = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) m[y * w + x] = src[Math.floorMod(y - dy, h) * w + Math.floorMod(x - dx, w)]
        for (y in 0 until 300) for (x in 0 until w) m[y * w + x] += 15f      // sky brightening
        for (y in 50 until 400) for (x in 500 until 502) m[y * w + x] += 150f // lightning line
        return m
    }

    @Test fun recoversKnownShifts() {
        val base = scene()
        for ((dx, dy) in listOf(0 to 0, 5 to -3, -9 to 6, 14 to 2)) {
            val s = Aligner.estimate(base, moved(base, dx, dy), w, h)
            assertTrue("confident for ($dx,$dy)", s.confident)
            assertEquals(dx, s.dx); assertEquals(dy, s.dy)
        }
    }

    @Test fun featurelessNightSkyIsNotConfident() {
        val a = FloatArray(w * h) { 20f + 2f * rnd.nextGaussian().toFloat() }
        val b = FloatArray(w * h) { 20f + 2f * rnd.nextGaussian().toFloat() }
        assertFalse(Aligner.estimate(a, b, w, h).confident)
    }
}

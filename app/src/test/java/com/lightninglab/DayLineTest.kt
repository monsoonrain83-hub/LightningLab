package com.lightninglab

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Mirrors the validated Python prototype: jittery handheld-like clip, one frame gets a thin vertical bolt. */
class DayLineTest {
    private val w = 320; private val h = 176
    private val rnd = Random(3)

    private fun scene(): FloatArray {
        val a = FloatArray(w * h)
        val blocks = FloatArray((w / 8) * (h / 8)) { 40f * rnd.nextFloat() }
        for (y in 0 until h) for (x in 0 until w) a[y * w + x] = 20f + blocks[(y / 8) * (w / 8) + x / 8]
        repeat(25) { val x = 10 + rnd.nextInt(w - 20); for (y in 60 until 120) for (k in 0 until 2) a[y * w + x + k] += 60f }
        for (y in 130 until h) for (x in 0 until w) a[y * w + x] += 30f * Math.sin(x / 6.0).toFloat()
        return a
    }

    private fun jitter(src: FloatArray, dx: Int, dy: Int): FloatArray {
        val o = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w)
            o[y * w + x] = src[Math.floorMod(y - dy, h) * w + Math.floorMod(x - dx, w)] + rnd.nextGaussian().toFloat()
        return o
    }

    private fun run(withBolt: Boolean): List<DayLine.Stat> {
        val base = scene(); val d = DayLine(w, h)
        for (i in 0 until 40) {
            val f = if (i == 0) jitter(base, 0, 0) else jitter(base, rnd.nextInt(7) - 3, rnd.nextInt(7) - 3)
            if (withBolt && i == 20) for (y in 40 until 140) for (x in 150 until 152) f[y * w + x] += 120f
            d.push(i * 33L, f)
        }
        d.finish()
        return d.stats
    }

    @Test fun jitterAloneNeverLooksLikeABolt() {
        val s = run(false)
        assertTrue("one stat per frame", s.size == 40)
        assertTrue("longest unpaired structure stays short", s.all { it.longest < FlashAnalyzer.dayLongest(0.6f) })
    }

    @Test fun thinBoltIsFoundDespiteJitter() {
        val s = run(true)
        assertTrue("bolt frame longest >= 60, was ${s[20].longest}", s[20].longest >= 60)
        assertTrue("others stay short", s.indices.filter { it != 20 }.all { s[it].longest < FlashAnalyzer.dayLongest(0.6f) })
    }
}

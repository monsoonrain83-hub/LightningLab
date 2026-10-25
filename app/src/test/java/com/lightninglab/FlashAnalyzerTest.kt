package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random

/** Synthetic-scenario tests for [FlashAnalyzer]. Mirrors the validated Python port (see docs/VALIDATION.md). */
class FlashAnalyzerTest {
    private val cn = FlashAnalyzer.GX * FlashAnalyzer.GY
    private val rnd = Random(1)

    private class Clip(val t: LongArray, val c: Array<FloatArray>)

    private fun clip(sec: Int, level: Float = 60f, noise: Float = 0.7f, fps: Int = 30): Clip {
        val n = sec * fps
        val t = LongArray(n) { it * 1000L / fps }
        val c = Array(n) { FloatArray(cn) { level + noise * rnd.nextGaussian().toFloat() } }
        return Clip(t, c)
    }

    private fun run(k: Clip, sens: Float = 0.6f, minSep: Long = 250L): List<LightningEvent> {
        val ms = k.t.indices.map { Sample(k.t[it], k.c[it].average().toFloat(), k.c[it], 0f) }
        return FlashAnalyzer.analyze(ms, sens, minSep)
    }

    private fun flash(k: Clip, atMs: Long, amp: Float, cells: Int = 3) {
        val i = k.t.indexOfFirst { it >= atMs }
        for (j in 0 until cells) k.c[i][j] += amp
    }

    @Test fun nightTwoFlashes500msApartAreSeparate() {
        val k = clip(20); flash(k, 5000, 60f); flash(k, 5500, 80f)
        assertEquals(2, run(k).size)
    }

    @Test fun faintDayFlashOverManyCells() {
        val k = clip(20, level = 170f); flash(k, 8000, 8f, cells = 10)
        assertEquals(1, run(k).size)
    }

    @Test fun permanentSceneCutIsNotAFlash() {
        val k = clip(20); for (i in 300 until k.c.size) for (j in 0 until cn) k.c[i][j] += 50f
        assertEquals(0, run(k).size)
    }

    @Test fun cameraShakeMixedSignIsRejected() {
        val k = clip(20)
        for (i in 100 until 500 step 3) for (j in 0 until cn) k.c[i][j] += 25f * rnd.nextGaussian().toFloat()
        assertEquals(0, run(k).size)
    }

    @Test fun slowExposureRampIsRejected() {
        val k = clip(20); val n = k.c.size
        for (i in 0 until n) for (j in 0 until cn) k.c[i][j] += 60f * i / n
        assertEquals(0, run(k).size)
    }

    @Test fun flashInFirstOrLast150msIsIgnored() {
        val k = clip(10); for (j in 0 until 4) { k.c[0][j] += 80f; k.c[1][j] += 80f }
        assertEquals(0, run(k).size)
    }

    @Test fun pureNoiseGivesNoEvents() {
        assertEquals(0, run(clip(60, noise = 1.0f)).size)
    }
}

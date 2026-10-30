package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random

/** Synthetic-scenario tests for [FlashAnalyzer]; the real-clip results are in docs/REAL_CLIP_FINDINGS.md. */
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

    private fun samples(k: Clip) = k.t.indices.map { Sample(k.t[it], k.c[it].average().toFloat(), k.c[it], 0f) }

    private fun run(k: Clip, sens: Float = 0.6f, minSep: Long = 250L, day: List<DayLine.Stat>? = null) =
        FlashAnalyzer.analyze(samples(k), sens, minSep, day)

    private fun flash(k: Clip, atMs: Long, amp: Float, cells: Int = 3, frames: Int = 1) {
        val i = k.t.indexOfFirst { it >= atMs }
        for (f in 0 until frames) for (j in 0 until cells) k.c[i + f][j] += amp
    }

    private fun noDay(k: Clip) = k.t.map { DayLine.Stat(it, 0f, 0f, 0) }

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

    @Test fun slowExposureRampIsRejected() {
        val k = clip(20); val n = k.c.size
        for (i in 0 until n) for (j in 0 until cn) k.c[i][j] += 60f * i / n
        assertEquals(0, run(k).size)
    }

    @Test fun flashAlreadyInProgressAtVideoStartIsFound() {
        val k = clip(10); flash(k, 0, 80f, cells = 4, frames = 2)
        assertEquals(1, run(k).size)
    }

    @Test fun pureNoiseGivesNoEvents() {
        assertEquals(0, run(clip(60, noise = 1.0f)).size)
    }

    @Test fun eightFrameFlashIsOneEvent() {
        val k = clip(30); flash(k, 10000, 70f, frames = 8)
        assertEquals(1, run(k).size)
    }

    @Test fun shakeLikeEpisodeWithNoMotionCompensatedResidualIsDropped() {
        val k = clip(20); flash(k, 5000, 60f)
        assertEquals(0, run(k, day = noDay(k)).size)
    }

    @Test fun episodeWithMotionCompensatedResidualIsKept() {
        val k = clip(20); flash(k, 5000, 60f)
        val day = k.t.map { DayLine.Stat(it, if (it in 4900L..5100L) 0.03f else 0f, 0f, 0) }
        assertEquals(1, run(k, day = day).size)
    }

    @Test fun thinLineStructureInDayLineStatsCreatesAnEvent() {
        val k = clip(20, level = 170f)
        val day = k.t.map { DayLine.Stat(it, 0f, if (it in 8000L..8030L) 300f else 0f, if (it in 8000L..8030L) 60 else 0) }
        assertEquals(1, run(k, day = day).size)
    }

    @Test fun twoDayLineFramesFarApartAreTwoEvents() {
        val k = clip(20, level = 170f)
        val day = k.t.map { DayLine.Stat(it, 0f, 0f, if (it in 4000L..4010L || it in 4100L..4110L) 60 else 0) }
        assertEquals(2, run(k, day = day).size)
    }

    @Test fun rapidStrokesWithClearDipsAreSeparatedInsideOneEpisode() {
        val k = clip(30)
        // two bright strokes 3 frames apart with a dark frame between them (inside one 250 ms episode)
        val i = k.t.indexOfFirst { it >= 10000 }
        for (j in 0 until 3) { k.c[i][j] += 90f; k.c[i + 1][j] += 90f }
        for (j in 0 until 3) { k.c[i + 4][j] += 90f; k.c[i + 5][j] += 90f }
        assertEquals(2, run(k).size)
    }
}

package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectCodecTest {
    private fun sample() = ProjectData(
        "Project 10-30", "main.mp4", 7000L, listOf(SegData(0, 2000, null), SegData(500, 1500, "clip_1.mp4")),
        "music.wav", null, true, 3, 0.5f, 0.2f, -0.1f, 90, true, false, 1.7777f,
        "Hello \u06a9\u0644\u0627\u0633", -16777216, 2, 0, true, "\u26a1", 1, 2, 0.5f, 1.5f, 0.25f,
        listOf(OverlayClipData(7L, "TEXT", "Rain alert", 1200L, 4200L, -1, 2, 0, true, listOf(OverlayKeyframeData(1200L, 0f, 0), OverlayKeyframeData(4200L, 1f, 2))),
            OverlayClipData(8L, "STICKER", "⚡", 5000L, 6500L, -1, 1, 2, false),
            OverlayClipData(9L, "IMAGE", "image", 1000L, 3000L, -1, 1, 1, false, listOf(OverlayKeyframeData(1000L, .2f, 0), OverlayKeyframeData(3000L, .8f, 2)), "overlay_9.png")), listOf(EffectClipData(11L, 3, 1000L, 5000L)), listOf(750L, 2500L, 6000L))

    @Test fun roundTripKeepsEverything() {
        val p = sample()
        assertEquals(p, ProjectCodec.decode(ProjectCodec.encode(p)))
    }

    @Test fun olderVersionOneProjectsRemainReadable() {
        val legacy = """{"v":1,"name":"old","video":"main.mp4","dur":1000,"segs":[{"s":0,"e":1000,"c":""}]}"""
        val decoded = ProjectCodec.decode(legacy)!!
        assertEquals("old", decoded.name)
        assertEquals(emptyList<OverlayClipData>(), decoded.overlayClips)
        assertEquals(emptyList<EffectClipData>(), decoded.effectClips)
        assertEquals(emptyList<Long>(), decoded.markers)
    }

    @Test fun corruptOrUnknownVersionIsRejected() {
        assertNull(ProjectCodec.decode("not json"))
        assertNull(ProjectCodec.decode("{\"v\":99,\"name\":\"x\"}"))
    }
}

package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectCodecTest {
    private fun sample() = ProjectData(
        "Project 10-30", "main.mp4", 7000L, listOf(SegData(0, 2000, null), SegData(500, 1500, "clip_1.mp4")),
        "music.wav", null, true, 3, 0.5f, 0.2f, -0.1f, 90, true, false, 1.7777f,
        "Hello \u06a9\u0644\u0627\u0633", -16777216, 2, 0, true, "\u26a1", 1, 2)

    @Test fun roundTripKeepsEverything() {
        val p = sample()
        assertEquals(p, ProjectCodec.decode(ProjectCodec.encode(p)))
    }

    @Test fun corruptOrUnknownVersionIsRejected() {
        assertNull(ProjectCodec.decode("not json"))
        assertNull(ProjectCodec.decode("{\"v\":99,\"name\":\"x\"}"))
    }
}

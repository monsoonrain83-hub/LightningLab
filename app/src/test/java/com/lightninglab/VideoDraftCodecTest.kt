package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class VideoDraftCodecTest {
    private fun project() = ProjectData(
        "draft", "content://media/external/video/media/10", 3500,
        listOf(SegData(0L, 2500L, "content://media/external/video/media/11")),
        "content://media/external/audio/media/9", null, false,
        2, 1.25f, .1f, .2f, 90, false, true, 1.0f,
        "sample", -1, 2, 1, false, null, 1, 1,
        overlayClips = listOf(OverlayClipData(20L, "IMAGE", "", 0, 2000, -1, 1, 1, false, assetFile = "overlay_20.png"))
    )

    @Test fun videoAndAudioUriDraftRoundTrips() {
        val draft = VideoDraft("content://media/external/video/media/10", project(), 123456L)
        assertEquals(draft, VideoDraftCodec.decode(VideoDraftCodec.encode(draft)))
    }

    @Test fun legacyProjectCodecRemainsUnchanged() {
        assertEquals(7, ProjectCodec.VERSION)
        assertEquals(project(), ProjectCodec.decode(ProjectCodec.encode(project())))
    }

    @Test fun rejectsUnsafeAndUnsupportedDrafts() {
        val valid = VideoDraftCodec.encode(VideoDraft("content://media/video/10", project(), 9))
        assertNull(VideoDraftCodec.decode(valid.replace("content://media/video/10", "https://example.org/video")))
        assertNull(VideoDraftCodec.decode(valid.replace("\"version\":1", "\"version\":99")))
        assertNull(VideoDraftCodec.decode("invalid"))
        assertNull(VideoDraftCodec.decode("a".repeat(2_000_001)))
    }

    @Test fun refusesEmptyVideoUri() {
        try {
            VideoDraftCodec.encode(VideoDraft("", project(), 0))
            fail("empty source must not serialize")
        } catch (_: IllegalArgumentException) { }
    }
}

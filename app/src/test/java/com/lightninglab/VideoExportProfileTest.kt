package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoExportProfileTest {
    @Test fun profilesNeverUpscaleSource() {
        for (profile in VideoExportProfile.values()) {
            val size = profile.outputSize(1280, 720)
            assertTrue(size.first <= 1280 && size.second <= 720)
            assertEquals(0, size.first % 2)
            assertEquals(0, size.second % 2)
        }
    }

    @Test fun portraitAndLandscapeKeepAspectRatioWithinRoundingTolerance() {
        for (profile in VideoExportProfile.values()) {
            val landscape = profile.outputSize(1920, 1080)
            val portrait = profile.outputSize(1080, 1920)
            assertTrue(kotlin.math.abs(landscape.first.toFloat() / landscape.second - 16f / 9f) < 0.003f)
            assertTrue(kotlin.math.abs(portrait.first.toFloat() / portrait.second - 9f / 16f) < 0.003f)
        }
    }

    @Test fun lowerResolutionUsesLowerOrEqualBitrate() {
        val source = VideoExportProfile.SOURCE.targetBitrate(3840, 2160)
        val hd = VideoExportProfile.HD.targetBitrate(3840, 2160)
        val sd = VideoExportProfile.SD.targetBitrate(3840, 2160)
        assertTrue(source >= hd && hd > sd)
    }

    @Test fun bitrateIsClampedAndProfileIdsAreRestored() {
        assertEquals(1_500_000, VideoExportProfile.SD.targetBitrate(320, 240))
        assertEquals(VideoExportProfile.HD, VideoExportProfile.fromId("HD"))
        assertEquals(VideoExportProfile.SOURCE, VideoExportProfile.fromId("missing"))
    }
}

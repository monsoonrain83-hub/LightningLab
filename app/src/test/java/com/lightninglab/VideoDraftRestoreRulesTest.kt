package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class VideoDraftRestoreRulesTest {
    private fun draft(overlays: List<OverlayClipData> = emptyList()): VideoDraft {
        val uri = "content://media/external/video/media/1"
        val project = ProjectData(
            name = "draft", video = uri, videoDur = 10000L,
            segs = listOf(SegData(0L, 10000L, null)), music = null, voice = null,
            mute = false, filter = 0, speed = 1f, brightness = 0f,
            contrast = 0f, rotate = 0, flipH = false, flipV = false, crop = 1f,
            text = "", textColor = -1, textSize = 1, textPos = 1,
            textBg = false, sticker = null, stickerSize = 1, stickerPos = 1,
            overlayClips = overlays
        )
        return VideoDraft(uri, project, 1000L)
    }

    @Test fun validLegacyAndRevisionedAssetsAreAccepted() {
        assertTrue(VideoDraftRestoreRules.validate(draft(listOf(
            OverlayClipData(1L, "IMAGE", "", 0, 1000, -1, 1, 1, false, assetFile = "overlay_1.png"),
            OverlayClipData(2L, "IMAGE", "", 0, 1000, -1, 1, 1, false, assetFile = "overlay_2_345.png")
        ))))
    }

    @Test fun rejectsDuplicateIdsAndAssetTraversal() {
        val a = OverlayClipData(1L, "IMAGE", "", 0, 1000, -1, 1, 1, false, assetFile = "overlay_1.png")
        assertFalse(VideoDraftRestoreRules.validate(draft(listOf(a, a))))
        assertFalse(VideoDraftRestoreRules.validate(draft(listOf(a.copy(assetFile = "../overlay_1.png")))))
        assertFalse(VideoDraftRestoreRules.validate(draft(listOf(a.copy(endMs = -1)))))
    }

    @Test fun safeguardsBitmapMemoryBeforeDecoding() {
        assertTrue(VideoDraftRestoreRules.permitsBitmap(2000, 2000, 0))
        assertFalse(VideoDraftRestoreRules.permitsBitmap(5000, 5000, 0))
        assertFalse(VideoDraftRestoreRules.permitsBitmap(2000, 2000, 47_000_000L))
        assertFalse(VideoDraftRestoreRules.permitsBitmap(0, 1000, 0))
    }

    @Test fun rejectsMismatchedSourceAndNonFiniteEditorValues() {
        assertFalse(VideoDraftRestoreRules.validate(
            draft().copy(videoUri = "content://media/external/video/media/999")))
        val d = draft()
        assertFalse(VideoDraftRestoreRules.validate(d.copy(project = d.project.copy(speed = Float.NaN))))
    }
}

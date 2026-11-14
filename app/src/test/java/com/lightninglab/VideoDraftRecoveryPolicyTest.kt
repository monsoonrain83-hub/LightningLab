package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class VideoDraftRecoveryPolicyTest {
    private val uri = "content://media/external/video/media/1"

    private fun draft(id: Long, file: String? = null): VideoDraft {
        val p = ProjectData(
            "autosave", uri, 5000L, listOf(SegData(0L, 5000L, null)),
            null, null, false, 0, 1f, 0f, 0f, 0, false, false, 1f,
            "", -1, 1, 1, false, null, 1, 1,
            overlayClips = listOf(OverlayClipData(id, "IMAGE", "", 0, 1000,
                -1, 1, 1, false, assetFile = file))
        )
        return VideoDraft(uri, p, 1234L)
    }

    @Test fun currentPreferredWhenBothSnapshotsUsable() {
        val current = draft(1, "overlay_1_200.png")
        val previous = draft(2, "overlay_2_100.png")
        assertEquals(current, VideoDraftRecoveryPolicy.select(current, previous) { true })
    }

    @Test fun fallsBackWhenCurrentManifestIsMissingOrCorrupt() {
        val previous = draft(2, "overlay_2_100.png")
        assertEquals(previous, VideoDraftRecoveryPolicy.select(null, previous) { true })
        val bad = previous.copy(videoUri = "file:///another/media.mp4")
        assertEquals(previous, VideoDraftRecoveryPolicy.select(bad, previous) { true })
    }

    @Test fun fallsBackWhenPrimaryReferencesMissingOverlay() {
        val current = draft(1, "overlay_1_200.png")
        val previous = draft(2, "overlay_2_100.png")
        val files = setOf("overlay_2_100.png")
        assertEquals(previous, VideoDraftRecoveryPolicy.select(current, previous) { it in files })
    }

    @Test fun protectsAssetsOfCurrentAndPreviousDuringPruning() {
        val current = draft(1, "overlay_1_200.png")
        val previous = draft(2, "overlay_2_100.png")
        assertEquals(setOf("overlay_1_200.png", "overlay_2_100.png"),
            VideoDraftRecoveryPolicy.protectedAssets(current, previous))
    }

    @Test fun rejectsUnsafeAndMissingFallbackAssets() {
        val unsafe = draft(2, "../outside.png")
        assertNull(VideoDraftRecoveryPolicy.select(null, unsafe) { true })
        assertNull(VideoDraftRecoveryPolicy.select(null,
            draft(2, "overlay_2_100.png")) { false })
    }

    @Test fun legacyUnversionedOverlayStillRecoverable() {
        val previous = draft(2, "overlay_2.png")
        assertEquals(previous, VideoDraftRecoveryPolicy.select(null, previous) { true })
    }
}

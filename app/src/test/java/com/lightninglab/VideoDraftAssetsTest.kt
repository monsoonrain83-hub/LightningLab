package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class VideoDraftAssetsTest {
    @Test fun revisionNamesCannotEscapePrivateFolder() {
        assertEquals("overlay_42_1234.png", VideoDraftAssets.revision(42L, 1234L))
        assertTrue(VideoDraftAssets.isSafe(42L, "overlay_42.png")) // r10.68 legacy
        assertTrue(VideoDraftAssets.isSafe(42L, "overlay_42_1234.png"))
        assertFalse(VideoDraftAssets.isSafe(42L, "../overlay_42_1234.png"))
        assertFalse(VideoDraftAssets.isSafe(42L, "overlay_43_1234.png"))
        assertFalse(VideoDraftAssets.isSafe(42L, "overlay_42_12.png.bak"))
    }

    @Test fun onlyUnreferencedRevisionsArePruned() {
        val files = listOf("overlay_42_10.png", "overlay_42_11.png", "overlay_7.png", "draft.json", "other.txt")
        assertEquals(listOf("overlay_42_10.png", "overlay_7.png"),
            VideoDraftAssets.obsolete(files, setOf("overlay_42_11.png")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeOverlayIdRejected() { VideoDraftAssets.revision(-1L, 1234L) }
}

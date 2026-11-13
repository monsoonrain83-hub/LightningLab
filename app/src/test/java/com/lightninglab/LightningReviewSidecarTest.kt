package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class LightningReviewSidecarTest {
    @Test fun eventKeyUsesTimeNotUnstableDetectorId() {
        assertEquals("100:250:130", LightningReviewKeys.event(100, 250, 130))
        assertEquals("100:250:130", LightningReviewKeys.event(100, 250, 130))
        assertFalse(LightningReviewKeys.event(100, 250, 130) == LightningReviewKeys.event(100, 251, 130))
    }

    @Test fun keyRejectsImpossibleIntervals() {
        try {
            LightningReviewKeys.event(120, 100, 115)
            fail("Invalid event range must fail")
        } catch (_: IllegalArgumentException) {}
        try {
            LightningReviewKeys.event(100, 200, 201)
            fail("Best frame outside event must fail")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun onlyKnownHumanReviewStatesAreAccepted() {
        assertEquals(LightningReview.CONFIRMED, LightningReviewKeys.decode("CONFIRMED"))
        assertEquals(LightningReview.REJECTED, LightningReviewKeys.decode("REJECTED"))
        assertEquals(LightningReview.UNCERTAIN, LightningReviewKeys.decode("UNCERTAIN"))
        assertNull(LightningReviewKeys.decode("fabricated"))
    }
}

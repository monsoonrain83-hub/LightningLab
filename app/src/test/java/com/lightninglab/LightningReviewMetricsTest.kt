package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class LightningReviewMetricsTest {
    @Test fun separatesCoverageFromAdjudicatedConfirmation() {
        val summary = LightningReviewMetrics.summarize(listOf(
            LightningReview.CONFIRMED, LightningReview.CONFIRMED,
            LightningReview.REJECTED, LightningReview.UNCERTAIN,
            LightningReview.UNREVIEWED, LightningReview.UNREVIEWED
        ))
        assertEquals(6, summary.total)
        assertEquals(4 * 100 / 6, summary.reviewCoveragePercent)
        assertEquals(3, summary.adjudicated)
        assertEquals(67, summary.confirmedAmongAdjudicatedPercent)
        assertTrue(summary.caveatFa.contains("دقت"))
    }

    @Test fun noReviewedDecisionsCannotHaveConfirmationRate() {
        val summary = LightningReviewMetrics.summarize(listOf(
            LightningReview.UNREVIEWED, LightningReview.UNCERTAIN))
        assertNull(summary.confirmedAmongAdjudicatedPercent)
        assertEquals(50, summary.reviewCoveragePercent)
    }

    @Test fun emptyDetectionHasDefinedZeroCoverage() {
        val summary = LightningReviewMetrics.summarize(emptyList())
        assertEquals(0, summary.reviewCoveragePercent)
        assertNull(summary.confirmedAmongAdjudicatedPercent)
    }

    @Test fun manyReviewedEventsUseLongMath() {
        val summary = LightningReviewMetrics.summarize(List(10001) {
            if (it < 5001) LightningReview.CONFIRMED else LightningReview.REJECTED
        })
        assertEquals(100, summary.reviewCoveragePercent)
        assertEquals(50, summary.confirmedAmongAdjudicatedPercent)
    }
}

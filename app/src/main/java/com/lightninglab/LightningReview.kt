package com.lightninglab

/** A human assessment; never silently changes the algorithm or machine-learning score. */
enum class LightningReview(val labelFa: String) {
    UNREVIEWED("بررسی‌نشده"),
    CONFIRMED("تأیید شد"),
    REJECTED("رد شد"),
    UNCERTAIN("نامشخص")
}

object LightningReviewSummary {
    fun count(reviews: Collection<LightningReview>): Map<LightningReview, Int> =
        LightningReview.values().associateWith { state -> reviews.count { it == state } }

    fun next(state: LightningReview): LightningReview = when (state) {
        LightningReview.UNREVIEWED -> LightningReview.CONFIRMED
        LightningReview.CONFIRMED -> LightningReview.REJECTED
        LightningReview.REJECTED -> LightningReview.UNCERTAIN
        LightningReview.UNCERTAIN -> LightningReview.UNREVIEWED
    }
}

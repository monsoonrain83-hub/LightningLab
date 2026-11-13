package com.lightninglab

/** Descriptive human-review statistics, NOT detector accuracy. */
object LightningReviewMetrics {
    data class Summary(
        val total: Int,
        val confirmed: Int,
        val rejected: Int,
        val uncertain: Int,
        val unreviewed: Int
    ) {
        val adjudicated: Int get() = confirmed + rejected
        val reviewCoveragePercent: Int get() =
            if (total == 0) 0 else ((total.toLong() - unreviewed) * 100L / total).toInt()
        val confirmedAmongAdjudicatedPercent: Int? get() =
            if (adjudicated == 0) null else ((confirmed.toLong() * 100L + adjudicated / 2L) / adjudicated).toInt()

        /**
         * Selection-biased reviewed set: neither precision over the full video
         * nor sensitivity/recall, because missed real flashes are unlabelled.
         */
        val caveatFa: String =
            "این نرخ فقط سهم تأیید در رخدادهای دارای رأی قطعی است؛ چون همهٔ فلاش‌ها و موارد جامانده برچسب نخورده‌اند، دقت یا حساسیت علمی آشکارساز نیست."
    }

    fun summarize(states: Collection<LightningReview>): Summary {
        val counts = LightningReviewSummary.count(states)
        return Summary(
            total = states.size,
            confirmed = counts[LightningReview.CONFIRMED] ?: 0,
            rejected = counts[LightningReview.REJECTED] ?: 0,
            uncertain = counts[LightningReview.UNCERTAIN] ?: 0,
            unreviewed = counts[LightningReview.UNREVIEWED] ?: 0
        )
    }
}

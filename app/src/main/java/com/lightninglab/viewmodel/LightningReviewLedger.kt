package com.lightninglab.viewmodel

import com.lightninglab.LightningReview

/**
 * Separates human-review bookkeeping from MainVM and all ML confidence
 * scores. Disk I/O still belongs to LightningReviewSidecar; this ledger owns
 * only the mutable, bounded snapshot and dirty/persisted transition.
 */
internal class LightningReviewLedger {
    private val records = LinkedHashMap<String, LightningReview>()
    var dirty: Boolean = false
        private set

    fun vote(key: String, decision: LightningReview) {
        require(Regex("[0-9]{1,19}:[0-9]{1,19}:[0-9]{1,19}").matches(key))
        if (decision == LightningReview.UNREVIEWED) records.remove(key)
        else records[key] = decision
        dirty = true
    }

    fun snapshot(): Map<String, LightningReview> = records.toMap()

    fun pendingSnapshot(): Map<String, LightningReview>? =
        if (dirty) snapshot() else null

    /** A late save must not mark later edits persisted. */
    fun markPersisted(saved: Map<String, LightningReview>): Boolean {
        if (records != saved) return false
        dirty = false
        return true
    }

    fun reset() {
        records.clear()
        dirty = false
    }

    /** Applies only after the corresponding source's scan has been accepted. */
    fun restore(saved: Map<String, LightningReview>) {
        records.clear()
        records.putAll(saved.filterValues { it != LightningReview.UNREVIEWED })
        dirty = false
    }
}

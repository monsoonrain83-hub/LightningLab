package com.lightninglab.viewmodel

import com.lightninglab.LightningReview
import org.junit.Assert.*
import org.junit.Test

class LightningReviewLedgerTest {
    private val key = "1:3:2"

    @Test fun votingAndClearingProduceDurableEmptySnapshots() {
        val ledger = LightningReviewLedger()
        ledger.vote(key, LightningReview.CONFIRMED)
        assertEquals(LightningReview.CONFIRMED, ledger.snapshot()[key])
        assertTrue(ledger.markPersisted(ledger.snapshot()))
        assertFalse(ledger.dirty)
        ledger.vote(key, LightningReview.UNREVIEWED)
        assertEquals(emptyMap<String, LightningReview>(), ledger.pendingSnapshot())
        assertTrue(ledger.dirty)
        assertTrue(ledger.markPersisted(emptyMap()))
        assertNull(ledger.pendingSnapshot())
    }

    @Test fun staleSaveNeverClearsPendingNewerEdits() {
        val ledger = LightningReviewLedger()
        ledger.vote(key, LightningReview.CONFIRMED)
        val old = ledger.snapshot()
        ledger.vote(key, LightningReview.REJECTED)
        assertFalse(ledger.markPersisted(old))
        assertTrue(ledger.dirty)
        assertEquals(LightningReview.REJECTED, ledger.snapshot()[key])
    }

    @Test fun restoringAndResettingNeverCreateFalseDirtyState() {
        val ledger = LightningReviewLedger()
        ledger.restore(mapOf(key to LightningReview.UNCERTAIN))
        assertFalse(ledger.dirty)
        assertEquals(LightningReview.UNCERTAIN, ledger.snapshot()[key])
        ledger.vote(key, LightningReview.REJECTED)
        ledger.reset()
        assertTrue(ledger.snapshot().isEmpty())
        assertNull(ledger.pendingSnapshot())
    }

    @Test(expected = IllegalArgumentException::class)
    fun maliciousOrUnknownEventKeysRejected() {
        LightningReviewLedger().vote("../../outside", LightningReview.CONFIRMED)
    }
}

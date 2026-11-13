package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class LightningScanProgressTest {
    @Test fun emitsOnlyMeaningfulIncreases() {
        val gate = LightningScanProgress()
        assertTrue(gate.shouldPublish(0f))
        assertFalse(gate.shouldPublish(0.005f))
        assertTrue(gate.shouldPublish(0.01f))
        assertFalse(gate.shouldPublish(0.009f))
        assertTrue(gate.shouldPublish(0.987f))
        assertTrue(gate.shouldPublish(1f))
        assertFalse(gate.shouldPublish(1f))
        assertEquals(100, gate.last())
    }

    @Test fun rejectsNonFiniteAndClampsOutOfRange() {
        val gate = LightningScanProgress()
        assertFalse(gate.shouldPublish(Float.NaN))
        assertFalse(gate.shouldPublish(Float.POSITIVE_INFINITY))
        assertTrue(gate.shouldPublish(-8f))
        assertEquals(0, gate.last())
        assertTrue(gate.shouldPublish(4f))
        assertEquals(100, gate.last())
    }

    @Test fun differentScansUseIndependentGates() {
        val old = LightningScanProgress()
        assertTrue(old.shouldPublish(1f))
        val newer = LightningScanProgress()
        assertTrue(newer.shouldPublish(0f))
        assertEquals(0, newer.last())
    }

    @Test fun concurrentCallbacksCannotRegressPercent() {
        val gate = LightningScanProgress()
        val threads = (0..8).map { threadIndex ->
            Thread {
                for (i in 0..100) gate.shouldPublish((i + threadIndex).toFloat() / 100f)
            }.also { it.start() }
        }
        threads.forEach { it.join() }
        assertEquals(100, gate.last())
    }
}

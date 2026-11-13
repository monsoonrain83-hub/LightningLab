package com.lightninglab

import java.util.concurrent.atomic.AtomicInteger

/**
 * De-duplicates noisy worker-thread progress callbacks before delivering
 * updates to Compose's main thread. No UI state is touched here.
 */
internal class LightningScanProgress {
    private val lastPercent = AtomicInteger(-1)

    fun shouldPublish(value: Float): Boolean {
        if (!value.isFinite()) return false
        val percent = (value.coerceIn(0f, 1f) * 100f).toInt()
        while (true) {
            val previous = lastPercent.get()
            if (percent <= previous) return false
            if (lastPercent.compareAndSet(previous, percent)) return true
        }
    }

    fun last(): Int = lastPercent.get()
}

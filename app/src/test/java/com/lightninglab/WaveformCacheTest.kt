package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaveformCacheTest {
    @Test fun leastRecentlyUsedEnvelopeIsEvicted() {
        val cache = WaveformCache(2)
        cache.put("music", listOf(0.1f, 0.5f))
        cache.put("voice", listOf(0.2f, 0.8f))
        assertEquals(listOf(0.1f, 0.5f), cache.get("music"))
        cache.put("new", listOf(1f))
        assertNull(cache.get("voice"))
        assertEquals(listOf(0.1f, 0.5f), cache.get("music"))
    }

    @Test fun cacheStoresAnImmutableSnapshot() {
        val cache = WaveformCache(1)
        val source = mutableListOf(0.2f, 0.4f)
        cache.put("a", source)
        source[0] = 1f
        assertEquals(listOf(0.2f, 0.4f), cache.get("a"))
    }
}

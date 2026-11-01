package com.lightninglab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineMathTest {
    private val keys = listOf(ZoomKey(1, 1000, 1f), ZoomKey(2, 3000, 2f), ZoomKey(3, 5000, 1f))
    @Test fun noKeysMeansNoZoom() = assertEquals(1f, TimelineMath.zoomAt(emptyList(), 500), 1e-6f)
    @Test fun zoomHoldsOutsideAndPeaksAtMarker() {
        assertEquals(1f, TimelineMath.zoomAt(keys, 0), 1e-6f)
        assertEquals(2f, TimelineMath.zoomAt(keys, 3000), 1e-6f)
        assertEquals(1f, TimelineMath.zoomAt(keys, 9000), 1e-6f)
        assertEquals(1.5f, TimelineMath.zoomAt(keys, 2000), 1e-4f)   // smoothstep midpoint
        assertTrue(TimelineMath.zoomAt(keys, 4000) in 1.4f..1.6f)
    }
    @Test fun unsortedKeysAreHandled() = assertEquals(2f, TimelineMath.zoomAt(keys.reversed(), 3000), 1e-6f)
    @Test fun clampKeepsClipInsideTimeline() {
        val r = TimelineMath.clampRange(9000, 12000, 10000); assertEquals(7000L, r[0]); assertEquals(10000L, r[1])
        val s = TimelineMath.clampRange(-500, 100, 10000); assertEquals(0L, s[0]); assertEquals(600L, s[1])
    }
    @Test fun locateAndOutPositionAreInverse() {
        val segs = listOf(2000L, 1000L, 4000L)
        assertEquals(2 to 500L, TimelineMath.locate(segs, 1f, 3500))
        assertEquals(1 to 0L, TimelineMath.locate(segs, 2f, 1000))
        assertEquals(3500L, TimelineMath.outPosition(segs, 1f, 2, 500))
        assertEquals(1000L, TimelineMath.outPosition(segs, 2f, 1, 0))
    }
    @Test fun splitNeedsRoomOnBothSides() {
        assertNull(TimelineMath.split(0, 1000, 100))
        assertNull(TimelineMath.split(0, 1000, 900))
        val p = TimelineMath.split(0, 1000, 400)!!
        assertEquals(400L, p.first[1]); assertEquals(400L, p.second[0]); assertEquals(1000L, p.second[1])
    }
}

package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class LumaGridGeometryTest {
    @Test fun allOddWidthColumnsAreRepresentedExactlyOnce() {
        val spans = (0 until 320).map { LumaGridGeometry.span(it, 320, 403) }
        assertEquals(0, spans.first().start)
        assertEquals(403, spans.last().endExclusive)
        assertEquals((0 until 403).toList(), spans.flatMap { it.start until it.endExclusive })
    }

    @Test fun verticalPartitionsCoverBottomRow() {
        val spans = (0 until 91).map { LumaGridGeometry.span(it, 91, 117) }
        assertEquals(117, spans.last().endExclusive)
        assertEquals((0 until 117).toList(), spans.flatMap { it.start until it.endExclusive })
    }

    @Test fun panoramaAndNarrowFrameNeverOverrunHeight() {
        assertEquals(1, LumaGridGeometry.targetHeight(403, 1, 320))
        assertEquals(15, LumaGridGeometry.targetHeight(400, 20, 300))
        assertEquals(LumaGridGeometry.Dims(53, 320), LumaGridGeometry.targetDims(320, 1920, 320))
        assertEquals(LumaGridGeometry.Dims(320, 53), LumaGridGeometry.targetDims(1920, 320, 320))
        assertEquals(LumaGridGeometry.Dims(320, 320), LumaGridGeometry.targetDims(800, 800, 320))
    }

    @Test fun invalidGeometryFailsFast() {
        try {
            LumaGridGeometry.span(320, 320, 403)
            fail("out of bounds")
        } catch (_: IllegalArgumentException) {}
        try {
            LumaGridGeometry.targetHeight(0, 20, 10)
            fail("invalid width")
        } catch (_: IllegalArgumentException) {}
    }
}

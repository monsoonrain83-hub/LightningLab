package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudAnalysisTest {
    private val w = 100; private val h = 100
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun clearBlueSkyIsZeroOktas() {
        val r = CloudAnalysis.analyze(IntArray(w * h) { rgb(100, 150, 230) }, w, h)
        assertEquals(0, r.oktas); assertEquals(CloudAnalysis.Kind.CLEAR, r.kind)
    }

    @Test fun flatGreyIsOvercastUniformLayer() {
        val r = CloudAnalysis.analyze(IntArray(w * h) { rgb(140, 140, 140) }, w, h)
        assertEquals(8, r.oktas); assertEquals(CloudAnalysis.Kind.UNIFORM_LAYER, r.kind)
    }

    @Test fun halfAndHalfIsFourOktas() {
        val px = IntArray(w * h) { if (it % w < w / 2) rgb(100, 150, 230) else rgb(200, 200, 200) }
        assertEquals(4, CloudAnalysis.analyze(px, w, h).oktas)
    }

    @Test fun veryDarkGreyIsDarkOvercast() {
        val r = CloudAnalysis.analyze(IntArray(w * h) { rgb(70, 70, 70) }, w, h)
        assertEquals(CloudAnalysis.Kind.DARK_OVERCAST, r.kind)
    }
}

package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudSynopticClassifierTest {
    @Test fun decodesThreeIndependentSoftmaxHeads() {
        val values = FloatArray(33)
        for (head in 0..2) values[head * 11 + head + 1] = 1f
        val result = CloudSynopticClassifier.decodeOutput(values)!!
        assertEquals("1", result.cl.first().first)
        assertEquals("2", result.cm.first().first)
        assertEquals("3", result.ch.first().first)
    }

    @Test fun rejectsWrongLengthInvalidValuesAndMalformedHeadSums() {
        assertNull(CloudSynopticClassifier.decodeOutput(FloatArray(32)))
        assertNull(CloudSynopticClassifier.decodeOutput(FloatArray(33) { if (it == 0) Float.NaN else 0f }))
        assertNull(CloudSynopticClassifier.decodeOutput(FloatArray(33)))
    }

    @Test fun slashCodeIsKeptAsNotDeterminable() {
        val values = FloatArray(33)
        for (head in 0..2) values[head * 11 + 10] = 1f
        val result = CloudSynopticClassifier.decodeOutput(values)!!
        assertEquals("/", result.cl.first().first)
        assertEquals("قابل تعیین نیست", CloudSynopticInfo.label("CL", "/"))
    }
}

package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeProcessorTest {
    @Test fun fadeInIsLinearAndReachesUnity() {
        assertEquals(0f, Gain.envelope(1f, 0L, 10_000L, 1_000L, 0L), 0.0001f)
        assertEquals(0.5f, Gain.envelope(1f, 500L, 10_000L, 1_000L, 0L), 0.0001f)
        assertEquals(1f, Gain.envelope(1f, 1_000L, 10_000L, 1_000L, 0L), 0.0001f)
    }

    @Test fun fadeOutIsLinearAndEndsAtSilence() {
        assertEquals(1f, Gain.envelope(1f, 8_000L, 10_000L, 0L, 2_000L), 0.0001f)
        assertEquals(0.5f, Gain.envelope(1f, 9_000L, 10_000L, 0L, 2_000L), 0.0001f)
        assertEquals(0f, Gain.envelope(1f, 10_000L, 10_000L, 0L, 2_000L), 0.0001f)
    }

    @Test fun overlappingFadesUseTheLowerEnvelopeAndRespectTrackGain() {
        assertEquals(0.4f, Gain.envelope(0.8f, 500L, 1_000L, 1_000L, 1_000L), 0.0001f)
    }

    @Test fun gainIsClampedAndNoFadePreservesVolume() {
        assertEquals(2f, Gain.envelope(3f, 500L, 1_000L, 0L, 0L), 0.0001f)
        assertEquals(0.75f, Gain.envelope(0.75f, 500L, 1_000L, 0L, 0L), 0.0001f)
    }
}

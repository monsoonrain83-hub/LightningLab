package com.lightninglab

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class SoundFxTest {
    @Test fun everyEffectIsAudibleBoundedAndNonTrivialInLength() {
        for (k in SoundFx.Kind.values()) {
            val p = SoundFx.render(k)
            assertTrue("$k length", p.size > SoundFx.RATE / 2)
            val peak = p.maxOf { abs(it.toInt()) }
            assertTrue("$k peak $peak", peak in 20000..32767)
            val rms = sqrt(p.map { it.toDouble() * it }.average())
            assertTrue("$k rms $rms", rms > 500.0)
        }
    }

    @Test fun renderingIsDeterministic() {
        assertArrayEquals(SoundFx.render(SoundFx.Kind.RUMBLE), SoundFx.render(SoundFx.Kind.RUMBLE))
    }

    @Test fun endsWithoutAClick() {
        for (k in SoundFx.Kind.values()) assertTrue("$k tail", abs(SoundFx.render(k).last().toInt()) < 50)
    }
}

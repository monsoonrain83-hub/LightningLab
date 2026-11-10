package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubRipTest {
    @Test fun parsesCommaAndDotMillisecondSeparators() {
        val cues = SubRip.parse(
            "1\\n00:00:01,250 --> 00:00:02,500\\nFirst line\\n\\n" +
            "2\\n00:00:03.5 --> 00:00:04.25\\nSecond line"
        )
        assertEquals(2, cues.size)
        assertEquals(1_250L, cues[0].startMs)
        assertEquals(2_500L, cues[0].endMs)
        assertEquals(3_500L, cues[1].startMs)
        assertEquals(4_250L, cues[1].endMs)
    }

    @Test fun preservesMultilineTextAndDecodesBasicEntities() {
        val cue = SubRip.parse("00:00:00,000 --> 00:00:02,000\\n<i>Hello &amp;</i>\\nworld &lt;sky&gt;").single()
        assertEquals("Hello &\\nworld <sky>", cue.text)
    }

    @Test fun ignoresMalformedAndZeroLengthBlocks() {
        val cues = SubRip.parse(
            "bad\\nnot a timestamp\\nignored\\n\\n" +
            "00:00:02,000 --> 00:00:02,000\\nzero\\n\\n" +
            "00:99:00,000 --> 00:99:01,000\\ninvalid minute"
        )
        assertTrue(cues.isEmpty())
    }

    @Test fun sortsCuesByStartTime() {
        val cues = SubRip.parse(
            "00:00:03,000 --> 00:00:04,000\\nB\\n\\n" +
            "00:00:01,000 --> 00:00:02,000\\nA"
        )
        assertEquals(listOf("A", "B"), cues.map { it.text })
    }

    @Test fun supportsBomAndCueSettings() {
        val cue = SubRip.parse("\\uFEFF1\\n00:00:00,100 --> 00:00:01,100 align:start\\nCaption").single()
        assertEquals(100L, cue.startMs)
        assertEquals("Caption", cue.text)
    }
}

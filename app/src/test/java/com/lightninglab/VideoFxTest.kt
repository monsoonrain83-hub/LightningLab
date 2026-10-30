package com.lightninglab

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoFxTest {
    @Test fun squareCropOfLandscapeNarrowsHorizontally() {
        assertArrayEquals(floatArrayOf(-0.5625f, 0.5625f, -1f, 1f), VideoFx.cropBounds(1920, 1080, 1f)!!, 0.001f)
    }

    @Test fun landscapeCropOfPortraitNarrowsVertically() {
        assertArrayEquals(floatArrayOf(-1f, 1f, -0.3164f, 0.3164f), VideoFx.cropBounds(1080, 1920, 16f / 9f)!!, 0.001f)
    }

    @Test fun sameAspectOrBadInputMeansNoCrop() {
        assertNull(VideoFx.cropBounds(1920, 1080, 16f / 9f))
        assertNull(VideoFx.cropBounds(0, 1080, 1f))
        assertNull(VideoFx.cropBounds(1920, 1080, 0f))
    }
}

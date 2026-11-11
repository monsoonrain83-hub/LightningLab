package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoProjectCodecTest {
    @Test fun roundTripPreservesAllEditablePhotoState() {
        val params = PhotoParams(exposure = .7f, brightness = -.1f, rotate = 90, aspect = 1f,
            highlights = -.3f, shadows = .4f, sharpness = .2f, vibrance = .15f, hsl = List(21) { it / 20f },
            rgbCurves = PhotoToneCurves.identity().toMutableList().also { it[2] = .7f; it[8] = .6f; it[14] = .8f })
        val data = PhotoProjectData("Storm cover", "source.png", params,
            listOf(PhotoTextData(1, "Headline", "بارش شدید", .5f, .2f, .09f, -1, .9f, true, true, 3, -27.5f)),
            listOf(PhotoShapeData(2, "Frame", "RECTANGLE", .5f, .5f, .8f, .6f, -1, .7f, .02f, true, 1, 13f)),
            listOf(PhotoImageData(3, "Logo", "layer_3.png", .8f, .8f, .2f, .95f, true, 2, 91f)),
            listOf(PhotoMaskData(listOf(.1f to .2f, .3f to .4f), .04f, .6f)), true)
        assertEquals(data, PhotoProjectCodec.decode(PhotoProjectCodec.encode(data)))
    }

    @Test fun corruptOrUnsupportedManifestIsRejected() {
        assertNull(PhotoProjectCodec.decode("broken"))
        assertNull(PhotoProjectCodec.decode("{\"v\":99}"))
    }
}

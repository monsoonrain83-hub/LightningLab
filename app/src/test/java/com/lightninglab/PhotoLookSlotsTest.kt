package com.lightninglab

import org.junit.Assert.*
import org.junit.Test

class PhotoLookSlotsTest {
    private val values = (0 until PhotoLookSlots.VALUES).map { it.toFloat() / 10f }

    @Test fun legacyFirstSlotRemainsBackwardCompatible() {
        assertEquals("photo_preset", PhotoLookSlots.key(1))
        assertEquals("photo_preset_slot_2", PhotoLookSlots.key(2))
        assertEquals("photo_preset_slot_3", PhotoLookSlots.key(3))
        assertEquals(values, PhotoLookSlots.decode(PhotoLookSlots.encode(values)))
    }

    @Test fun corruptOrNonFinitePresetsCannotPoisonEditor() {
        assertNull(PhotoLookSlots.decode("1,2,3"))
        assertNull(PhotoLookSlots.decode("garbled"))
        assertNull(PhotoLookSlots.decode(null))
        assertNull(PhotoLookSlots.decode(List(11) { "NaN" }.joinToString(",")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSlotRejected() { PhotoLookSlots.key(0) }
}

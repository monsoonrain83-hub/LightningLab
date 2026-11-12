package com.lightninglab

/** Lightweight user-editable photo look slots; legacy slot 1 retains its original key. */
object PhotoLookSlots {
    const val COUNT = 3
    const val VALUES = 11

    fun key(slot: Int): String {
        require(slot in 1..COUNT) { "Photo look slot must be between 1 and $COUNT" }
        return if (slot == 1) "photo_preset" else "photo_preset_slot_$slot"
    }

    fun encode(values: List<Float>): String {
        require(values.size == VALUES && values.all { it.isFinite() }) { "Invalid look" }
        return values.joinToString(",")
    }

    fun decode(csv: String?): List<Float>? {
        val list = csv?.split(",")?.map { it.toFloatOrNull() ?: return null } ?: return null
        return list.takeIf { it.size == VALUES && it.all { value -> value.isFinite() } }
    }
}

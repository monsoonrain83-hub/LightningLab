package com.lightninglab

/** Audio track inclusion policy shared by export and unit tests. */
object AudioTrackRouting {
    const val NONE = "NONE"
    const val ORIGINAL = "ORIGINAL"
    const val MUSIC = "MUSIC"
    const val VOICE = "VOICE"

    fun normalize(value: String): String = when (value) {
        ORIGINAL, MUSIC, VOICE -> value
        else -> NONE
    }

    fun include(track: String, muted: Boolean, solo: String): Boolean {
        val selectedSolo = normalize(solo)
        return !muted && (selectedSolo == NONE || selectedSolo == track)
    }
}

package com.lightninglab

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTrackRoutingTest {
    @Test fun noSoloKeepsAllUnmutedTracks() {
        assertTrue(AudioTrackRouting.include(AudioTrackRouting.ORIGINAL, false, AudioTrackRouting.NONE))
        assertTrue(AudioTrackRouting.include(AudioTrackRouting.MUSIC, false, AudioTrackRouting.NONE))
        assertTrue(AudioTrackRouting.include(AudioTrackRouting.VOICE, false, AudioTrackRouting.NONE))
    }

    @Test fun soloKeepsOnlyTheSelectedTrack() {
        assertTrue(AudioTrackRouting.include(AudioTrackRouting.MUSIC, false, AudioTrackRouting.MUSIC))
        assertFalse(AudioTrackRouting.include(AudioTrackRouting.ORIGINAL, false, AudioTrackRouting.MUSIC))
        assertFalse(AudioTrackRouting.include(AudioTrackRouting.VOICE, false, AudioTrackRouting.MUSIC))
    }

    @Test fun muteOverridesSoloAndUnknownSoloFallsBackToAll() {
        assertFalse(AudioTrackRouting.include(AudioTrackRouting.MUSIC, true, AudioTrackRouting.MUSIC))
        assertTrue(AudioTrackRouting.include(AudioTrackRouting.VOICE, false, "INVALID"))
    }
}

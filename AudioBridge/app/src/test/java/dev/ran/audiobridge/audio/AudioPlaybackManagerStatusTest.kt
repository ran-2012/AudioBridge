package dev.ran.audiobridge.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AudioPlaybackManagerStatusTest {
    @Test
    fun createPlaybackStatus_shouldExposeProgressWhenTrackIsNotConfigured() {
        val manager = AudioPlaybackManager()

        val status = manager.createPlaybackStatus(
            sequence = 2u,
            lastSequence = 10u,
            lastAudioFrameAgeMillis = 150,
            timestampElapsedRealtimeMillis = 500,
        )

        assertEquals(2u, status.sequence)
        assertFalse(status.isPlaying)
        assertEquals(10u, status.lastSequence)
        assertEquals(150L, status.lastAudioFrameAgeMillis)
        assertNull(status.bufferedLatencyMillis)
        assertEquals(500L, status.timestampElapsedRealtimeMillis)
    }
}
package dev.ran.audiobridge.network

import dev.ran.audiobridge.model.AndroidPlaybackStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPlaybackStatusJsonCodecTest {
    @Test
    fun buildStatus_shouldEncodePlaybackStateAndProgress() {
        val json = AndroidPlaybackStatusJsonCodec.buildStatus(
            AndroidPlaybackStatus(
                sequence = 3u,
                isPlaying = true,
                lastSequence = 42u,
                lastAudioFrameAgeMillis = 120,
                bufferedLatencyMillis = null,
                timestampElapsedRealtimeMillis = 999,
            ),
        )

        val root = JSONObject(json)
        assertEquals(3L, root.getLong("sequence"))
        assertTrue(root.getBoolean("isPlaying"))
        assertEquals(42L, root.getLong("lastSequence"))
        assertEquals(120L, root.getLong("lastAudioFrameAgeMillis"))
        assertTrue(root.isNull("bufferedLatencyMillis"))
        assertEquals(999L, root.getLong("timestampElapsedRealtimeMillis"))
    }

    @Test
    fun parseAck_shouldParseNullableEchoedTimestamp() {
        val ack = AndroidPlaybackStatusJsonCodec.parseAck(
            """
                {
                  "sequence": 4,
                  "accepted": false,
                  "receivedAtMillis": 88,
                  "echoedTimestampElapsedRealtimeMillis": null
                }
            """.trimIndent(),
        )

        assertEquals(4u, ack.sequence)
        assertFalse(ack.accepted)
        assertEquals(88L, ack.receivedAtMillis)
        assertNull(ack.echoedTimestampElapsedRealtimeMillis)
    }
}
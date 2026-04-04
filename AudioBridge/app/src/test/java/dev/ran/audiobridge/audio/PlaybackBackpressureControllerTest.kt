package dev.ran.audiobridge.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBackpressureControllerTest {
    @Test
    fun normalize_shouldClampAndSnapToStep() {
        assertEquals(40, PlaybackCacheConfig.normalize(1))
        assertEquals(120, PlaybackCacheConfig.normalize(121))
        assertEquals(400, PlaybackCacheConfig.normalize(999))
    }

    @Test
    fun shouldDropStalePacket_whenPacketAgeExceedsTarget() {
        val controller = PlaybackBackpressureController(
            targetBufferMilliseconds = 120,
        )

        val packetTimestampMillis = 1_000L
        controller.shouldDropStalePacket(packetTimestampMillis = packetTimestampMillis, nowMillis = 1_000L)

        assertTrue(controller.shouldDropStalePacket(packetTimestampMillis = packetTimestampMillis, nowMillis = 1_121L))
    }

    @Test
    fun shouldNotDropFreshPacket_afterClockAligned() {
        val controller = PlaybackBackpressureController(
            targetBufferMilliseconds = 120,
        )

        assertFalse(controller.shouldDropStalePacket(packetTimestampMillis = 5_000L, nowMillis = 5_020L))
        assertFalse(controller.shouldDropStalePacket(packetTimestampMillis = 5_040L, nowMillis = 5_080L))
    }

    @Test
    fun updateTargetBufferMilliseconds_shouldDelayDropThreshold() {
        val controller = PlaybackBackpressureController(
            targetBufferMilliseconds = 40,
        )

        val packetTimestampMillis = 10_000L
        controller.shouldDropStalePacket(packetTimestampMillis = packetTimestampMillis, nowMillis = 10_000L)
        assertTrue(controller.shouldDropStalePacket(packetTimestampMillis = packetTimestampMillis, nowMillis = 10_041L))

        controller.updateTargetBufferMilliseconds(200)

        assertFalse(controller.shouldDropStalePacket(packetTimestampMillis = packetTimestampMillis, nowMillis = 10_041L))
    }
}
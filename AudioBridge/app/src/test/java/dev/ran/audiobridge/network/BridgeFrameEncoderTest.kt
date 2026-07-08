package dev.ran.audiobridge.network

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class BridgeFrameEncoderTest {
    @Test
    fun encodeHeartbeatAck_shouldProduceValidEmptyPayloadHeader() {
        val header = BridgeFrameEncoder.encodeHeartbeatAck()

        assertEquals(12, header.size)

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x57414231, buffer.int)
        assertEquals(1, buffer.short.toInt())
        assertEquals(0x18, buffer.short.toInt() and 0xFFFF)
        assertEquals(0, buffer.int)
    }

    @Test
    fun encodeHeader_shouldEncodeMessageTypeAndPayloadSize() {
        val header = BridgeFrameEncoder.encodeHeader(BridgeMessageType.VOLUME_CATALOG_REQUEST, 42)

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x57414231, buffer.int)
        assertEquals(1, buffer.short.toInt())
        assertEquals(BridgeMessageType.VOLUME_CATALOG_REQUEST, buffer.short.toInt() and 0xFFFF)
        assertEquals(42, buffer.int)
    }

    @Test
    fun encodeAndroidPlaybackStatus_shouldProduceHeaderAndPayload() {
        val payload = "{}".toByteArray(Charsets.UTF_8)
        val packet = BridgeFrameEncoder.encodeAndroidPlaybackStatus(payload)

        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x57414231, buffer.int)
        assertEquals(1, buffer.short.toInt())
        assertEquals(BridgeMessageType.ANDROID_PLAYBACK_STATUS, buffer.short.toInt() and 0xFFFF)
        assertEquals(payload.size, buffer.int)
        assertEquals('{'.code.toByte(), packet[BridgeFrameEncoder.HEADER_LENGTH])
    }
}

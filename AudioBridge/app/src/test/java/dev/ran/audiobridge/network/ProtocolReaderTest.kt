package dev.ran.audiobridge.network

import dev.ran.audiobridge.model.BridgePacket
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolReaderTest {
    private val reader = ProtocolReader()

    @Test
    fun readPacket_shouldParseSessionInit() {
        val payload = ByteBuffer.allocate(14)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(2)
            .putInt(48_000)
            .putShort(2)
            .putShort(16)
            .putInt(20)
            .array()

        val packet = reader.readPacket(ByteArrayInputStream(createPacket(BridgeMessageType.SESSION_INIT, payload)))

        val session = packet as BridgePacket.SessionInit
        assertEquals(2, session.encodingCode)
        assertEquals(48_000, session.sampleRate)
        assertEquals(2, session.channels)
        assertEquals(16, session.bitsPerSample)
        assertEquals(20, session.bufferMilliseconds)
    }

    @Test
    fun readPacket_shouldParseAudioFrame() {
        val audioBytes = byteArrayOf(1, 2, 3, 4)
        val payload = ByteBuffer.allocate(12 + audioBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(42)
            .putLong(123456789L)
            .put(audioBytes)
            .array()

        val packet = reader.readPacket(ByteArrayInputStream(createPacket(BridgeMessageType.AUDIO_FRAME, payload)))

        val frame = packet as BridgePacket.AudioFrame
        assertEquals(42u, frame.sequence)
        assertEquals(123456789L, frame.timestampMillis)
        assertArrayEquals(audioBytes, frame.audioData)
    }

    @Test
    fun readPacket_shouldParseVolumeCatalogSnapshot() {
        val payload = """
            {
              "requestId": 5,
              "catalog": {
                "capturedAtMillis": 10,
                "masterVolume": { "deviceName": "扬声器", "volume": 0.6, "isMuted": false },
                "sessions": []
              }
            }
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val packet = reader.readPacket(ByteArrayInputStream(createPacket(BridgeMessageType.VOLUME_CATALOG_SNAPSHOT, payload)))

        val snapshot = packet as BridgePacket.VolumeCatalogSnapshot
        assertEquals(5u, snapshot.catalog.requestId)
        assertEquals("扬声器", snapshot.catalog.masterVolume.deviceName)
        assertEquals(0.6f, snapshot.catalog.masterVolume.volume)
    }

    @Test
    fun readPacket_shouldParseCommandAck() {
        val payload = """
            {
              "requestId": 12,
              "success": true,
              "message": "done",
              "masterVolume": { "deviceName": "耳机", "volume": 0.8, "isMuted": true }
            }
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val packet = reader.readPacket(ByteArrayInputStream(createPacket(BridgeMessageType.COMMAND_ACK, payload)))

        val ack = packet as BridgePacket.CommandAck
        assertTrue(ack.ack.success)
        assertEquals(12u, ack.ack.requestId)
        assertEquals("done", ack.ack.message)
        assertEquals("耳机", ack.ack.masterVolume?.deviceName)
        assertEquals(0.8f, ack.ack.masterVolume?.volume)
    }

    @Test
    fun readPacket_shouldRejectInvalidMagic() {
        val bytes = createPacket(BridgeMessageType.COMMAND_ACK, ByteArray(0)).clone()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(0x12345678)

        val error = assertThrows(IllegalArgumentException::class.java) {
            reader.readPacket(ByteArrayInputStream(bytes))
        }

        assertTrue(error.message.orEmpty().contains("Magic"))
    }

    private fun createPacket(messageType: Int, payload: ByteArray): ByteArray {
        return ByteBuffer.allocate(12 + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x57414231u.toInt())
            .putShort(1)
            .putShort(messageType.toShort())
            .putInt(payload.size)
            .put(payload)
            .array()
    }
}

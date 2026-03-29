package dev.ran.audiobridge.network

import dev.ran.audiobridge.model.BridgePacket
import java.nio.charset.StandardCharsets
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ProtocolReader {
    companion object {
        private const val MAGIC: UInt = 0x57414231u
        private const val HEADER_LENGTH = 12
        private const val MAX_PAYLOAD_LENGTH = 1024 * 1024
    }

    fun readPacket(inputStream: InputStream): BridgePacket {
        val header = readFully(inputStream, HEADER_LENGTH)
        val headerBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        val magic = headerBuffer.int.toUInt()
        require(magic == MAGIC) { "协议 Magic 不匹配: $magic" }

        val version = headerBuffer.short.toInt() and 0xFFFF
        require(version == 1) { "不支持的协议版本: $version" }

        val messageType = headerBuffer.short.toInt() and 0xFFFF
        val payloadLength = headerBuffer.int
        require(payloadLength in 0..MAX_PAYLOAD_LENGTH) { "非法负载长度: $payloadLength" }

        val payload = readFully(inputStream, payloadLength)
        val payloadBuffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val payloadText = if (payload.isNotEmpty()) String(payload, StandardCharsets.UTF_8) else ""

        return when (messageType) {
            BridgeMessageType.SESSION_INIT -> BridgePacket.SessionInit(
                encodingCode = payloadBuffer.short.toInt() and 0xFFFF,
                sampleRate = payloadBuffer.int,
                channels = payloadBuffer.short.toInt() and 0xFFFF,
                bitsPerSample = payloadBuffer.short.toInt() and 0xFFFF,
                bufferMilliseconds = payloadBuffer.int,
            )
            BridgeMessageType.AUDIO_FRAME -> {
                val sequence = payloadBuffer.int.toUInt()
                val timestamp = payloadBuffer.long
                val audioData = ByteArray(payloadLength - 12)
                payloadBuffer.get(audioData)
                BridgePacket.AudioFrame(sequence, timestamp, audioData)
            }
            BridgeMessageType.VOLUME_CATALOG_SNAPSHOT -> {
                BridgePacket.VolumeCatalogSnapshot(WindowsVolumeJsonCodec.parseCatalogSnapshot(payloadText))
            }
            BridgeMessageType.VOLUME_SESSION_DELTA -> {
                val delta = WindowsVolumeJsonCodec.parseDelta(payloadText)
                BridgePacket.VolumeSessionDelta(
                    deltaType = delta.first,
                    masterVolume = delta.second,
                    session = delta.third,
                    removedSessionId = WindowsVolumeJsonCodec.parseRemovedSessionId(payloadText),
                )
            }
            BridgeMessageType.COMMAND_ACK -> {
                BridgePacket.CommandAck(WindowsVolumeJsonCodec.parseCommandAck(payloadText))
            }
            else -> error("暂不支持的消息类型: $messageType")
        }
    }

    private fun readFully(inputStream: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = inputStream.read(buffer, offset, length - offset)
            if (count < 0) {
                error("连接已断开，未能读取完整数据包")
            }
            offset += count
        }
        return buffer
    }
}

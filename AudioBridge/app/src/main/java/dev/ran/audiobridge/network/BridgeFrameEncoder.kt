package dev.ran.audiobridge.network

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 协议帧编码工具：构建 12 字节协议头（Magic + Version + MessageType + PayloadLength）。
 * 抽取为纯函数以便单元测试，供出站消息（心跳 ACK、音量控制等）复用。
 */
object BridgeFrameEncoder {
    const val MAGIC: Int = 0x57414231
    const val VERSION: Short = 1
    const val HEADER_LENGTH: Int = 12

    fun encodeHeader(messageType: Int, payloadSize: Int): ByteArray {
        require(payloadSize >= 0) { "负载长度不能为负：$payloadSize" }
        return ByteBuffer.allocate(HEADER_LENGTH)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(MAGIC)
            .putShort(VERSION)
            .putShort(messageType.toShort())
            .putInt(payloadSize)
            .array()
    }

    fun encodeHeartbeatAck(): ByteArray = encodeHeader(BridgeMessageType.HEARTBEAT_ACK, 0)

    fun encodePacket(messageType: Int, payload: ByteArray): ByteArray {
        val header = encodeHeader(messageType, payload.size)
        return ByteArray(header.size + payload.size).also { packet ->
            System.arraycopy(header, 0, packet, 0, header.size)
            System.arraycopy(payload, 0, packet, header.size, payload.size)
        }
    }

    fun encodeAndroidPlaybackStatus(payload: ByteArray): ByteArray =
        encodePacket(BridgeMessageType.ANDROID_PLAYBACK_STATUS, payload)
}

package dev.ran.audiobridge.model

sealed interface BridgePacket {
    data class SessionInit(
        val encodingCode: Int,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val bufferMilliseconds: Int,
    ) : BridgePacket

    data class AudioFrame(
        val sequence: UInt,
        val timestampMillis: Long,
        val audioData: ByteArray,
    ) : BridgePacket

    data class VolumeCatalogSnapshot(
        val catalog: WindowsVolumeCatalog,
    ) : BridgePacket

    data class VolumeSessionDelta(
        val deltaType: String,
        val masterVolume: WindowsMasterVolume?,
        val session: WindowsAppVolumeSession?,
        val removedSessionId: String?,
    ) : BridgePacket

    data class CommandAck(
        val ack: WindowsCommandAck,
    ) : BridgePacket
}

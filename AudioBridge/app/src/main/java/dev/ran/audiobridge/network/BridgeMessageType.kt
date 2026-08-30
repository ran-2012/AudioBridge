package dev.ran.audiobridge.network

object BridgeMessageType {
    const val SESSION_INIT = 0x01
    const val AUDIO_FRAME = 0x02
    const val HEARTBEAT = 0x03
    const val STATUS = 0x04
    const val STOP = 0x05
    const val VOLUME_CATALOG_REQUEST = 0x10
    const val VOLUME_CATALOG_SNAPSHOT = 0x11
    const val VOLUME_SET_MASTER_REQUEST = 0x12
    const val VOLUME_SET_SESSION_REQUEST = 0x13
    const val VOLUME_SESSION_DELTA = 0x14
    const val ICON_CONTENT_REQUEST = 0x15
    const val ICON_CONTENT_RESPONSE = 0x16
    const val COMMAND_ACK = 0x17
    const val HEARTBEAT_ACK = 0x18
    const val ANDROID_PLAYBACK_STATUS = 0x19
    const val ANDROID_PLAYBACK_STATUS_ACK = 0x1A
    const val LATENCY_PROBE = 0x1B
    const val LATENCY_PROBE_ACK = 0x1C
}
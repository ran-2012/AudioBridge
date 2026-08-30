package dev.ran.audiobridge.model

/** 局域网内发现到的 Windows 服务器信息。 */
data class LanServerInfo(
    val name: String,
    val host: String,
    val port: Int,
    val lastSeenMillis: Long,
)

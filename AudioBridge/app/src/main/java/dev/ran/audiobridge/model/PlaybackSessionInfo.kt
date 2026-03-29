package dev.ran.audiobridge.model

data class PlaybackSessionInfo(
    val encoding: String = "-",
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val bitsPerSample: Int = 0,
    val bufferMilliseconds: Int = 0,
)

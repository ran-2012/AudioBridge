package dev.ran.audiobridge.model

data class AndroidPlaybackStatus(
    val sequence: UInt,
    val isPlaying: Boolean,
    val lastSequence: UInt,
    val lastAudioFrameAgeMillis: Long?,
    val bufferedLatencyMillis: Long?,
    val timestampElapsedRealtimeMillis: Long,
)

data class AndroidPlaybackStatusAck(
    val sequence: UInt,
    val accepted: Boolean,
    val receivedAtMillis: Long,
    val echoedTimestampElapsedRealtimeMillis: Long?,
)
package dev.ran.audiobridge.audio

class PlaybackBackpressureController(
    targetBufferMilliseconds: Int,
) {
    private var targetBufferMilliseconds: Int = PlaybackCacheConfig.normalize(targetBufferMilliseconds)
    private var remoteClockOffsetMillis: Long? = null

    fun updateTargetBufferMilliseconds(milliseconds: Int) {
        targetBufferMilliseconds = PlaybackCacheConfig.normalize(milliseconds)
    }

    fun shouldDropStalePacket(packetTimestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (packetTimestampMillis <= 0L) {
            return false
        }

        val offset = remoteClockOffsetMillis ?: (nowMillis - packetTimestampMillis).also { remoteClockOffsetMillis = it }
        val adjustedPacketTimestampMillis = packetTimestampMillis + offset
        val packetAgeMillis = nowMillis - adjustedPacketTimestampMillis

        return packetAgeMillis > targetBufferMilliseconds
    }
}
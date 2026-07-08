package dev.ran.audiobridge.network

import dev.ran.audiobridge.model.AndroidPlaybackStatus
import dev.ran.audiobridge.model.AndroidPlaybackStatusAck
import org.json.JSONObject

object AndroidPlaybackStatusJsonCodec {
    fun buildStatus(status: AndroidPlaybackStatus): String = JSONObject().apply {
        put("sequence", status.sequence.toLong())
        put("isPlaying", status.isPlaying)
        put("lastSequence", status.lastSequence.toLong())
        if (status.lastAudioFrameAgeMillis != null) {
            put("lastAudioFrameAgeMillis", status.lastAudioFrameAgeMillis)
        } else {
            put("lastAudioFrameAgeMillis", JSONObject.NULL)
        }
        if (status.bufferedLatencyMillis != null) {
            put("bufferedLatencyMillis", status.bufferedLatencyMillis)
        } else {
            put("bufferedLatencyMillis", JSONObject.NULL)
        }
        put("timestampElapsedRealtimeMillis", status.timestampElapsedRealtimeMillis)
    }.toString()

    fun parseAck(payload: String): AndroidPlaybackStatusAck {
        val root = JSONObject(payload)
        return AndroidPlaybackStatusAck(
            sequence = root.optLong("sequence", 0L).toUInt(),
            accepted = root.optBoolean("accepted", false),
            receivedAtMillis = root.optLong("receivedAtMillis", 0L),
            echoedTimestampElapsedRealtimeMillis = root.optionalLong("echoedTimestampElapsedRealtimeMillis"),
        )
    }

    private fun JSONObject.optionalLong(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null
}
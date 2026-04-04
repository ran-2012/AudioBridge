package dev.ran.audiobridge.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dev.ran.audiobridge.model.BridgePacket
import dev.ran.audiobridge.model.PlaybackSessionInfo

class AudioPlaybackManager {
    data class WriteResult(
        val trimmedBufferedAudio: Boolean = false,
        val droppedBytes: Int = 0,
    )

    private data class PlaybackRuntime(
        val controller: PlaybackBackpressureController,
        var lastPlaybackHeadPosition: Long = 0,
        var hasPlaybackProgressed: Boolean = false,
    )

    private var audioTrack: AudioTrack? = null
    private var currentVolume: Float = 1.0f
    private var playbackCacheMilliseconds: Int = PlaybackCacheConfig.DEFAULT_MILLISECONDS
    private var playbackRuntime: PlaybackRuntime? = null

    fun updatePlaybackCacheMilliseconds(milliseconds: Int): Int {
        val normalized = PlaybackCacheConfig.normalize(milliseconds)
        playbackCacheMilliseconds = normalized
        playbackRuntime?.controller?.updateTargetBufferMilliseconds(normalized)
        return normalized
    }

    fun configure(sessionInit: BridgePacket.SessionInit): PlaybackSessionInfo {
        release()

        val channelMask = when (sessionInit.channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            else -> AudioFormat.CHANNEL_OUT_STEREO
        }
        val encoding = when (sessionInit.encodingCode) {
            2 -> AudioFormat.ENCODING_PCM_FLOAT
            else -> AudioFormat.ENCODING_PCM_16BIT
        }

        val minBufferSize = AudioTrack.getMinBufferSize(sessionInit.sampleRate, channelMask, encoding)
        require(minBufferSize > 0) {
            "AudioTrack.getMinBufferSize 返回异常值: $minBufferSize, sampleRate=${sessionInit.sampleRate}, channels=${sessionInit.channels}, encoding=${sessionInit.encodingCode}"
        }
        val bytesPerSample = sessionInit.bitsPerSample / 8
        val bytesPerFrame = sessionInit.channels * bytesPerSample
        val frameBytes = sessionInit.sampleRate * sessionInit.channels * bytesPerSample * sessionInit.bufferMilliseconds / 1000
        val targetCacheBytes = sessionInit.sampleRate * bytesPerFrame * playbackCacheMilliseconds / 1000
        val bufferSize = maxOf(minBufferSize, frameBytes * 2, targetCacheBytes)
        val controller = PlaybackBackpressureController(
            targetBufferMilliseconds = playbackCacheMilliseconds,
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sessionInit.sampleRate)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .apply {
                require(state == AudioTrack.STATE_INITIALIZED) {
                    "AudioTrack 初始化失败，state=$state"
                }
                setVolume(currentVolume)
                play()
            }
        playbackRuntime = PlaybackRuntime(controller = controller)

        return PlaybackSessionInfo(
            encoding = if (sessionInit.encodingCode == 2) "Float32" else "PCM16",
            sampleRate = sessionInit.sampleRate,
            channels = sessionInit.channels,
            bitsPerSample = sessionInit.bitsPerSample,
            bufferMilliseconds = sessionInit.bufferMilliseconds,
        )
    }

    fun write(packet: BridgePacket.AudioFrame): WriteResult {
        if (packet.audioData.isEmpty()) {
            return WriteResult()
        }

        val track = audioTrack ?: return WriteResult()
        val runtime = playbackRuntime ?: return WriteResult()
        var trimmedBufferedAudio = false
        val currentPlaybackHeadPosition = track.playbackHeadPosition.toLong()

        if (currentPlaybackHeadPosition > runtime.lastPlaybackHeadPosition) {
            runtime.hasPlaybackProgressed = true
            runtime.lastPlaybackHeadPosition = currentPlaybackHeadPosition
        }

        if (runtime.controller.shouldDropStalePacket(packet.timestampMillis)) {
            discardBufferedAudio(track, runtime)
            return WriteResult(
                trimmedBufferedAudio = true,
                droppedBytes = packet.audioData.size,
            )
        }

        var result = if (runtime.hasPlaybackProgressed) {
            writeNonBlocking(track, packet.audioData)
        } else {
            track.write(packet.audioData, 0, packet.audioData.size)
        }

        if (runtime.hasPlaybackProgressed && result in 0 until packet.audioData.size) {
            discardBufferedAudio(track, runtime)
            trimmedBufferedAudio = true
            result = writeNonBlocking(track, packet.audioData)
        }

        require(result >= 0) {
            "AudioTrack.write 返回错误码: $result, sequence=${packet.sequence}, bytes=${packet.audioData.size}"
        }

        runtime.lastPlaybackHeadPosition = maxOf(runtime.lastPlaybackHeadPosition, track.playbackHeadPosition.toLong())
        return WriteResult(
            trimmedBufferedAudio = trimmedBufferedAudio,
            droppedBytes = (packet.audioData.size - result).coerceAtLeast(0),
        )
    }

    fun updateVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        audioTrack?.setVolume(currentVolume)
    }

    fun release() {
        audioTrack?.run {
            runCatching { pause() }
            runCatching { flush() }
            runCatching { stop() }
            release()
        }
        audioTrack = null
        playbackRuntime = null
    }

    private fun writeNonBlocking(track: AudioTrack, audioData: ByteArray): Int =
        track.write(audioData, 0, audioData.size, AudioTrack.WRITE_NON_BLOCKING)

    private fun discardBufferedAudio(track: AudioTrack, runtime: PlaybackRuntime) {
        runCatching { track.pause() }
        runCatching { track.flush() }
        runtime.lastPlaybackHeadPosition = track.playbackHeadPosition.toLong()
        runtime.hasPlaybackProgressed = false
        runCatching { track.play() }
    }
}

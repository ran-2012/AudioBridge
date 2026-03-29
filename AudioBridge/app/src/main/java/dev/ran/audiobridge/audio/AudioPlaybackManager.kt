package dev.ran.audiobridge.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dev.ran.audiobridge.model.BridgePacket
import dev.ran.audiobridge.model.PlaybackSessionInfo

class AudioPlaybackManager {
    private var audioTrack: AudioTrack? = null
    private var currentVolume: Float = 1.0f

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
        val frameBytes = sessionInit.sampleRate * sessionInit.channels * bytesPerSample * sessionInit.bufferMilliseconds / 1000
        val bufferSize = maxOf(minBufferSize, frameBytes * 2)

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

        return PlaybackSessionInfo(
            encoding = if (sessionInit.encodingCode == 2) "Float32" else "PCM16",
            sampleRate = sessionInit.sampleRate,
            channels = sessionInit.channels,
            bitsPerSample = sessionInit.bitsPerSample,
            bufferMilliseconds = sessionInit.bufferMilliseconds,
        )
    }

    fun write(packet: BridgePacket.AudioFrame) {
        if (packet.audioData.isEmpty()) {
            return
        }

        val track = audioTrack ?: return
        val result = track.write(packet.audioData, 0, packet.audioData.size)
        require(result >= 0) {
            "AudioTrack.write 返回错误码: $result, sequence=${packet.sequence}, bytes=${packet.audioData.size}"
        }
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
    }
}

package dev.ran.audiobridge.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AudioVisualizationActivity {
    Waiting,
    Active,
    Idle,
}

data class AudioVisualizationState(
    val activity: AudioVisualizationActivity = AudioVisualizationActivity.Waiting,
    val frame: AudioVisualizationFrame? = null,
)

class AudioVisualizationProcessor(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val elapsedRealtimeMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) : AutoCloseable {
    private data class AnalysisInput(
        val audioData: ByteArray,
        val encodingCode: Int,
        val sampleRate: Int,
        val channels: Int,
        val generation: Long,
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val inputs = Channel<AnalysisInput>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val stateLock = Any()
    private var generation = 0L
    private var closed = false
    private var windowBuffer = ByteArray(0)
    private var windowEncodingCode = 0
    private var windowSampleRate = 0
    private var windowChannels = 0
    private val mutableState = MutableStateFlow(AudioVisualizationState())

    val state: StateFlow<AudioVisualizationState> = mutableState.asStateFlow()

    init {
        scope.launch {
            var lastPublishedAt = Long.MIN_VALUE
            for (received in inputs) {
                var latest = received
                if (lastPublishedAt != Long.MIN_VALUE) {
                    val waitMillis = PUBLISH_INTERVAL_MILLIS - (elapsedRealtimeMillis() - lastPublishedAt)
                    if (waitMillis > 0) delay(waitMillis)
                }

                while (true) {
                    latest = inputs.tryReceive().getOrNull() ?: break
                }

                val capturedAt = elapsedRealtimeMillis()
                val frame = AudioVisualizationAnalyzer.analyze(
                    audioData = latest.audioData,
                    encodingCode = latest.encodingCode,
                    sampleRate = latest.sampleRate,
                    channels = latest.channels,
                    capturedAtElapsedRealtimeMillis = capturedAt,
                )
                val published = synchronized(stateLock) {
                    if (!closed && latest.generation == generation) {
                        mutableState.value = AudioVisualizationState(
                            activity = AudioVisualizationActivity.Active,
                            frame = frame,
                        )
                        true
                    } else {
                        false
                    }
                }
                if (published) {
                    lastPublishedAt = capturedAt
                    scheduleIdleReset(latest.generation, capturedAt)
                }
            }
        }
    }

    private fun scheduleIdleReset(frameGeneration: Long, capturedAt: Long) {
        scope.launch {
            delay(IDLE_TIMEOUT_MILLIS)
            synchronized(stateLock) {
                val currentFrame = mutableState.value.frame
                if (
                    !closed &&
                    frameGeneration == generation &&
                    currentFrame?.capturedAtElapsedRealtimeMillis == capturedAt
                ) {
                    mutableState.value = AudioVisualizationState(AudioVisualizationActivity.Idle)
                }
            }
        }
    }

    fun submit(audioData: ByteArray, encodingCode: Int, sampleRate: Int, channels: Int) {
        require(sampleRate > 0)
        require(channels == 1 || channels == 2)

        require(encodingCode == 1 || encodingCode == 2)

        val input = synchronized(stateLock) {
            if (closed) return
            if (
                encodingCode != windowEncodingCode ||
                sampleRate != windowSampleRate ||
                channels != windowChannels
            ) {
                resetWindow(encodingCode, sampleRate, channels)
            }

            val bytesPerSample = if (encodingCode == 2) Float.SIZE_BYTES else Short.SIZE_BYTES
            val windowBytes = AudioVisualizationAnalyzer.WINDOW_SIZE * channels * bytesPerSample
            windowBuffer = appendToWindow(windowBuffer, audioData, windowBytes)
            if (windowBuffer.size < windowBytes) {
                null
            } else {
                AnalysisInput(
                    audioData = windowBuffer.copyOf(),
                    encodingCode = encodingCode,
                    sampleRate = sampleRate,
                    channels = channels,
                    generation = generation,
                )
            }
        }
        input?.let(inputs::trySend)
    }

    fun reset() {
        synchronized(stateLock) {
            generation++
            windowBuffer = ByteArray(0)
            mutableState.value = AudioVisualizationState()
        }
        while (inputs.tryReceive().isSuccess) {
            // Discard queued frames from the previous playback session.
        }
    }

    override fun close() {
        synchronized(stateLock) {
            if (closed) return
            closed = true
            generation++
            windowBuffer = ByteArray(0)
            mutableState.value = AudioVisualizationState()
        }
        inputs.close()
        scope.cancel()
    }

    private fun resetWindow(encodingCode: Int, sampleRate: Int, channels: Int) {
        generation++
        windowBuffer = ByteArray(0)
        windowEncodingCode = encodingCode
        windowSampleRate = sampleRate
        windowChannels = channels
        mutableState.value = AudioVisualizationState()
    }

    private fun appendToWindow(current: ByteArray, incoming: ByteArray, capacity: Int): ByteArray {
        if (incoming.size >= capacity) {
            return incoming.copyOfRange(incoming.size - capacity, incoming.size)
        }
        val retainedBytes = minOf(current.size, capacity - incoming.size)
        return ByteArray(retainedBytes + incoming.size).also { combined ->
            current.copyInto(
                destination = combined,
                destinationOffset = 0,
                startIndex = current.size - retainedBytes,
            )
            incoming.copyInto(combined, destinationOffset = retainedBytes)
        }
    }

    companion object {
        const val PUBLISH_INTERVAL_MILLIS = 33L
        const val IDLE_TIMEOUT_MILLIS = 500L
    }
}
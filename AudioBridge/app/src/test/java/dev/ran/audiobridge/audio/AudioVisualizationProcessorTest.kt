package dev.ran.audiobridge.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioVisualizationProcessorTest {
    @Test
    fun rapidInput_rateLimitsPublishingAndKeepsLatestFrame() {
        AudioVisualizationProcessor().use { processor ->
            processor.submit(pcm16Mono(0.1f), ENCODING_PCM16, SAMPLE_RATE, channels = 1)
            val firstFrame = awaitFrame(processor)

            processor.submit(pcm16Mono(0.2f), ENCODING_PCM16, SAMPLE_RATE, channels = 1)
            processor.submit(pcm16Mono(0.4f), ENCODING_PCM16, SAMPLE_RATE, channels = 1)
            processor.submit(pcm16Mono(0.8f), ENCODING_PCM16, SAMPLE_RATE, channels = 1)
            val latestFrame = awaitFrame(processor) { it !== firstFrame }

            assertTrue(
                latestFrame.capturedAtElapsedRealtimeMillis - firstFrame.capturedAtElapsedRealtimeMillis >=
                    AudioVisualizationProcessor.PUBLISH_INTERVAL_MILLIS,
            )
            assertEquals(0.8f, latestFrame.phasePoints.first().left, 0.001f)
        }
    }

    @Test
    fun idleAndSessionReset_clearPublishedFrame() {
        AudioVisualizationProcessor().use { processor ->
            processor.submit(pcm16Mono(0.3f), ENCODING_PCM16, SAMPLE_RATE, channels = 1)
            assertNotNull(awaitFrame(processor))
            awaitCondition { processor.state.value.activity == AudioVisualizationActivity.Idle }
            assertNull(processor.state.value.frame)

            processor.submit(pcm16Mono(0.6f), ENCODING_PCM16, SAMPLE_RATE, channels = 1)
            assertEquals(0.6f, awaitFrame(processor).phasePoints.first().left, 0.001f)

            processor.reset()

            assertEquals(AudioVisualizationActivity.Waiting, processor.state.value.activity)
            assertNull(processor.state.value.frame)
            processor.submit(pcm16Mono(0.9f), ENCODING_PCM16, SAMPLE_RATE, channels = 1)
            assertEquals(0.9f, awaitFrame(processor).phasePoints.first().left, 0.001f)
        }
    }

    @Test
    fun shortPackets_fillRollingWindowBeforePublishing() {
        AudioVisualizationProcessor().use { processor ->
            val samples = pcm16Mono(0.7f)
            val splitByte = 960 * Short.SIZE_BYTES

            processor.submit(samples.copyOfRange(0, splitByte), ENCODING_PCM16, SAMPLE_RATE, channels = 1)
            Thread.sleep(50)
            assertEquals(AudioVisualizationActivity.Waiting, processor.state.value.activity)

            processor.submit(samples.copyOfRange(splitByte, samples.size), ENCODING_PCM16, SAMPLE_RATE, channels = 1)

            assertEquals(0.7f, awaitFrame(processor).phasePoints.first().left, 0.001f)
        }
    }

    @Test
    fun resetWhileFrameIsQueued_doesNotRepublishPreviousSession() {
        AudioVisualizationProcessor().use { processor ->
            processor.submit(pcm16Mono(0.5f), ENCODING_PCM16, SAMPLE_RATE, channels = 1)
            processor.reset()

            Thread.sleep(100)

            assertEquals(AudioVisualizationActivity.Waiting, processor.state.value.activity)
            assertNull(processor.state.value.frame)
        }
    }

    private fun awaitFrame(
        processor: AudioVisualizationProcessor,
        predicate: (AudioVisualizationFrame) -> Boolean = { true },
    ): AudioVisualizationFrame {
        awaitCondition {
            processor.state.value.frame?.let(predicate) == true
        }
        return requireNotNull(processor.state.value.frame)
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TEST_TIMEOUT_MILLIS * 1_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for visualization state" }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
    }

    private fun pcm16Mono(amplitude: Float): ByteArray =
        ByteBuffer.allocate(AudioVisualizationAnalyzer.WINDOW_SIZE * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                repeat(AudioVisualizationAnalyzer.WINDOW_SIZE) {
                    putShort((amplitude * Short.MAX_VALUE).toInt().toShort())
                }
            }
            .array()

    private companion object {
        const val ENCODING_PCM16 = 1
        const val SAMPLE_RATE = 48_000
        const val TEST_TIMEOUT_MILLIS = 2_000L
        const val POLL_INTERVAL_MILLIS = 5L
    }
}
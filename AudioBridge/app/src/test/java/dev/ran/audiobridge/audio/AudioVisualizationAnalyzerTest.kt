package dev.ran.audiobridge.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioVisualizationAnalyzerTest {
    @Test
    fun pcm16Stereo_keepsChannelsSeparateAndFindsDominantBands() {
        val data = pcm16Stereo(
            left = sineWave(440.0),
            right = sineWave(2_000.0),
        )

        val frame = analyze(data, encodingCode = 1, channels = 2)

        assertTrue(frame.leftSpectrum.indexOfMax() < frame.rightSpectrum.indexOfMax())
        assertTrue(frame.leftSpectrum.max() > 0.8f)
        assertTrue(frame.rightSpectrum.max() > 0.8f)
    }

    @Test
    fun pcm16Mono_copiesSamplesToBothChannels() {
        val samples = sineWave(880.0)
        val buffer = ByteBuffer.allocate(samples.size * Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buffer.putShort((it * Short.MAX_VALUE).toInt().toShort()) }

        val frame = analyze(buffer.array(), encodingCode = 1, channels = 1)

        assertEquals(frame.leftSpectrum, frame.rightSpectrum)
        frame.phasePoints.forEach { assertEquals(it.left, it.right, 0.0001f) }
    }

    @Test
    fun float32_sanitizesNonFiniteAndOutOfRangeSamples() {
        val samples = FloatArray(AudioVisualizationAnalyzer.WINDOW_SIZE)
        samples[0] = Float.NaN
        samples[1] = Float.POSITIVE_INFINITY
        samples[2] = 2f
        samples[3] = -2f
        val buffer = ByteBuffer.allocate(samples.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach(buffer::putFloat)

        val frame = analyze(buffer.array(), encodingCode = 2, channels = 1)

        assertTrue(frame.phasePoints.all { it.left.isFinite() && it.left in -1f..1f })
        assertTrue(frame.levelDbfs.isFinite())
    }

    @Test
    fun silence_usesFloorLevelAndEmptySpectrum() {
        val frame = analyze(
            ByteArray(AudioVisualizationAnalyzer.WINDOW_SIZE * Short.SIZE_BYTES * 2),
            encodingCode = 1,
            channels = 2,
        )

        assertEquals(AudioVisualizationAnalyzer.MIN_DBFS, frame.levelDbfs)
        assertTrue(frame.leftSpectrum.all { it == 0f })
        assertTrue(frame.rightSpectrum.all { it == 0f })
    }

    @Test
    fun fullScaleSine_reportsExpectedRmsDbfsAndPhaseMapping() {
        val samples = sineWave(1_000.0)
        val frame = analyze(pcm16Stereo(samples, samples), encodingCode = 1, channels = 2)

        assertEquals(-3.01f, frame.levelDbfs, 0.15f)
        assertEquals(-3.01f, frame.leftLevelDbfs, 0.15f)
        assertEquals(-3.01f, frame.rightLevelDbfs, 0.15f)
        assertEquals(SAMPLE_RATE, frame.sampleRate)
        assertEquals(AudioVisualizationAnalyzer.PHASE_POINT_COUNT, frame.phasePoints.size)
        frame.phasePoints.forEach { assertEquals(it.left, it.right, 0.0001f) }
    }

    @Test
    fun stereoLevels_reportEachChannelIndependently() {
        val left = sineWave(1_000.0)
        val right = FloatArray(left.size) { index -> left[index] * 0.25f }

        val frame = analyze(pcm16Stereo(left, right), encodingCode = 1, channels = 2)

        assertEquals(-3.01f, frame.leftLevelDbfs, 0.15f)
        assertEquals(-15.05f, frame.rightLevelDbfs, 0.2f)
        assertTrue(frame.leftLevelDbfs > frame.rightLevelDbfs)
    }

    private fun analyze(data: ByteArray, encodingCode: Int, channels: Int) =
        AudioVisualizationAnalyzer.analyze(
            audioData = data,
            encodingCode = encodingCode,
            sampleRate = SAMPLE_RATE,
            channels = channels,
            capturedAtElapsedRealtimeMillis = 123L,
        )

    private fun sineWave(frequency: Double): FloatArray =
        FloatArray(AudioVisualizationAnalyzer.WINDOW_SIZE) { index ->
            sin(2.0 * PI * frequency * index / SAMPLE_RATE).toFloat()
        }

    private fun pcm16Stereo(left: FloatArray, right: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(left.size * Short.SIZE_BYTES * 2).order(ByteOrder.LITTLE_ENDIAN)
        left.indices.forEach { index ->
            buffer.putShort((left[index] * Short.MAX_VALUE).toInt().toShort())
            buffer.putShort((right[index] * Short.MAX_VALUE).toInt().toShort())
        }
        return buffer.array()
    }

    private fun List<Float>.indexOfMax(): Int = indices.maxBy { this[it] }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
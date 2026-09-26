package dev.ran.audiobridge.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class StereoPhasePoint(
    val left: Float,
    val right: Float,
)

data class AudioVisualizationFrame(
    val leftSpectrum: List<Float>,
    val rightSpectrum: List<Float>,
    val phasePoints: List<StereoPhasePoint>,
    val levelDbfs: Float,
    val capturedAtElapsedRealtimeMillis: Long,
    val sampleRate: Int = 48_000,
    val leftLevelDbfs: Float = levelDbfs,
    val rightLevelDbfs: Float = levelDbfs,
) {
    init {
        require(leftSpectrum.size == AudioVisualizationAnalyzer.SPECTRUM_BAND_COUNT)
        require(rightSpectrum.size == AudioVisualizationAnalyzer.SPECTRUM_BAND_COUNT)
        require(phasePoints.size <= AudioVisualizationAnalyzer.PHASE_POINT_COUNT)
        require(levelDbfs in AudioVisualizationAnalyzer.MIN_DBFS..AudioVisualizationAnalyzer.MAX_DBFS)
        require(sampleRate > 0)
        require(leftLevelDbfs in AudioVisualizationAnalyzer.MIN_DBFS..AudioVisualizationAnalyzer.MAX_DBFS)
        require(rightLevelDbfs in AudioVisualizationAnalyzer.MIN_DBFS..AudioVisualizationAnalyzer.MAX_DBFS)
    }
}

object AudioVisualizationAnalyzer {
    const val WINDOW_SIZE = 4096
    const val SPECTRUM_BAND_COUNT = 32
    const val PHASE_POINT_COUNT = 128
    const val MIN_DBFS = -60f
    const val MAX_DBFS = 0f

    fun analyze(
        audioData: ByteArray,
        encodingCode: Int,
        sampleRate: Int,
        channels: Int,
        capturedAtElapsedRealtimeMillis: Long,
    ): AudioVisualizationFrame {
        require(sampleRate > 0)
        require(channels == 1 || channels == 2)

        val samples = decodeWindow(audioData, encodingCode, channels)
        val phasePoints = List(PHASE_POINT_COUNT) { index ->
            val sampleIndex = index * WINDOW_SIZE / PHASE_POINT_COUNT
            StereoPhasePoint(samples.left[sampleIndex], samples.right[sampleIndex])
        }

        return AudioVisualizationFrame(
            leftSpectrum = calculateSpectrum(samples.left, sampleRate),
            rightSpectrum = calculateSpectrum(samples.right, sampleRate),
            phasePoints = phasePoints,
            levelDbfs = calculateLevelDbfs(samples.left, samples.right),
            capturedAtElapsedRealtimeMillis = capturedAtElapsedRealtimeMillis,
            sampleRate = sampleRate,
            leftLevelDbfs = calculateLevelDbfs(samples.left),
            rightLevelDbfs = calculateLevelDbfs(samples.right),
        )
    }

    private data class StereoSamples(
        val left: FloatArray,
        val right: FloatArray,
    )

    private fun decodeWindow(audioData: ByteArray, encodingCode: Int, channels: Int): StereoSamples {
        val bytesPerSample = if (encodingCode == 2) Float.SIZE_BYTES else Short.SIZE_BYTES
        val availableFrames = audioData.size / (bytesPerSample * channels)
        val framesToRead = minOf(availableFrames, WINDOW_SIZE)
        val firstFrame = (availableFrames - framesToRead).coerceAtLeast(0)
        val left = FloatArray(WINDOW_SIZE)
        val right = FloatArray(WINDOW_SIZE)
        val destinationOffset = WINDOW_SIZE - framesToRead

        repeat(framesToRead) { frameOffset ->
            val sourceFrame = firstFrame + frameOffset
            val leftValue = decodeSample(audioData, sourceFrame * channels * bytesPerSample, encodingCode)
            val rightValue = if (channels == 2) {
                decodeSample(audioData, (sourceFrame * channels + 1) * bytesPerSample, encodingCode)
            } else {
                leftValue
            }
            left[destinationOffset + frameOffset] = leftValue
            right[destinationOffset + frameOffset] = rightValue
        }

        return StereoSamples(left, right)
    }

    private fun decodeSample(audioData: ByteArray, offset: Int, encodingCode: Int): Float {
        return if (encodingCode == 2) {
            val bits = (audioData[offset].toInt() and 0xff) or
                ((audioData[offset + 1].toInt() and 0xff) shl 8) or
                ((audioData[offset + 2].toInt() and 0xff) shl 16) or
                ((audioData[offset + 3].toInt() and 0xff) shl 24)
            Float.fromBits(bits).takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
        } else {
            val value = (audioData[offset].toInt() and 0xff) or (audioData[offset + 1].toInt() shl 8)
            value.toShort() / 32768f
        }
    }

    private fun calculateLevelDbfs(left: FloatArray, right: FloatArray): Float {
        var sumSquares = 0.0
        for (index in left.indices) {
            sumSquares += left[index] * left[index]
            sumSquares += right[index] * right[index]
        }
        val rms = sqrt(sumSquares / (left.size * 2))
        if (rms <= 0.0) return MIN_DBFS
        return (20.0 * log10(rms)).toFloat().coerceIn(MIN_DBFS, MAX_DBFS)
    }

    private fun calculateLevelDbfs(samples: FloatArray): Float {
        var sumSquares = 0.0
        samples.forEach { sample -> sumSquares += sample * sample }
        val rms = sqrt(sumSquares / samples.size)
        if (rms <= 0.0) return MIN_DBFS
        return (20.0 * log10(rms)).toFloat().coerceIn(MIN_DBFS, MAX_DBFS)
    }

    private fun calculateSpectrum(samples: FloatArray, sampleRate: Int): List<Float> {
        val real = DoubleArray(WINDOW_SIZE) { index ->
            val hann = 0.5 * (1.0 - cos(2.0 * PI * index / (WINDOW_SIZE - 1)))
            samples[index] * hann
        }
        val imaginary = DoubleArray(WINDOW_SIZE)
        fft(real, imaginary)

        val nyquist = sampleRate / 2.0
        val minimumFrequency = minOf(20.0, nyquist)
        return List(SPECTRUM_BAND_COUNT) { band ->
            val startFrequency = logarithmicFrequency(minimumFrequency, nyquist, band)
            val endFrequency = logarithmicFrequency(minimumFrequency, nyquist, band + 1)
            val startBin = max(1, (startFrequency * WINDOW_SIZE / sampleRate).toInt())
            val endBin = max(startBin + 1, (endFrequency * WINDOW_SIZE / sampleRate).toInt())
                .coerceAtMost(WINDOW_SIZE / 2)
            var peak = 0.0
            for (bin in startBin until endBin) {
                peak = max(peak, sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin]))
            }
            val normalizedMagnitude = peak / (WINDOW_SIZE / 4.0)
            if (normalizedMagnitude <= 0.0) 0f else {
                ((20.0 * log10(normalizedMagnitude) - MIN_DBFS) / -MIN_DBFS)
                    .toFloat()
                    .coerceIn(0f, 1f)
            }
        }
    }

    private fun logarithmicFrequency(minimum: Double, maximum: Double, boundary: Int): Double {
        if (maximum <= minimum) return maximum
        val progress = boundary.toDouble() / SPECTRUM_BAND_COUNT
        return minimum * kotlin.math.exp(ln(maximum / minimum) * progress)
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        var target = 0
        for (source in 1 until WINDOW_SIZE) {
            var bit = WINDOW_SIZE shr 1
            while (target and bit != 0) {
                target = target xor bit
                bit = bit shr 1
            }
            target = target xor bit
            if (source < target) {
                real[source] = real[target].also { real[target] = real[source] }
                imaginary[source] = imaginary[target].also { imaginary[target] = imaginary[source] }
            }
        }

        var length = 2
        while (length <= WINDOW_SIZE) {
            val angle = -2.0 * PI / length
            val stepReal = cos(angle)
            val stepImaginary = sin(angle)
            for (start in 0 until WINDOW_SIZE step length) {
                var rotationReal = 1.0
                var rotationImaginary = 0.0
                for (offset in 0 until length / 2) {
                    val even = start + offset
                    val odd = even + length / 2
                    val oddReal = real[odd] * rotationReal - imaginary[odd] * rotationImaginary
                    val oddImaginary = real[odd] * rotationImaginary + imaginary[odd] * rotationReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextRotationReal = rotationReal * stepReal - rotationImaginary * stepImaginary
                    rotationImaginary = rotationReal * stepImaginary + rotationImaginary * stepReal
                    rotationReal = nextRotationReal
                }
            }
            length = length shl 1
        }
    }
}
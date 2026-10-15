package dev.ran.audiobridge.ui

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ran.audiobridge.audio.AudioVisualizationAnalyzer
import dev.ran.audiobridge.audio.AudioVisualizationActivity
import dev.ran.audiobridge.audio.AudioVisualizationFrame
import dev.ran.audiobridge.audio.AudioVisualizationState
import dev.ran.audiobridge.audio.StereoPhasePoint
import dev.ran.audiobridge.model.PlaybackUiState
import kotlinx.coroutines.isActive
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val LeftChannelColor = Color(0xFF47D7C4)
private val RightChannelColor = Color(0xFFFFB454)
private val EmptyVisualizationFrame = AudioVisualizationFrame(
    leftSpectrum = List(AudioVisualizationAnalyzer.SPECTRUM_BAND_COUNT) { 0f },
    rightSpectrum = List(AudioVisualizationAnalyzer.SPECTRUM_BAND_COUNT) { 0f },
    phasePoints = emptyList(),
    levelDbfs = AudioVisualizationAnalyzer.MIN_DBFS,
    capturedAtElapsedRealtimeMillis = 0L,
)

private enum class VisualizationStatus(val label: String) {
    Disconnected("未连接"),
    Waiting("等待音频"),
    Active("音频活动"),
    Idle("音频空闲"),
}

@Composable
internal fun AudioVisualizerScreen(
    uiState: PlaybackUiState,
    visualizationState: AudioVisualizationState,
    contentPadding: PaddingValues,
) {
    val status = when {
        !uiState.isConnected -> VisualizationStatus.Disconnected
        visualizationState.activity == AudioVisualizationActivity.Active -> VisualizationStatus.Active
        visualizationState.activity == AudioVisualizationActivity.Idle -> VisualizationStatus.Idle
        else -> VisualizationStatus.Waiting
    }
    val renderedFrame = rememberSmoothedFrame(visualizationState.frame)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("visualizer-root")
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val isTabletLandscape = maxWidth >= 840.dp && maxWidth > maxHeight
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VisualizationStatusHeader(status)
            if (isTabletLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.46f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PhasePanel(
                        frame = renderedFrame,
                        modifier = Modifier.weight(1f),
                    )
                    LevelMeter(
                        leftLevelDbfs = renderedFrame.leftLevelDbfs,
                        rightLevelDbfs = renderedFrame.rightLevelDbfs,
                        modifier = Modifier
                            .width(112.dp)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.46f),
                ) {
                    PhasePanel(
                        frame = renderedFrame,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 92.dp),
                    )
                    LevelMeter(
                        leftLevelDbfs = renderedFrame.leftLevelDbfs,
                        rightLevelDbfs = renderedFrame.rightLevelDbfs,
                        modifier = Modifier
                            .width(84.dp)
                            .fillMaxHeight()
                            .align(Alignment.TopEnd),
                    )
                }
            }
            SpectrumPanel(
                frame = renderedFrame,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.54f),
            )
        }
    }
}

@Composable
private fun VisualizationStatusHeader(status: VisualizationStatus) {
    val color = when (status) {
        VisualizationStatus.Active -> LeftChannelColor
        VisualizationStatus.Idle -> RightChannelColor
        VisualizationStatus.Waiting -> MaterialTheme.colorScheme.onSurfaceVariant
        VisualizationStatus.Disconnected -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun rememberSmoothedFrame(frame: AudioVisualizationFrame?): AudioVisualizationFrame {
    if (LocalInspectionMode.current) {
        return frame ?: EmptyVisualizationFrame
    }

    var start by remember { mutableStateOf(EmptyVisualizationFrame) }
    var target by remember { mutableStateOf(EmptyVisualizationFrame) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(frame) {
        start = interpolateFrame(start, target, progress.value)
        target = frame ?: EmptyVisualizationFrame
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 140))
    }
    return interpolateFrame(start, target, progress.value)
}

private fun interpolateFrame(
    start: AudioVisualizationFrame,
    target: AudioVisualizationFrame,
    progress: Float,
): AudioVisualizationFrame {
    fun blend(from: Float, to: Float) = from + (to - from) * progress
    val phasePointCount = maxOf(start.phasePoints.size, target.phasePoints.size)
    return AudioVisualizationFrame(
        leftSpectrum = start.leftSpectrum.zip(target.leftSpectrum) { from, to -> blend(from, to) },
        rightSpectrum = start.rightSpectrum.zip(target.rightSpectrum) { from, to -> blend(from, to) },
        phasePoints = List(phasePointCount) { index ->
            val from = start.phasePoints.getOrElse(index) { StereoPhasePoint(0f, 0f) }
            val to = target.phasePoints.getOrElse(index) { StereoPhasePoint(0f, 0f) }
            StereoPhasePoint(blend(from.left, to.left), blend(from.right, to.right))
        },
        levelDbfs = blend(start.levelDbfs, target.levelDbfs),
        capturedAtElapsedRealtimeMillis = target.capturedAtElapsedRealtimeMillis,
        sampleRate = target.sampleRate,
        leftLevelDbfs = blend(start.leftLevelDbfs, target.leftLevelDbfs),
        rightLevelDbfs = blend(start.rightLevelDbfs, target.rightLevelDbfs),
    )
}

@Composable
private fun PhasePanel(frame: AudioVisualizationFrame, modifier: Modifier = Modifier) {
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val phasePointBatches = remember { mutableStateListOf<TimedPhasePointBatch>() }
    val inspectionMode = LocalInspectionMode.current

    LaunchedEffect(frame.capturedAtElapsedRealtimeMillis) {
        if (frame.phasePoints.isNotEmpty()) {
            phasePointBatches += TimedPhasePointBatch(
                points = frame.phasePoints,
                addedAtNanos = System.nanoTime(),
            )
        }
    }
    LaunchedEffect(Unit) {
        if (!inspectionMode) {
            while (isActive) {
                withFrameNanos { frameNanos ->
                    phasePointBatches.removeAll { batch ->
                        frameNanos - batch.addedAtNanos >= PHASE_POINT_LIFETIME_NANOS
                    }
                }
            }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("L/R 相位", style = MaterialTheme.typography.labelLarge)
            Text("L × R", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("phase-plot")
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(6.dp))
                .semantics { contentDescription = "左右声道 Lissajous 相位图" }
                .padding(12.dp),
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f
            clipRect(
                left = center.x - radius,
                top = center.y - radius,
                right = center.x + radius,
                bottom = center.y + radius,
            ) {
                rotate(degrees = PHASE_ROTATION_DEGREES, pivot = center) {
                    drawLine(
                        axisColor,
                        Offset(center.x - radius, center.y),
                        Offset(center.x + radius, center.y),
                        1.dp.toPx(),
                    )
                    drawLine(
                        axisColor,
                        Offset(center.x, center.y - radius),
                        Offset(center.x, center.y + radius),
                        1.dp.toPx(),
                    )
                    phasePointBatches.forEach { batch ->
                        drawPoints(
                            points = batch.points.map { point ->
                                Offset(
                                    x = center.x + point.left * radius * PHASE_TRACE_SCALE,
                                    y = center.y - point.right * radius * PHASE_TRACE_SCALE,
                                )
                            },
                            pointMode = PointMode.Points,
                            color = LeftChannelColor,
                            strokeWidth = PHASE_POINT_SIZE.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

private data class TimedPhasePointBatch(
    val points: List<StereoPhasePoint>,
    val addedAtNanos: Long,
)

@Composable
private fun LevelMeter(
    leftLevelDbfs: Float,
    rightLevelDbfs: Float,
    modifier: Modifier = Modifier,
) {
    val leftLevel = normalizeDbfs(leftLevelDbfs)
    val rightLevel = normalizeDbfs(rightLevelDbfs)
    val leftPeak = rememberPeakLevel(leftLevel)
    val rightPeak = rememberPeakLevel(rightLevel)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val peakColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    Column(
        modifier = modifier
            .testTag("level-meter")
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("短时电平", style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LevelValue("L", leftLevelDbfs, LeftChannelColor)
            LevelValue("R", rightLevelDbfs, RightChannelColor)
        }
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        "左右声道短时 RMS 电平 ${formatDbfs(leftLevelDbfs)} 和 ${formatDbfs(rightLevelDbfs)} dBFS"
                },
        ) {
            val gap = 6.dp.toPx()
            val barWidth = min(18.dp.toPx(), (size.width - gap) / 2f)
            val totalWidth = barWidth * 2f + gap
            val startX = (size.width - totalWidth) / 2f
            drawLevelBar(startX, barWidth, leftLevel, leftPeak, LeftChannelColor, trackColor, peakColor)
            drawLevelBar(startX + barWidth + gap, barWidth, rightLevel, rightPeak, RightChannelColor, trackColor, peakColor)
        }
        Text("dBFS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LevelValue(channel: String, levelDbfs: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(channel, style = MaterialTheme.typography.labelSmall, color = color)
        Text(formatDbfs(levelDbfs), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun rememberPeakLevel(level: Float): Float {
    if (LocalInspectionMode.current) {
        return (level + 0.1f).coerceAtMost(1f)
    }

    val currentLevel by rememberUpdatedState(level)
    val peak = remember { mutableFloatStateOf(level) }
    LaunchedEffect(Unit) {
        var holdUntilNanos = 0L
        var previousFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (previousFrameNanos == 0L) previousFrameNanos = frameNanos
                val elapsedSeconds = (frameNanos - previousFrameNanos) / 1_000_000_000f
                val current = currentLevel
                if (current >= peak.floatValue) {
                    peak.floatValue = current
                    holdUntilNanos = frameNanos + PEAK_HOLD_MILLIS * 1_000_000L
                } else if (frameNanos >= holdUntilNanos) {
                    peak.floatValue = max(current, peak.floatValue - PEAK_FALL_PER_SECOND * elapsedSeconds)
                }
                previousFrameNanos = frameNanos
            }
        }
    }
    return peak.floatValue
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLevelBar(
    x: Float,
    width: Float,
    level: Float,
    peak: Float,
    color: Color,
    trackColor: Color,
    peakColor: Color,
) {
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
    drawRoundRect(trackColor, topLeft = Offset(x, 0f), size = Size(width, size.height), cornerRadius = cornerRadius)
    val fillHeight = size.height * level
    drawRoundRect(
        color = color,
        topLeft = Offset(x, size.height - fillHeight),
        size = Size(width, fillHeight),
        cornerRadius = cornerRadius,
    )
    val peakY = (size.height * (1f - peak)).coerceIn(1.dp.toPx(), size.height - 1.dp.toPx())
    drawLine(
        color = peakColor,
        start = Offset(x - 1.dp.toPx(), peakY),
        end = Offset(x + width + 1.dp.toPx(), peakY),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

private data class SpectrumPeakLevels(
    val left: FloatArray,
    val right: FloatArray,
    val revision: Int,
)

@Composable
private fun rememberSpectrumPeakLevels(
    leftSpectrum: List<Float>,
    rightSpectrum: List<Float>,
): SpectrumPeakLevels {
    if (LocalInspectionMode.current) {
        return SpectrumPeakLevels(
            left = FloatArray(leftSpectrum.size) { index -> (leftSpectrum[index] + 0.1f).coerceAtMost(1f) },
            right = FloatArray(rightSpectrum.size) { index -> (rightSpectrum[index] + 0.1f).coerceAtMost(1f) },
            revision = 0,
        )
    }

    val currentLeft by rememberUpdatedState(leftSpectrum)
    val currentRight by rememberUpdatedState(rightSpectrum)
    val leftPeaks = remember { FloatArray(AudioVisualizationAnalyzer.SPECTRUM_BAND_COUNT) }
    val rightPeaks = remember { FloatArray(AudioVisualizationAnalyzer.SPECTRUM_BAND_COUNT) }
    var revision by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val leftHoldUntil = LongArray(AudioVisualizationAnalyzer.SPECTRUM_BAND_COUNT)
        val rightHoldUntil = LongArray(AudioVisualizationAnalyzer.SPECTRUM_BAND_COUNT)
        var previousFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (previousFrameNanos == 0L) previousFrameNanos = frameNanos
                val elapsedSeconds = (frameNanos - previousFrameNanos) / 1_000_000_000f
                var changed = false

                for (index in leftPeaks.indices) {
                    changed = updateSpectrumPeak(
                        current = currentLeft[index],
                        peaks = leftPeaks,
                        holdUntil = leftHoldUntil,
                        index = index,
                        frameNanos = frameNanos,
                        elapsedSeconds = elapsedSeconds,
                    ) || changed
                    changed = updateSpectrumPeak(
                        current = currentRight[index],
                        peaks = rightPeaks,
                        holdUntil = rightHoldUntil,
                        index = index,
                        frameNanos = frameNanos,
                        elapsedSeconds = elapsedSeconds,
                    ) || changed
                }
                if (changed) revision++
                previousFrameNanos = frameNanos
            }
        }
    }

    return SpectrumPeakLevels(leftPeaks, rightPeaks, revision)
}

private fun updateSpectrumPeak(
    current: Float,
    peaks: FloatArray,
    holdUntil: LongArray,
    index: Int,
    frameNanos: Long,
    elapsedSeconds: Float,
): Boolean {
    val level = current.coerceIn(0f, 1f)
    if (level >= peaks[index]) {
        holdUntil[index] = frameNanos + PEAK_HOLD_MILLIS * 1_000_000L
        if (level == peaks[index]) return false
        peaks[index] = level
        return true
    }
    if (frameNanos < holdUntil[index]) return false

    val nextPeak = max(level, peaks[index] - PEAK_FALL_PER_SECOND * elapsedSeconds)
    if (nextPeak == peaks[index]) return false
    peaks[index] = nextPeak
    return true
}

@Composable
private fun SpectrumPanel(frame: AudioVisualizationFrame, modifier: Modifier = Modifier) {
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val peakColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    val peakLevels = rememberSpectrumPeakLevels(frame.leftSpectrum, frame.rightSpectrum)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("双声道频谱 · dBFS", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("L", color = LeftChannelColor, style = MaterialTheme.typography.labelLarge)
                Text("R", color = RightChannelColor, style = MaterialTheme.typography.labelLarge)
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("spectrum-plot")
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(6.dp))
                .semantics { contentDescription = "左右双声道实时频谱" }
                .padding(6.dp),
        ) {
            val plotLeft = 30.dp.toPx()
            val plotRight = size.width - 4.dp.toPx()
            val plotTop = 4.dp.toPx()
            val plotBottom = size.height - 18.dp.toPx()
            val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
            val middle = (plotTop + plotBottom) / 2f
            val bandWidth = plotWidth / AudioVisualizationAnalyzer.SPECTRUM_BAND_COUNT
            val barWidth = (bandWidth * 0.68f).coerceAtLeast(1f)
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = labelColor.toArgb()
                textSize = 9.sp.toPx()
            }

            SPECTRUM_DB_TICKS.forEach { dbfs ->
                val normalized = normalizeDbfs(dbfs.toFloat())
                val upperY = middle - normalized * (middle - plotTop)
                val lowerY = middle + normalized * (plotBottom - middle)
                drawLine(gridColor, Offset(plotLeft, upperY), Offset(plotRight, upperY), 1.dp.toPx())
                if (dbfs != AudioVisualizationAnalyzer.MIN_DBFS.toInt()) {
                    drawLine(gridColor, Offset(plotLeft, lowerY), Offset(plotRight, lowerY), 1.dp.toPx())
                }
                drawContext.canvas.nativeCanvas.drawText(
                    dbfs.toString(),
                    2.dp.toPx(),
                    upperY + 3.dp.toPx(),
                    labelPaint,
                )
                if (dbfs != AudioVisualizationAnalyzer.MIN_DBFS.toInt()) {
                    drawContext.canvas.nativeCanvas.drawText(
                        dbfs.toString(),
                        2.dp.toPx(),
                        lowerY + 3.dp.toPx(),
                        labelPaint,
                    )
                }
            }

            val minimumFrequency = min(20f, frame.sampleRate / 2f)
            val maximumFrequency = frame.sampleRate / 2f
            frequencyTicks(
                maximumFrequency = maximumFrequency,
                plotWidth = plotWidth,
                minimumSpacing = 52.dp.toPx(),
            ).forEach { (frequency, label) ->
                val x = plotLeft + logarithmicPosition(frequency, minimumFrequency, maximumFrequency) * plotWidth
                drawLine(gridColor, Offset(x, plotTop), Offset(x, plotBottom), 1.dp.toPx())
                val labelWidth = labelPaint.measureText(label)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    (x - labelWidth / 2f).coerceIn(plotLeft, plotRight - labelWidth),
                    size.height - 2.dp.toPx(),
                    labelPaint,
                )
            }
            drawLine(baselineColor, Offset(plotLeft, middle), Offset(plotRight, middle), 1.dp.toPx())
            frame.leftSpectrum.forEachIndexed { index, magnitude ->
                val height = magnitude * (middle - plotTop)
                val x = plotLeft + index * bandWidth + (bandWidth - barWidth) / 2f
                drawRoundRect(
                    color = LeftChannelColor,
                    topLeft = Offset(x, middle - height),
                    size = Size(barWidth, height),
                )
                val peak = peakLevels.left[index]
                if (peak > 0f) {
                    val peakY = middle - peak * (middle - plotTop)
                    drawLine(
                        color = peakColor,
                        start = Offset(x - 1.dp.toPx(), peakY),
                        end = Offset(x + barWidth + 1.dp.toPx(), peakY),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            frame.rightSpectrum.forEachIndexed { index, magnitude ->
                val height = magnitude * (plotBottom - middle)
                val x = plotLeft + index * bandWidth + (bandWidth - barWidth) / 2f
                drawRoundRect(
                    color = RightChannelColor,
                    topLeft = Offset(x, middle),
                    size = Size(barWidth, height),
                )
                val peak = peakLevels.right[index]
                if (peak > 0f) {
                    val peakY = middle + peak * (plotBottom - middle)
                    drawLine(
                        color = peakColor,
                        start = Offset(x - 1.dp.toPx(), peakY),
                        end = Offset(x + barWidth + 1.dp.toPx(), peakY),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

private fun normalizeDbfs(levelDbfs: Float): Float =
    ((levelDbfs - AudioVisualizationAnalyzer.MIN_DBFS) / -AudioVisualizationAnalyzer.MIN_DBFS)
        .coerceIn(0f, 1f)

private fun formatDbfs(levelDbfs: Float): String =
    if (levelDbfs <= AudioVisualizationAnalyzer.MIN_DBFS) "-∞" else levelDbfs.roundToInt().toString()

private fun frequencyTicks(
    maximumFrequency: Float,
    plotWidth: Float,
    minimumSpacing: Float,
): List<Pair<Float, String>> {
    val minimumFrequency = min(20f, maximumFrequency)
    if (maximumFrequency <= minimumFrequency) {
        return listOf(maximumFrequency to formatFrequency(maximumFrequency))
    }

    val candidates = buildList {
        var decade = 10f
        while (decade <= maximumFrequency) {
            listOf(1f, 2f, 3f, 5f).forEach { multiplier ->
                val frequency = decade * multiplier
                if (frequency in minimumFrequency..maximumFrequency) add(frequency)
            }
            decade *= 10f
        }
    }
    val frequencies = listOf(minimumFrequency) + candidates + maximumFrequency
    val distinctFrequencies = frequencies.distinct().sorted()
    val selected = mutableListOf(distinctFrequencies.first())
    var lastPosition = 0f

    distinctFrequencies.drop(1).dropLast(1).forEach { frequency ->
        val position = logarithmicPosition(frequency, minimumFrequency, maximumFrequency) * plotWidth
        val remainingToEnd = plotWidth - position
        if (position - lastPosition >= minimumSpacing && remainingToEnd >= minimumSpacing) {
            selected += frequency
            lastPosition = position
        }
    }
    if (distinctFrequencies.last() != selected.last()) {
        selected += distinctFrequencies.last()
    }
    return selected.map { it to formatFrequency(it) }
}

private fun formatFrequency(frequency: Float): String = when {
    frequency >= 1_000f && frequency % 1_000f == 0f -> "${(frequency / 1_000f).roundToInt()}k"
    frequency >= 1_000f -> "${(frequency / 1_000f * 10f).roundToInt() / 10f}k"
    else -> frequency.roundToInt().toString()
}

private fun logarithmicPosition(frequency: Float, minimum: Float, maximum: Float): Float {
    if (maximum <= minimum) return 1f
    return (ln(frequency / minimum) / ln(maximum / minimum)).coerceIn(0f, 1f)
}

private val Color.argb: Int
    get() = android.graphics.Color.argb(
        (alpha * 255).roundToInt(),
        (red * 255).roundToInt(),
        (green * 255).roundToInt(),
        (blue * 255).roundToInt(),
    )

private fun Color.toArgb(): Int = argb

private val SPECTRUM_DB_TICKS = listOf(-60, -48, -36, -24, -12, 0)
private const val PHASE_TRACE_SCALE = 2f
private const val PHASE_ROTATION_DEGREES = -45f
private val PHASE_POINT_SIZE = 2.dp
private const val PHASE_POINT_LIFETIME_NANOS = 1_000_000_000L
private const val PEAK_HOLD_MILLIS = 700L
private const val PEAK_FALL_PER_SECOND = 0.36f
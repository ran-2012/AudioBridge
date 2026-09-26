package dev.ran.audiobridge.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import dev.ran.audiobridge.audio.AudioVisualizationAnalyzer
import dev.ran.audiobridge.audio.AudioVisualizationActivity
import dev.ran.audiobridge.audio.AudioVisualizationFrame
import dev.ran.audiobridge.audio.AudioVisualizationState
import dev.ran.audiobridge.audio.StereoPhasePoint
import dev.ran.audiobridge.model.PlaybackUiState
import dev.ran.audiobridge.model.WindowsAppVolumeSession
import dev.ran.audiobridge.ui.theme.AudioBridgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AudioBridgeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theme_remainsDarkForLightAndDarkSystemModes() {
        val expectedBackground = Color(0xFF111113).toString()
        var nightMode by mutableIntStateOf(Configuration.UI_MODE_NIGHT_NO)
        composeRule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                AudioBridgeTheme {
                    Text(MaterialTheme.colorScheme.background.toString())
                }
            }
        }

        composeRule.onNodeWithText(expectedBackground).assertExists()
        composeRule.runOnUiThread { nightMode = Configuration.UI_MODE_NIGHT_YES }
        composeRule.onNodeWithText(expectedBackground).assertExists()
    }

    @Test
    fun tabsSwitchWithoutLosingDetailsReturnPage() {
        composeRule.setContent {
            AudioBridgeTheme {
                TestAudioBridgeScreen()
            }
        }

        composeRule.onNodeWithText("频谱").performClick()
        composeRule.onNodeWithTag("visualizer-root").assertExists()
        composeRule.onNodeWithTag("details-button").performClick()
        composeRule.onNodeWithText("当前会话与日志").assertExists()
        composeRule.onNodeWithTag("details-close").performClick()

        composeRule.onNodeWithTag("visualizer-root").assertExists()
        composeRule.onNodeWithContentDescription("左右声道 Lissajous 相位图").assertExists()
        composeRule.onNodeWithContentDescription("左右双声道实时频谱").assertExists()
    }

    @Test
    fun phonePortrait_visualizerRegionsDoNotOverlapOrResize() {
        assertVisualizerLayout(DpSize(412.dp, 915.dp))
    }

    @Test
    fun tabletLandscape_visualizerRegionsDoNotOverlapOrResize() {
        assertVisualizerLayout(DpSize(1280.dp, 800.dp))
    }

    private fun assertVisualizerLayout(size: DpSize) {
        var frame by mutableStateOf(sampleFrame(-42f))
        composeRule.setContent {
            Box(modifier = androidx.compose.ui.Modifier.requiredSize(size)) {
                AudioBridgeTheme {
                    AudioVisualizerScreen(
                        uiState = PlaybackUiState(serviceRunning = true, isConnected = true, isPlaying = true),
                        visualizationState = AudioVisualizationState(AudioVisualizationActivity.Active, frame),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val initialPhase = bounds("phase-plot")
        val initialMeter = bounds("level-meter")
        val initialSpectrum = bounds("spectrum-plot")
        assertFalse(initialPhase.overlaps(initialMeter))
        assertFalse(initialSpectrum.overlaps(initialPhase))
        assertFalse(initialSpectrum.overlaps(initialMeter))

        composeRule.runOnUiThread { frame = sampleFrame(-3f) }
        composeRule.waitForIdle()

        assertEquals(initialPhase, bounds("phase-plot"))
        assertEquals(initialMeter, bounds("level-meter"))
        assertEquals(initialSpectrum, bounds("spectrum-plot"))
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun sampleFrame(levelDbfs: Float) = AudioVisualizationFrame(
        leftSpectrum = List(AudioVisualizationAnalyzer.SPECTRUM_BAND_COUNT) { it / 32f },
        rightSpectrum = List(AudioVisualizationAnalyzer.SPECTRUM_BAND_COUNT) { (32 - it) / 32f },
        phasePoints = List(AudioVisualizationAnalyzer.PHASE_POINT_COUNT) { index ->
            val value = index.toFloat() / AudioVisualizationAnalyzer.PHASE_POINT_COUNT * 2f - 1f
            StereoPhasePoint(value, value)
        },
        levelDbfs = levelDbfs,
        capturedAtElapsedRealtimeMillis = 1L,
    )

    @androidx.compose.runtime.Composable
    private fun TestAudioBridgeScreen() {
        AudioBridgeScreen(
            uiState = PlaybackUiState(serviceRunning = true, isConnected = true),
            visualizationState = AudioVisualizationState(
                AudioVisualizationActivity.Active,
                sampleFrame(-12f),
            ),
            onStartService = {},
            onStopService = {},
            onReconnect = {},
            onConnectUsb = {},
            onConnectLanServer = { _, _ -> },
            onVolumeChanged = {},
            onPlaybackCacheChanged = {},
            onApplyScreenOffPlaybackCachePreset = {},
            onOpenBatteryOptimizationSettings = {},
            onRequestWindowsVolumeSnapshot = {},
            onWindowsMasterVolumeChanged = {},
            onWindowsMasterMuteChanged = {},
            onWindowsSessionVolumeChanged = { _, _ -> },
            onWindowsSessionMuteChanged = { _, _ -> },
            onHideWindowsApp = { _: WindowsAppVolumeSession -> },
            onUnhideWindowsApp = {},
        )
    }

}
package dev.ran.audiobridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ran.audiobridge.ui.AudioBridgeScreen
import dev.ran.audiobridge.ui.theme.AudioBridgeTheme
import dev.ran.audiobridge.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.autoStartServiceIfNeeded()
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            AudioBridgeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    AudioBridgeScreen(
                        uiState = uiState,
                        onStartService = viewModel::startService,
                        onStopService = viewModel::stopService,
                        onVolumeChanged = viewModel::updateVolume,
                        onRequestWindowsVolumeSnapshot = viewModel::requestWindowsVolumeSnapshot,
                        onWindowsMasterVolumeChanged = viewModel::updateWindowsMasterVolume,
                        onWindowsMasterMuteChanged = viewModel::updateWindowsMasterMute,
                        onWindowsSessionVolumeChanged = viewModel::updateWindowsSessionVolume,
                        onWindowsSessionMuteChanged = viewModel::updateWindowsSessionMute,
                    )
                }
            }
        }
    }
}
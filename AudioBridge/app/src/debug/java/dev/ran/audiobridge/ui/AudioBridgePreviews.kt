package dev.ran.audiobridge.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.ran.audiobridge.model.PlaybackSessionInfo
import dev.ran.audiobridge.model.PlaybackUiState
import dev.ran.audiobridge.model.WindowsAppVolumeSession
import dev.ran.audiobridge.model.WindowsMasterVolume
import dev.ran.audiobridge.model.WindowsVolumeCatalog
import dev.ran.audiobridge.ui.theme.AudioBridgeTheme

private object PreviewPlaybackStates {
    private val sessions = listOf(
        WindowsAppVolumeSession(
            sessionId = "chrome",
            processId = 1204,
            processName = "chrome.exe",
            displayName = "Google Chrome",
            state = "Active",
            volume = 0.64f,
        ),
        WindowsAppVolumeSession(
            sessionId = "steam",
            processId = 2480,
            processName = "steamwebhelper.exe",
            displayName = "Steam",
            state = "Active",
            volume = 0.82f,
            isMuted = true,
        ),
        WindowsAppVolumeSession(
            sessionId = "long-name",
            processId = 3912,
            processName = "Code - Insiders Remote Workspace.exe",
            displayName = "Visual Studio Code - Insiders Remote Workspace",
            state = "Active",
            volume = 0.38f,
        ),
        WindowsAppVolumeSession(
            sessionId = "powertoys",
            processId = 4288,
            processName = "PowerToys.AlwaysOnTop.exe",
            displayName = "PowerToys Always On Top",
            state = "Inactive",
            volume = 0.55f,
        ),
    )

    val playing = PlaybackUiState(
        serviceRunning = true,
        isConnected = true,
        isPlaying = true,
        statusMessage = "正在播放 Windows 音频",
        connectTarget = "DESKTOP-43HIOR2 / USB",
        rttMillis = 18,
        isIgnoringBatteryOptimizations = true,
        batteryOptimizationStatusMessage = "已允许后台不受限运行",
        volume = 0.86f,
        playbackCacheMilliseconds = 60,
        lastSequence = 8281u,
        sessionInfo = PlaybackSessionInfo(
            encoding = "PCM16",
            sampleRate = 48_000,
            channels = 2,
            bitsPerSample = 16,
            bufferMilliseconds = 20,
        ),
        windowsVolumeCatalog = WindowsVolumeCatalog(
            capturedAtMillis = 1_789_222_800_000,
            masterVolume = WindowsMasterVolume(
                deviceId = "preview-device",
                deviceName = "Steam Streaming Speakers",
                volume = 0.72f,
            ),
            sessions = sessions,
        ),
        windowsVolumeStatusMessage = "已同步 4 个应用",
        recentLogs = listOf(
            "22:17:58 Audio: playing=true latency=18ms",
            "22:17:52 Audio: frame sequence=8200 bytes=11520",
            "22:17:40 Protocol: heartbeat acknowledged",
        ),
    )

    val disconnected = playing.copy(
        isConnected = false,
        isPlaying = false,
        statusMessage = "与 Windows 的连接已断开",
        rttMillis = null,
        windowsVolumeStatusMessage = "等待重新连接",
        windowsVolumeErrorMessage = "无法连接 Windows，请检查 USB 或局域网连接。",
    )

    val loading = playing.copy(
        windowsVolumeLoading = true,
        windowsVolumeStatusMessage = "正在同步 Windows 应用音量...",
    )

    val empty = playing.copy(
        windowsVolumeCatalog = playing.windowsVolumeCatalog.copy(sessions = emptyList()),
        windowsVolumeStatusMessage = "未发现活动音频会话",
    )
}

@Composable
private fun MainPagePreview(uiState: PlaybackUiState) {
    AudioBridgeTheme(dynamicColor = false) {
        MainPageScreen(
            uiState = uiState,
            contentPadding = PaddingValues(),
            onVolumeChanged = {},
            onPlaybackCacheChanged = {},
            onRequestWindowsVolumeSnapshot = {},
            onWindowsMasterVolumeChanged = {},
            onWindowsMasterMuteChanged = {},
            onWindowsSessionVolumeChanged = { _, _ -> },
            onWindowsSessionMuteChanged = { _, _ -> },
        )
    }
}

@Preview(name = "Phone - Playing", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PhonePlayingPreview() = MainPagePreview(PreviewPlaybackStates.playing)

@Preview(name = "Phone - Disconnected", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PhoneDisconnectedPreview() = MainPagePreview(PreviewPlaybackStates.disconnected)

@Preview(name = "Phone - Loading", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PhoneLoadingPreview() = MainPagePreview(PreviewPlaybackStates.loading)

@Preview(name = "Phone - Empty", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PhoneEmptyPreview() = MainPagePreview(PreviewPlaybackStates.empty)

@Preview(name = "Tablet - Playing", widthDp = 1024, heightDp = 720, showBackground = true)
@Composable
private fun TabletPlayingPreview() = MainPagePreview(PreviewPlaybackStates.playing)
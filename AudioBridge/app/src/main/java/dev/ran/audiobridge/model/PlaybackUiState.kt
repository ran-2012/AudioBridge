package dev.ran.audiobridge.model

import dev.ran.audiobridge.audio.PlaybackCacheConfig

data class PlaybackUiState(
    val serviceRunning: Boolean = false,
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val statusMessage: String = "服务未启动",
    val volume: Float = 1.0f,
    val playbackCacheMilliseconds: Int = PlaybackCacheConfig.DEFAULT_MILLISECONDS,
    val lastSequence: UInt = 0u,
    val sessionInfo: PlaybackSessionInfo = PlaybackSessionInfo(),
    val windowsVolumeCatalog: WindowsVolumeCatalog = WindowsVolumeCatalog(),
    val windowsVolumeLoading: Boolean = false,
    val windowsVolumeStatusMessage: String = "等待 Windows 音量目录",
    val windowsVolumeErrorMessage: String? = null,
    val recentLogs: List<String> = emptyList(),
)

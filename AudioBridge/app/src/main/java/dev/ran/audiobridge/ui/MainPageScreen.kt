package dev.ran.audiobridge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.ran.audiobridge.audio.PlaybackCacheConfig
import dev.ran.audiobridge.model.PlaybackUiState
import dev.ran.audiobridge.model.WindowsAppVolumeSession
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun MainPageScreen(
    uiState: PlaybackUiState,
    contentPadding: PaddingValues,
    onVolumeChanged: (Float) -> Unit,
    onPlaybackCacheChanged: (Int) -> Unit,
    onRequestWindowsVolumeSnapshot: () -> Unit,
    onWindowsMasterVolumeChanged: (Float) -> Unit,
    onWindowsMasterMuteChanged: (Boolean) -> Unit,
    onWindowsSessionVolumeChanged: (String, Float) -> Unit,
    onWindowsSessionMuteChanged: (String, Boolean) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTabletLandscape = configuration.screenWidthDp >= 840 && configuration.screenWidthDp > configuration.screenHeightDp

    if (isTabletLandscape) {
        TabletLandscapeMainPage(
            uiState = uiState,
            contentPadding = contentPadding,
            onVolumeChanged = onVolumeChanged,
            onPlaybackCacheChanged = onPlaybackCacheChanged,
            onRequestWindowsVolumeSnapshot = onRequestWindowsVolumeSnapshot,
            onWindowsMasterVolumeChanged = onWindowsMasterVolumeChanged,
            onWindowsMasterMuteChanged = onWindowsMasterMuteChanged,
            onWindowsSessionVolumeChanged = onWindowsSessionVolumeChanged,
            onWindowsSessionMuteChanged = onWindowsSessionMuteChanged,
        )
    } else {
        PhoneMainPage(
            uiState = uiState,
            contentPadding = contentPadding,
            onVolumeChanged = onVolumeChanged,
            onPlaybackCacheChanged = onPlaybackCacheChanged,
            onRequestWindowsVolumeSnapshot = onRequestWindowsVolumeSnapshot,
            onWindowsMasterVolumeChanged = onWindowsMasterVolumeChanged,
            onWindowsMasterMuteChanged = onWindowsMasterMuteChanged,
            onWindowsSessionVolumeChanged = onWindowsSessionVolumeChanged,
            onWindowsSessionMuteChanged = onWindowsSessionMuteChanged,
        )
    }
}

@Composable
private fun PhoneMainPage(
    uiState: PlaybackUiState,
    contentPadding: PaddingValues,
    onVolumeChanged: (Float) -> Unit,
    onPlaybackCacheChanged: (Int) -> Unit,
    onRequestWindowsVolumeSnapshot: () -> Unit,
    onWindowsMasterVolumeChanged: (Float) -> Unit,
    onWindowsMasterMuteChanged: (Boolean) -> Unit,
    onWindowsSessionVolumeChanged: (String, Float) -> Unit,
    onWindowsSessionMuteChanged: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RunningStatusCard(uiState = uiState)
        PlaybackVolumeCard(uiState = uiState, onVolumeChanged = onVolumeChanged, onPlaybackCacheChanged = onPlaybackCacheChanged)
        WindowsVolumeControlCard(
            uiState = uiState,
            onRequestWindowsVolumeSnapshot = onRequestWindowsVolumeSnapshot,
            onWindowsMasterVolumeChanged = onWindowsMasterVolumeChanged,
            onWindowsMasterMuteChanged = onWindowsMasterMuteChanged,
            onWindowsSessionVolumeChanged = onWindowsSessionVolumeChanged,
            onWindowsSessionMuteChanged = onWindowsSessionMuteChanged,
        )
    }
}

@Composable
private fun TabletLandscapeMainPage(
    uiState: PlaybackUiState,
    contentPadding: PaddingValues,
    onVolumeChanged: (Float) -> Unit,
    onPlaybackCacheChanged: (Int) -> Unit,
    onRequestWindowsVolumeSnapshot: () -> Unit,
    onWindowsMasterVolumeChanged: (Float) -> Unit,
    onWindowsMasterMuteChanged: (Boolean) -> Unit,
    onWindowsSessionVolumeChanged: (String, Float) -> Unit,
    onWindowsSessionMuteChanged: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabletMasterVolumeCard(
            uiState = uiState,
            onVolumeChanged = onVolumeChanged,
            onPlaybackCacheChanged = onPlaybackCacheChanged,
            onRequestWindowsVolumeSnapshot = onRequestWindowsVolumeSnapshot,
            onWindowsMasterVolumeChanged = onWindowsMasterVolumeChanged,
            onWindowsMasterMuteChanged = onWindowsMasterMuteChanged,
        )
        TabletSessionsCard(
            modifier = Modifier.weight(1f),
            uiState = uiState,
            onWindowsSessionVolumeChanged = onWindowsSessionVolumeChanged,
            onWindowsSessionMuteChanged = onWindowsSessionMuteChanged,
        )
    }
}

@Composable
private fun TabletMasterVolumeCard(
    uiState: PlaybackUiState,
    onVolumeChanged: (Float) -> Unit,
    onPlaybackCacheChanged: (Int) -> Unit,
    onRequestWindowsVolumeSnapshot: () -> Unit,
    onWindowsMasterVolumeChanged: (Float) -> Unit,
    onWindowsMasterMuteChanged: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Windows 主音量", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${uiState.windowsVolumeCatalog.masterVolume.deviceName} · ${(uiState.windowsVolumeCatalog.masterVolume.volume * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "最近同步：${formatTimestamp(uiState.windowsVolumeCatalog.capturedAtMillis)} · ${uiState.windowsVolumeStatusMessage}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    uiState.windowsVolumeErrorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                        Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                FilledTonalButton(onClick = onRequestWindowsVolumeSnapshot) {
                    Text(if (uiState.windowsVolumeLoading) "同步中..." else "刷新")
                }
                OutlinedButton(onClick = { onWindowsMasterMuteChanged(!uiState.windowsVolumeCatalog.masterVolume.isMuted) }) {
                    Text(if (uiState.windowsVolumeCatalog.masterVolume.isMuted) "取消静音" else "静音")
                }
            }

            Slider(
                value = uiState.windowsVolumeCatalog.masterVolume.volume,
                onValueChange = onWindowsMasterVolumeChanged,
                valueRange = 0f..1f,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("本机播放音量", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = uiState.volume,
                    onValueChange = onVolumeChanged,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )
                Text("${(uiState.volume * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("播放缓存", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = uiState.playbackCacheMilliseconds.toFloat(),
                    onValueChange = { onPlaybackCacheChanged(it.toInt()) },
                    valueRange = PlaybackCacheConfig.MIN_MILLISECONDS.toFloat()..PlaybackCacheConfig.MAX_MILLISECONDS.toFloat(),
                    steps = PlaybackCacheConfig.sliderSteps(),
                    modifier = Modifier.weight(1f),
                )
                Text("${uiState.playbackCacheMilliseconds}ms", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TabletSessionsCard(
    modifier: Modifier = Modifier,
    uiState: PlaybackUiState,
    onWindowsSessionVolumeChanged: (String, Float) -> Unit,
    onWindowsSessionMuteChanged: (String, Boolean) -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.SpaceBetween,
//                verticalAlignment = Alignment.CenterVertically,
//            ) {
//                Text("应用音频会话", style = MaterialTheme.typography.titleMedium)
//                Text("${uiState.windowsVolumeCatalog.sessions.size} 个", style = MaterialTheme.typography.bodyMedium)
//            }

            if (uiState.windowsVolumeCatalog.sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("当前还没有可展示的 Windows 应用音量会话。")
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = uiState.windowsVolumeCatalog.sessions,
                        key = { session -> session.sessionId },
                    ) { session ->
                        TabletWindowsSessionCard(
                            session = session,
                            onVolumeChanged = { volume -> onWindowsSessionVolumeChanged(session.sessionId, volume) },
                            onToggleMute = { onWindowsSessionMuteChanged(session.sessionId, !session.isMuted) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletWindowsSessionCard(
    session: WindowsAppVolumeSession,
    onVolumeChanged: (Float) -> Unit,
    onToggleMute: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(148.dp)
            .fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SessionIcon(session)
            Text(
                text = session.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = session.processName.ifBlank { "unknown" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
            Text(
                text = "${(session.volume * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Slider(
                    value = session.volume,
                    onValueChange = onVolumeChanged,
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .width(190.dp)
                        .rotate(-90f),
                )
            }
            OutlinedButton(onClick = onToggleMute) {
                Text(if (session.isMuted) "取消静音" else "静音")
            }
        }
    }
}

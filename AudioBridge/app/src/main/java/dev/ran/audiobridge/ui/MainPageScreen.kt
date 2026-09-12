package dev.ran.audiobridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import dev.ran.audiobridge.audio.PlaybackCacheConfig
import dev.ran.audiobridge.model.HiddenWindowsAppSupport
import dev.ran.audiobridge.model.PlaybackUiState
import dev.ran.audiobridge.model.WindowsAppVolumeSession

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
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhoneWindowsVolumeControlCard(
            uiState = uiState,
            onRequestWindowsVolumeSnapshot = onRequestWindowsVolumeSnapshot,
            onWindowsMasterVolumeChanged = onWindowsMasterVolumeChanged,
            onWindowsMasterMuteChanged = onWindowsMasterMuteChanged,
            onWindowsSessionVolumeChanged = onWindowsSessionVolumeChanged,
            onWindowsSessionMuteChanged = onWindowsSessionMuteChanged,
        )
        RunningStatusCard(uiState = uiState)
        PlaybackVolumeCard(uiState = uiState, onVolumeChanged = onVolumeChanged, onPlaybackCacheChanged = onPlaybackCacheChanged)
    }
}

@Composable
private fun PhoneWindowsVolumeControlCard(
    uiState: PlaybackUiState,
    onRequestWindowsVolumeSnapshot: () -> Unit,
    onWindowsMasterVolumeChanged: (Float) -> Unit,
    onWindowsMasterMuteChanged: (Boolean) -> Unit,
    onWindowsSessionVolumeChanged: (String, Float) -> Unit,
    onWindowsSessionMuteChanged: (String, Boolean) -> Unit,
) {
    val visibleSessions = HiddenWindowsAppSupport.filterVisibleSessions(
        sessions = uiState.windowsVolumeCatalog.sessions,
        hiddenProcessNames = uiState.hiddenProcessNames,
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WindowsMasterVolumeCard(
            uiState = uiState,
            onRequestWindowsVolumeSnapshot = onRequestWindowsVolumeSnapshot,
            onWindowsMasterVolumeChanged = onWindowsMasterVolumeChanged,
            onWindowsMasterMuteChanged = onWindowsMasterMuteChanged,
        )
        SessionsHeader(sessionCount = visibleSessions.size, onRefresh = onRequestWindowsVolumeSnapshot)
        SessionsState(
            uiState = uiState,
            visibleSessions = visibleSessions,
            onRefresh = onRequestWindowsVolumeSnapshot,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visibleSessions.forEach { session ->
                    CompactWindowsSessionCard(
                        session = session,
                        onVolumeChanged = { volume -> onWindowsSessionVolumeChanged(session.sessionId, volume) },
                        onToggleMute = { onWindowsSessionMuteChanged(session.sessionId, !session.isMuted) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactWindowsSessionCard(
    session: WindowsAppVolumeSession,
    onVolumeChanged: (Float) -> Unit,
    onToggleMute: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SessionIcon(session)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = session.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = session.processName.ifBlank { "unknown" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${(session.volume * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = session.volume,
                    onValueChange = onVolumeChanged,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )
                OutlinedIconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(44.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Icon(
                        imageVector = if (session.isMuted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (session.isMuted) "取消 ${session.displayName} 静音" else "将 ${session.displayName} 静音",
                        tint = if (session.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
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
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier.weight(0.82f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WindowsMasterVolumeCard(
                uiState = uiState,
                onRequestWindowsVolumeSnapshot = onRequestWindowsVolumeSnapshot,
                onWindowsMasterVolumeChanged = onWindowsMasterVolumeChanged,
                onWindowsMasterMuteChanged = onWindowsMasterMuteChanged,
            )
            RunningStatusCard(uiState = uiState)
            PlaybackVolumeCard(
                uiState = uiState,
                onVolumeChanged = onVolumeChanged,
                onPlaybackCacheChanged = onPlaybackCacheChanged,
            )
        }
        TabletSessionsCard(
            modifier = Modifier.weight(1.48f),
            uiState = uiState,
            onRequestWindowsVolumeSnapshot = onRequestWindowsVolumeSnapshot,
            onWindowsSessionVolumeChanged = onWindowsSessionVolumeChanged,
            onWindowsSessionMuteChanged = onWindowsSessionMuteChanged,
        )
    }
}

@Composable
internal fun ScreenOffStabilityCard(
    uiState: PlaybackUiState,
    onApplyScreenOffPlaybackCachePreset: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
) {
    val isIgnoringBatteryOptimizations = uiState.isIgnoringBatteryOptimizations
    val statusColor = when (isIgnoringBatteryOptimizations) {
        true -> Color(0xFF1B8A5A)
        false -> Color(0xFFD06A00)
        null -> MaterialTheme.colorScheme.outline
    }
    val shouldRecommendHigherCache =
        uiState.playbackCacheMilliseconds < PlaybackCacheConfig.SCREEN_OFF_RECOMMENDED_MILLISECONDS

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .height(12.dp)
                        .width(12.dp)
                        .rotate(45f)
                        .background(statusColor, CircleShape),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("锁屏稳定性", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (isIgnoringBatteryOptimizations) {
                            true -> "后台已放开，当前更可能受瞬时调度抖动影响。"
                            false -> "系统仍可能在熄屏后限流，优先关闭电池优化。"
                            null -> "正在检查系统是否允许后台不受限运行。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Text(uiState.batteryOptimizationStatusMessage, style = MaterialTheme.typography.bodySmall)
            Text(
                "建议锁屏缓存：${PlaybackCacheConfig.SCREEN_OFF_RECOMMENDED_MILLISECONDS}ms，当前：${uiState.playbackCacheMilliseconds}ms",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onOpenBatteryOptimizationSettings,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isIgnoringBatteryOptimizations == true) "查看电池设置" else "允许后台不受限")
                }

                OutlinedButton(
                    onClick = onApplyScreenOffPlaybackCachePreset,
                    modifier = Modifier.weight(1f),
                    enabled = shouldRecommendHigherCache,
                ) {
                    Text(if (shouldRecommendHigherCache) "切到 240ms" else "缓存已达建议值")
                }
            }

            Text(
                "如果你用的是小米、华为、OPPO、vivo 等 ROM，还需要在系统设置里额外允许自启动、后台活动和锁屏运行。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TabletSessionsCard(
    modifier: Modifier = Modifier,
    uiState: PlaybackUiState,
    onRequestWindowsVolumeSnapshot: () -> Unit,
    onWindowsSessionVolumeChanged: (String, Float) -> Unit,
    onWindowsSessionMuteChanged: (String, Boolean) -> Unit,
) {
    val visibleSessions = HiddenWindowsAppSupport.filterVisibleSessions(
        sessions = uiState.windowsVolumeCatalog.sessions,
        hiddenProcessNames = uiState.hiddenProcessNames,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SessionsHeader(sessionCount = visibleSessions.size, onRefresh = onRequestWindowsVolumeSnapshot)
        SessionsState(uiState, visibleSessions, onRefresh = onRequestWindowsVolumeSnapshot) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(250.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visibleSessions, key = { it.sessionId }) { session ->
                    CompactWindowsSessionCard(
                        session = session,
                        onVolumeChanged = { volume -> onWindowsSessionVolumeChanged(session.sessionId, volume) },
                        onToggleMute = { onWindowsSessionMuteChanged(session.sessionId, !session.isMuted) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WindowsMasterVolumeCard(
    uiState: PlaybackUiState,
    onRequestWindowsVolumeSnapshot: () -> Unit,
    onWindowsMasterVolumeChanged: (Float) -> Unit,
    onWindowsMasterMuteChanged: (Boolean) -> Unit,
) {
    val master = uiState.windowsVolumeCatalog.masterVolume
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Windows 主音量", style = MaterialTheme.typography.titleMedium)
                    Text(
                        master.deviceName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${(master.volume * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { onWindowsMasterMuteChanged(!master.isMuted) }) {
                    Icon(
                        imageVector = if (master.isMuted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (master.isMuted) "取消 Windows 主音量静音" else "将 Windows 主音量静音",
                        tint = if (master.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Slider(
                value = master.volume,
                onValueChange = onWindowsMasterVolumeChanged,
                valueRange = 0f..1f,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${uiState.windowsVolumeStatusMessage}  ${formatTimestamp(uiState.windowsVolumeCatalog.capturedAtMillis)}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBFC5CC),
                )
                IconButton(onClick = onRequestWindowsVolumeSnapshot, enabled = !uiState.windowsVolumeLoading) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "刷新 Windows 音量",
                        tint = Color(0xFFF7F8FA),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionsHeader(sessionCount: Int, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("应用音量", style = MaterialTheme.typography.titleMedium)
            Text("$sessionCount 个 Windows 音频会话", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "刷新应用音量会话")
        }
    }
}

@Composable
private fun SessionsState(
    uiState: PlaybackUiState,
    visibleSessions: List<WindowsAppVolumeSession>,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    when {
        uiState.windowsVolumeLoading && visibleSessions.isEmpty() -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Text("正在同步 Windows 应用音量")
            }
        }
        !uiState.windowsVolumeErrorMessage.isNullOrBlank() -> SessionMessage(
            title = "无法同步应用音量",
            message = uiState.windowsVolumeErrorMessage,
            action = "重新同步",
            onAction = onRefresh,
        )
        visibleSessions.isEmpty() -> SessionMessage(
            title = "暂无音频会话",
            message = "在 Windows 上播放音频后刷新，会话会显示在这里。",
            action = "刷新会话",
            onAction = onRefresh,
        )
        else -> content()
    }
}

@Composable
private fun SessionMessage(title: String, message: String, action: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

package dev.ran.audiobridge.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.ran.audiobridge.audio.PlaybackCacheConfig
import dev.ran.audiobridge.model.PlaybackUiState
import dev.ran.audiobridge.model.WindowsAppVolumeSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class RunningStatusUi(
    val color: Color,
    val label: String,
    val description: String,
)

private enum class AudioBridgePage {
    Main,
    Details,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioBridgeScreen(
    uiState: PlaybackUiState,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onPlaybackCacheChanged: (Int) -> Unit,
    onRequestWindowsVolumeSnapshot: () -> Unit,
    onWindowsMasterVolumeChanged: (Float) -> Unit,
    onWindowsMasterMuteChanged: (Boolean) -> Unit,
    onWindowsSessionVolumeChanged: (String, Float) -> Unit,
    onWindowsSessionMuteChanged: (String, Boolean) -> Unit,
) {
    var currentPage by rememberSaveable { mutableStateOf(AudioBridgePage.Main.name) }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val page = remember(currentPage) {
        if (currentPage == AudioBridgePage.Details.name) {
            AudioBridgePage.Details
        } else {
            AudioBridgePage.Main
        }
    }

    Scaffold(
        topBar = {
            if (page == AudioBridgePage.Main) {
                val runningStatus = remember(uiState) { buildRunningStatusUi(uiState) }

                TopAppBar(
                    title = { Text("AudioBridge") },
                    actions = {
                        StatusBadgeCompact(status = runningStatus)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Text("⋯", style = MaterialTheme.typography.headlineMedium)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("当前会话与日志") },
                                    onClick = {
                                        menuExpanded = false
                                        currentPage = AudioBridgePage.Details.name
                                    },
                                )
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("当前会话与日志") },
                    navigationIcon = {
                        TextButton(onClick = { currentPage = AudioBridgePage.Main.name }) {
                            Text("返回")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        when (page) {
            AudioBridgePage.Main -> MainPageScreen(
                uiState = uiState,
                contentPadding = innerPadding,
                onVolumeChanged = onVolumeChanged,
                onPlaybackCacheChanged = onPlaybackCacheChanged,
                onRequestWindowsVolumeSnapshot = onRequestWindowsVolumeSnapshot,
                onWindowsMasterVolumeChanged = onWindowsMasterVolumeChanged,
                onWindowsMasterMuteChanged = onWindowsMasterMuteChanged,
                onWindowsSessionVolumeChanged = onWindowsSessionVolumeChanged,
                onWindowsSessionMuteChanged = onWindowsSessionMuteChanged,
            )

            AudioBridgePage.Details -> DetailsPageScreen(
                uiState = uiState,
                contentPadding = innerPadding,
                onStartService = onStartService,
                onStopService = onStopService,
            )
        }
    }
}

@Composable
internal fun RunningStatusCard(uiState: PlaybackUiState) {
    val status = remember(uiState) { buildRunningStatusUi(uiState) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("运行状态", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(status.color),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(status.label, style = MaterialTheme.typography.headlineSmall)
                    Text(status.description, style = MaterialTheme.typography.bodyMedium)
                    Text(uiState.statusMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StatusBadgeCompact(status: RunningStatusUi) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(status.color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(status.color),
        )
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun PlaybackVolumeCard(
    uiState: PlaybackUiState,
    onVolumeChanged: (Float) -> Unit,
    onPlaybackCacheChanged: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("播放音量", style = MaterialTheme.typography.titleMedium)
            Text("当前音量：${(uiState.volume * 100).toInt()}%")
            Slider(
                value = uiState.volume,
                onValueChange = onVolumeChanged,
                valueRange = 0f..1f,
            )
            Text("该音量仅控制 AudioBridge 播放输出，不直接修改系统媒体音量。")

            Text("播放缓存：${uiState.playbackCacheMilliseconds} ms", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = uiState.playbackCacheMilliseconds.toFloat(),
                onValueChange = { onPlaybackCacheChanged(it.toInt()) },
                valueRange = PlaybackCacheConfig.MIN_MILLISECONDS.toFloat()..PlaybackCacheConfig.MAX_MILLISECONDS.toFloat(),
                steps = PlaybackCacheConfig.sliderSteps(),
            )
            Text("缓存越短延迟越低，缓存越长越抗抖动。检测到积压时会优先丢弃旧音频。")
        }
    }
}

@Composable
internal fun WindowsVolumeControlCard(
    uiState: PlaybackUiState,
    onRequestWindowsVolumeSnapshot: () -> Unit,
    onWindowsMasterVolumeChanged: (Float) -> Unit,
    onWindowsMasterMuteChanged: (Boolean) -> Unit,
    onWindowsSessionVolumeChanged: (String, Float) -> Unit,
    onWindowsSessionMuteChanged: (String, Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Windows 音量控制", style = MaterialTheme.typography.titleMedium)
            Text("状态：${uiState.windowsVolumeStatusMessage}")
            Text("最近同步：${formatTimestamp(uiState.windowsVolumeCatalog.capturedAtMillis)}")
            uiState.windowsVolumeErrorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onRequestWindowsVolumeSnapshot) {
                    Text(if (uiState.windowsVolumeLoading) "同步中..." else "刷新 Windows 音量")
                }
                OutlinedButton(onClick = { onWindowsMasterMuteChanged(!uiState.windowsVolumeCatalog.masterVolume.isMuted) }) {
                    Text(if (uiState.windowsVolumeCatalog.masterVolume.isMuted) "取消主静音" else "主静音")
                }
            }
            Text("设备：${uiState.windowsVolumeCatalog.masterVolume.deviceName}")
            Text("主音量：${(uiState.windowsVolumeCatalog.masterVolume.volume * 100).toInt()}% / ${if (uiState.windowsVolumeCatalog.masterVolume.isMuted) "静音" else "未静音"}")
            Slider(
                value = uiState.windowsVolumeCatalog.masterVolume.volume,
                onValueChange = onWindowsMasterVolumeChanged,
                valueRange = 0f..1f,
            )
            Text("应用会话数：${uiState.windowsVolumeCatalog.sessions.size}")
            if (uiState.windowsVolumeCatalog.sessions.isEmpty()) {
                Text("当前还没有可展示的 Windows 应用音量会话。")
            } else {
                uiState.windowsVolumeCatalog.sessions.forEach { session ->
                    WindowsSessionCard(
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
internal fun WindowsSessionCard(
    session: WindowsAppVolumeSession,
    onVolumeChanged: (Float) -> Unit,
    onToggleMute: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SessionIcon(session)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(session.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${session.processName.ifBlank { "unknown" }} / ${session.state}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = onToggleMute) {
                    Text(if (session.isMuted) "取消静音" else "静音")
                }
            }

            Text("音量：${(session.volume * 100).toInt()}%")
            Slider(value = session.volume, onValueChange = onVolumeChanged, valueRange = 0f..1f)
        }
    }
}

@Composable
internal fun SessionIcon(session: WindowsAppVolumeSession) {
    val bitmap = remember(session.iconBase64) {
        session.iconBase64?.let(::decodeBase64Bitmap)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = session.displayName,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        return
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = session.displayName.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

internal fun decodeBase64Bitmap(base64: String) = runCatching {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

internal fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "未同步"
    }

    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

internal fun buildRunningStatusUi(uiState: PlaybackUiState): RunningStatusUi {
    return when {
        !uiState.serviceRunning -> RunningStatusUi(
            color = Color(0xFFB3261E),
            label = "未启动",
            description = "后台播放服务未运行",
        )

        uiState.isConnected && uiState.isPlaying -> RunningStatusUi(
            color = Color(0xFF2E7D32),
            label = "运行中",
            description = "已连接 Windows 并正在播放音频",
        )

        uiState.isConnected -> RunningStatusUi(
            color = Color(0xFF1565C0),
            label = "已连接",
            description = "服务正常，等待新的音频播放数据",
        )

        else -> RunningStatusUi(
            color = Color(0xFFF9A825),
            label = "等待连接",
            description = "服务已启动，正在等待 Windows 端接入",
        )
    }
}

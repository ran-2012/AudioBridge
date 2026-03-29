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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.ran.audiobridge.model.PlaybackUiState
import dev.ran.audiobridge.model.WindowsAppVolumeSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AudioBridgeScreen(
    uiState: PlaybackUiState,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onRequestWindowsVolumeSnapshot: () -> Unit,
    onWindowsMasterVolumeChanged: (Float) -> Unit,
    onWindowsMasterMuteChanged: (Boolean) -> Unit,
    onWindowsSessionVolumeChanged: (String, Float) -> Unit,
    onWindowsSessionMuteChanged: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AudioBridge", style = MaterialTheme.typography.headlineSmall)
                Text("状态：${uiState.statusMessage}")
                Text("服务：${if (uiState.serviceRunning) "运行中" else "未启动"}")
                Text("连接：${if (uiState.isConnected) "已连接" else "未连接"}")
                Text("播放：${if (uiState.isPlaying) "播放中" else "未播放"}")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前会话", style = MaterialTheme.typography.titleMedium)
                Text("编码：${uiState.sessionInfo.encoding}")
                Text("采样率：${uiState.sessionInfo.sampleRate} Hz")
                Text("声道：${uiState.sessionInfo.channels}")
                Text("位深：${uiState.sessionInfo.bitsPerSample}")
                Text("Buffer：${uiState.sessionInfo.bufferMilliseconds} ms")
                Text("最新序号：${uiState.lastSequence}")
            }
        }

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
            }
        }

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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("联调日志", style = MaterialTheme.typography.titleMedium)
                Column(modifier = Modifier.height(220.dp).verticalScroll(rememberScrollState())) {
                    uiState.recentLogs.forEach { log ->
                        Text(text = log, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onStartService) {
                Text("启动后台播放")
            }
            Button(onClick = onStopService) {
                Text("停止后台播放")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("提示：服务启动后会在后台监听 5000 端口，并通过通知栏保持前台服务状态。")
    }
}

@Composable
private fun WindowsSessionCard(
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
private fun SessionIcon(session: WindowsAppVolumeSession) {
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

private fun decodeBase64Bitmap(base64: String) = runCatching {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "未同步"
    }

    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

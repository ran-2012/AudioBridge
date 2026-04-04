package dev.ran.audiobridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ran.audiobridge.model.PlaybackUiState

@Composable
internal fun DetailsPageScreen(
    uiState: PlaybackUiState,
    contentPadding: PaddingValues,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("运行详情", style = MaterialTheme.typography.titleMedium)
                Text("状态：${uiState.statusMessage}")
                Text("服务：${if (uiState.serviceRunning) "运行中" else "未启动"}")
                Text("连接：${if (uiState.isConnected) "已连接" else "未连接"}")
                Text("播放：${if (uiState.isPlaying) "播放中" else "未播放"}")
                Text("最新序号：${uiState.lastSequence}")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onStartService) {
                        Text("启动后台播放")
                    }
                    OutlinedButton(onClick = onStopService) {
                        Text("停止后台播放")
                    }
                }
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
                Text("本地播放缓存：${uiState.playbackCacheMilliseconds} ms")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("联调日志", style = MaterialTheme.typography.titleMedium)
                if (uiState.recentLogs.isEmpty()) {
                    Text("当前没有日志。")
                } else {
                    Column(modifier = Modifier.height(260.dp).verticalScroll(rememberScrollState())) {
                        uiState.recentLogs.forEach { log ->
                            Text(text = log, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Text("提示：服务启动后会在后台监听 5000 端口，并通过通知栏保持前台服务状态。")
        Spacer(modifier = Modifier.height(8.dp))
    }
}

package dev.ran.audiobridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import dev.ran.audiobridge.model.HiddenWindowsApp
import dev.ran.audiobridge.model.HiddenWindowsAppSupport
import dev.ran.audiobridge.model.PlaybackUiState
import dev.ran.audiobridge.model.WindowsAppVolumeSession

@Composable
internal fun DetailsPageScreen(
    uiState: PlaybackUiState,
    contentPadding: PaddingValues,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onConnectUsb: () -> Unit,
    onConnectLanServer: (String, Int) -> Unit,
    onApplyScreenOffPlaybackCachePreset: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onHideWindowsApp: (WindowsAppVolumeSession) -> Unit,
    onUnhideWindowsApp: (String) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isWideLayout = configuration.screenWidthDp >= 840 && configuration.screenWidthDp > configuration.screenHeightDp
    val visibleSessionCandidates = remember(uiState.windowsVolumeCatalog.sessions, uiState.hiddenProcessNames) {
        HiddenWindowsAppSupport.distinctSessionsByProcessName(uiState.windowsVolumeCatalog.sessions)
            .filterNot { uiState.hiddenProcessNames.contains(it.processName.trim()) }
    }

    LazyVerticalGrid(
        columns = if (isWideLayout) GridCells.Fixed(2) else GridCells.Fixed(1),
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenOffStabilityCard(
                uiState = uiState,
                onApplyScreenOffPlaybackCachePreset = onApplyScreenOffPlaybackCachePreset,
                onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings,
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("运行详情", style = MaterialTheme.typography.titleMedium)
                    DetailValueRow("状态", uiState.statusMessage)
                    DetailValueRow("服务", if (uiState.serviceRunning) "运行中" else "未启动")
                    DetailValueRow("连接", if (uiState.isConnected) "已连接" else "未连接")
                    DetailValueRow("播放", if (uiState.isPlaying) "播放中" else "未播放")
                    DetailValueRow("最新序号", uiState.lastSequence.toString())
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
        }

        item {
            LanServerCard(
                uiState = uiState,
                onConnectUsb = onConnectUsb,
                onConnectLanServer = onConnectLanServer,
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前会话", style = MaterialTheme.typography.titleMedium)
                    DetailValueRow("编码", uiState.sessionInfo.encoding)
                    DetailValueRow("采样率", "${uiState.sessionInfo.sampleRate} Hz")
                    DetailValueRow("声道", uiState.sessionInfo.channels.toString())
                    DetailValueRow("位深", uiState.sessionInfo.bitsPerSample.toString())
                    DetailValueRow("发送缓冲", "${uiState.sessionInfo.bufferMilliseconds} ms")
                    DetailValueRow("本地缓存", "${uiState.playbackCacheMilliseconds} ms")
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("联调日志", style = MaterialTheme.typography.titleMedium)
                    if (uiState.recentLogs.isEmpty()) {
                        Text("当前没有日志。")
                    } else {
                        Column(
                            modifier = Modifier
                                .height(220.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            uiState.recentLogs.forEach { log ->
                                Text(text = log, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("应用过滤", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "已隐藏 ${uiState.hiddenWindowsApps.size} 个应用，主页面不会显示这些进程名对应的会话。",
                    style = MaterialTheme.typography.bodySmall,
                )

                Text("已隐藏应用", style = MaterialTheme.typography.titleSmall)
                if (uiState.hiddenWindowsApps.isEmpty()) {
                    Text("当前没有隐藏应用。")
                } else {
                    uiState.hiddenWindowsApps.forEach { app ->
                        HiddenWindowsAppRow(
                            app = app,
                            buttonText = "取消隐藏",
                            onClick = { onUnhideWindowsApp(app.processName) },
                        )
                    }
                }

                Text("当前会话应用", style = MaterialTheme.typography.titleSmall)
                if (visibleSessionCandidates.isEmpty()) {
                    Text("当前没有可加入隐藏列表的应用。")
                } else {
                    visibleSessionCandidates.forEach { session ->
                        VisibleWindowsSessionRow(
                            session = session,
                            onHide = { onHideWindowsApp(session) },
                        )
                    }
                }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "服务启动后会自动连接 Windows。USB 模式使用 reverse 端口，局域网模式使用所选服务器。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun HiddenWindowsAppRow(
    app: HiddenWindowsApp,
    buttonText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HiddenWindowsAppIcon(app = app)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = app.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = app.processName,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = onClick) {
            Text(buttonText)
        }
    }
}

@Composable
private fun VisibleWindowsSessionRow(
    session: WindowsAppVolumeSession,
    onHide: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionIcon(session)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = session.displayName.ifBlank { session.processName },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = session.processName,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        FilledTonalButton(onClick = onHide) {
            Text("隐藏")
        }
    }
}

@Composable
private fun HiddenWindowsAppIcon(app: HiddenWindowsApp) {
    val bitmap = remember(app.iconBase64) {
        app.iconBase64?.let(::decodeBase64Bitmap)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = app.displayName,
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
            text = app.displayName.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
internal fun LanServerCard(
    uiState: PlaybackUiState,
    onConnectUsb: () -> Unit,
    onConnectLanServer: (String, Int) -> Unit,
) {
    var manualHost by rememberSaveable { mutableStateOf(uiState.savedLanHost) }
    var manualPort by rememberSaveable { mutableStateOf(uiState.savedLanPort.ifBlank { "6000" }) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("连接方式", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onConnectUsb) { Text("USB 连接") }
            }

            Text("自动发现的局域网服务器：", style = MaterialTheme.typography.titleSmall)
            if (uiState.lanServers.isEmpty()) {
                Text("未发现服务器（请确认 Windows 端已启用局域网模式）", style = MaterialTheme.typography.bodySmall)
            } else {
                uiState.lanServers.forEach { server ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(server.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${server.host}:${server.port}", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { onConnectLanServer(server.host, server.port) }) {
                            Text("连接")
                        }
                    }
                }
            }

            Text("手动连接（回退）：", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = manualHost,
                    onValueChange = { manualHost = it },
                    placeholder = { Text("IP 地址") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = manualPort,
                    onValueChange = { manualPort = it.filter { c -> c.isDigit() }.take(5) },
                    placeholder = { Text("端口") },
                    singleLine = true,
                    modifier = Modifier.width(96.dp),
                )
                FilledTonalButton(
                    onClick = {
                        val port = manualPort.toIntOrNull()
                        if (manualHost.isNotBlank() && port != null) {
                            onConnectLanServer(manualHost.trim(), port)
                        }
                    },
                ) { Text("连接") }
            }
        }
    }
}

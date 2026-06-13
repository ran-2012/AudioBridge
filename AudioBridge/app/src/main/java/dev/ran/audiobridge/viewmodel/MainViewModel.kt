package dev.ran.audiobridge.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ran.audiobridge.audio.PlaybackCacheConfig
import dev.ran.audiobridge.data.VolumePreferencesRepository
import dev.ran.audiobridge.model.HiddenWindowsAppSupport
import dev.ran.audiobridge.model.WindowsAppVolumeSession
import dev.ran.audiobridge.repository.PlaybackStateRepository
import dev.ran.audiobridge.service.AudioBridgeService
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val volumePreferencesRepository = VolumePreferencesRepository(application)
    private var hasAutoStartedService = false

    val uiState = PlaybackStateRepository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaybackStateRepository.state.value,
    )

    init {
        refreshBatteryOptimizationState()

        viewModelScope.launch {
            volumePreferencesRepository.volumeFlow.collect { volume ->
                PlaybackStateRepository.updateVolume(volume)
            }
        }

        viewModelScope.launch {
            volumePreferencesRepository.playbackCacheMillisecondsFlow.collect { milliseconds ->
                PlaybackStateRepository.updatePlaybackCacheMilliseconds(milliseconds)
            }
        }

        viewModelScope.launch {
            combine(
                volumePreferencesRepository.hiddenProcessNamesFlow,
                volumePreferencesRepository.hiddenWindowsAppsFlow,
            ) { hiddenProcessNames, hiddenWindowsApps ->
                hiddenProcessNames to hiddenWindowsApps
            }.collect { (hiddenProcessNames, hiddenWindowsApps) ->
                PlaybackStateRepository.updateHiddenWindowsApps(hiddenProcessNames, hiddenWindowsApps)
            }
        }

        viewModelScope.launch {
            uiState.collect { state ->
                HiddenWindowsAppSupport.distinctSessionsByProcessName(state.windowsVolumeCatalog.sessions)
                    .forEach { session ->
                        val processName = session.processName.trim()
                        if (!state.hiddenProcessNames.contains(processName)) {
                            return@forEach
                        }

                        val existing = state.hiddenWindowsApps.firstOrNull { it.processName == processName }
                        val updated = HiddenWindowsAppSupport.buildHiddenWindowsApp(session, existing) ?: return@forEach
                        if (existing != updated) {
                            volumePreferencesRepository.updateHiddenWindowsAppMetadata(updated)
                        }
                    }
            }
        }
    }

    fun startService() {
        val context = getApplication<Application>()
        PlaybackStateRepository.appendLog("UI: 用户请求启动后台播放")
        context.startForegroundService(AudioBridgeService.createStartIntent(context))
    }

    fun autoStartServiceIfNeeded() {
        if (hasAutoStartedService || uiState.value.serviceRunning) {
            return
        }

        hasAutoStartedService = true
        val context = getApplication<Application>()
        PlaybackStateRepository.appendLog("UI: 应用启动，自动拉起后台播放服务")
        context.startForegroundService(AudioBridgeService.createStartIntent(context))
    }

    fun refreshBatteryOptimizationState() {
        val context = getApplication<Application>()
        val isIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(PowerManager::class.java)
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }
        val statusMessage = if (isIgnoring) {
            "系统已允许 AudioBridge 在锁屏后保持不受限后台运行。"
        } else {
            "系统仍可能在熄屏后限制网络与 CPU 调度，建议关闭电池优化并允许后台活动。"
        }
        PlaybackStateRepository.updateBatteryOptimizationState(isIgnoring, statusMessage)
    }

    fun openBatteryOptimizationSettings() {
        val context = getApplication<Application>()
        val packageUri = Uri.parse("package:${context.packageName}")
        val requestIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = packageUri
            }
        } else {
            null
        }
        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = packageUri
        }

        val launched = sequenceOf(requestIntent, fallbackIntent, appDetailsIntent)
            .filterNotNull()
            .any { intent ->
                runCatching {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
            }

        if (launched) {
            PlaybackStateRepository.appendLog("UI: 已打开电池优化相关设置页面")
        } else {
            PlaybackStateRepository.appendLog("UI: 打开电池优化设置失败，请手动在系统设置中允许后台运行")
        }
    }

    fun applyScreenOffPlaybackCachePreset() {
        updatePlaybackCacheMilliseconds(PlaybackCacheConfig.SCREEN_OFF_RECOMMENDED_MILLISECONDS)
    }

    fun stopService() {
        val context = getApplication<Application>()
        PlaybackStateRepository.appendLog("UI: 用户请求停止后台播放")
        context.startService(AudioBridgeService.createStopIntent(context))
    }

    fun restartService() {
        val context = getApplication<Application>()
        PlaybackStateRepository.appendLog("UI: 用户请求重启后台播放")
        context.startService(AudioBridgeService.createRestartIntent(context))
    }

    fun updateVolume(volume: Float) {
        val context = getApplication<Application>()
        PlaybackStateRepository.updateVolume(volume)
        PlaybackStateRepository.appendLog("UI: 用户调整音量为 ${(volume.coerceIn(0f, 1f) * 100).toInt()}%")
        viewModelScope.launch {
            volumePreferencesRepository.saveVolume(volume)
        }
        context.startService(AudioBridgeService.createVolumeIntent(context, volume))
    }

    fun updatePlaybackCacheMilliseconds(milliseconds: Int) {
        val normalized = PlaybackCacheConfig.normalize(milliseconds)
        val context = getApplication<Application>()
        PlaybackStateRepository.updatePlaybackCacheMilliseconds(normalized)
        PlaybackStateRepository.appendLog("UI: 用户调整播放缓存为 ${normalized}ms")
        viewModelScope.launch {
            volumePreferencesRepository.savePlaybackCacheMilliseconds(normalized)
        }
        context.startService(AudioBridgeService.createPlaybackCacheIntent(context, normalized))
    }

    fun requestWindowsVolumeSnapshot() {
        val context = getApplication<Application>()
        PlaybackStateRepository.appendLog("UI: 用户请求同步 Windows 音量目录")
        context.startService(AudioBridgeService.createRequestWindowsVolumeIntent(context))
    }

    fun updateWindowsMasterVolume(volume: Float) {
        val context = getApplication<Application>()
        PlaybackStateRepository.appendLog("UI: 用户调整 Windows 主音量为 ${(volume.coerceIn(0f, 1f) * 100).toInt()}%")
        context.startService(AudioBridgeService.createWindowsMasterVolumeIntent(context, volume))
    }

    fun updateWindowsMasterMute(isMuted: Boolean) {
        val context = getApplication<Application>()
        PlaybackStateRepository.appendLog("UI: 用户切换 Windows 主静音为 $isMuted")
        context.startService(AudioBridgeService.createWindowsMasterMuteIntent(context, isMuted))
    }

    fun updateWindowsSessionVolume(sessionId: String, volume: Float) {
        val context = getApplication<Application>()
        PlaybackStateRepository.appendLog("UI: 用户调整应用音量 sessionId=$sessionId 为 ${(volume.coerceIn(0f, 1f) * 100).toInt()}%")
        context.startService(AudioBridgeService.createWindowsSessionVolumeIntent(context, sessionId, volume))
    }

    fun updateWindowsSessionMute(sessionId: String, isMuted: Boolean) {
        val context = getApplication<Application>()
        PlaybackStateRepository.appendLog("UI: 用户切换应用静音 sessionId=$sessionId 为 $isMuted")
        context.startService(AudioBridgeService.createWindowsSessionMuteIntent(context, sessionId, isMuted))
    }

    fun hideWindowsApp(session: WindowsAppVolumeSession) {
        val hiddenApp = HiddenWindowsAppSupport.buildHiddenWindowsApp(
            session = session,
            existing = uiState.value.hiddenWindowsApps.firstOrNull { it.processName == session.processName.trim() },
        ) ?: return

        PlaybackStateRepository.appendLog("UI: 用户隐藏应用 processName=${hiddenApp.processName}")
        viewModelScope.launch {
            volumePreferencesRepository.hideWindowsApp(hiddenApp)
        }
    }

    fun unhideWindowsApp(processName: String) {
        val normalizedProcessName = processName.trim()
        if (normalizedProcessName.isBlank()) {
            return
        }

        PlaybackStateRepository.appendLog("UI: 用户取消隐藏应用 processName=$normalizedProcessName")
        viewModelScope.launch {
            volumePreferencesRepository.unhideWindowsApp(normalizedProcessName)
        }
    }
}

package dev.ran.audiobridge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ran.audiobridge.audio.PlaybackCacheConfig
import dev.ran.audiobridge.data.VolumePreferencesRepository
import dev.ran.audiobridge.repository.PlaybackStateRepository
import dev.ran.audiobridge.service.AudioBridgeService
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

    fun stopService() {
        val context = getApplication<Application>()
        PlaybackStateRepository.appendLog("UI: 用户请求停止后台播放")
        context.startService(AudioBridgeService.createStopIntent(context))
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
}

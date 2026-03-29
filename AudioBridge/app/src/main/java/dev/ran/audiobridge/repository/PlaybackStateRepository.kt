package dev.ran.audiobridge.repository

import android.util.Log
import dev.ran.audiobridge.model.PlaybackSessionInfo
import dev.ran.audiobridge.model.PlaybackUiState
import dev.ran.audiobridge.model.WindowsAppVolumeSession
import dev.ran.audiobridge.model.WindowsCommandAck
import dev.ran.audiobridge.model.WindowsMasterVolume
import dev.ran.audiobridge.model.WindowsVolumeCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlaybackStateRepository {
    private const val TAG = "AudioBridge"
    private const val MAX_LOG_COUNT = 120
    private val mutableState = MutableStateFlow(PlaybackUiState())

    val state: StateFlow<PlaybackUiState> = mutableState.asStateFlow()

    fun updateServiceRunning(isRunning: Boolean, statusMessage: String) {
        mutableState.value = mutableState.value.copy(
            serviceRunning = isRunning,
            statusMessage = statusMessage,
            isPlaying = if (isRunning) mutableState.value.isPlaying else false,
            isConnected = if (isRunning) mutableState.value.isConnected else false,
        )
    }

    fun updateConnection(isConnected: Boolean, statusMessage: String) {
        mutableState.value = mutableState.value.copy(
            isConnected = isConnected,
            statusMessage = statusMessage,
            isPlaying = if (isConnected) mutableState.value.isPlaying else false,
            windowsVolumeStatusMessage = if (isConnected) mutableState.value.windowsVolumeStatusMessage else "Windows 未连接",
        )
    }

    fun updatePlayback(isPlaying: Boolean, statusMessage: String) {
        mutableState.value = mutableState.value.copy(
            isPlaying = isPlaying,
            statusMessage = statusMessage,
        )
    }

    fun updateVolume(volume: Float) {
        mutableState.value = mutableState.value.copy(volume = volume)
    }

    fun updateSession(sessionInfo: PlaybackSessionInfo, statusMessage: String) {
        mutableState.value = mutableState.value.copy(
            sessionInfo = sessionInfo,
            statusMessage = statusMessage,
        )
    }

    fun updateSequence(sequence: UInt) {
        mutableState.value = mutableState.value.copy(lastSequence = sequence)
    }

    fun updateError(message: String) {
        Log.e(TAG, message)
        mutableState.value = mutableState.value.copy(
            statusMessage = message,
            isPlaying = false,
        )
    }

    fun updateWindowsVolumeLoading(isLoading: Boolean, statusMessage: String? = null) {
        mutableState.value = mutableState.value.copy(
            windowsVolumeLoading = isLoading,
            windowsVolumeStatusMessage = statusMessage ?: mutableState.value.windowsVolumeStatusMessage,
            windowsVolumeErrorMessage = if (isLoading) null else mutableState.value.windowsVolumeErrorMessage,
        )
    }

    fun updateWindowsVolumeCatalog(catalog: WindowsVolumeCatalog, statusMessage: String = "已同步 Windows 音量目录") {
        mutableState.value = mutableState.value.copy(
            windowsVolumeCatalog = catalog,
            windowsVolumeLoading = false,
            windowsVolumeStatusMessage = statusMessage,
            windowsVolumeErrorMessage = null,
        )
    }

    fun updateWindowsVolumeError(message: String) {
        Log.w(TAG, message)
        mutableState.value = mutableState.value.copy(
            windowsVolumeLoading = false,
            windowsVolumeErrorMessage = message,
            windowsVolumeStatusMessage = message,
        )
    }

    fun updateWindowsMasterVolume(masterVolume: WindowsMasterVolume, statusMessage: String) {
        mutableState.value = mutableState.value.copy(
            windowsVolumeCatalog = mutableState.value.windowsVolumeCatalog.copy(masterVolume = masterVolume),
            windowsVolumeLoading = false,
            windowsVolumeStatusMessage = statusMessage,
            windowsVolumeErrorMessage = null,
        )
    }

    fun upsertWindowsSession(session: WindowsAppVolumeSession, statusMessage: String) {
        val current = mutableState.value.windowsVolumeCatalog
        val updated = current.sessions
            .filterNot { it.sessionId == session.sessionId }
            .plus(session)
            .sortedWith(compareByDescending<WindowsAppVolumeSession> { it.state.equals("Active", ignoreCase = true) }.thenBy { it.displayName.lowercase() })

        mutableState.value = mutableState.value.copy(
            windowsVolumeCatalog = current.copy(sessions = updated),
            windowsVolumeLoading = false,
            windowsVolumeStatusMessage = statusMessage,
            windowsVolumeErrorMessage = null,
        )
    }

    fun removeWindowsSession(sessionId: String, statusMessage: String) {
        val current = mutableState.value.windowsVolumeCatalog
        mutableState.value = mutableState.value.copy(
            windowsVolumeCatalog = current.copy(sessions = current.sessions.filterNot { it.sessionId == sessionId }),
            windowsVolumeLoading = false,
            windowsVolumeStatusMessage = statusMessage,
        )
    }

    fun applyWindowsCommandAck(ack: WindowsCommandAck) {
        if (!ack.success) {
            updateWindowsVolumeError(ack.message.ifBlank { "Windows 音量命令执行失败，错误码=${ack.errorCode}" })
            return
        }

        when {
            ack.catalog != null -> updateWindowsVolumeCatalog(ack.catalog, ack.message.ifBlank { "Windows 音量命令执行成功" })
            ack.masterVolume != null -> updateWindowsMasterVolume(ack.masterVolume, ack.message.ifBlank { "Windows 主音量已更新" })
            ack.session != null -> upsertWindowsSession(ack.session, ack.message.ifBlank { "Windows 应用音量已更新" })
            else -> updateWindowsVolumeLoading(false, ack.message.ifBlank { "Windows 音量命令执行成功" })
        }
    }

    fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val newLog = "$timestamp  $message"
        Log.d(TAG, message)
        val currentLogs = mutableState.value.recentLogs
        val updatedLogs = listOf(newLog) + currentLogs.take(MAX_LOG_COUNT - 1)
        mutableState.value = mutableState.value.copy(recentLogs = updatedLogs)
    }
}

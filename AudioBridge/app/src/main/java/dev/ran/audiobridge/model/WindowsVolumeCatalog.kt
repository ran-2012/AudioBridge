package dev.ran.audiobridge.model

data class WindowsMasterVolume(
    val deviceId: String = "",
    val deviceName: String = "未知输出设备",
    val volume: Float = 0f,
    val isMuted: Boolean = false,
    val capturedAtMillis: Long = 0L,
)

data class WindowsAppVolumeSession(
    val sessionId: String = "",
    val processId: Int = 0,
    val processName: String = "",
    val displayName: String = "",
    val state: String = "",
    val volume: Float = 0f,
    val isMuted: Boolean = false,
    val iconKey: String = "",
    val iconHash: String = "",
    val iconBase64: String? = null,
)

data class WindowsVolumeCatalog(
    val requestId: UInt = 0u,
    val capturedAtMillis: Long = 0L,
    val masterVolume: WindowsMasterVolume = WindowsMasterVolume(),
    val sessions: List<WindowsAppVolumeSession> = emptyList(),
)

data class WindowsCommandAck(
    val requestId: UInt = 0u,
    val success: Boolean = false,
    val errorCode: Int = 0,
    val message: String = "",
    val catalog: WindowsVolumeCatalog? = null,
    val masterVolume: WindowsMasterVolume? = null,
    val session: WindowsAppVolumeSession? = null,
)
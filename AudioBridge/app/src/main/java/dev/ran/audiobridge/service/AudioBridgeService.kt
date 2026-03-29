package dev.ran.audiobridge.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import dev.ran.audiobridge.audio.AudioPlaybackManager
import dev.ran.audiobridge.data.VolumePreferencesRepository
import dev.ran.audiobridge.model.BridgePacket
import dev.ran.audiobridge.notification.NotificationController
import dev.ran.audiobridge.repository.PlaybackStateRepository
import dev.ran.audiobridge.network.BridgeMessageType
import dev.ran.audiobridge.network.ProtocolReader
import dev.ran.audiobridge.network.WindowsVolumeJsonCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.ServerSocket
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

class AudioBridgeService : Service() {
    companion object {
        private const val ACTION_START = "dev.ran.audiobridge.action.START"
        private const val ACTION_STOP = "dev.ran.audiobridge.action.STOP"
        private const val ACTION_SET_VOLUME = "dev.ran.audiobridge.action.SET_VOLUME"
        private const val ACTION_REQUEST_WINDOWS_VOLUME = "dev.ran.audiobridge.action.REQUEST_WINDOWS_VOLUME"
        private const val ACTION_SET_WINDOWS_MASTER_VOLUME = "dev.ran.audiobridge.action.SET_WINDOWS_MASTER_VOLUME"
        private const val ACTION_SET_WINDOWS_MASTER_MUTE = "dev.ran.audiobridge.action.SET_WINDOWS_MASTER_MUTE"
        private const val ACTION_SET_WINDOWS_SESSION_VOLUME = "dev.ran.audiobridge.action.SET_WINDOWS_SESSION_VOLUME"
        private const val ACTION_SET_WINDOWS_SESSION_MUTE = "dev.ran.audiobridge.action.SET_WINDOWS_SESSION_MUTE"
        private const val EXTRA_VOLUME = "extra_volume"
        private const val EXTRA_MUTED = "extra_muted"
        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val SERVER_PORT = 5000

        fun createStartIntent(context: Context) = Intent(context, AudioBridgeService::class.java).apply {
            action = ACTION_START
        }

        fun createStopIntent(context: Context) = Intent(context, AudioBridgeService::class.java).apply {
            action = ACTION_STOP
        }

        fun createVolumeIntent(context: Context, volume: Float) = Intent(context, AudioBridgeService::class.java).apply {
            action = ACTION_SET_VOLUME
            putExtra(EXTRA_VOLUME, volume)
        }

        fun createRequestWindowsVolumeIntent(context: Context) = Intent(context, AudioBridgeService::class.java).apply {
            action = ACTION_REQUEST_WINDOWS_VOLUME
        }

        fun createWindowsMasterVolumeIntent(context: Context, volume: Float) = Intent(context, AudioBridgeService::class.java).apply {
            action = ACTION_SET_WINDOWS_MASTER_VOLUME
            putExtra(EXTRA_VOLUME, volume)
        }

        fun createWindowsMasterMuteIntent(context: Context, muted: Boolean) = Intent(context, AudioBridgeService::class.java).apply {
            action = ACTION_SET_WINDOWS_MASTER_MUTE
            putExtra(EXTRA_MUTED, muted)
        }

        fun createWindowsSessionVolumeIntent(context: Context, sessionId: String, volume: Float) = Intent(context, AudioBridgeService::class.java).apply {
            action = ACTION_SET_WINDOWS_SESSION_VOLUME
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_VOLUME, volume)
        }

        fun createWindowsSessionMuteIntent(context: Context, sessionId: String, muted: Boolean) = Intent(context, AudioBridgeService::class.java).apply {
            action = ACTION_SET_WINDOWS_SESSION_MUTE
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_MUTED, muted)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val protocolReader = ProtocolReader()
    private val playbackManager = AudioPlaybackManager()
    private lateinit var notificationController: NotificationController
    private lateinit var volumePreferencesRepository: VolumePreferencesRepository
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var activeClientOutputStream: OutputStream? = null
    private val requestIdGenerator = AtomicInteger(1)
    private val outputLock = Any()

    override fun onCreate() {
        super.onCreate()
        notificationController = NotificationController(this)
        volumePreferencesRepository = VolumePreferencesRepository(this)
        notificationController.ensureChannel()
        PlaybackStateRepository.appendLog("Service: onCreate 完成，通知通道已就绪")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startBridge()
            ACTION_STOP -> stopBridge()
            ACTION_SET_VOLUME -> updateVolume(intent.getFloatExtra(EXTRA_VOLUME, 1.0f))
            ACTION_REQUEST_WINDOWS_VOLUME -> requestWindowsVolumeSnapshot()
            ACTION_SET_WINDOWS_MASTER_VOLUME -> sendWindowsMasterVolume(intent.getFloatExtra(EXTRA_VOLUME, 0f))
            ACTION_SET_WINDOWS_MASTER_MUTE -> sendWindowsMasterMute(intent.getBooleanExtra(EXTRA_MUTED, false))
            ACTION_SET_WINDOWS_SESSION_VOLUME -> sendWindowsSessionVolume(intent.getStringExtra(EXTRA_SESSION_ID), intent.getFloatExtra(EXTRA_VOLUME, 0f))
            ACTION_SET_WINDOWS_SESSION_MUTE -> sendWindowsSessionMute(intent.getStringExtra(EXTRA_SESSION_ID), intent.getBooleanExtra(EXTRA_MUTED, false))
            else -> startBridge()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopBridge()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startBridge() {
        if (serverJob?.isActive == true) {
            PlaybackStateRepository.appendLog("Service: 后台服务已在运行，忽略重复启动")
            return
        }

        startForeground(
            NotificationController.NOTIFICATION_ID,
            notificationController.build("后台服务运行中，等待 Windows 连接"),
        )
        PlaybackStateRepository.updateServiceRunning(true, "后台服务已启动，等待 Windows 连接")
        PlaybackStateRepository.appendLog("Service: 前台服务已启动，开始监听端口 $SERVER_PORT")

        serverJob = serviceScope.launch {
            val initialVolume = volumePreferencesRepository.volumeFlow.first()
            playbackManager.updateVolume(initialVolume)
            PlaybackStateRepository.updateVolume(initialVolume)
            PlaybackStateRepository.appendLog("Service: 已加载音量设置 ${(initialVolume * 100).toInt()}%")

            runCatching {
                ServerSocket(SERVER_PORT).use { socket ->
                    serverSocket = socket
                    PlaybackStateRepository.appendLog("Network: ServerSocket 已监听 0.0.0.0:$SERVER_PORT")
                    while (true) {
                        PlaybackStateRepository.appendLog("Network: 等待 Windows 建立连接")
                        val client = socket.accept()
                        PlaybackStateRepository.updateConnection(true, "已建立 Windows 连接，等待初始化消息")
                        PlaybackStateRepository.appendLog("Network: 客户端已连接 ${client.inetAddress.hostAddress}:${client.port}")
                        notificationController.ensureChannel()
                        startForeground(
                            NotificationController.NOTIFICATION_ID,
                            notificationController.build("已连接 Windows，正在接收音频"),
                        )

                        client.use { incoming ->
                            synchronized(outputLock) {
                                activeClientOutputStream = incoming.getOutputStream()
                            }
                            runCatching {
                                handleClient(incoming)
                            }.onFailure { throwable ->
                                PlaybackStateRepository.appendLog("Network: 连接中断 ${throwable.message ?: "未知错误"}")
                                PlaybackStateRepository.updateError("连接中断：${throwable.message ?: "未知错误"}")
                            }
                            synchronized(outputLock) {
                                activeClientOutputStream = null
                            }
                        }

                        PlaybackStateRepository.appendLog("Network: 当前连接已关闭，释放 AudioTrack")
                        PlaybackStateRepository.updateConnection(false, "连接已断开，等待重新连接")
                        PlaybackStateRepository.updatePlayback(false, "播放已停止，等待新的连接")
                        playbackManager.release()
                    }
                }
            }.onFailure { throwable ->
                if (throwable !is SocketException) {
                    PlaybackStateRepository.appendLog("Service: 服务异常 ${throwable.message ?: "未知错误"}")
                    PlaybackStateRepository.updateError("服务异常：${throwable.message ?: "未知错误"}")
                }
            }
        }
    }

    private fun handleClient(client: java.net.Socket) {
        val inputStream = client.getInputStream()
        while (!client.isClosed) {
            when (val packet = protocolReader.readPacket(inputStream)) {
                is BridgePacket.SessionInit -> {
                    PlaybackStateRepository.appendLog(
                        "Protocol: 收到 SessionInit encoding=${packet.encodingCode}, sampleRate=${packet.sampleRate}, channels=${packet.channels}, bits=${packet.bitsPerSample}, buffer=${packet.bufferMilliseconds}ms",
                    )
                    val sessionInfo = playbackManager.configure(packet)
                    PlaybackStateRepository.updateSession(sessionInfo, "收到 SessionInit，播放参数已初始化")
                    PlaybackStateRepository.updatePlayback(true, "已开始后台播放")
                    PlaybackStateRepository.appendLog("Audio: AudioTrack 已配置 ${sessionInfo.sampleRate}Hz/${sessionInfo.channels}ch/${sessionInfo.bitsPerSample}bit")
                }

                is BridgePacket.AudioFrame -> {
                    playbackManager.write(packet)
                    PlaybackStateRepository.updateSequence(packet.sequence)
                    PlaybackStateRepository.updatePlayback(true, "后台播放中")
                    if (packet.sequence == 1u || packet.sequence % 200u == 0u) {
                        PlaybackStateRepository.appendLog(
                            "Audio: 收到音频帧 sequence=${packet.sequence}, bytes=${packet.audioData.size}, ts=${packet.timestampMillis}",
                        )
                    }
                }

                is BridgePacket.VolumeCatalogSnapshot -> {
                    PlaybackStateRepository.updateWindowsVolumeCatalog(
                        packet.catalog,
                        "已同步 Windows 音量目录（${packet.catalog.sessions.size} 个应用）",
                    )
                    PlaybackStateRepository.appendLog("Protocol: 收到 Windows 音量快照，会话数=${packet.catalog.sessions.size}")
                }

                is BridgePacket.VolumeSessionDelta -> {
                    when {
                        packet.masterVolume != null -> {
                            PlaybackStateRepository.updateWindowsMasterVolume(packet.masterVolume, "Windows 主音量已同步")
                        }

                        packet.session != null -> {
                            PlaybackStateRepository.upsertWindowsSession(packet.session, "Windows 应用音量已同步")
                        }

                        !packet.removedSessionId.isNullOrBlank() -> {
                            PlaybackStateRepository.removeWindowsSession(packet.removedSessionId, "Windows 应用音量会话已移除")
                        }
                    }

                    PlaybackStateRepository.appendLog("Protocol: 收到 Windows 音量增量 delta=${packet.deltaType}")
                }

                is BridgePacket.CommandAck -> {
                    PlaybackStateRepository.applyWindowsCommandAck(packet.ack)
                    PlaybackStateRepository.appendLog(
                        "Protocol: 收到命令回执 requestId=${packet.ack.requestId} success=${packet.ack.success} code=${packet.ack.errorCode}",
                    )
                }
            }
        }
    }

    private fun updateVolume(volume: Float) {
        playbackManager.updateVolume(volume)
        PlaybackStateRepository.updateVolume(volume)
        PlaybackStateRepository.appendLog("Audio: 音量已更新为 ${(volume.coerceIn(0f, 1f) * 100).toInt()}%")
        serviceScope.launch {
            volumePreferencesRepository.saveVolume(volume)
        }
    }

    private fun requestWindowsVolumeSnapshot() {
        PlaybackStateRepository.updateWindowsVolumeLoading(true, "正在请求 Windows 音量目录...")
        val requestId = nextRequestId()
        sendControlMessage(
            BridgeMessageType.VOLUME_CATALOG_REQUEST,
            WindowsVolumeJsonCodec.buildCatalogRequest(requestId),
            "Control: 已发送 Windows 音量目录请求 requestId=$requestId",
        )
    }

    private fun sendWindowsMasterVolume(volume: Float) {
        PlaybackStateRepository.updateWindowsVolumeLoading(true, "正在更新 Windows 主音量...")
        val requestId = nextRequestId()
        sendControlMessage(
            BridgeMessageType.VOLUME_SET_MASTER_REQUEST,
            WindowsVolumeJsonCodec.buildSetMasterRequest(requestId, volume = volume),
            "Control: 已发送 Windows 主音量更新 requestId=$requestId volume=${(volume.coerceIn(0f, 1f) * 100).toInt()}%",
        )
    }

    private fun sendWindowsMasterMute(isMuted: Boolean) {
        PlaybackStateRepository.updateWindowsVolumeLoading(true, "正在更新 Windows 主静音...")
        val requestId = nextRequestId()
        sendControlMessage(
            BridgeMessageType.VOLUME_SET_MASTER_REQUEST,
            WindowsVolumeJsonCodec.buildSetMasterRequest(requestId, isMuted = isMuted),
            "Control: 已发送 Windows 主静音更新 requestId=$requestId muted=$isMuted",
        )
    }

    private fun sendWindowsSessionVolume(sessionId: String?, volume: Float) {
        if (sessionId.isNullOrBlank()) {
            PlaybackStateRepository.updateWindowsVolumeError("无法发送应用音量命令：缺少 sessionId")
            return
        }

        PlaybackStateRepository.updateWindowsVolumeLoading(true, "正在更新应用音量...")
        val requestId = nextRequestId()
        sendControlMessage(
            BridgeMessageType.VOLUME_SET_SESSION_REQUEST,
            WindowsVolumeJsonCodec.buildSetSessionRequest(requestId, sessionId, volume = volume),
            "Control: 已发送应用音量更新 requestId=$requestId sessionId=$sessionId volume=${(volume.coerceIn(0f, 1f) * 100).toInt()}%",
        )
    }

    private fun sendWindowsSessionMute(sessionId: String?, isMuted: Boolean) {
        if (sessionId.isNullOrBlank()) {
            PlaybackStateRepository.updateWindowsVolumeError("无法发送应用静音命令：缺少 sessionId")
            return
        }

        PlaybackStateRepository.updateWindowsVolumeLoading(true, "正在更新应用静音...")
        val requestId = nextRequestId()
        sendControlMessage(
            BridgeMessageType.VOLUME_SET_SESSION_REQUEST,
            WindowsVolumeJsonCodec.buildSetSessionRequest(requestId, sessionId, isMuted = isMuted),
            "Control: 已发送应用静音更新 requestId=$requestId sessionId=$sessionId muted=$isMuted",
        )
    }

    private fun sendControlMessage(messageType: Int, json: String, successLog: String) {
        val outputStream = synchronized(outputLock) { activeClientOutputStream }
        if (outputStream == null) {
            PlaybackStateRepository.updateWindowsVolumeError("Windows 未连接，无法发送控制命令")
            return
        }

        serviceScope.launch {
            runCatching {
                val payload = json.toByteArray(Charsets.UTF_8)
                val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(0x57414231)
                    .putShort(1)
                    .putShort(messageType.toShort())
                    .putInt(payload.size)
                    .array()

                synchronized(outputLock) {
                    activeClientOutputStream?.write(header)
                    activeClientOutputStream?.write(payload)
                    activeClientOutputStream?.flush()
                }
            }.onSuccess {
                PlaybackStateRepository.appendLog(successLog)
            }.onFailure { throwable ->
                PlaybackStateRepository.updateWindowsVolumeError("发送控制命令失败：${throwable.message ?: "未知错误"}")
            }
        }
    }

    private fun nextRequestId(): UInt = requestIdGenerator.getAndIncrement().toUInt()

    private fun stopBridge() {
        PlaybackStateRepository.appendLog("Service: 正在停止后台服务")
        serverJob?.cancel()
        serverJob = null
        serverSocket?.close()
        serverSocket = null
        synchronized(outputLock) {
            activeClientOutputStream = null
        }
        playbackManager.release()
        PlaybackStateRepository.updateServiceRunning(false, "后台服务已停止")
        PlaybackStateRepository.appendLog("Service: 后台服务已停止，监听与播放资源已释放")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}

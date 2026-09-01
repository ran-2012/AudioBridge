package dev.ran.audiobridge.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import dev.ran.audiobridge.audio.AudioPlaybackManager
import dev.ran.audiobridge.audio.PlaybackCacheConfig
import dev.ran.audiobridge.data.VolumePreferencesRepository
import dev.ran.audiobridge.model.BridgePacket
import dev.ran.audiobridge.notification.NotificationController
import dev.ran.audiobridge.repository.PlaybackStateRepository
import dev.ran.audiobridge.network.AndroidPlaybackStatusJsonCodec
import dev.ran.audiobridge.network.BridgeFrameEncoder
import dev.ran.audiobridge.network.BridgeMessageType
import dev.ran.audiobridge.network.ProtocolReader
import dev.ran.audiobridge.network.WindowsVolumeJsonCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

class AudioBridgeService : Service() {
    companion object {
        private const val ACTION_START = "dev.ran.audiobridge.action.START"
        private const val ACTION_STOP = "dev.ran.audiobridge.action.STOP"
        private const val ACTION_RESTART = "dev.ran.audiobridge.action.RESTART"
        private const val ACTION_CONNECT = "dev.ran.audiobridge.action.CONNECT"
        private const val ACTION_SET_VOLUME = "dev.ran.audiobridge.action.SET_VOLUME"
        private const val ACTION_SET_PLAYBACK_CACHE = "dev.ran.audiobridge.action.SET_PLAYBACK_CACHE"
        private const val ACTION_REQUEST_WINDOWS_VOLUME = "dev.ran.audiobridge.action.REQUEST_WINDOWS_VOLUME"
        private const val ACTION_SET_WINDOWS_MASTER_VOLUME = "dev.ran.audiobridge.action.SET_WINDOWS_MASTER_VOLUME"
        private const val ACTION_SET_WINDOWS_MASTER_MUTE = "dev.ran.audiobridge.action.SET_WINDOWS_MASTER_MUTE"
        private const val ACTION_SET_WINDOWS_SESSION_VOLUME = "dev.ran.audiobridge.action.SET_WINDOWS_SESSION_VOLUME"
        private const val ACTION_SET_WINDOWS_SESSION_MUTE = "dev.ran.audiobridge.action.SET_WINDOWS_SESSION_MUTE"
        private const val EXTRA_VOLUME = "extra_volume"
        private const val EXTRA_PLAYBACK_CACHE_MILLISECONDS = "extra_playback_cache_milliseconds"
        private const val EXTRA_MUTED = "extra_muted"
        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val EXTRA_CONNECT_HOST = "extra_connect_host"
        private const val EXTRA_CONNECT_PORT = "extra_connect_port"
        private const val DEFAULT_HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 5000
        private const val CONNECT_TIMEOUT_MILLIS = 5_000
        private const val RECONNECT_DELAY_MILLIS = 3_000L
        private const val HEARTBEAT_LOG_INTERVAL = 12
        private const val PLAYBACK_STATUS_INTERVAL_MILLIS = 3_000L
        private const val PLAYBACK_STATUS_LOG_INTERVAL = 10
        private const val IDLE_RESUME_LOG_THRESHOLD_MILLIS = 5_000L

        fun createStartIntent(context: Context) = Intent(context, AudioBridgeService::class.java).apply {
            action = ACTION_START
        }

        fun createStopIntent(context: Context) = Intent(context, AudioBridgeService::class.java).apply {
            action = ACTION_STOP
        }

        fun createRestartIntent(context: Context) = Intent(context, AudioBridgeService::class.java).apply {
            action = ACTION_RESTART
        }

        fun createConnectIntent(context: Context, host: String, port: Int) = Intent(context, AudioBridgeService::class.java).apply {
            action = ACTION_CONNECT
            putExtra(EXTRA_CONNECT_HOST, host)
            putExtra(EXTRA_CONNECT_PORT, port)
        }

        fun createVolumeIntent(context: Context, volume: Float) = Intent(context, AudioBridgeService::class.java).apply {
            action = ACTION_SET_VOLUME
            putExtra(EXTRA_VOLUME, volume)
        }

        fun createPlaybackCacheIntent(context: Context, milliseconds: Int) = Intent(context, AudioBridgeService::class.java).apply {
            action = ACTION_SET_PLAYBACK_CACHE
            putExtra(EXTRA_PLAYBACK_CACHE_MILLISECONDS, PlaybackCacheConfig.normalize(milliseconds))
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

        /**
         * 解析连接目标：优先使用显式指定的 host/port（LAN 手动/发现），否则回退到 USB reverse 本机端口。
         * 抽取为纯函数以便单元测试。
         */
        internal fun resolveConnectTarget(explicitHost: String?, explicitPort: Int): Pair<String, Int> =
            if (!explicitHost.isNullOrBlank() && explicitPort > 0) {
                explicitHost to explicitPort
            } else {
                DEFAULT_HOST to DEFAULT_PORT
            }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val protocolReader = ProtocolReader()
    private val playbackManager = AudioPlaybackManager()
    private lateinit var notificationController: NotificationController
    private lateinit var volumePreferencesRepository: VolumePreferencesRepository
    private var connectJob: Job? = null
    private var activeSocket: Socket? = null
    private var activeClientOutputStream: OutputStream? = null
    private val requestIdGenerator = AtomicInteger(1)
    private val outputLock = Any()
    private var playbackStatusJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastPacketElapsedRealtime: Long = 0L
    private var lastAudioFrameElapsedRealtime: Long = 0L
    private var lastAudioSequence: UInt = 0u
    private var playbackStatusSequence: UInt = 0u
    private var heartbeatCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        notificationController = NotificationController(this)
        volumePreferencesRepository = VolumePreferencesRepository(this)
        notificationController.ensureChannel()
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:AudioBridgeReceiveLoop",
        ).apply {
            setReferenceCounted(false)
        }
        PlaybackStateRepository.appendLog("Service: onCreate 完成，通知通道已就绪")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startBridge()
            ACTION_STOP -> stopBridge()
            ACTION_RESTART -> restartBridge()
            ACTION_CONNECT -> startBridge(
                host = intent.getStringExtra(EXTRA_CONNECT_HOST),
                port = intent.getIntExtra(EXTRA_CONNECT_PORT, -1),
            )
            ACTION_SET_VOLUME -> updateVolume(intent.getFloatExtra(EXTRA_VOLUME, 1.0f))
            ACTION_SET_PLAYBACK_CACHE -> updatePlaybackCacheMilliseconds(intent.getIntExtra(EXTRA_PLAYBACK_CACHE_MILLISECONDS, PlaybackCacheConfig.DEFAULT_MILLISECONDS))
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

    private fun startBridge(host: String? = null, port: Int = -1) {
        if (connectJob?.isActive == true && host == null) {
            PlaybackStateRepository.appendLog("Service: 后台服务已在运行，忽略重复启动")
            return
        }

        // 用户显式指定了新的连接目标：先停旧连接再按新目标重连
        if (host != null) {
            PlaybackStateRepository.appendLog("Service: 收到新的连接目标，重启连接流程 $host:$port")
            stopBridge(stopSelfAfterStop = false)
        }

        acquireWakeLock()
        lastPacketElapsedRealtime = SystemClock.elapsedRealtime()
        heartbeatCount = 0

        startForeground(
            NotificationController.NOTIFICATION_ID,
            notificationController.build("后台服务运行中，正在连接 Windows"),
        )
        PlaybackStateRepository.updateServiceRunning(true, "后台服务已启动，正在连接 Windows")
        PlaybackStateRepository.appendLog("Service: 前台服务已启动，开始连接 Windows 服务器")

        connectJob = serviceScope.launch {
            val initialVolume = volumePreferencesRepository.volumeFlow.first()
            val initialPlaybackCacheMilliseconds = volumePreferencesRepository.playbackCacheMillisecondsFlow.first()
            playbackManager.updateVolume(initialVolume)
            playbackManager.updatePlaybackCacheMilliseconds(initialPlaybackCacheMilliseconds)
            PlaybackStateRepository.updateVolume(initialVolume)
            PlaybackStateRepository.updatePlaybackCacheMilliseconds(initialPlaybackCacheMilliseconds)
            PlaybackStateRepository.appendLog("Service: 已加载音量设置 ${(initialVolume * 100).toInt()}%")
            PlaybackStateRepository.appendLog("Service: 已加载播放缓存 ${initialPlaybackCacheMilliseconds}ms")

            connectLoop(host, port)
        }
    }

    /**
     * 客户端连接主循环：主动连接 Windows 服务器，断线/失败后按固定间隔自动重连，
     * 不计代价保证连接稳定性（任何异常都不退出循环，直到用户显式停止服务）。
     */
    private suspend fun connectLoop(explicitHost: String?, explicitPort: Int) {
        var attempt = 0
        var lastTarget: Pair<String, Int>? = null

        while (currentCoroutineContext().isActive) {
            attempt++
            try {
                val target = resolveConnectTarget(explicitHost, explicitPort)
                if (lastTarget != target) {
                    lastTarget = target
                    PlaybackStateRepository.updateConnectTarget(target.first, target.second)
                    PlaybackStateRepository.appendLog("Network: 连接目标 ${target.first}:${target.second}")
                }

                val (connectHost, connectPort) = target
                PlaybackStateRepository.appendLog("Network: 尝试连接 $connectHost:$connectPort（第 $attempt 次）")

                runCatching {
                    val socket = Socket()
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(connectHost, connectPort), CONNECT_TIMEOUT_MILLIS)
                    activeSocket = socket
                    synchronized(outputLock) {
                        activeClientOutputStream = socket.getOutputStream()
                    }
                    playbackStatusSequence = 0u
                    lastAudioSequence = 0u
                    lastAudioFrameElapsedRealtime = 0L
                    lastPacketElapsedRealtime = SystemClock.elapsedRealtime()

                    PlaybackStateRepository.updateConnection(true, "已连接 Windows $connectHost:$connectPort，等待初始化消息")
                    PlaybackStateRepository.appendLog("Network: 已连接 $connectHost:$connectPort")
                    notificationController.ensureChannel()
                    startForeground(
                        NotificationController.NOTIFICATION_ID,
                        notificationController.build("已连接 Windows，正在接收音频"),
                    )

                    playbackStatusJob = startPlaybackStatusLoop()
                    runCatching {
                        handleClient(socket)
                    }.onFailure { throwable ->
                        PlaybackStateRepository.appendLog("Network: 连接中断 ${throwable.message ?: "未知错误"}，准备自动重连")
                        PlaybackStateRepository.updateError("连接中断：${throwable.message ?: "未知错误"}，正在自动重连")
                    }
                    playbackStatusJob?.cancel()
                    playbackStatusJob = null
                    synchronized(outputLock) {
                        activeClientOutputStream = null
                    }
                    runCatching { socket.close() }
                    activeSocket = null

                    PlaybackStateRepository.appendLog("Network: 当前连接已关闭，释放 AudioTrack")
                    PlaybackStateRepository.updateConnection(false, "连接已断开，正在自动重连（第 $attempt 次）")
                    PlaybackStateRepository.updatePlayback(false, "播放已停止，正在自动重连")
                    playbackManager.release()
                }.onFailure { throwable ->
                    PlaybackStateRepository.appendLog("Network: 连接失败 ${throwable.message ?: "未知错误"}（第 $attempt 次，将自动重连）")
                    PlaybackStateRepository.updateConnection(false, "连接失败，正在自动重连（第 $attempt 次）")
                    playbackManager.release()
                }
            } catch (throwable: Throwable) {
                PlaybackStateRepository.appendLog("Network: 重连循环异常 ${throwable.message ?: "未知错误"}，将继续自动重试")
                playbackManager.release()
            }

            delay(RECONNECT_DELAY_MILLIS)
        }
    }

    private fun handleClient(client: java.net.Socket) {
        val inputStream = client.getInputStream()
        lastPacketElapsedRealtime = SystemClock.elapsedRealtime()
        while (!client.isClosed) {
            when (val packet = protocolReader.readPacket(inputStream)) {
                is BridgePacket.SessionInit -> {
                    val idleMillis = markPacketReceived()
                    PlaybackStateRepository.appendLog(
                        "Protocol: 收到 SessionInit encoding=${packet.encodingCode}, sampleRate=${packet.sampleRate}, channels=${packet.channels}, bits=${packet.bitsPerSample}, buffer=${packet.bufferMilliseconds}ms",
                    )
                    if (idleMillis >= IDLE_RESUME_LOG_THRESHOLD_MILLIS) {
                        PlaybackStateRepository.appendLog("Protocol: 空闲 ${idleMillis}ms 后重新收到 SessionInit")
                    }
                    val sessionInfo = playbackManager.configure(packet)
                    PlaybackStateRepository.updateSession(sessionInfo, "收到 SessionInit，播放参数已初始化")
                    PlaybackStateRepository.updatePlayback(true, "已开始后台播放")
                    PlaybackStateRepository.appendLog("Audio: AudioTrack 已配置 ${sessionInfo.sampleRate}Hz/${sessionInfo.channels}ch/${sessionInfo.bitsPerSample}bit")
                }

                is BridgePacket.AudioFrame -> {
                    val idleMillis = markPacketReceived()
                    val result = playbackManager.write(packet)
                    lastAudioSequence = packet.sequence
                    lastAudioFrameElapsedRealtime = SystemClock.elapsedRealtime()
                    PlaybackStateRepository.updateSequence(packet.sequence)
                    PlaybackStateRepository.updatePlayback(true, "后台播放中")
                    if (idleMillis >= IDLE_RESUME_LOG_THRESHOLD_MILLIS) {
                        PlaybackStateRepository.appendLog("Audio: 空闲 ${idleMillis}ms 后恢复收到音频，sequence=${packet.sequence}")
                    }
                    if (result.trimmedBufferedAudio || result.droppedBytes > 0) {
                        PlaybackStateRepository.appendLog(
                            "Audio: 检测到播放积压，已丢弃旧音频 trimmed=${result.trimmedBufferedAudio} droppedBytes=${result.droppedBytes} cache=${PlaybackStateRepository.state.value.playbackCacheMilliseconds}ms sequence=${packet.sequence}",
                        )
                    }
                    if (packet.sequence == 1u || packet.sequence % 200u == 0u) {
                        PlaybackStateRepository.appendLog(
                            "Audio: 收到音频帧 sequence=${packet.sequence}, bytes=${packet.audioData.size}, ts=${packet.timestampMillis}",
                        )
                    }
                }

                BridgePacket.Heartbeat -> {
                    val idleMillis = markPacketReceived()
                    heartbeatCount += 1
                    sendHeartbeatAck()
                    if (heartbeatCount == 1 || heartbeatCount % HEARTBEAT_LOG_INTERVAL == 0) {
                        PlaybackStateRepository.appendLog(
                            "Protocol: 收到保活心跳并回送 ACK heartbeatCount=$heartbeatCount idle=${idleMillis}ms",
                        )
                    }
                }

                is BridgePacket.LatencyProbe -> {
                    markPacketReceived()
                    sendLatencyProbeAck(packet.sendTimestampMillis)
                }

                is BridgePacket.LatencyProbeAck -> {
                    markPacketReceived()
                    handleLatencyProbeAck(packet.sendTimestampMillis)
                }

                is BridgePacket.VolumeCatalogSnapshot -> {
                    markPacketReceived()
                    PlaybackStateRepository.updateWindowsVolumeCatalog(
                        packet.catalog,
                        "已同步 Windows 音量目录（${packet.catalog.sessions.size} 个应用）",
                    )
                    PlaybackStateRepository.appendLog("Protocol: 收到 Windows 音量快照，会话数=${packet.catalog.sessions.size}")
                }

                is BridgePacket.VolumeSessionDelta -> {
                    markPacketReceived()
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
                    markPacketReceived()
                    PlaybackStateRepository.applyWindowsCommandAck(packet.ack)
                    PlaybackStateRepository.appendLog(
                        "Protocol: 收到命令回执 requestId=${packet.ack.requestId} success=${packet.ack.success} code=${packet.ack.errorCode}",
                    )
                }

                is BridgePacket.AndroidPlaybackStatusAckPacket -> {
                    markPacketReceived()
                    if (packet.ack.sequence == 1u || packet.ack.sequence % PLAYBACK_STATUS_LOG_INTERVAL.toUInt() == 0u) {
                        PlaybackStateRepository.appendLog(
                            "Protocol: 收到播放状态 ACK sequence=${packet.ack.sequence} accepted=${packet.ack.accepted}",
                        )
                    }
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

    private fun updatePlaybackCacheMilliseconds(milliseconds: Int) {
        val normalized = playbackManager.updatePlaybackCacheMilliseconds(milliseconds)
        PlaybackStateRepository.updatePlaybackCacheMilliseconds(normalized)
        PlaybackStateRepository.appendLog("Audio: 播放缓存已更新为 ${normalized}ms")
        serviceScope.launch {
            volumePreferencesRepository.savePlaybackCacheMilliseconds(normalized)
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
                writePacketToActiveClient(messageType, payload)
            }.onSuccess {
                PlaybackStateRepository.appendLog(successLog)
            }.onFailure { throwable ->
                PlaybackStateRepository.updateWindowsVolumeError("发送控制命令失败：${throwable.message ?: "未知错误"}")
            }
        }
    }

    private fun sendHeartbeatAck() {
        runCatching {
            writePacketToActiveClient(BridgeMessageType.HEARTBEAT_ACK, ByteArray(0))
        }.onFailure { throwable ->
            PlaybackStateRepository.appendLog("Protocol: 回送心跳 ACK 失败：${throwable.message ?: "未知错误"}")
        }
    }

    private fun sendLatencyProbeAck(sendTimestampMillis: Long) {
        val payload = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(sendTimestampMillis)
            .array()
        runCatching {
            writePacketToActiveClient(BridgeMessageType.LATENCY_PROBE_ACK, payload)
        }.onFailure { throwable ->
            PlaybackStateRepository.appendLog("Protocol: 回送延迟探测 ACK 失败：${throwable.message ?: "未知错误"}")
        }
    }

    private fun startPlaybackStatusLoop(): Job = serviceScope.launch {
        while (isActive) {
            sendPlaybackStatus()
            sendLatencyProbe()
            delay(PLAYBACK_STATUS_INTERVAL_MILLIS)
        }
    }

    private fun sendLatencyProbe() {
        val payload = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(System.currentTimeMillis())
            .array()
        runCatching {
            writePacketToActiveClient(BridgeMessageType.LATENCY_PROBE, payload)
        }.onFailure {
            // 发送失败静默处理，由连接中断逻辑统一处理
        }
    }

    private fun handleLatencyProbeAck(sendTimestampMillis: Long) {
        val rtt = System.currentTimeMillis() - sendTimestampMillis
        if (rtt >= 0) {
            PlaybackStateRepository.updateRttMillis(rtt)
        }
    }

    private fun sendPlaybackStatus() {
        playbackStatusSequence += 1u
        val now = SystemClock.elapsedRealtime()
        val lastAudioAge = if (lastAudioFrameElapsedRealtime > 0L) now - lastAudioFrameElapsedRealtime else null
        val status = playbackManager.createPlaybackStatus(
            sequence = playbackStatusSequence,
            lastSequence = lastAudioSequence,
            lastAudioFrameAgeMillis = lastAudioAge,
            timestampElapsedRealtimeMillis = now,
        )

        runCatching {
            val payload = AndroidPlaybackStatusJsonCodec.buildStatus(status).toByteArray(Charsets.UTF_8)
            writePacketToActiveClient(BridgeMessageType.ANDROID_PLAYBACK_STATUS, payload)
        }.onSuccess {
            val isAbnormal = !status.isPlaying || (status.bufferedLatencyMillis ?: 0L) > 0L
            if (status.sequence == 1u || status.sequence % PLAYBACK_STATUS_LOG_INTERVAL.toUInt() == 0u || isAbnormal) {
                PlaybackStateRepository.appendLog(
                    "Protocol: 已发送播放状态 sequence=${status.sequence} playing=${status.isPlaying} lastAudio=${status.lastSequence} age=${status.lastAudioFrameAgeMillis ?: -1}ms latency=${status.bufferedLatencyMillis ?: -1}ms",
                )
            }
        }.onFailure { throwable ->
            PlaybackStateRepository.appendLog("Protocol: 发送播放状态失败：${throwable.message ?: "未知错误"}")
        }
    }

    private fun writePacketToActiveClient(messageType: Int, payload: ByteArray) {
        val packet = BridgeFrameEncoder.encodePacket(messageType, payload)

        synchronized(outputLock) {
            val outputStream = activeClientOutputStream ?: error("Windows 未连接")
            outputStream.write(packet)
            outputStream.flush()
        }
    }

    private fun nextRequestId(): UInt = requestIdGenerator.getAndIncrement().toUInt()

    private fun markPacketReceived(): Long {
        val now = SystemClock.elapsedRealtime()
        val idleMillis = if (lastPacketElapsedRealtime > 0L) now - lastPacketElapsedRealtime else 0L
        lastPacketElapsedRealtime = now
        return idleMillis
    }

    private fun acquireWakeLock() {
        val lock = wakeLock ?: return
        if (!lock.isHeld) {
            lock.acquire()
            PlaybackStateRepository.appendLog("Service: 已获取 PARTIAL_WAKE_LOCK，锁屏时保持接收线程活跃")
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            lock.release()
            PlaybackStateRepository.appendLog("Service: 已释放 PARTIAL_WAKE_LOCK")
        }
    }

    private fun restartBridge() {
        PlaybackStateRepository.appendLog("Service: 正在重启后台服务")
        stopBridge(stopSelfAfterStop = false)
        startBridge()
    }

    private fun stopBridge(stopSelfAfterStop: Boolean = true) {
        PlaybackStateRepository.appendLog("Service: 正在停止后台服务")
        connectJob?.cancel()
        connectJob = null
        activeSocket?.close()
        activeSocket = null
        playbackStatusJob?.cancel()
        playbackStatusJob = null
        synchronized(outputLock) {
            activeClientOutputStream = null
        }
        playbackManager.release()
        releaseWakeLock()
        PlaybackStateRepository.updateServiceRunning(false, "后台服务已停止")
        PlaybackStateRepository.appendLog("Service: 后台服务已停止，连接与播放资源已释放")
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (stopSelfAfterStop) {
            stopSelf()
        }
    }
}

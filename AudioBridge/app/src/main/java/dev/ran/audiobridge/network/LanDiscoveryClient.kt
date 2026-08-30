package dev.ran.audiobridge.network

import dev.ran.audiobridge.model.LanServerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * 局域网发现客户端：发送探测广播（pull 模式），接收 Windows 服务器的单播应答，
 * 维护并去重可用服务器列表；超时未收到应答的服务器会被剔除。
 */
class LanDiscoveryClient(
    private val discoveryPort: Int = 9000,
    private val defaultConnectPort: Int = 6000,
) {
    companion object {
        private const val PROBE_TYPE = "winAudioBridgeProbe"
        private const val ANNOUNCE_TYPE = "winAudioBridgeAnnounce"
        private const val PROBE_APP = "dev.ran.audiobridge"
        private const val SCAN_INTERVAL_MILLIS = 5_000L
        private const val RECEIVE_TIMEOUT_MILLIS = 1_500
        private const val STALE_TIMEOUT_MILLIS = 15_000L
        private const val MAX_DATAGRAM_SIZE = 2048
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableServers = MutableStateFlow<List<LanServerInfo>>(emptyList())

    /** 当前可用的 Windows 服务器列表。 */
    val servers: StateFlow<List<LanServerInfo>> = mutableServers.asStateFlow()

    private val seen = ConcurrentHashMap<String, LanServerInfo>()
    private var scanJob: Job? = null

    /** 开始周期扫描（发送探测并接收应答），直到调用 [stopScanning]。 */
    fun startScanning() {
        if (scanJob?.isActive == true) {
            return
        }
        scanJob = scope.launch {
            while (isActive) {
                scanOnce()
                delay(SCAN_INTERVAL_MILLIS)
                pruneStaleServers()
            }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
    }

    fun clearServers() {
        seen.clear()
        mutableServers.value = emptyList()
    }

    private suspend fun scanOnce() {
        val socket = runCatching { DatagramSocket(discoveryPort) }.getOrNull() ?: return

        try {
            socket.broadcast = true
            socket.soTimeout = RECEIVE_TIMEOUT_MILLIS

            val probe = JSONObject().apply {
                put("t", PROBE_TYPE)
                put("app", PROBE_APP)
                put("ver", 1)
            }.toString().toByteArray(Charsets.UTF_8)

            runCatching {
                socket.send(DatagramPacket(probe, probe.size, InetAddress.getByName("255.255.255.255"), discoveryPort))
            }

            val buffer = ByteArray(MAX_DATAGRAM_SIZE)
            while (currentCoroutineContext().isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                val received = runCatching { socket.receive(packet) }.isSuccess
                if (!received) {
                    break
                }
                val json = String(packet.data, 0, packet.length, Charsets.UTF_8)
                handleAnnounce(json)
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun handleAnnounce(json: String) {
        val server = parseAnnounce(json) ?: return
        val key = "${server.name}|${server.host}"
        seen[key] = server
        mutableServers.value = seen.values.toList()
    }

    /** 解析单条公告 JSON；无效或类型不匹配时返回 null。抽取为纯函数以便单元测试。 */
    internal fun parseAnnounce(json: String): LanServerInfo? = runCatching {
        val obj = JSONObject(json)
        if (obj.optString("t") != ANNOUNCE_TYPE) {
            return@runCatching null
        }
        val name = obj.optString("name")
        val host = obj.optString("host")
        if (name.isBlank() || host.isBlank()) {
            return@runCatching null
        }
        val port = obj.optInt("port", defaultConnectPort)
        LanServerInfo(
            name = name,
            host = host,
            port = port,
            lastSeenMillis = System.currentTimeMillis(),
        )
    }.getOrNull()

    private fun pruneStaleServers() {
        val now = System.currentTimeMillis()
        seen.entries.removeAll { now - it.value.lastSeenMillis > STALE_TIMEOUT_MILLIS }
        mutableServers.value = seen.values.toList()
    }
}

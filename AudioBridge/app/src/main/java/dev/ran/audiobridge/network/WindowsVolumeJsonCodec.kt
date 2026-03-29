package dev.ran.audiobridge.network

import dev.ran.audiobridge.model.WindowsAppVolumeSession
import dev.ran.audiobridge.model.WindowsCommandAck
import dev.ran.audiobridge.model.WindowsMasterVolume
import dev.ran.audiobridge.model.WindowsVolumeCatalog
import org.json.JSONArray
import org.json.JSONObject

object WindowsVolumeJsonCodec {
    fun buildCatalogRequest(requestId: UInt): String = JSONObject()
        .put("requestId", requestId.toLong())
        .put("includeIconsInline", false)
        .toString()

    fun buildSetMasterRequest(requestId: UInt, volume: Float? = null, isMuted: Boolean? = null): String {
        return JSONObject().apply {
            put("requestId", requestId.toLong())
            if (volume != null) {
                put("volume", volume.coerceIn(0f, 1f).toDouble())
            }
            put("hasMute", isMuted != null)
            if (isMuted != null) {
                put("mute", isMuted)
            }
        }.toString()
    }

    fun buildSetSessionRequest(requestId: UInt, sessionId: String, volume: Float? = null, isMuted: Boolean? = null): String {
        return JSONObject().apply {
            put("requestId", requestId.toLong())
            put("sessionId", sessionId)
            if (volume != null) {
                put("volume", volume.coerceIn(0f, 1f).toDouble())
            }
            put("hasMute", isMuted != null)
            if (isMuted != null) {
                put("mute", isMuted)
            }
        }.toString()
    }

    fun parseCatalogSnapshot(payload: String): WindowsVolumeCatalog {
        val root = JSONObject(payload)
        val catalogNode = root.optJSONObject("catalog") ?: root
        val requestId = root.optLong("requestId", catalogNode.optLong("requestId", 0L)).toUInt()
        return parseCatalog(catalogNode, requestId)
    }

    fun parseCommandAck(payload: String): WindowsCommandAck {
        val root = JSONObject(payload)
        val requestId = root.optLong("requestId", 0L).toUInt()
        return WindowsCommandAck(
            requestId = requestId,
            success = root.optBoolean("success", false),
            errorCode = root.optInt("errorCode", 0),
            message = root.optString("message", root.optString("statusMessage", "")),
            catalog = root.optJSONObject("catalog")?.let { parseCatalog(it, requestId) },
            masterVolume = root.optJSONObject("masterVolume")?.let(::parseMasterVolume),
            session = root.optJSONObject("session")?.let(::parseSession),
        )
    }

    fun parseDelta(payload: String): Triple<String, WindowsMasterVolume?, WindowsAppVolumeSession?> {
        val root = JSONObject(payload)
        val deltaType = root.optString("deltaType", root.optString("type", "unknown"))
        val masterVolume = root.optJSONObject("masterVolume")?.let(::parseMasterVolume)
        val session = root.optJSONObject("session")?.let(::parseSession)
        return Triple(deltaType, masterVolume, session)
    }

    fun parseRemovedSessionId(payload: String): String? {
        val root = JSONObject(payload)
        return root.optString("removedSessionId", "").takeIf { it.isNotBlank() }
    }

    private fun parseCatalog(node: JSONObject, requestId: UInt): WindowsVolumeCatalog {
        val sessionsArray = node.optJSONArray("sessions") ?: JSONArray()
        return WindowsVolumeCatalog(
            requestId = requestId,
            capturedAtMillis = node.optLong("capturedAtMillis", node.optLong("capturedAtUtc", 0L)),
            masterVolume = parseMasterVolume(node.optJSONObject("masterVolume") ?: JSONObject()),
            sessions = buildList {
                for (index in 0 until sessionsArray.length()) {
                    add(parseSession(sessionsArray.getJSONObject(index)))
                }
            },
        )
    }

    private fun parseMasterVolume(node: JSONObject): WindowsMasterVolume {
        return WindowsMasterVolume(
            deviceId = node.optString("deviceId", ""),
            deviceName = node.optString("deviceName", "未知输出设备"),
            volume = node.optDouble("volume", 0.0).toFloat().coerceIn(0f, 1f),
            isMuted = node.optBoolean("isMuted", node.optBoolean("muted", false)),
            capturedAtMillis = node.optLong("capturedAtMillis", node.optLong("capturedAtUtc", node.optLong("lastUpdatedUtc", 0L))),
        )
    }

    private fun parseSession(node: JSONObject): WindowsAppVolumeSession {
        return WindowsAppVolumeSession(
            sessionId = node.optString("sessionId", ""),
            processId = node.optInt("processId", 0),
            processName = node.optString("processName", ""),
            displayName = node.optString("displayName", node.optString("processName", "未知应用")),
            state = node.optString("state", "Unknown"),
            volume = node.optDouble("volume", 0.0).toFloat().coerceIn(0f, 1f),
            isMuted = node.optBoolean("isMuted", node.optBoolean("muted", false)),
            iconKey = node.optString("iconKey", ""),
            iconHash = node.optString("iconHash", ""),
            iconBase64 = node.optString("iconBase64", "").takeIf { it.isNotBlank() },
        )
    }
}
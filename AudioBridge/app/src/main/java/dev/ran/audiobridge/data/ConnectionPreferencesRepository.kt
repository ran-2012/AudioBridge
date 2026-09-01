package dev.ran.audiobridge.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.connectionDataStore by preferencesDataStore(name = "connection_prefs")

/** 已保存的局域网连接目标。 */
data class SavedLanTarget(
    val host: String,
    val port: Int,
)

/**
 * 连接偏好仓库：持久化用户手动输入的局域网服务器 IP 与端口，
 * 避免每次都需要重新配置。
 */
class ConnectionPreferencesRepository(private val context: Context) {
    private val hostKey = stringPreferencesKey("lan_host")
    private val portKey = intPreferencesKey("lan_port")

    /** 已保存的 LAN 目标；无有效保存时为 null。 */
    val savedLanTargetFlow: Flow<SavedLanTarget?> = context.connectionDataStore.data.map { preferences ->
        val host = preferences[hostKey]?.takeIf { it.isNotBlank() }
        val port = preferences[portKey]
        if (host != null && port != null) SavedLanTarget(host, port) else null
    }

    suspend fun saveLanTarget(host: String, port: Int) {
        if (host.isBlank() || port <= 0) {
            return
        }
        context.connectionDataStore.edit { preferences ->
            preferences[hostKey] = host.trim()
            preferences[portKey] = port
        }
    }

    suspend fun clearLanTarget() {
        context.connectionDataStore.edit { preferences ->
            preferences.remove(hostKey)
            preferences.remove(portKey)
        }
    }
}

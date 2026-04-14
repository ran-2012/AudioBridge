package dev.ran.audiobridge.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.ran.audiobridge.audio.PlaybackCacheConfig
import dev.ran.audiobridge.model.HiddenWindowsApp
import dev.ran.audiobridge.model.HiddenWindowsAppSupport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "audio_bridge_settings")

class VolumePreferencesRepository(private val context: Context) {
    private val volumeKey = floatPreferencesKey("playback_volume")
    private val playbackCacheMillisecondsKey = intPreferencesKey("playback_cache_milliseconds")
    private val hiddenAppsInitializedKey = booleanPreferencesKey("hidden_windows_apps_initialized")
    private val hiddenProcessNamesKey = stringSetPreferencesKey("hidden_windows_process_names")
    private val hiddenWindowsAppsKey = stringPreferencesKey("hidden_windows_apps_json")

    val volumeFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[volumeKey] ?: 1.0f
    }

    val playbackCacheMillisecondsFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        PlaybackCacheConfig.normalize(
            preferences[playbackCacheMillisecondsKey] ?: PlaybackCacheConfig.DEFAULT_MILLISECONDS,
        )
    }

    val hiddenProcessNamesFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        readHiddenProcessNames(preferences)
    }

    val hiddenWindowsAppsFlow: Flow<List<HiddenWindowsApp>> = context.dataStore.data.map { preferences ->
        readHiddenWindowsApps(preferences)
    }

    suspend fun saveVolume(volume: Float) {
        context.dataStore.edit { preferences ->
            preferences[volumeKey] = volume.coerceIn(0f, 1f)
        }
    }

    suspend fun savePlaybackCacheMilliseconds(milliseconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[playbackCacheMillisecondsKey] = PlaybackCacheConfig.normalize(milliseconds)
        }
    }

    suspend fun hideWindowsApp(app: HiddenWindowsApp) {
        if (app.processName.isBlank()) {
            return
        }

        context.dataStore.edit { preferences ->
            val hiddenProcessNames = readHiddenProcessNames(preferences).toMutableSet()
            hiddenProcessNames += app.processName.trim()

            val currentApps = readHiddenWindowsApps(preferences)
            val updatedApps = currentApps
                .filterNot { it.processName == app.processName.trim() }
                .plus(HiddenWindowsAppSupport.mergeHiddenWindowsApp(currentApps.firstOrNull { it.processName == app.processName.trim() }, app))

            preferences[hiddenAppsInitializedKey] = true
            preferences[hiddenProcessNamesKey] = hiddenProcessNames
            preferences[hiddenWindowsAppsKey] = HiddenWindowsAppPreferencesCodec.encodeHiddenWindowsApps(updatedApps)
        }
    }

    suspend fun unhideWindowsApp(processName: String) {
        val normalizedProcessName = processName.trim()
        if (normalizedProcessName.isBlank()) {
            return
        }

        context.dataStore.edit { preferences ->
            val hiddenProcessNames = readHiddenProcessNames(preferences)
                .filterNot { it == normalizedProcessName }
                .toSet()
            val hiddenApps = readHiddenWindowsApps(preferences)
                .filterNot { it.processName == normalizedProcessName }

            preferences[hiddenAppsInitializedKey] = true
            preferences[hiddenProcessNamesKey] = hiddenProcessNames
            preferences[hiddenWindowsAppsKey] = HiddenWindowsAppPreferencesCodec.encodeHiddenWindowsApps(hiddenApps)
        }
    }

    suspend fun updateHiddenWindowsAppMetadata(app: HiddenWindowsApp) {
        val normalizedProcessName = app.processName.trim()
        if (normalizedProcessName.isBlank()) {
            return
        }

        context.dataStore.edit { preferences ->
            val hiddenProcessNames = readHiddenProcessNames(preferences)
            if (!hiddenProcessNames.contains(normalizedProcessName)) {
                return@edit
            }

            val currentApps = readHiddenWindowsApps(preferences)
            val updatedApps = currentApps
                .filterNot { it.processName == normalizedProcessName }
                .plus(HiddenWindowsAppSupport.mergeHiddenWindowsApp(currentApps.firstOrNull { it.processName == normalizedProcessName }, app))

            preferences[hiddenAppsInitializedKey] = true
            preferences[hiddenWindowsAppsKey] = HiddenWindowsAppPreferencesCodec.encodeHiddenWindowsApps(updatedApps)
        }
    }

    private fun readHiddenProcessNames(preferences: Preferences): Set<String> {
        if (!(preferences[hiddenAppsInitializedKey] ?: false)) {
            return HiddenWindowsAppPreferencesCodec.defaultHiddenProcessNames()
        }

        return preferences[hiddenProcessNamesKey] ?: emptySet()
    }

    private fun readHiddenWindowsApps(preferences: Preferences): List<HiddenWindowsApp> {
        if (!(preferences[hiddenAppsInitializedKey] ?: false)) {
            return HiddenWindowsAppPreferencesCodec.defaultHiddenWindowsApps()
        }

        return HiddenWindowsAppPreferencesCodec.decodeHiddenWindowsApps(preferences[hiddenWindowsAppsKey])
    }
}

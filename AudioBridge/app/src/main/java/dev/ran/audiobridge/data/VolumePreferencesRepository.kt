package dev.ran.audiobridge.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.ran.audiobridge.audio.PlaybackCacheConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "audio_bridge_settings")

class VolumePreferencesRepository(private val context: Context) {
    private val volumeKey = floatPreferencesKey("playback_volume")
    private val playbackCacheMillisecondsKey = intPreferencesKey("playback_cache_milliseconds")

    val volumeFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[volumeKey] ?: 1.0f
    }

    val playbackCacheMillisecondsFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        PlaybackCacheConfig.normalize(
            preferences[playbackCacheMillisecondsKey] ?: PlaybackCacheConfig.DEFAULT_MILLISECONDS,
        )
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
}

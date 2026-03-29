package dev.ran.audiobridge.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "audio_bridge_settings")

class VolumePreferencesRepository(private val context: Context) {
    private val volumeKey = floatPreferencesKey("playback_volume")

    val volumeFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[volumeKey] ?: 1.0f
    }

    suspend fun saveVolume(volume: Float) {
        context.dataStore.edit { preferences ->
            preferences[volumeKey] = volume.coerceIn(0f, 1f)
        }
    }
}

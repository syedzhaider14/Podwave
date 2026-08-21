package app.podwave.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class Settings(
    val downloadOnWifiOnly: Boolean = true,
    val autoRefresh: Boolean = true,
    val playbackSpeed: Float = 1.0f,
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<Settings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            Settings(
                downloadOnWifiOnly = prefs[WIFI_ONLY] ?: true,
                autoRefresh = prefs[AUTO_REFRESH] ?: true,
                playbackSpeed = (prefs[SPEED] ?: 1.0f).coerceIn(MIN_SPEED, MAX_SPEED),
            )
        }

    suspend fun setDownloadOnWifiOnly(value: Boolean) {
        dataStore.edit { it[WIFI_ONLY] = value }
    }

    suspend fun setAutoRefresh(value: Boolean) {
        dataStore.edit { it[AUTO_REFRESH] = value }
    }

    suspend fun setPlaybackSpeed(value: Float) {
        dataStore.edit { it[SPEED] = value.coerceIn(MIN_SPEED, MAX_SPEED) }
    }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.0f
        val SPEEDS = listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.8f, 2.0f, 2.5f)

        private val WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        private val AUTO_REFRESH = booleanPreferencesKey("auto_refresh")
        private val SPEED = floatPreferencesKey("playback_speed")
    }
}

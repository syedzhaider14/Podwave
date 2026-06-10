package dev.podwave.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.podwave.model.AppSettings
import dev.podwave.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun setPlaybackSpeed(speed: Float) = edit { it[Keys.speed] = speed.coerceIn(0.5f, 3f) }

    suspend fun setSkipBack(seconds: Int) = edit { it[Keys.skipBack] = seconds }

    suspend fun setSkipForward(seconds: Int) = edit { it[Keys.skipForward] = seconds }

    suspend fun setAutoDownload(enabled: Boolean) = edit { it[Keys.autoDownload] = enabled }

    suspend fun setWifiOnly(enabled: Boolean) = edit { it[Keys.wifiOnly] = enabled }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.theme] = mode.name }

    private suspend fun edit(block: suspend (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private object Keys {
        val speed = floatPreferencesKey("playback_speed")
        val skipBack = intPreferencesKey("skip_back_seconds")
        val skipForward = intPreferencesKey("skip_forward_seconds")
        val autoDownload = booleanPreferencesKey("auto_download")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val theme = stringPreferencesKey("theme")
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            playbackSpeed = this[Keys.speed] ?: defaults.playbackSpeed,
            skipBackSeconds = this[Keys.skipBack] ?: defaults.skipBackSeconds,
            skipForwardSeconds = this[Keys.skipForward] ?: defaults.skipForwardSeconds,
            autoDownloadNewEpisodes = this[Keys.autoDownload] ?: defaults.autoDownloadNewEpisodes,
            downloadOnWifiOnly = this[Keys.wifiOnly] ?: defaults.downloadOnWifiOnly,
            themeMode = this[Keys.theme]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: defaults.themeMode,
        )
    }
}

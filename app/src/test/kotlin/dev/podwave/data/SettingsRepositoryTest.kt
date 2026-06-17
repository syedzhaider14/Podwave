package dev.podwave.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.podwave.model.AppSettings
import dev.podwave.model.ThemeMode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.repository() = SettingsRepository(
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { folder.newFile("settings.preferences_pb") },
        ),
    )

    @Test
    fun `starts from the defaults`() = runTest {
        assertThat(repository().current()).isEqualTo(AppSettings())
    }

    @Test
    fun `persists every setting`() = runTest {
        val settings = repository()

        settings.setPlaybackSpeed(1.5f)
        settings.setSkipBack(15)
        settings.setSkipForward(45)
        settings.setAutoDownload(true)
        settings.setWifiOnly(false)
        settings.setThemeMode(ThemeMode.Dark)

        assertThat(settings.current()).isEqualTo(
            AppSettings(
                playbackSpeed = 1.5f,
                skipBackSeconds = 15,
                skipForwardSeconds = 45,
                autoDownloadNewEpisodes = true,
                downloadOnWifiOnly = false,
                themeMode = ThemeMode.Dark,
            ),
        )
    }

    @Test
    fun `keeps the speed within what the player supports`() = runTest {
        val settings = repository()

        settings.setPlaybackSpeed(5f)
        assertThat(settings.current().playbackSpeed).isEqualTo(3f)

        settings.setPlaybackSpeed(0.1f)
        assertThat(settings.current().playbackSpeed).isEqualTo(0.5f)
    }
}

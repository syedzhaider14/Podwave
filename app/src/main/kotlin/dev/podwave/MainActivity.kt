package dev.podwave

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.podwave.data.SettingsRepository
import dev.podwave.model.AppSettings
import dev.podwave.playback.PlayerController
import dev.podwave.ui.PodwaveApp
import dev.podwave.ui.theme.PodwaveTheme
import dev.podwave.ui.theme.isDark
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playerController: PlayerController

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = settings.themeMode.isDark(isSystemInDarkTheme())

            // Keep status and navigation bar icons readable when the in-app theme differs from the system's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }

            PodwaveTheme(themeMode = settings.themeMode) {
                PodwaveApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Picks up playback that kept running in the service while the UI was gone.
        playerController.connect()
    }

    private companion object {
        // The defaults `enableEdgeToEdge` uses for three-button navigation.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

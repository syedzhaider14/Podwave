package app.podwave

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.podwave.ui.navigation.PodwaveNavHost
import app.podwave.ui.theme.PodwaveTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Playback works either way; the permission only controls the media and download notifications.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && savedInstanceState == null) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            PodwaveTheme {
                PodwaveNavHost(appVersion = BuildConfig.VERSION_NAME)
            }
        }
    }
}

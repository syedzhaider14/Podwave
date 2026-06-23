package dev.podwave.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Returns a function that asks for the notification permission (Android 13+) if it hasn't been
 * granted. Called right before the first download, when the reason for asking is obvious.
 * Downloads still work without it; only the progress notification is hidden.
 */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return remember { {} }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

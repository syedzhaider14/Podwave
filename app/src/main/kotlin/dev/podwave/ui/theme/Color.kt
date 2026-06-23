package dev.podwave.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Used when dynamic color is unavailable (Android 11 and below).
internal val LightColors = lightColorScheme(
    primary = Color(0xFF6B4EA2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBDDFF),
    onPrimaryContainer = Color(0xFF250059),
    secondary = Color(0xFF00677F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB7EAFF),
    onSecondaryContainer = Color(0xFF001F28),
    tertiary = Color(0xFF9A4058),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9DF),
    onTertiaryContainer = Color(0xFF3F0017),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454E),
    surfaceContainer = Color(0xFFF2ECF4),
    surfaceContainerHigh = Color(0xFFECE6EE),
    outline = Color(0xFF7A757F),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFD3BBFF),
    onPrimary = Color(0xFF3B1F70),
    primaryContainer = Color(0xFF523788),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFF5DD5FC),
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF004E60),
    onSecondaryContainer = Color(0xFFB7EAFF),
    tertiary = Color(0xFFFFB1C1),
    onTertiary = Color(0xFF5F112B),
    tertiaryContainer = Color(0xFF7D2941),
    onTertiaryContainer = Color(0xFFFFD9DF),
    background = Color(0xFF151218),
    onBackground = Color(0xFFE7E0E8),
    surface = Color(0xFF151218),
    onSurface = Color(0xFFE7E0E8),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFCBC4CF),
    surfaceContainer = Color(0xFF211F24),
    surfaceContainerHigh = Color(0xFF2C292F),
    outline = Color(0xFF948F99),
)

package dev.ran.audiobridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE5E5E7),
    onPrimary = Color(0xFF1A1A1C),
    primaryContainer = Color(0xFF3A3A3D),
    onPrimaryContainer = Color(0xFFF2F2F4),
    secondary = Color(0xFFC8C8CC),
    onSecondary = Color(0xFF242426),
    secondaryContainer = Color(0xFF3C3C40),
    onSecondaryContainer = Color(0xFFE8E8EB),
    background = Color(0xFF111113),
    onBackground = Color(0xFFE7E7E9),
    surface = Color(0xFF19191B),
    onSurface = Color(0xFFE7E7E9),
    surfaceDim = Color(0xFF111113),
    surfaceBright = Color(0xFF38383B),
    surfaceContainerLowest = Color(0xFF0C0C0E),
    surfaceContainerLow = Color(0xFF19191B),
    surfaceContainer = Color(0xFF1E1E20),
    surfaceContainerHigh = Color(0xFF28282B),
    surfaceContainerHighest = Color(0xFF333336),
    surfaceVariant = Color(0xFF29292C),
    onSurfaceVariant = Color(0xFFC7C7CB),
    outline = Color(0xFF909094),
    outlineVariant = Color(0xFF454548),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun AudioBridgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
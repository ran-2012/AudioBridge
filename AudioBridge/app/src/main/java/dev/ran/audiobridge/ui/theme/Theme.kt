package dev.ran.audiobridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF303034),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E3E6),
    onPrimaryContainer = Color(0xFF202023),
    secondary = Color(0xFF5F5F64),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E8EB),
    onSecondaryContainer = Color(0xFF222225),
    background = Color(0xFFF8F8FA),
    onBackground = Color(0xFF202022),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202022),
    surfaceDim = Color(0xFFDADADD),
    surfaceBright = Color(0xFFFCFCFE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F4F6),
    surfaceContainer = Color(0xFFEEEEF1),
    surfaceContainerHigh = Color(0xFFE8E8EB),
    surfaceContainerHighest = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFFE9E9EC),
    onSurfaceVariant = Color(0xFF48484C),
    outline = Color(0xFF77777C),
    outlineVariant = Color(0xFFC8C8CC),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

@Composable
fun AudioBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
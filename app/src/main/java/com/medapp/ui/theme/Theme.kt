package com.medapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Brand Colors ─────────────────────────────────────────────────────────────
val MedBlue = Color(0xFF1565C0)
val MedBlueDark = Color(0xFF0D47A1)
val MedBlueLight = Color(0xFF1E88E5)
val MedTeal = Color(0xFF00ACC1)
val MedGreen = Color(0xFF2E7D32)
val MedOrange = Color(0xFFF57C00)
val MedRed = Color(0xFFC62828)
val MedSurface = Color(0xFFF5F7FA)
val MedCard = Color(0xFFFFFFFF)

// Status Colors
val StatusPending = Color(0xFFF57C00)
val StatusConfirmed = Color(0xFF1565C0)
val StatusCompleted = Color(0xFF2E7D32)
val StatusCancelled = Color(0xFFC62828)

private val LightColorScheme = lightColorScheme(
    primary = MedBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = MedTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2EBF2),
    onSecondaryContainer = Color(0xFF001F24),
    background = MedSurface,
    onBackground = Color(0xFF1A1C1E),
    surface = MedCard,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE8EEF7),
    onSurfaceVariant = Color(0xFF43474E),
    error = MedRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = MedBlueDark,
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF80DEEA),
    onSecondary = Color(0xFF003640),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun MedAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

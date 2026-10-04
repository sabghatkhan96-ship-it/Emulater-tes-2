package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = VaultDarkPrimary,
    onPrimary = VaultDarkOnPrimary,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = VaultDarkSecondary,
    onSecondary = VaultDarkOnSecondary,
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = VaultDarkTertiary,
    onTertiary = VaultDarkOnTertiary,
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFDE68A),
    background = VaultDarkBackground,
    onBackground = VaultDarkText,
    surface = VaultDarkSurface,
    onSurface = VaultDarkText,
    surfaceVariant = VaultDarkSurfaceVariant,
    onSurfaceVariant = VaultDarkTextSecondary,
    error = VaultDanger,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = VaultLightPrimary,
    onPrimary = VaultLightOnPrimary,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF065F46),
    secondary = VaultLightSecondary,
    onSecondary = VaultLightOnSecondary,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = VaultLightTertiary,
    onTertiary = VaultLightOnTertiary,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF92400E),
    background = VaultLightBackground,
    onBackground = VaultLightText,
    surface = VaultLightSurface,
    onSurface = VaultLightText,
    surfaceVariant = VaultLightSurfaceVariant,
    onSurfaceVariant = VaultLightTextSecondary,
    error = VaultDanger,
    onError = Color.White
)

@Composable
fun VaultPassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep branded high-security aesthetic
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward-compatible alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = VaultPassTheme(darkTheme, dynamicColor, content)

package com.kasirkita.pos.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = KasirDarkPrimary,
    onPrimary = KasirDarkOnPrimary,
    primaryContainer = KasirDarkPrimaryContainer,
    onPrimaryContainer = KasirDarkOnPrimaryContainer,
    background = KasirDarkBackground,
    onBackground = KasirDarkOnBackground,
    surface = KasirDarkSurface,
    onSurface = KasirDarkOnSurface,
    surfaceVariant = KasirDarkSurfaceVariant,
    onSurfaceVariant = KasirDarkOnSurfaceVariant,
    outline = KasirDarkOutline,
    outlineVariant = KasirDarkOutlineVariant,
    error = KasirError,
    onError = KasirOnError,
)

private val LightColorScheme = lightColorScheme(
    primary = KasirPrimary,
    onPrimary = KasirOnPrimary,
    primaryContainer = KasirPrimaryContainer,
    onPrimaryContainer = KasirOnPrimaryContainer,
    background = KasirBackground,
    onBackground = KasirOnBackground,
    surface = KasirSurface,
    onSurface = KasirOnSurface,
    surfaceVariant = KasirSurfaceVariant,
    onSurfaceVariant = KasirOnSurfaceVariant,
    outline = KasirOutline,
    outlineVariant = KasirOutlineVariant,
    error = KasirError,
    onError = KasirOnError,
    errorContainer = KasirErrorContainer,
    onErrorContainer = KasirOnErrorContainer,
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
        content = content,
    )
}

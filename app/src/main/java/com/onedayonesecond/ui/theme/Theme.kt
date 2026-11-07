package com.onedayonesecond.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A0),
    onPrimary = Color(0xFF5F1600),
    surface = Color(0xFF101014),
    background = Color(0xFF0B0B0E),
    surfaceVariant = Color(0xFF1C1C22),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9C4324),
    onPrimary = Color.White,
)

@Composable
fun OneDayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

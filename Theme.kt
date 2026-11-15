package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val EkambaniGreen = Color(0xFF1F6B2C)

private val EkambaniColors = lightColorScheme(
    primary = EkambaniGreen,
    onPrimary = Color.White,
    background = Color.White,
    surface = Color.White,
    onBackground = Color(0xFF1B1B1B),
    onSurface = Color(0xFF1B1B1B),
    onSurfaceVariant = Color(0xFF4A4A4A)
)

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EkambaniColors, content = content)
}

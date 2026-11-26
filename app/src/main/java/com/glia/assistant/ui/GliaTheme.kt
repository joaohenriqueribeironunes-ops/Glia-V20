package com.glia.assistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Paleta roxa HyperOS-like do Glia (sem amarelo; claro/escuro). */
private val GliaPurple = Color(0xFF7C6CFF)
private val GliaPurpleDeep = Color(0xFF6D28D9)
private val GliaTeal = Color(0xFF4FD1C5)

private val DarkColors = darkColorScheme(
    primary = GliaPurple,
    onPrimary = Color.White,
    primaryContainer = GliaPurpleDeep,
    onPrimaryContainer = Color.White,
    secondary = GliaTeal,
    onSecondary = Color(0xFF0A0A12),
    background = Color(0xFF0A0A12),
    onBackground = Color(0xFFE8E8F2),
    surface = Color(0xFF14142A),
    onSurface = Color(0xFFE8E8F2),
    surfaceVariant = Color(0xFF1E1E38),
    onSurfaceVariant = Color(0xFFB0B0C8),
    error = Color(0xFFFF6B6B)
)

private val LightColors = lightColorScheme(
    primary = GliaPurpleDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = GliaPurpleDeep,
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    background = Color(0xFFF7F6FC),
    onBackground = Color(0xFF1B1B2F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B2F),
    surfaceVariant = Color(0xFFF0EEFA),
    onSurfaceVariant = Color(0xFF57576E),
    error = Color(0xFFB3261E)
)

private val GliaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun GliaTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = GliaShapes,
        content = content
    )
}

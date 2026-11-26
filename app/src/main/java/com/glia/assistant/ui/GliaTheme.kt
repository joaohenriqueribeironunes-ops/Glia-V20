package com.glia.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Tokens de movimento e geometria inspirados na linguagem visual HyperOS. */
object GliaMotion {
    const val FAST = 140
    const val NORMAL = 220
    const val SLOW = 360
}

object GliaRadius {
    val xs = 12.dp
    val sm = 16.dp
    val md = 22.dp
    val lg = 28.dp
    val pill = 50.dp
}

object GliaAccent {
    val Purple = Color(0xFF7C6CFF)
    val PurpleDeep = Color(0xFF6D28D9)
    val Teal = Color(0xFF4FD1C5)
    val Coral = Color(0xFFFF7A59)
    val Lavender = Color(0xFFA78BFA)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA99BFF),
    onPrimary = Color(0xFF17122A),
    primaryContainer = Color(0xFF2A2340),
    onPrimaryContainer = Color(0xFFEDE9FF),
    secondary = GliaAccent.Teal,
    onSecondary = Color(0xFF07191A),
    background = Color(0xFF0C0B12),
    onBackground = Color(0xFFF4F2FA),
    surface = Color(0xFF181722),
    onSurface = Color(0xFFF4F2FA),
    surfaceVariant = Color(0xFF262436),
    onSurfaceVariant = Color(0xFFB7B2C7),
    outline = Color(0xFF3E3A52),
    error = Color(0xFFFF8A7A)
)

private val LightColors = lightColorScheme(
    primary = GliaAccent.PurpleDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FF),
    onPrimaryContainer = Color(0xFF3B2A7A),
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    background = Color(0xFFF5F4FA),
    onBackground = Color(0xFF1A1826),
    surface = Color.White,
    onSurface = Color(0xFF1A1826),
    surfaceVariant = Color(0xFFF0EEF9),
    onSurfaceVariant = Color(0xFF605B72),
    outline = Color(0xFFD8D4E6),
    error = Color(0xFFB3261E)
)

private val GliaShapes = Shapes(
    small = RoundedCornerShape(GliaRadius.xs),
    medium = RoundedCornerShape(GliaRadius.md),
    large = RoundedCornerShape(GliaRadius.lg)
)

private val GliaTypography = Typography().copy(
    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.Medium),
    bodyMedium = Typography().bodyMedium.copy(fontWeight = FontWeight.Normal)
)

@Composable
fun GliaTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = GliaTypography,
        shapes = GliaShapes,
        content = content
    )
}

/** Fundo ambiente com luz suave e cor adaptável ao estado do sistema. */
@Composable
fun GliaAmbientModifier(): Modifier {
    val dark = isSystemInDarkTheme()
    val top = if (dark) Color(0xFF1A1533) else Color(0xFFEDE8FF)
    val mid = if (dark) Color(0xFF0C0B12) else Color(0xFFF7F6FC)
    val bottom = if (dark) Color(0xFF0A1419) else Color(0xFFEAF7F5)
    return Modifier.background(
        Brush.verticalGradient(listOf(top, mid, bottom))
    )
}

/** Cartão translúcido, cantos generosos e borda luminosa discreta. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val container = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.70f else 0.82f)
    val borderColor = if (highlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
    }
    Card(
        modifier = modifier
            .shadow(
                elevation = if (highlighted) 12.dp else 3.dp,
                shape = RoundedCornerShape(GliaRadius.lg)
            )
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(GliaRadius.lg)),
        shape = RoundedCornerShape(GliaRadius.lg),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        content()
    }
}

/** "Ilha" de status: pílula compacta no topo, com respiro quando ativa. */
@Composable
fun HyperIsland(
    label: String,
    active: Boolean,
    amplitude: Float,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "island")
    val glow by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "islandGlow"
    )
    val shape = RoundedCornerShape(GliaRadius.pill)
    val islandBg = Color(0xFF11101A).copy(alpha = 0.94f)
    Box(
        modifier = Modifier
            .widthIn(min = 190.dp)
            .height(if (active) 46.dp else 38.dp)
            .clip(shape)
            .background(islandBg)
            .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val dot = if (active) GliaAccent.Purple else Color(0xFF6E6A80)
            Canvas(modifier = Modifier.size(9.dp)) {
                drawCircle(color = dot.copy(alpha = if (active) glow else 0.7f))
            }
            Text(
                text = label,
                color = Color(0xFFF6F4FF),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (active) {
                MiniWave(amplitude = amplitude)
            }
        }
    }
}

/** Onda mínima desenhada na ilha (sem imagens nem emoji). */
@Composable
private fun MiniWave(amplitude: Float) {
    val bars = listOf(0.35f, 0.8f, 0.55f, 1f, 0.4f)
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        bars.forEach { base ->
            val h = (6f + base * 12f * (0.55f + amplitude.coerceIn(0f, 1f) * 0.7f)).dp
            Box(
                Modifier
                    .size(width = 2.dp, height = h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GliaAccent.Purple.copy(alpha = 0.9f))
            )
        }
    }
}

/** Bloco do centro de controle: ícone vetorial, rótulo e estado. */
@Composable
fun HyperTile(
    icon: @Composable () -> Unit,
    label: String,
    caption: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(GliaRadius.md)
    Card(
        modifier = modifier
            .height(104.dp)
            .clip(shape)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                shape
            )
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(
                alpha = if (active) 0.94f else 0.78f
            )
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (active) accent.copy(alpha = 0.92f) else accent.copy(alpha = 0.16f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
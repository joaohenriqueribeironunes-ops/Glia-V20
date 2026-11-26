package com.glia.assistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Ícones vetoriais próprios, traço limpo e consistente (sem emoji e sem
 * dependência de biblioteca de ícones). Usados nas pastilhas do centro de
 * controle e na barra superior.
 */
@Composable
fun GliaGlyph(kind: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        when (kind) {
            "mic" -> drawMic(tint)
            "chat" -> drawChat(tint)
            "brain" -> drawBrain(tint)
            "reload" -> drawReload(tint)
            "stop" -> drawStop(tint)
            "settings" -> drawSettings(tint)
            "spark" -> drawSpark(tint)
            "wifi" -> drawWifi(tint)
            "bluetooth" -> drawBluetooth(tint)
        }
    }
}

private fun DrawScope.line(c: Color, from: Offset, to: Offset) {
    drawLine(
        color = c,
        start = from,
        end = to,
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
}

private fun DrawScope.outline(c: Color) = Stroke(width = 2.dp.toPx())

private fun DrawScope.drawMic(c: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = c,
        topLeft = Offset(w * 0.36f, h * 0.18f),
        size = Size(w * 0.28f, h * 0.42f),
        cornerRadius = CornerRadius(w * 0.14f),
        style = outline(c)
    )
    drawArc(
        color = c,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.24f, h * 0.38f),
        size = Size(w * 0.52f, h * 0.34f),
        style = outline(c)
    )
    line(c, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.86f))
    line(c, Offset(w * 0.36f, h * 0.86f), Offset(w * 0.64f, h * 0.86f))
}

private fun DrawScope.drawChat(c: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = c,
        topLeft = Offset(w * 0.16f, h * 0.2f),
        size = Size(w * 0.68f, h * 0.52f),
        cornerRadius = CornerRadius(w * 0.16f),
        style = outline(c)
    )
    line(c, Offset(w * 0.28f, h * 0.72f), Offset(w * 0.28f, h * 0.84f))
    for (i in 0..2) {
        val y = h * (0.38f + i * 0.13f)
        line(c, Offset(w * 0.3f, y), Offset(w * (0.62f - i * 0.08f), y))
    }
}

private fun DrawScope.drawBrain(c: Color) {
    val w = size.width
    val h = size.height
    drawCircle(c, radius = w * 0.22f, center = Offset(w * 0.37f, h * 0.42f), style = outline(c))
    drawCircle(c, radius = w * 0.22f, center = Offset(w * 0.63f, h * 0.42f), style = outline(c))
    drawCircle(c, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.66f), style = outline(c))
    line(c, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.84f))
}

private fun DrawScope.drawReload(c: Color) {
    val w = size.width
    val h = size.height
    drawArc(
        color = c,
        startAngle = -70f,
        sweepAngle = 280f,
        useCenter = false,
        topLeft = Offset(w * 0.2f, h * 0.2f),
        size = Size(w * 0.6f, h * 0.6f),
        style = outline(c)
    )
    line(c, Offset(w * 0.78f, h * 0.3f), Offset(w * 0.8f, h * 0.48f))
    line(c, Offset(w * 0.8f, h * 0.48f), Offset(w * 0.62f, h * 0.46f))
}

private fun DrawScope.drawStop(c: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = c,
        topLeft = Offset(w * 0.32f, h * 0.32f),
        size = Size(w * 0.36f, h * 0.36f),
        cornerRadius = CornerRadius(w * 0.07f)
    )
}

private fun DrawScope.drawSettings(c: Color) {
    val w = size.width
    val h = size.height
    drawCircle(c, radius = w * 0.14f, center = Offset(w * 0.5f, h * 0.5f), style = outline(c))
    for (i in 0..5) {
        val a = Math.toRadians((i * 60).toDouble()).toFloat()
        line(
            c,
            Offset(w * 0.5f + kotlin.math.cos(a) * w * 0.22f, h * 0.5f + kotlin.math.sin(a) * h * 0.22f),
            Offset(w * 0.5f + kotlin.math.cos(a) * w * 0.36f, h * 0.5f + kotlin.math.sin(a) * h * 0.36f)
        )
    }
}

private fun DrawScope.drawSpark(c: Color) {
    val w = size.width
    val h = size.height
    drawCircle(c, radius = w * 0.14f, center = Offset(w * 0.5f, h * 0.5f))
    for (i in 0..7) {
        val a = Math.toRadians((i * 45).toDouble()).toFloat()
        line(
            c,
            Offset(w * 0.5f + kotlin.math.cos(a) * w * 0.22f, h * 0.5f + kotlin.math.sin(a) * h * 0.22f),
            Offset(w * 0.5f + kotlin.math.cos(a) * w * 0.38f, h * 0.5f + kotlin.math.sin(a) * h * 0.38f)
        )
    }
}

private fun DrawScope.drawWifi(c: Color) {
    val w = size.width
    val h = size.height
    for (i in 0..2) {
        val inset = w * (0.12f + i * 0.12f)
        drawArc(
            color = c,
            startAngle = 210f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(inset, h * (0.24f + i * 0.14f)),
            size = Size(w - inset * 2, h * 0.7f),
            style = outline(c)
        )
    }
    drawCircle(c, radius = w * 0.04f, center = Offset(w * 0.5f, h * 0.8f))
}

private fun DrawScope.drawBluetooth(c: Color) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    line(c, Offset(cx, h * 0.15f), Offset(cx, h * 0.85f))
    line(c, Offset(w * 0.26f, h * 0.35f), Offset(w * 0.74f, h * 0.68f))
    line(c, Offset(w * 0.26f, h * 0.68f), Offset(w * 0.74f, h * 0.35f))
}

package com.forgebuild.forgehouse50.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Material 3 Expressive celebration: a genuine multi-particle confetti burst.
 * Varied shapes (rect / circle), theme-derived + warm palette, gravity with
 * flutter, rotation per piece, and a comfortable ~3.2s lifetime. Rendered as
 * a non-interactive overlay (does not block taps on the completion CTA).
 */
private enum class Shape { RECT, CIRCLE }

private data class Particle(
    val x: Float, val y: Float, val vx: Float, val vy: Float,
    val color: Color, val size: Float, val angle: Float, val spin: Float,
    val shape: Shape, val flutter: Float, val phase: Float,
)

@Composable
fun ConfettiOverlay(
    visible: Boolean,
    onFinished: () -> Unit,
    accent: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    accent2: Color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
) {
    if (!visible) return
    val palette = remember(accent, accent2) {
        listOf(
            accent, accent2,
            Color(0xFFC9A227), Color(0xFF5F8A6B),
            Color(0xFF8A5A6B), Color(0xFFE0B84C), Color(0xFF7F9FC4),
        )
    }
    var particles by remember { mutableStateOf<List<Particle>>(emptyList()) }

    LaunchedEffect(visible) {
        var current = List(160) {
            val a = Random.nextDouble(0.0, Math.PI * 2)
            val speed = Random.nextFloat() * 16f + 6f
            Particle(
                x = 0.5f + (Random.nextFloat() * 0.16f - 0.08f),
                y = 0.34f,
                vx = (cos(a) * speed).toFloat(),
                vy = (sin(a) * speed).toFloat() - 13f,
                color = palette[Random.nextInt(palette.size)],
                size = Random.nextFloat() * 14f + 7f,
                angle = Random.nextFloat() * 360f,
                spin = Random.nextFloat() * 16f - 8f,
                shape = if (Random.nextFloat() < 0.6f) Shape.RECT else Shape.CIRCLE,
                flutter = Random.nextFloat() * 0.9f + 0.4f,
                phase = Random.nextFloat() * 6.28f,
            )
        }
        val start = System.nanoTime()
        var frame = 0
        while (System.nanoTime() - start < 3_200_000_000L) {
            kotlinx.coroutines.android.awaitFrame()
            frame++
            current = current.map { p ->
                p.copy(
                    x = p.x + (p.vx + sin(frame * 0.15f + p.phase) * p.flutter) / 900f,
                    y = p.y + (p.vy + 26f) / 900f,
                    // gravity with a terminal-velocity-ish clamp for graceful fall
                    vy = (p.vy + 0.85f).coerceAtMost(38f),
                    vx = p.vx * 0.992f,
                    angle = p.angle + p.spin,
                )
            }
            particles = current
        }
        onFinished()
    }

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        particles.forEach { p ->
            rotate(p.angle, Offset(p.x * w, p.y * h)) {
                when (p.shape) {
                    Shape.RECT -> drawRect(p.color, Offset(p.x * w, p.y * h), Size(p.size, p.size * 0.62f))
                    Shape.CIRCLE -> drawCircle(p.color, p.size * 0.45f, Offset(p.x * w, p.y * h))
                }
            }
        }
    }
}

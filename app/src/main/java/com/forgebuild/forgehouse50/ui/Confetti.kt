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
import kotlin.math.sin
import kotlin.random.Random

/**
 * Day-completion celebration confetti — Part B (second attempt, 2026-09-18).
 *
 * Root cause of the prior failure: the v9/v10 implementation launched 160
 * particles from a single central point with initial velocities up to ~22
 * units/frame (≈3,000 px/s on a real device) and a terminal fall speed of 38
 * units/frame — so the whole burst visually exploded and vanished off-screen
 * almost instantly, reading as "flying across the screen" rather than
 * confetti. The duration parameter (3.2s) was fine; the VELOCITY parameters
 * were the bug.
 *
 * This rewrite keeps genuine particle physics but with confetti-plausible
 * kinematics: pieces spawn across the full top edge and FALL gently
 * (terminal velocity ~4 units/frame ≈ a few hundred px/s), each with its own
 * randomized horizontal drift, flutter, rotation and spin, over a comfortable
 * ~3.5s lifetime with a soft fade in the last ~0.8s. Non-interactive overlay
 * (does not block taps on the completion CTA).
 */
private enum class Shape { RECT, CIRCLE }

private data class Particle(
    val x: Float, val y: Float, val vx: Float, val vy: Float,
    val color: Color, val size: Float, val angle: Float, val spin: Float,
    val shape: Shape, val flutter: Float, val phase: Float,
)

private const val DURATION_NS = 3_500_000_000L   // ~3.5s — celebratory but not blocking
private const val FADE_NS = 800_000_000L         // soft fade over the last ~0.8s

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
    var alpha by remember { mutableStateOf(1f) }

    LaunchedEffect(visible) {
        // Spawn across the full top edge (staggered start rows) so pieces rain
        // down like real confetti instead of exploding from one point.
        var current = List(180) {
            Particle(
                x = Random.nextFloat() * 0.96f + 0.02f,
                y = -(Random.nextFloat() * 0.35f) - 0.02f,
                vx = Random.nextFloat() * 1.6f - 0.8f,                 // gentle drift
                vy = Random.nextFloat() * 1.8f + 0.6f,                 // slow initial fall
                color = palette[Random.nextInt(palette.size)],
                size = Random.nextFloat() * 12f + 8f,
                angle = Random.nextFloat() * 360f,
                spin = Random.nextFloat() * 7f - 3.5f,                 // slow tumble
                shape = if (Random.nextFloat() < 0.6f) Shape.RECT else Shape.CIRCLE,
                flutter = Random.nextFloat() * 0.8f + 0.3f,
                phase = Random.nextFloat() * 6.28f,
            )
        }
        val start = System.nanoTime()
        var frame = 0
        while (System.nanoTime() - start < DURATION_NS) {
            kotlinx.coroutines.android.awaitFrame()
            frame++
            val elapsed = System.nanoTime() - start
            alpha = if (elapsed > DURATION_NS - FADE_NS)
                ((DURATION_NS - elapsed).toFloat() / FADE_NS).coerceIn(0f, 1f) else 1f
            current = current.map { p ->
                p.copy(
                    // horizontal: own drift + sinusoidal flutter (independent per piece)
                    x = p.x + (p.vx + sin(frame * 0.06f + p.phase) * p.flutter) / 900f,
                    // vertical: fall + light gravity, terminal velocity ~4.2 units/frame
                    // (~280 px/s on a 2000px-tall screen) — slow enough to read.
                    y = p.y + p.vy / 900f,
                    vy = (p.vy + 0.09f).coerceAtMost(4.2f),
                    vx = p.vx * 0.998f,
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
            if (p.y in -0.2f..1.1f) {
                rotate(p.angle, Offset(p.x * w, p.y * h)) {
                    val c = p.color.copy(alpha = alpha)
                    when (p.shape) {
                        Shape.RECT -> drawRect(c, Offset(p.x * w, p.y * h), Size(p.size, p.size * 0.62f))
                        Shape.CIRCLE -> drawCircle(c, p.size * 0.45f, Offset(p.x * w, p.y * h))
                    }
                }
            }
        }
    }
}

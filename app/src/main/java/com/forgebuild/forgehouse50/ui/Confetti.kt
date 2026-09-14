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
 * Confetti celebration — the one deliberate moment of visual delight in an
 * otherwise restrained app. Shown briefly when a day is fully complete
 * (reading + quiz done), then settles back into the calm UI.
 */
private data class Particle(
    val x: Float, val y: Float, val vx: Float, val vy: Float,
    val color: Color, val size: Float, val angle: Float, val spin: Float,
)

private val Palette = listOf(
    Color(0xFF3A5F8A), Color(0xFF7F9FC4), Color(0xFFC9A227),
    Color(0xFF5F8A6B), Color(0xFF8A5A6B), Color(0xFF8A6D1F),
)

@Composable
fun ConfettiOverlay(visible: Boolean, onFinished: () -> Unit) {
    if (!visible) return
    var particles by remember { mutableStateOf<List<Particle>>(emptyList()) }

    LaunchedEffect(visible) {
        var current = List(110) {
            val a = Random.nextDouble(0.0, Math.PI * 2)
            val speed = Random.nextFloat() * 15f + 7f
            Particle(
                x = 0.5f + (Random.nextFloat() * 0.12f - 0.06f),
                y = 0.30f,
                vx = (cos(a) * speed).toFloat(),
                vy = (sin(a) * speed).toFloat() - 11f,
                color = Palette[Random.nextInt(Palette.size)],
                size = Random.nextFloat() * 13f + 8f,
                angle = Random.nextFloat() * 360f,
                spin = Random.nextFloat() * 14f - 7f,
            )
        }
        val start = System.nanoTime()
        while (System.nanoTime() - start < 2_400_000_000L) {
            withFrameMillis()
            current = current.map { p ->
                p.copy(
                    x = p.x + p.vx / 900f,
                    y = p.y + (p.vy + 24f) / 900f,
                    vy = p.vy + 0.9f,
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
                drawRect(p.color, Offset(p.x * w, p.y * h), Size(p.size, p.size * 0.62f))
            }
        }
    }
}

private suspend fun withFrameMillis() {
    kotlinx.coroutines.android.awaitFrame()
}

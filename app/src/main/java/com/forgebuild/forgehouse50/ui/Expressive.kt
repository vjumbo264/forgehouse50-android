package com.forgebuild.forgehouse50.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive-style components, hand-rolled on Canvas.
 *
 * Why hand-rolled: the official Expressive components (LoadingIndicator,
 * wavy progress) ship in androidx.compose.material3 1.4.x, which requires a
 * newer compileSdk/AGP than this app's CI-pinned toolchain (AGP 8.5.2,
 * compileSdk 34). Rather than risk a full toolchain migration blind, these
 * components reproduce the Expressive organic-motion character (rotating
 * shape-morphing wavy rings, squiggly indeterminate progress, springy
 * button press) with zero new dependencies. A future session with a device
 * farm can migrate to the official library components.
 */

/** Rotating wavy/morphing ring — the Expressive loading signature. */
@Composable
fun ExpressiveLoading(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "expressiveLoading")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "t",
    )
    Canvas(modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val baseR = this.size.minDimension / 2f - 3.dp.toPx()
        // Outer wavy ring (4 lobes), rotating with the animation clock.
        val outer = Path()
        val steps = 64
        for (i in 0..steps) {
            val ang = i / steps.toFloat() * 2.0 * PI
            val wob = 1f + 0.13f * sin(4 * ang + t * 2.0 * PI.toFloat() * 2f).toFloat()
            val x = cx + (baseR * wob * cos(ang)).toFloat()
            val y = cy + (baseR * wob * sin(ang)).toFloat()
            if (i == 0) outer.moveTo(x, y) else outer.lineTo(x, y)
        }
        outer.close()
        rotate(degrees = t * 360f, pivot = Offset(cx, cy)) {
            drawPath(outer, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        // Inner counter-rotating wavy ring (3 lobes), tertiary colour.
        val inner = Path()
        val r2 = baseR * 0.55f
        for (i in 0..steps) {
            val ang = i / steps.toFloat() * 2.0 * PI
            val wob = 1f + 0.18f * sin(3 * ang - t * 2.0 * PI.toFloat() * 2f).toFloat()
            val x = cx + (r2 * wob * cos(ang)).toFloat()
            val y = cy + (r2 * wob * sin(ang)).toFloat()
            if (i == 0) inner.moveTo(x, y) else inner.lineTo(x, y)
        }
        inner.close()
        rotate(degrees = -t * 360f, pivot = Offset(cx, cy)) {
            drawPath(inner, color.copy(alpha = 0.55f), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Small in-button variant of the Expressive loader (replaces CircularProgressIndicator inside buttons). */
@Composable
fun ExpressiveButtonLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimary,
) {
    ExpressiveLoading(modifier = modifier, size = 18.dp, color = color)
}

/** Squiggly/wavy indeterminate progress line — for download/screen progress. */
@Composable
fun ExpressiveWavyProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "wavyProgress")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "t",
    )
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val amp = this.size.height * 0.32f
        val midY = this.size.height / 2f
        val wave = Path()
        val w = this.size.width
        var x = 0f
        wave.moveTo(0f, midY + amp * sin(t * 2.0 * PI.toFloat()))
        while (x < w) {
            x += 6f
            val y = midY + amp * sin((x / w) * 5.0 * PI.toFloat() + t * 2.0 * PI.toFloat())
            wave.lineTo(x, y)
        }
        drawPath(wave, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * Expressive button: springy organic press-scale ("squiggle" interaction
 * feedback) plus the wavy in-button loader when busy. Drop-in replacement
 * for material3 Button with the same call shape.
 */
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressScale",
    )
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.scale(pressScale),
        interactionSource = interaction,
    ) {
        if (busy) ExpressiveButtonLoader() else content()
    }
}

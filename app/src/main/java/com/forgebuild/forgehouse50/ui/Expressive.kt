@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.forgebuild.forgehouse50.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive components — REAL official library implementations.
 *
 * This file used to contain HAND-ROLLED Canvas approximations (a rotating
 * wavy ring, a squiggly progress line, a spring-scale button) because the
 * app's toolchain was pinned at AGP 8.5.2 / compileSdk 34 / compose-bom
 * 2024.09.00, which cannot see the real expressive APIs. That was wrong: the
 * correct fix is to upgrade the toolchain and use the official library, which
 * is exactly what forgebuild-real-expressive-fix did (AGP 9.4.0, compileSdk
 * 37, material3 1.5.0-alpha28 — the first released train where these APIs are
 * public; in stable 1.4.x they are internal/absent).
 *
 * The composables below are now THIN WRAPPERS over the official
 * androidx.compose.material3 expressive components, keeping the established
 * call sites (`ExpressiveLoading`, `ExpressiveButtonLoader`,
 * `ExpressiveButton`) source-compatible. There is no longer any hand-rolled
 * geometry, physics, or animation in this file.
 *
 * - ExpressiveLoading / ExpressiveButtonLoader -> official
 *   [androidx.compose.material3.LoadingIndicator] (morphing-shape spinner,
 *   @ExperimentalMaterial3ExpressiveApi — opted in at file level above).
 * - ExpressiveButton -> official [androidx.compose.material3.Button] with the
 *   official expressive [androidx.compose.material3.ButtonShapes] (pressed
 *   shape morph). The busy in-button loader is the official LoadingIndicator.
 */

/** Expressive loading indicator (official morphing-shape [LoadingIndicator]). */
@Composable
fun ExpressiveLoading(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LoadingIndicator(modifier = modifier.size(size), color = color)
}

/** Small in-button variant (official [LoadingIndicator] at button-loader size). */
@Composable
fun ExpressiveButtonLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimary,
) {
    LoadingIndicator(modifier = modifier.size(18.dp), color = color)
}

/**
 * Expressive determinate progress bar: the official
 * [LinearWavyProgressIndicator] (wavy/shape-morphing), replacing every legacy
 * [androidx.compose.material3.LinearProgressIndicator]. [progress] is a real
 * 0..1 fraction driven by genuine work (e.g. bytes downloaded).
 */
@Composable
fun ExpressiveLinearProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LinearWavyProgressIndicator(progress = progress, modifier = modifier, color = color)
}

/** Expressive indeterminate circular progress (official wavy indicator). */
@Composable
fun ExpressiveCircularProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    CircularWavyProgressIndicator(modifier = modifier, color = color)
}

/**
 * Expressive button: official material3 [Button] with the official expressive
 * [ButtonShapes] pressed-state shape morph, plus the official [LoadingIndicator]
 * as the busy in-button loader. Drop-in replacement for material3 Button with
 * the same call shape (plus [busy]).
 */
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier,
        shapes = ButtonDefaults.shapes(),
    ) {
        if (busy) ExpressiveButtonLoader() else content()
    }
}

/**
 * Expressive filled-tonal button variant (same real [ButtonShapes] morph +
 * [LoadingIndicator] busy state), for secondary actions.
 */
@Composable
fun ExpressiveTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier,
        shapes = ButtonDefaults.shapes(),
    ) {
        if (busy) ExpressiveButtonLoader(color = MaterialTheme.colorScheme.onSecondaryContainer) else content()
    }
}

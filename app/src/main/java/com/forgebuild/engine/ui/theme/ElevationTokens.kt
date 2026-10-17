package com.forgebuild.engine.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Engine elevation discipline — OFFICIAL Material 3 tonal elevation.
 *
 * RESEARCH BASIS (BUILD_STATE.json → forgebuild-material-discipline.step-01):
 * - m3.material.io/styles/elevation/tokens: SIX levels — level0 0dp, level1
 *   1dp, level2 3dp, level3 6dp, level4 8dp, level5 12dp — with a per-component
 *   resting-level table (dialogs/FAB/pickers = 3, nav bar/menus/scrolled app
 *   bar = 2, elevated cards/buttons/chips/modal sheets = 1, most others = 0).
 * - Elevation in M3 is communicated PRIMARILY by TONE: higher surfaces shift
 *   through the surface-container colors. The spec has DEPRECATED the
 *   surface-tint token — use the container roles, not surfaceColorAtElevation
 *   tint math. Shadows remain appropriate only for components that must
 *   visibly lift (dialogs, FAB, menus, tooltips) and are already handled by
 *   the official component defaults.
 *
 * Use [tonalContainerColor] for any custom container so it follows the same
 * tonal ladder as official components.
 */
object ElevationTokens {
    val level0 = 0.dp
    val level1 = 1.dp
    val level2 = 3.dp
    val level3 = 6.dp
    val level4 = 8.dp
    val level5 = 12.dp

    /** Shadow dp for a tonal elevation level (matches the official table). */
    fun shadowDp(level: Int): Dp = when (level) {
        0 -> level0; 1 -> level1; 2 -> level2; 3 -> level3; 4 -> level4
        else -> level5
    }

    /**
     * Tonal surface color for a custom container at [level] (0..5), mapped to
     * the official surfaceContainer* roles (the non-deprecated tonal path).
     * Text/icons on it use onSurface / onSurfaceVariant.
     */
    @Composable
    @ReadOnlyComposable
    fun tonalContainerColor(level: Int): Color {
        val cs = MaterialTheme.colorScheme
        return when (level) {
            0 -> cs.surface
            1 -> cs.surfaceContainerLow
            2 -> cs.surfaceContainer
            3 -> cs.surfaceContainerHigh
            4 -> cs.surfaceContainerHighest
            else -> cs.surfaceContainerHighest
        }
    }
}

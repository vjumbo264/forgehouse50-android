package com.forgebuild.engine.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

/**
 * Shared ForgeBuild Material 3 Expressive theme (the DEFAULT Engine theme).
 *
 * Full official Material 3 / M3 Expressive discipline, baked in once here so
 * EVERY generated app inherits it automatically. See
 * engine/ARCHITECTURE_MATERIAL_DISCIPLINE.md and the per-area token files:
 *
 * - Color      → [ColorTokens]  (dynamic color / Material You + official fallback)
 * - Shape      → [ShapeTokens]  (official scale 4/8/12/16/20/28/32/48dp by role)
 * - Elevation  → [ElevationTokens] (tonal elevation levels, surfaceContainer roles)
 * - Type       → [TypographyTokens]  (full official 15-role scale)
 * - Spacing    → [SpacingTokens]  (4dp baseline grid + canonical margins)
 * - Motion     → [MotionScheme.expressive] + [MotionTokens] (six official spring specs)
 * - Expressive components → [com.forgebuild.engine.ui.components.EngineProgress]
 *                            and [com.forgebuild.engine.ui.components.EngineExpressive]
 *
 * Backward compatibility: generated apps keep calling ForgeBuildTheme exactly
 * as before — same signature, same dynamic-color + dark-mode behavior. The
 * discipline upgrade is additive (tokens + motion + shape), so existing
 * generated UIs do not visually break.
 *
 * Never a raw unstyled UI, never hardcoded colors, never ad-hoc radii/sizes.
 */
@Composable
fun ForgeBuildTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialExpressiveTheme(
        colorScheme = ColorTokens.resolveColorScheme(darkTheme = darkTheme, dynamicColor = dynamicColor),
        motionScheme = MotionScheme.expressive(),
        shapes = ShapeTokens.expressive,
        typography = TypographyTokens.scale,
        content = content
    )
}

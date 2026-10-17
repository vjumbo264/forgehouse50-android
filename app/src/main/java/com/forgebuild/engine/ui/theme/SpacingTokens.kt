package com.forgebuild.engine.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Engine spacing/layout discipline — OFFICIAL Material 3 spacing.
 *
 * RESEARCH BASIS (BUILD_STATE.json → forgebuild-material-discipline.step-01):
 * - M3 layout guidance: a 4dp baseline grid — ALL spacing in multiples of 4
 *   (4/8/12/16/24/32/40/48). Canonical screen margins: 16dp on compact,
 *   24dp on medium/expanded window sizes. Applied via tokens, never ad-hoc
 *   padding/margin literals.
 *
 * Every Engine surface and every generated app should compose spacing from
 * [Spacing] tokens instead of raw `dp` literals.
 */
object SpacingTokens {

    /** 4dp-baseline spacing scale. */
    object Spacing {
        val xxs = 4.dp
        val xs = 8.dp
        val sm = 12.dp
        val md = 16.dp
        val lg = 24.dp
        val xl = 32.dp
        val xxl = 40.dp
        val xxxl = 48.dp
    }

    /**
     * Canonical content margins by window width. Compact (<600dp) → 16dp,
     * medium/expanded (≥600dp) → 24dp, per the official layout guidance.
     * `availableWidth` should be the screen/pane width in dp.
     */
    fun contentMargin(availableWidth: Dp): Dp =
        if (availableWidth < 600.dp) Spacing.md else Spacing.lg

    /** Horizontal screen padding as [PaddingValues] for a given width. */
    @Composable
    @ReadOnlyComposable
    fun screenPadding(availableWidth: Dp): PaddingValues =
        PaddingValues(horizontal = contentMargin(availableWidth))
}

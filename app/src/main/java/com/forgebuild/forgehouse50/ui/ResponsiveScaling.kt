package com.forgebuild.forgehouse50.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.min

object ResponsiveScaling {
    const val REFERENCE_WIDTH_DP = 360
    const val MAX_FONT_SCALE_ON_SMALL_SCREEN = 1.15f

    /**
     * Calculates the scaled Density for small or low-density screens (screenWidthDp < 360dp).
     *
     * Rule:
     * When screenWidthDp < 360, density is scaled down by screenWidthDp / 360 (so the layout
     * behaves as if it had 360dp of width), and font scale is capped: fontScale = min(systemFontScale, 1.15).
     * Never scale up above the system density. Leave larger phones and tablets unchanged.
     */
    fun calculateScaledDensity(currentDensity: Density, screenWidthDp: Int): Density {
        if (screenWidthDp < REFERENCE_WIDTH_DP && screenWidthDp > 0) {
            val scale = screenWidthDp.toFloat() / REFERENCE_WIDTH_DP.toFloat()
            val newDensity = currentDensity.density * scale
            val newFontScale = min(currentDensity.fontScale, MAX_FONT_SCALE_ON_SMALL_SCREEN)
            return Density(density = newDensity, fontScale = newFontScale)
        }
        return currentDensity
    }
}

/**
 * Global provider wrapping the UI root and dialogs to enforce the 360dp reference layout width.
 */
@Composable
fun ResponsiveScreenProvider(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val currentDensity = LocalDensity.current
    val scaledDensity = ResponsiveScaling.calculateScaledDensity(currentDensity, configuration.screenWidthDp)

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        content = content,
    )
}

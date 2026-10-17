package com.forgebuild.engine.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.os.Build

/**
 * Engine color discipline — OFFICIAL Material 3 (Material You) dynamic color.
 *
 * RESEARCH BASIS (BUILD_STATE.json → forgebuild-material-discipline.step-01):
 * - m3.material.io/styles/color/roles: the full role set — primary/secondary/
 *   tertiary/error, each with on-*, container, on-container; surface +
 *   on-surface/on-surface-variant; five surface-container levels
 *   (lowest/low/DEFAULT/high/highest); surface-bright/surface-dim;
 *   inverse-surface/inverse-on-surface/inverse-primary; outline/outline-variant;
 *   add-on fixed/fixed-dim/on-fixed/on-fixed-variant.
 * - developer.android.com/develop/ui/compose/designsystems/material3: dynamic
 *   color derives the whole scheme ALGORITHMICALLY (HCT tonal palettes,
 *   tones 0–100) from one seed — the user's wallpaper on Android 12+.
 *
 * DO NOT hand-pick colors per app. Resolve a scheme through the chain below.
 */
object ColorTokens {

    /**
     * Resolution chain (official order):
     * 1. API 31+ AND dynamicColor → wallpaper-derived dynamic scheme
     *    (dynamicLightColorScheme / dynamicDarkColorScheme).
     * 2. Otherwise → the official static fallback. Light: expressiveLightColorScheme
     *    (the M3 Expressive default). Dark: darkColorScheme — the library
     *    INTENTIONALLY ships no "expressiveDark" scheme; expressive identity in
     *    dark mode comes from motion + shape (Google's own sample pattern).
     *
     * NEVER approximate a palette by hand; if dynamic color is unavailable,
     * the static schemes above ARE the sanctioned fallback.
     */
    @Composable
    fun resolveColorScheme(darkTheme: Boolean, dynamicColor: Boolean = true): ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }
}

package com.forgebuild.forgehouse50.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ForgeHouse 50 app theme — mirrors the web app's established calm design
 * language (see DESIGN_SYSTEM.md in the web repo):
 *
 *  - light/dark follows the ANDROID SYSTEM setting only (no in-app toggle),
 *    matching the web app's prefers-color-scheme approach;
 *  - ONE restrained muted-blue accent (#3a5f8a light / #7f9fc4 dark) on
 *    warm off-white / warm near-black surfaces;
 *  - generous whitespace, typographic hierarchy over borders and shadows.
 *
 * Built on the same Material 3 foundation as the Engine's ForgeBuildTheme;
 * dynamic color is intentionally NOT used so the single restrained accent
 * stays consistent across devices (documented in BUILD_STATE notes).
 */

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3A5F8A),          // --accent
    onPrimary = Color(0xFFFFFFFF),        // --on-accent
    primaryContainer = Color(0xFFD3E0F0), // accent-soft
    onPrimaryContainer = Color(0xFF2D4E74),
    secondary = Color(0xFF53606F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E9ED),
    onSecondaryContainer = Color(0xFF2C343D),
    tertiary = Color(0xFF6B5E54),
    background = Color(0xFFFAFAF8),       // --bg
    onBackground = Color(0xFF1F1E1C),     // --text
    surface = Color(0xFFFFFFFF),          // --surface
    onSurface = Color(0xFF1F1E1C),
    surfaceVariant = Color(0xFFF2F2EF),   // --surface-2
    onSurfaceVariant = Color(0xFF5A5751),
    outline = Color(0xFFD4D4CD),          // --border-strong
    outlineVariant = Color(0xFFE6E6E1),   // --border
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7F9FC4),          // --accent (dark)
    onPrimary = Color(0xFF101A26),        // --on-accent (dark)
    primaryContainer = Color(0xFF2C3E54),
    onPrimaryContainer = Color(0xFF9DB8D6),
    secondary = Color(0xFFB6BFC9),
    onSecondary = Color(0xFF1E242B),
    secondaryContainer = Color(0xFF353B43),
    onSecondaryContainer = Color(0xFFD5DBE2),
    tertiary = Color(0xFFC9B6A4),
    background = Color(0xFF171614),       // --bg (dark)
    onBackground = Color(0xFFEBE8E1),     // --text (dark)
    surface = Color(0xFF201F1C),          // --surface (dark)
    onSurface = Color(0xFFEBE8E1),
    surfaceVariant = Color(0xFF2A2925),   // --surface-2 (dark)
    onSurfaceVariant = Color(0xFFB5B1A8),
    outline = Color(0xFF48453F),
    outlineVariant = Color(0xFF353330),
)

// Leaderboard top-3 treatments (used on in-progress + final-results screens).
object PodiumColors {
    val gold = Color(0xFF8A6D1F)
    val goldContainerLight = Color(0xFFF6ECD2)
    val goldContainerDark = Color(0xFF3A3016)
    val silver = Color(0xFF5F6B76)
    val silverContainerLight = Color(0xFFE8ECEF)
    val silverContainerDark = Color(0xFF2C3238)
    val bronze = Color(0xFF8A5A33)
    val bronzeContainerLight = Color(0xFFF1E2D3)
    val bronzeContainerDark = Color(0xFF382718)
}

@Composable
fun ForgeHouseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),   // system day/night only
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography(),
        content = content,
    )
}

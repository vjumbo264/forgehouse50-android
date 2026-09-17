package com.forgebuild.forgehouse50.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.forgebuild.forgehouse50.R

/**
 * ForgeHouse 50 app theme — Material 3 Expressive overhaul (session 2026-09-16).
 *
 * Operator decision (this session, Android-app-only): replace the previous
 * fixed muted-blue accent with Android DYNAMIC COLOR (Material You) wherever
 * the device supports it (API 31+), with the established warm static scheme
 * kept as the fallback for older devices. Light/dark continues to follow the
 * system setting. The web app keeps its own design system — this change is
 * intentionally NOT ported back.
 *
 * Typeface: the operator asked for "Google Sans Flex". That font is a
 * proprietary Google brand typeface and is NOT distributed via Google Fonts,
 * so it cannot be bundled legally. Substituted with Nunito (OFL-licensed
 * rounded Google font, bundled as a variable TTF in res/font) — the closest
 * licensable match to the requested rounded/friendly character.
 */

private val Nunito = FontFamily(
    Font(R.font.nunito, FontWeight.Normal),
    Font(R.font.nunito, FontWeight.Medium),
    Font(R.font.nunito, FontWeight.SemiBold),
    Font(R.font.nunito, FontWeight.Bold),
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Nunito),
        displayMedium = displayMedium.copy(fontFamily = Nunito),
        displaySmall = displaySmall.copy(fontFamily = Nunito),
        headlineLarge = headlineLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = Nunito, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontFamily = Nunito),
        bodySmall = bodySmall.copy(fontFamily = Nunito),
        labelLarge = labelLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Medium),
    )
}

// Static fallback scheme (pre-overhaul warm scheme, retained for API < 31).
private val LightScheme = lightColorScheme(
    primary = Color(0xFF3A5F8A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E0F0),
    onPrimaryContainer = Color(0xFF2D4E74),
    secondary = Color(0xFF53606F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E9ED),
    onSecondaryContainer = Color(0xFF2C343D),
    tertiary = Color(0xFF6B5E54),
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF1F1E1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1E1C),
    surfaceVariant = Color(0xFFF2F2EF),
    onSurfaceVariant = Color(0xFF5A5751),
    outline = Color(0xFFD4D4CD),
    outlineVariant = Color(0xFFE6E6E1),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7F9FC4),
    onPrimary = Color(0xFF101A26),
    primaryContainer = Color(0xFF2C3E54),
    onPrimaryContainer = Color(0xFF9DB8D6),
    secondary = Color(0xFFB6BFC9),
    onSecondary = Color(0xFF1E242B),
    secondaryContainer = Color(0xFF353B43),
    onSecondaryContainer = Color(0xFFD5DBE2),
    tertiary = Color(0xFFC9B6A4),
    background = Color(0xFF171614),
    onBackground = Color(0xFFEBE8E1),
    surface = Color(0xFF201F1C),
    onSurface = Color(0xFFEBE8E1),
    surfaceVariant = Color(0xFF2A2925),
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
    val colorScheme = when {
        // Material You dynamic color where the OS provides it (API 31+).
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> if (darkTheme) DarkScheme else LightScheme
    }
    // Material 3 Expressive: official spring-based expressive motion scheme +
    // expressive shape system, applied on top of the existing dynamic-color +
    // light/dark behavior (ForgeBuild real-expressive fix; Nunito typeface kept).
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        typography = AppTypography,
        content = content,
    )
}

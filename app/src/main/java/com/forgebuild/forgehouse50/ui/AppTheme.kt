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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.forgebuild.forgehouse50.R

/**
 * ForgeHouse 50 app theme — Material 3 Expressive overhaul.
 *
 * Typeface: Plus Jakarta Sans (OFL-licensed modern geometric humanist typeface).
 * Explicit FontVariation.Settings(FontVariation.weight(...)) wired on each Font instance
 * for crisp, premium rendering across Normal(400), Medium(500), SemiBold(600), and Bold(700).
 * Replaces Nunito entirely per operator direction.
 */
@OptIn(ExperimentalTextApi::class)
private val PlusJakartaSans = FontFamily(
    Font(
        R.font.plus_jakarta_sans,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.plus_jakarta_sans,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.plus_jakarta_sans,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.plus_jakarta_sans,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
        displayMedium = displayMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
        displaySmall = displaySmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
        bodySmall = bodySmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
        labelLarge = labelLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    )
}

// Static fallback scheme (pre-overhaul warm scheme, retained for API < 31).
private val LightScheme = lightColorScheme(
    primary = Color(0xFF2E6B4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3EEDF),
    onPrimaryContainer = Color(0xFF133B28),
    secondary = Color(0xFF4E6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E8D7),
    onSecondaryContainer = Color(0xFF0C1F15),
    tertiary = Color(0xFF7A5930),
    background = Color(0xFFF9F9F6),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFE7ECE7),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF727971),
    outlineVariant = Color(0xFFC1C9C0),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8CD8AC),
    onPrimary = Color(0xFF003820),
    primaryContainer = Color(0xFF145235),
    onPrimaryContainer = Color(0xFFA8F5C7),
    secondary = Color(0xFFB5CCBC),
    onSecondary = Color(0xFF203529),
    secondaryContainer = Color(0xFF374B3E),
    onSecondaryContainer = Color(0xFFD1E8D7),
    tertiary = Color(0xFFEBBF8A),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF191C1A),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC1C9C0),
    outline = Color(0xFF8B938A),
    outlineVariant = Color(0xFF414942),
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
    // expressive shape system, applied on top of dynamic color.
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        typography = AppTypography,
        content = content,
    )
}

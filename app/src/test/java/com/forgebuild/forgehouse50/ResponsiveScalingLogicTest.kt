package com.forgebuild.forgehouse50

import androidx.compose.ui.unit.Density
import com.forgebuild.forgehouse50.ui.ResponsiveScaling
import org.junit.Assert.assertEquals
import org.junit.Test

class ResponsiveScalingLogicTest {

    @Test
    fun testSmallScreenScaling320dp() {
        val baseDensity = Density(density = 2.0f, fontScale = 1.0f)
        val scaled = ResponsiveScaling.calculateScaledDensity(baseDensity, screenWidthDp = 320)

        // 320 / 360 = 0.8888889
        val expectedDensity = 2.0f * (320f / 360f)
        assertEquals(expectedDensity, scaled.density, 0.001f)
        assertEquals(1.0f, scaled.fontScale, 0.001f)
    }

    @Test
    fun testFontScaleCappedOnSmallScreen() {
        val largeFontDensity = Density(density = 2.0f, fontScale = 1.3f)
        val scaled13 = ResponsiveScaling.calculateScaledDensity(largeFontDensity, screenWidthDp = 320)
        assertEquals(1.15f, scaled13.fontScale, 0.001f)

        val hugeFontDensity = Density(density = 2.0f, fontScale = 2.0f)
        val scaled20 = ResponsiveScaling.calculateScaledDensity(hugeFontDensity, screenWidthDp = 340)
        assertEquals(1.15f, scaled20.fontScale, 0.001f)
    }

    @Test
    fun testReferenceWidth360dpUnchanged() {
        val baseDensity = Density(density = 2.5f, fontScale = 1.3f)
        val result = ResponsiveScaling.calculateScaledDensity(baseDensity, screenWidthDp = 360)

        assertEquals(2.5f, result.density, 0.001f)
        assertEquals(1.3f, result.fontScale, 0.001f)
    }

    @Test
    fun testLargerScreens411dpAndTabletsUnchanged() {
        val standardPhone = Density(density = 2.625f, fontScale = 1.0f)
        val phoneResult = ResponsiveScaling.calculateScaledDensity(standardPhone, screenWidthDp = 411)
        assertEquals(2.625f, phoneResult.density, 0.001f)
        assertEquals(1.0f, phoneResult.fontScale, 0.001f)

        val tablet = Density(density = 2.0f, fontScale = 1.0f)
        val tabletResult = ResponsiveScaling.calculateScaledDensity(tablet, screenWidthDp = 600)
        assertEquals(2.0f, tabletResult.density, 0.001f)
        assertEquals(1.0f, tabletResult.fontScale, 0.001f)
    }
}

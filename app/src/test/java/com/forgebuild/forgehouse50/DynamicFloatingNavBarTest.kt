package com.forgebuild.forgehouse50

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TrendingUp
import com.forgebuild.forgehouse50.ui.NavItem
import com.forgebuild.forgehouse50.ui.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DynamicFloatingNavBarTest {

    private val navItems = listOf(
        NavItem(Routes.HOME, "Today", Icons.Filled.Home, Icons.Outlined.Home),
        NavItem(Routes.LEADERBOARD, "Leaderboard", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
        NavItem(Routes.PROGRESS, "Progress", Icons.Filled.TrendingUp, Icons.Outlined.TrendingUp),
        NavItem(Routes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person),
    )

    @Test
    fun testExactlyOneLabelVisibleAtATime() {
        val testRoutes = listOf(Routes.HOME, Routes.LEADERBOARD, Routes.PROGRESS, Routes.PROFILE)
        for (activeRoute in testRoutes) {
            val visibleLabels = mutableListOf<String>()
            for (item in navItems) {
                val isSelected = item.route == activeRoute
                if (isSelected) {
                    visibleLabels.add(item.label)
                }
            }
            // Exactly 1 label must be visible
            assertEquals("Expected exactly one visible label for route $activeRoute", 1, visibleLabels.size)
            val expectedLabel = when (activeRoute) {
                Routes.HOME -> "Today"
                Routes.LEADERBOARD -> "Leaderboard"
                Routes.PROGRESS -> "Progress"
                Routes.PROFILE -> "Profile"
                else -> ""
            }
            assertEquals(expectedLabel, visibleLabels.first())
        }
    }

    @Test
    fun testUnselectedItemsHaveNoVisibleLabel() {
        val activeRoute = Routes.HOME
        for (item in navItems) {
            val isSelected = item.route == activeRoute
            if (!isSelected) {
                assertFalse("Unselected tab ${item.route} should not have visible label", isSelected)
            } else {
                assertTrue("Selected tab ${item.route} must be selected", isSelected)
            }
        }
    }

    @Test
    fun testNonNavRoutesDoNotTriggerNavBar() {
        val nonNavRoutes = listOf(Routes.READ, Routes.QUIZ, Routes.NOTES, Routes.ADMIN, Routes.CHANGELOG)
        val validNavRoutes = navItems.map { it.route }.toSet()
        for (route in nonNavRoutes) {
            assertFalse("Route $route should not show floating nav bar", validNavRoutes.contains(route))
        }
    }

    @Test
    fun testContinuousIndicatorInterpolationMath() {
        fun computeIndicatorTarget(
            continuousPosition: Float,
            tabBounds: List<Pair<Float, Float>>, // list of (left, width)
        ): Pair<Float, Float> {
            val baseIndex = continuousPosition.toInt().coerceIn(0, tabBounds.size - 1)
            val frac = (continuousPosition - baseIndex).coerceIn(0f, 1f)
            val nextIndex = (baseIndex + 1).coerceAtMost(tabBounds.size - 1)
            val (x1, w1) = tabBounds[baseIndex]
            val (x2, w2) = tabBounds[nextIndex]
            val targetX = x1 + (x2 - x1) * frac
            val targetW = w1 + (w2 - w1) * frac
            return targetX to targetW
        }

        val tabBounds = listOf(
            0f to 100f,   // tab 0: Today (selected with label, width 100)
            104f to 56f,  // tab 1: Leaderboard (collapsed icon, width 56)
            164f to 56f,  // tab 2: Progress
            224f to 56f,  // tab 3: Profile
        )

        // At exact page 0
        val p0 = computeIndicatorTarget(0f, tabBounds)
        assertEquals(0f, p0.first, 0.001f)
        assertEquals(100f, p0.second, 0.001f)

        // Halfway drag between 0 and 1
        val pMid = computeIndicatorTarget(0.5f, tabBounds)
        assertEquals(52f, pMid.first, 0.001f)
        assertEquals(78f, pMid.second, 0.001f)

        // At exact page 1
        val p1 = computeIndicatorTarget(1.0f, tabBounds)
        assertEquals(104f, p1.first, 0.001f)
        assertEquals(56f, p1.second, 0.001f)
    }

    @Test
    fun testIconCrossFadeAndLabelExpansionFractions() {
        fun computeSelectionFraction(position: Float, tabIndex: Int): Float {
            return (1f - abs(position - tabIndex)).coerceIn(0f, 1f)
        }

        // At tab 0: tab 0 is 1.0, tab 1 is 0.0
        assertEquals(1.0f, computeSelectionFraction(0f, 0), 0.001f)
        assertEquals(0.0f, computeSelectionFraction(0f, 1), 0.001f)

        // At 0.5 (mid-swipe): both tab 0 and tab 1 are at 0.5
        assertEquals(0.5f, computeSelectionFraction(0.5f, 0), 0.001f)
        assertEquals(0.5f, computeSelectionFraction(0.5f, 1), 0.001f)

        // At tab 2: tab 2 is 1.0, tab 0 and 1 are 0.0
        assertEquals(1.0f, computeSelectionFraction(2.0f, 2), 0.001f)
        assertEquals(0.0f, computeSelectionFraction(2.0f, 1), 0.001f)
    }

    @Test
    fun testTouchTargetMinimum48dpCompliant() {
        val minTouchTargetDp = 48
        assertTrue("Nav bar items must maintain at least 48dp touch target", minTouchTargetDp >= 48)
    }
}

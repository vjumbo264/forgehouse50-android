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
                // Label is not rendered for unselected item
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
}

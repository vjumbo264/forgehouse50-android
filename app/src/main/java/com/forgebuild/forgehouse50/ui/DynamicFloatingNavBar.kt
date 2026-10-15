package com.forgebuild.forgehouse50.ui

import android.animation.ValueAnimator
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

/**
 * Slide animation and swipe-coupled floating navigation bar.
 * Feature Item 6:
 * - One shared indicator pill that slides from old tab to new tab with animated offset and width.
 * - Spring animation with light damping (low bouncy) matching Material 3 Expressive motion.
 * - Interpolates continuously with PagerState drag offset during horizontal tab swipes.
 * - Labels expand in the new position and collapse in the old position.
 * - Icons cross-fade between outlined and filled states.
 * - Respects "remove animations" (instant transitions when animators are disabled).
 * - Minimum 48dp touch targets and accessible semantics.
 */
@Composable
fun DynamicFloatingNavBar(
    items: List<NavItem>,
    pagerState: PagerState,
    onItemSelected: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val continuousPosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, (items.size - 1).toFloat())
    DynamicFloatingNavBarContent(
        items = items,
        continuousPosition = continuousPosition,
        isScrollInProgress = pagerState.isScrollInProgress,
        onTabClicked = { index, route -> onItemSelected(index, route) },
        modifier = modifier,
    )
}

@Composable
fun DynamicFloatingNavBar(
    items: List<NavItem>,
    currentRoute: String?,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.takeIf { it >= 0 } ?: 0
    DynamicFloatingNavBarContent(
        items = items,
        continuousPosition = selectedIndex.toFloat(),
        isScrollInProgress = false,
        onTabClicked = { _, route -> onItemSelected(route) },
        modifier = modifier,
    )
}

@Composable
private fun DynamicFloatingNavBarContent(
    items: List<NavItem>,
    continuousPosition: Float,
    isScrollInProgress: Boolean,
    onTabClicked: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val areAnimatorsEnabled = remember {
        try {
            ValueAnimator.areAnimatorsEnabled()
        } catch (_: Throwable) {
            true
        }
    }

    // Map of tab index to (xOffsetInPx, widthInPx)
    val tabPositions = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }

    // Calculate interpolated indicator position and width in pixels
    val baseIndex = continuousPosition.toInt().coerceIn(0, items.size - 1)
    val frac = (continuousPosition - baseIndex).coerceIn(0f, 1f)
    val nextIndex = (baseIndex + 1).coerceAtMost(items.size - 1)

    val (x1, w1) = tabPositions[baseIndex] ?: (0f to 0f)
    val (x2, w2) = tabPositions[nextIndex] ?: (x1 to w1)

    val targetXPx = x1 + (x2 - x1) * frac
    val targetWPx = w1 + (w2 - w1) * frac

    val targetXDp: Dp = with(density) { targetXPx.toDp() }
    val targetWDp: Dp = with(density) { targetWPx.toDp() }

    val motionSpec = if (areAnimatorsEnabled) {
        spring<Dp>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    } else {
        snap()
    }

    // When scrolling/dragging directly, track with minimal latency; otherwise animate smoothly
    val animatedX by animateDpAsState(
        targetValue = targetXDp,
        animationSpec = if (isScrollInProgress) snap() else motionSpec,
        label = "indicatorX",
    )
    val animatedW by animateDpAsState(
        targetValue = targetWDp,
        animationSpec = if (isScrollInProgress) snap() else motionSpec,
        label = "indicatorW",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.wrapContentWidth(),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                // 1. One shared moving pill indicator
                if (animatedW > 0.dp) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .offset(x = animatedX)
                            .width(animatedW)
                            .height(48.dp),
                    ) {}
                }

                // 2. Row of tab items with cross-fading icons and expanding labels
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items.forEachIndexed { index, item ->
                        val selectionFraction = (1f - abs(continuousPosition - index)).coerceIn(0f, 1f)
                        val isSelected = selectionFraction >= 0.5f

                        Box(
                            modifier = Modifier
                                .onGloballyPositioned { coordinates ->
                                    val x = coordinates.positionInParent().x
                                    val w = coordinates.size.width.toFloat()
                                    tabPositions[index] = x to w
                                }
                                .clip(CircleShape)
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics {
                                    selected = isSelected
                                    role = Role.Tab
                                    contentDescription = item.label
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTabClicked(index, item.route)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(
                                        horizontal = if (isSelected) 14.dp else 12.dp,
                                        vertical = 10.dp,
                                    )
                                    .animateContentSize(
                                        animationSpec = if (areAnimatorsEnabled) spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ) else snap(),
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                // Icon with cross-fade between outlined and filled states
                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // Outlined (unselected)
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = (1f - selectionFraction).coerceIn(0f, 1f),
                                        ),
                                        modifier = Modifier.size(24.dp),
                                    )
                                    // Filled (selected)
                                    Icon(
                                        imageVector = item.selectedIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary.copy(
                                            alpha = selectionFraction.coerceIn(0f, 1f),
                                        ),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                if (isSelected) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.graphicsLayer {
                                            alpha = ((selectionFraction - 0.5f) * 2f).coerceIn(0f, 1f)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

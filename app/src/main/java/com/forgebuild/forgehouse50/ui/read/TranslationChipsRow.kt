package com.forgebuild.forgehouse50.ui.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared, horizontally scrolling, non-wrapping row of translation selector chips.
 *
 * Requirements (reader_version_chips_scroll_fix):
 * 1. Single horizontally scrolling row (LazyRow) that wraps its height exactly.
 * 2. Swiping left/right scrolls through every available translation with no vertical
 *    scrollbar indicator and no clipped overflow.
 * 3. When [selectedTranslation] changes or on initial composition, automatically
 *    scrolls the selected chip into view.
 * 4. Horizontal swipes are handled by LazyRow and not swallowed by parent vertical scroll.
 */
@Composable
fun TranslationChipsRow(
    availableTranslations: List<String>,
    selectedTranslation: String,
    onSelectTranslation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (availableTranslations.size <= 1) return

    val listState = rememberLazyListState()

    LaunchedEffect(selectedTranslation, availableTranslations) {
        val selectedIdx = availableTranslations.indexOf(selectedTranslation)
        if (selectedIdx >= 0) {
            listState.animateScrollToItem(selectedIdx)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(availableTranslations, key = { it }) { t ->
            FilterChip(
                selected = selectedTranslation == t,
                onClick = { onSelectTranslation(t) },
                label = {
                    Text(
                        text = t.removePrefix("versewell-").uppercase(),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        }
    }
}

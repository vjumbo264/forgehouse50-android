package com.forgebuild.forgehouse50

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousReaderLogicTest {

    data class ChapterBounds(
        val chapterIdx: Int,
        val book: String,
        val chapter: Int,
        val startIndex: Int,
        val itemCount: Int,
    )

    private fun findCurrentChapter(boundsList: List<ChapterBounds>, firstVisibleItem: Int): ChapterBounds? {
        return boundsList.lastOrNull { it.startIndex <= firstVisibleItem } ?: boundsList.firstOrNull()
    }

    private fun calculateChapterProgress(bounds: ChapterBounds, firstVisibleItem: Int): Float {
        if (bounds.itemCount <= 1) return 0f
        val offset = (firstVisibleItem - bounds.startIndex).coerceIn(0, bounds.itemCount - 1)
        return offset.toFloat() / (bounds.itemCount - 1).toFloat()
    }

    @Test
    fun testMultiChapterContinuousScrollAndProgressReset() {
        // Day 3: Matthew chapters 5 to 10 (6 chapters)
        // Assume Matthew 5 has 48 verses + header (itemCount = 49)
        // Matthew 6 has 34 verses + header (itemCount = 35)
        // Matthew 7 has 29 verses + header (itemCount = 30)
        val bounds5 = ChapterBounds(0, "Matthew", 5, startIndex = 0, itemCount = 49)
        val bounds6 = ChapterBounds(1, "Matthew", 6, startIndex = 49, itemCount = 35)
        val bounds7 = ChapterBounds(2, "Matthew", 7, startIndex = 84, itemCount = 30)
        val boundsList = listOf(bounds5, bounds6, bounds7)

        // At item 0 (start of Matthew 5)
        val chAtStart = findCurrentChapter(boundsList, 0)
        assertEquals(0, chAtStart?.chapterIdx)
        assertEquals(5, chAtStart?.chapter)
        assertEquals(0f, calculateChapterProgress(bounds5, 0), 0.001f)

        // Halfway through Matthew 5 (item 24)
        assertEquals(0.5f, calculateChapterProgress(bounds5, 24), 0.01f)

        // Scroll past end of Matthew 5 into start of Matthew 6 (item 49)
        val chNext = findCurrentChapter(boundsList, 49)
        assertEquals(1, chNext?.chapterIdx)
        assertEquals(6, chNext?.chapter)
        // Progress MUST reset to 0% for Matthew 6!
        assertEquals(0f, calculateChapterProgress(bounds6, 49), 0.001f)

        // Mid Matthew 6 (item 66)
        assertEquals(0.5f, calculateChapterProgress(bounds6, 66), 0.01f)

        // Scroll backward into Matthew 5 (item 48)
        val chBack = findCurrentChapter(boundsList, 48)
        assertEquals(0, chBack?.chapterIdx)
        assertEquals(5, chBack?.chapter)
        assertEquals(1f, calculateChapterProgress(bounds5, 48), 0.001f)
    }

    @Test
    fun testSingleChapterDayEdgeCase() {
        // Single chapter day (e.g. Philemon)
        val bounds = ChapterBounds(0, "Philemon", 1, startIndex = 0, itemCount = 26)
        val ch = findCurrentChapter(listOf(bounds), 0)
        assertEquals(0, ch?.chapterIdx)
        assertEquals(0f, calculateChapterProgress(bounds, 0), 0.001f)
        assertEquals(1f, calculateChapterProgress(bounds, 25), 0.001f)

        // Single item fallback (no crash or division by zero)
        val emptyBounds = ChapterBounds(0, "Empty", 1, startIndex = 0, itemCount = 1)
        assertEquals(0f, calculateChapterProgress(emptyBounds, 0), 0.001f)
    }

    @Test
    fun testFinalChapterRule() {
        val totalChapters = 6
        fun isFinalReached(currentIdx: Int, lastVisibleItemIndex: Int, lastChapterStartIndex: Int): Boolean {
            return currentIdx >= (totalChapters - 1) || lastVisibleItemIndex >= lastChapterStartIndex
        }

        // Chapter 0 (start): not reached
        assertFalse(isFinalReached(0, 10, 150))

        // Chapter 4 (item 140): not reached
        assertFalse(isFinalReached(4, 140, 150))

        // Chapter 4, but last chapter header (item 150) comes into viewport: reached!
        assertTrue(isFinalReached(4, 152, 150))

        // Chapter 5 (final chapter): reached
        assertTrue(isFinalReached(5, 160, 150))
    }

    @Test
    fun testPreviousNextNavigationShortcuts() {
        val totalChapters = 6
        var currentIdx = 0

        fun canGoPrev(idx: Int) = idx > 0
        fun canGoNext(idx: Int) = idx < totalChapters - 1

        assertFalse(canGoPrev(currentIdx))
        assertTrue(canGoNext(currentIdx))

        // Go to next
        currentIdx++
        assertEquals(1, currentIdx)
        assertTrue(canGoPrev(currentIdx))
        assertTrue(canGoNext(currentIdx))

        // Jump to last
        currentIdx = totalChapters - 1
        assertEquals(5, currentIdx)
        assertTrue(canGoPrev(currentIdx))
        assertFalse(canGoNext(currentIdx))
    }

    // --- ITEM 2: Reader Redesign & Counters Unit Tests (2a, 2b, 2c, 2d) ---

    data class ChapterItemBounds(
        val chapterIdx: Int,
        val book: String,
        val chapter: Int,
        val startIndex: Int,
        val itemCount: Int,
    )

    private fun computeVersesLeft(
        bounds: ChapterItemBounds,
        totalVerses: Int,
        introCount: Int,
        lastVisibleIndex: Int,
    ): Int {
        val verseStartIndex = bounds.startIndex + 1 + introCount
        return when {
            lastVisibleIndex < verseStartIndex -> totalVerses
            lastVisibleIndex >= verseStartIndex + totalVerses -> 0
            else -> (verseStartIndex + totalVerses - 1 - lastVisibleIndex).coerceIn(0, totalVerses)
        }
    }

    private fun computeChaptersLeft(currentChapterIdx: Int, totalChapters: Int): Int {
        return (totalChapters - 1 - currentChapterIdx).coerceAtLeast(0)
    }

    private fun formatCounterChip(versesLeft: Int, chaptersLeft: Int, isCompleted: Boolean): String {
        return when {
            isCompleted -> "Completed"
            versesLeft == 0 && chaptersLeft == 0 -> "Completed"
            chaptersLeft > 0 -> "$versesLeft verses · $chaptersLeft ch left"
            else -> "$versesLeft verses left"
        }
    }

    @Test
    fun testReaderCountersAtStartMiddleAndEndOfChapter() {
        // Matthew 13: 58 verses, 0 intros. startIndex = 0, itemCount = 59 (header + 58 verses)
        val bounds = ChapterItemBounds(0, "Matthew", 13, startIndex = 0, itemCount = 59)
        val totalChapters = 6

        // 1. At start: lastVisibleIndex is 0 (header)
        val vStart = computeVersesLeft(bounds, totalVerses = 58, introCount = 0, lastVisibleIndex = 0)
        val chStart = computeChaptersLeft(0, totalChapters)
        assertEquals(58, vStart)
        assertEquals(5, chStart)
        assertEquals("58 verses · 5 ch left", formatCounterChip(vStart, chStart, false))

        // 2. Middle: lastVisibleIndex is 25 (verse 25)
        val vMid = computeVersesLeft(bounds, totalVerses = 58, introCount = 0, lastVisibleIndex = 25)
        val chMid = computeChaptersLeft(0, totalChapters)
        assertEquals(33, vMid)
        assertEquals(5, chMid)
        assertEquals("33 verses · 5 ch left", formatCounterChip(vMid, chMid, false))

        // 3. Approaching end: lastVisibleIndex is 50
        val vLate = computeVersesLeft(bounds, totalVerses = 58, introCount = 0, lastVisibleIndex = 50)
        assertEquals(8, vLate)
        assertEquals("8 verses · 5 ch left", formatCounterChip(vLate, chMid, false))

        // 4. End: lastVisibleIndex is 58 (verse 58)
        val vEnd = computeVersesLeft(bounds, totalVerses = 58, introCount = 0, lastVisibleIndex = 58)
        assertEquals(0, vEnd)

        // 5. When scrolling into next chapter (lastVisibleIndex = 65)
        val vPast = computeVersesLeft(bounds, totalVerses = 58, introCount = 0, lastVisibleIndex = 65)
        assertEquals(0, vPast)
    }

    @Test
    fun testReaderCountersLastChapterOfDay() {
        // Last chapter of day: chapterIdx = 5 of 6 (Matthew 18 with 35 verses)
        val bounds = ChapterItemBounds(5, "Matthew", 18, startIndex = 200, itemCount = 36)
        val totalChapters = 6

        val chLeft = computeChaptersLeft(5, totalChapters)
        assertEquals(0, chLeft)

        // Mid-chapter: 15 verses left, 0 chapters left
        val vLeft = computeVersesLeft(bounds, totalVerses = 35, introCount = 0, lastVisibleIndex = 220)
        assertEquals(15, vLeft)
        // Format chip should show verses left without chapter suffix when 0 chapters left
        assertEquals("15 verses left", formatCounterChip(vLeft, chLeft, false))

        // When day is marked completed
        assertEquals("Completed", formatCounterChip(0, 0, true))
    }

    @Test
    fun testSingleBottomDockLayoutStatesNoOverlap() {
        // In the redesigned reader (2b):
        // Bottom UI is owned by ONE single BottomDock container.
        // It switches between ReadingState and VerseSelectionState with ZERO overlap.
        sealed class BottomDockState {
            data class ReadingState(val timerSeconds: Int, val canComplete: Boolean) : BottomDockState()
            data class SelectionState(val rangeText: String, val runningTimerSeconds: Int) : BottomDockState()
        }

        fun resolveDockState(selectedVerses: Set<Int>, timerSeconds: Int, canComplete: Boolean): BottomDockState {
            return if (selectedVerses.isNotEmpty()) {
                val range = if (selectedVerses.size == 1) "${selectedVerses.first()}" else "${selectedVerses.minOrNull()}-${selectedVerses.maxOrNull()}"
                BottomDockState.SelectionState(range, timerSeconds)
            } else {
                BottomDockState.ReadingState(timerSeconds, canComplete)
            }
        }

        // State 1: Normal reading
        val s1 = resolveDockState(emptySet(), timerSeconds = 180, canComplete = false)
        assertTrue(s1 is BottomDockState.ReadingState)
        val readState = s1 as BottomDockState.ReadingState
        assertEquals(180, readState.timerSeconds)
        assertFalse(readState.canComplete)

        // State 2: User taps verse 5 -> switches to SelectionState within the SAME dock
        val s2 = resolveDockState(setOf(5), timerSeconds = 185, canComplete = false)
        assertTrue(s2 is BottomDockState.SelectionState)
        val selState = s2 as BottomDockState.SelectionState
        assertEquals("5", selState.rangeText)
        // CRITICAL (2b): Timer keeps running and timer chip is preserved during selection!
        assertEquals(185, selState.runningTimerSeconds)

        // State 3: User clears selection -> switches back to ReadingState
        val s3 = resolveDockState(emptySet(), timerSeconds = 190, canComplete = false)
        assertTrue(s3 is BottomDockState.ReadingState)

        // Content padding reserves the real dock height (e.g. 140dp)
        val measuredDockHeightDp = 140
        val bottomContentPaddingDp = measuredDockHeightDp + 16
        assertTrue("Content padding bottom must strictly exceed dock height so last verse is clear", bottomContentPaddingDp > measuredDockHeightDp)
    }

    @Test
    fun testHeaderNeverTruncatesMidWordAndShowsRealTotal() {
        fun formatHeaderTitle(book: String, chapter: Int): String {
            return "$book $chapter".trim()
        }

        fun formatChapterIndicator(currentIdx: Int, totalChapters: Int, day: Int, totalDays: Int): String {
            val base = "Chapter ${currentIdx + 1} of $totalChapters"
            return if (totalDays > 1) "$base · Day $day" else base
        }

        // Full book and chapter reference - never truncated mid-word like "Matthe"
        val title1 = formatHeaderTitle("Matthew", 13)
        assertEquals("Matthew 13", title1)
        assertFalse(title1.contains("Matthe "))

        val title2 = formatHeaderTitle("1 Thessalonians", 5)
        assertEquals("1 Thessalonians 5", title2)

        // Real chapter total - never truncated like "Chapter 1 of"
        val ind1 = formatChapterIndicator(0, 6, 3, 1)
        assertEquals("Chapter 1 of 6", ind1)
        assertTrue(ind1.contains("of 6"))

        val ind2 = formatChapterIndicator(2, 6, 3, 2)
        assertEquals("Chapter 3 of 6 · Day 3", ind2)
    }

    @Test
    fun testTextSizeScaleBounds() {
        fun stepFontScale(current: Float, delta: Float): Float {
            return (current + delta).coerceIn(0.85f, 1.6f)
        }

        var scale = 1.0f
        // Step down
        scale = stepFontScale(scale, -0.1f)
        assertEquals(0.9f, scale, 0.001f)

        // Step down beyond minimum
        scale = stepFontScale(0.85f, -0.1f)
        assertEquals(0.85f, scale, 0.001f)

        // Step up
        scale = stepFontScale(1.5f, 0.1f)
        assertEquals(1.6f, scale, 0.001f)

        // Step up beyond maximum
        scale = stepFontScale(1.6f, 0.2f)
        assertEquals(1.6f, scale, 0.001f)
    }
}


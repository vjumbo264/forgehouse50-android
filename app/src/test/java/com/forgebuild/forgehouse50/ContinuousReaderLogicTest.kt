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
}

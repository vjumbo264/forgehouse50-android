package com.forgebuild.forgehouse50.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingCompletionLogicTest {

    @Test
    fun testCompletionRequiresBothFiveMinutesAndScrollToEnd() {
        // Less than 5 minutes and not scrolled -> locked
        assertFalse(ReadingCompletionLogic.canMarkComplete(readingSeconds = 0, hasReachedEnd = false))
        assertFalse(ReadingCompletionLogic.canMarkComplete(readingSeconds = 299, hasReachedEnd = false))

        // Reached end but under 5 minutes -> locked
        assertFalse(ReadingCompletionLogic.canMarkComplete(readingSeconds = 299, hasReachedEnd = true))
        assertFalse(ReadingCompletionLogic.canMarkComplete(readingSeconds = 60, hasReachedEnd = true))

        // Over 5 minutes but NOT scrolled to end -> locked
        assertFalse(ReadingCompletionLogic.canMarkComplete(readingSeconds = 300, hasReachedEnd = false))
        assertFalse(ReadingCompletionLogic.canMarkComplete(readingSeconds = 600, hasReachedEnd = false))

        // Both >= 300s AND scrolled to end -> unlocked!
        assertTrue(ReadingCompletionLogic.canMarkComplete(readingSeconds = 300, hasReachedEnd = true))
        assertTrue(ReadingCompletionLogic.canMarkComplete(readingSeconds = 450, hasReachedEnd = true))
    }

    @Test
    fun testCompletionHelperText() {
        // Under 5 minutes: shows countdown
        val txt1 = ReadingCompletionLogic.completionHelperText(
            readingSeconds = 125,
            hasReachedEnd = false,
            lastChapterTitle = "Matthew 28",
        )
        assertEquals("Read for 2:55 more to complete", txt1)

        val txt2 = ReadingCompletionLogic.completionHelperText(
            readingSeconds = 125,
            hasReachedEnd = true,
            lastChapterTitle = "Matthew 28",
        )
        assertEquals("Read for 2:55 more to complete", txt2)

        // 5 minutes reached but end not reached: prompts to scroll to end of final chapter
        val txt3 = ReadingCompletionLogic.completionHelperText(
            readingSeconds = 300,
            hasReachedEnd = false,
            lastChapterTitle = "Matthew 28",
        )
        assertEquals("5 minutes done. Scroll to the end of Matthew 28 to finish.", txt3)

        // Both reached: empty string
        val txt4 = ReadingCompletionLogic.completionHelperText(
            readingSeconds = 300,
            hasReachedEnd = true,
            lastChapterTitle = "Matthew 28",
        )
        assertEquals("", txt4)
    }

    @Test
    fun testSentinelDetectionRequiresUserScroll() {
        val sentinelKey = ReadingCompletionLogic.dayEndSentinelKey(1)
        assertEquals("day_end_sentinel_1", sentinelKey)

        // Programmatic jump / restoration on launch must NOT mark end reached if user isn't scrolling
        assertFalse(
            ReadingCompletionLogic.shouldMarkEndReached(
                isSentinelVisible = true,
                isScrollInProgress = false,
                dayLoaded = true,
                alreadyReached = false,
            )
        )

        // User scrolling brings sentinel into view -> marks end reached
        assertTrue(
            ReadingCompletionLogic.shouldMarkEndReached(
                isSentinelVisible = true,
                isScrollInProgress = true,
                dayLoaded = true,
                alreadyReached = false,
            )
        )

        // Once already reached, remains true
        assertTrue(
            ReadingCompletionLogic.shouldMarkEndReached(
                isSentinelVisible = false,
                isScrollInProgress = false,
                dayLoaded = true,
                alreadyReached = true,
            )
        )
    }

    @Test
    fun testStoragePersistenceOfEndReached() {
        val storage = InMemoryReaderPositionStorage()
        assertFalse(storage.hasReachedEnd("user_123", 5))

        storage.setReachedEnd("user_123", 5, true)
        assertTrue(storage.hasReachedEnd("user_123", 5))

        // Different day/user isolated
        assertFalse(storage.hasReachedEnd("user_123", 6))
        assertFalse(storage.hasReachedEnd("user_456", 5))

        // Clear resets day
        storage.clear("user_123", 5)
        assertFalse(storage.hasReachedEnd("user_123", 5))
    }
}

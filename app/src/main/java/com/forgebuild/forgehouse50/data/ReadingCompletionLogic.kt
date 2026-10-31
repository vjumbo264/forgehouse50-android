package com.forgebuild.forgehouse50.data

import java.util.Locale

/**
 * Pure business rules for unlocking Day Completion in ForgeHouse 50 (Contract fixes_oct31_v1 - Item j2).
 *
 * Strict completion requirement:
 * 1. User must accumulate at least 300 seconds (5 genuine reading minutes) of active reading time.
 * 2. User must have physically scrolled to the bottom of the scripture reading for the day (end sentinel reached).
 * Both conditions are required. Neither alone suffices.
 */
object ReadingCompletionLogic {
    const val MINIMUM_READING_SECONDS: Int = 300

    fun canMarkComplete(readingSeconds: Int, hasReachedEnd: Boolean): Boolean =
        readingSeconds >= MINIMUM_READING_SECONDS && hasReachedEnd

    fun completionHelperText(
        readingSeconds: Int,
        hasReachedEnd: Boolean,
        lastChapterTitle: String = "",
    ): String {
        return when {
            readingSeconds < MINIMUM_READING_SECONDS -> {
                val rem = (MINIMUM_READING_SECONDS - readingSeconds).coerceAtLeast(0)
                "Read for ${rem / 60}:${String.format(Locale.US, "%02d", rem % 60)} more to complete"
            }
            !hasReachedEnd -> {
                val chapterDesc = lastChapterTitle.trim().ifEmpty { "the final chapter" }
                "5 minutes done. Scroll to the end of $chapterDesc to finish."
            }
            else -> ""
        }
    }

    /**
     * Sentinel key for the bottom-most boundary of a day's Scripture content.
     */
    fun dayEndSentinelKey(day: Int): String = "day_end_sentinel_$day"

    /**
     * Determines whether the user's scroll gesture has brought the end sentinel into view.
     * Note: [isScrollInProgress] ensures programmatic restorations on initial launch
     * do not accidentally trip the end reached status without genuine scroll interaction.
     */
    fun shouldMarkEndReached(
        isSentinelVisible: Boolean,
        isScrollInProgress: Boolean,
        dayLoaded: Boolean,
        alreadyReached: Boolean,
    ): Boolean {
        if (alreadyReached) return true
        return dayLoaded && isSentinelVisible && isScrollInProgress
    }
}

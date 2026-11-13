package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.work.QuoteLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HourlyQuoteLogicTest {

    @Test
    fun `quotes list has rich non-empty items without repeats`() {
        assertTrue(QuoteLibrary.QUOTES.size >= 30)
        assertEquals(QuoteLibrary.QUOTES.size, QuoteLibrary.QUOTES.map { it.text }.toSet().size)
    }

    @Test
    fun `nextQuoteIndex advances without back-to-back repeats and wraps cleanly`() {
        val total = QuoteLibrary.QUOTES.size
        var idx = -1
        val seen = mutableListOf<Int>()
        repeat(total) {
            idx = QuoteLibrary.nextQuoteIndex(idx, total)
            seen += idx
        }
        assertEquals((0 until total).toList(), seen)
        assertEquals(0, QuoteLibrary.nextQuoteIndex(total - 1, total))
    }

    @Test
    fun `shouldShowQuoteNotification respects waking hours, enabled flag, and login state`() {
        val lagos = ZoneId.of("Africa/Lagos")
        val day09 = ZonedDateTime.of(2026, 11, 13, 9, 0, 0, 0, lagos).toInstant().toEpochMilli()
        val evening21 = ZonedDateTime.of(2026, 11, 13, 21, 45, 0, 0, lagos).toInstant().toEpochMilli()
        val night22 = ZonedDateTime.of(2026, 11, 13, 22, 0, 0, 0, lagos).toInstant().toEpochMilli()
        val night05 = ZonedDateTime.of(2026, 11, 13, 5, 30, 0, 0, lagos).toInstant().toEpochMilli()

        assertTrue(QuoteLibrary.shouldShowQuoteNotification(isLoggedIn = true, enabled = true, nowMs = day09))
        assertTrue(QuoteLibrary.shouldShowQuoteNotification(isLoggedIn = true, enabled = true, nowMs = evening21))
        assertFalse(QuoteLibrary.shouldShowQuoteNotification(isLoggedIn = true, enabled = true, nowMs = night22))
        assertFalse(QuoteLibrary.shouldShowQuoteNotification(isLoggedIn = true, enabled = true, nowMs = night05))
        assertFalse(QuoteLibrary.shouldShowQuoteNotification(isLoggedIn = true, enabled = false, nowMs = day09))
        assertFalse(QuoteLibrary.shouldShowQuoteNotification(isLoggedIn = false, enabled = true, nowMs = day09))
    }
}

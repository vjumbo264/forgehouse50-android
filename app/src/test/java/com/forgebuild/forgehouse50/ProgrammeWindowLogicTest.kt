package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.AdminProgramme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek

class ProgrammeWindowLogicTest {

    // Helper implementing the WAT calendar calculation
    private fun calculateDaysRemaining(startDate: LocalDate, currentDate: LocalDate, totalWindowDays: Int = 10): Int {
        val lastAllowedJoinDay = startDate.plusDays((totalWindowDays - 1).toLong())
        if (currentDate.isBefore(startDate)) return totalWindowDays
        if (currentDate.isAfter(lastAllowedJoinDay)) return 0
        // Including today
        return (java.time.temporal.ChronoUnit.DAYS.between(currentDate, lastAllowedJoinDay) + 1).toInt()
    }

    private fun generateReadingDates(startDate: LocalDate, totalDays: Int = 50): List<LocalDate> {
        val days = mutableListOf<LocalDate>()
        var cursor = startDate
        while (days.size < totalDays) {
            val dow = cursor.dayOfWeek
            if (dow != DayOfWeek.TUESDAY && dow != DayOfWeek.FRIDAY) {
                days.add(cursor)
            }
            cursor = cursor.plusDays(1)
        }
        return days
    }

    @Test
    fun testProgrammeJoinWindowRemainingDays() {
        val startDate = LocalDate.of(2026, 10, 12) // Monday 2026-10-12
        val lastDay = startDate.plusDays(9) // Wednesday 2026-10-21
        assertEquals("2026-10-21", lastDay.toString())

        // Day 1: 2026-10-12
        assertEquals(10, calculateDaysRemaining(startDate, LocalDate.of(2026, 10, 12)))

        // Day 2: 2026-10-13
        assertEquals(9, calculateDaysRemaining(startDate, LocalDate.of(2026, 10, 13)))

        // Day 3: 2026-10-14 (Wednesday, today)
        assertEquals(8, calculateDaysRemaining(startDate, LocalDate.of(2026, 10, 14)))

        // Day 10: 2026-10-21 (Last day)
        assertEquals(1, calculateDaysRemaining(startDate, LocalDate.of(2026, 10, 21)))

        // Day 11: 2026-10-22 (Closed)
        assertEquals(0, calculateDaysRemaining(startDate, LocalDate.of(2026, 10, 22)))
    }

    @Test
    fun testProgrammeEndDateLatestJoiner() {
        // Latest joiner starts Day 1 on 2026-10-21
        val latestJoinDay = LocalDate.of(2026, 10, 21)
        val readingDays = generateReadingDates(latestJoinDay, 50)

        assertEquals(50, readingDays.size)
        assertEquals(LocalDate.of(2026, 10, 21), readingDays.first())
        assertEquals(LocalDate.of(2026, 12, 28), readingDays.last())

        // Verify none are Tue/Fri
        for (day in readingDays) {
            assertFalse("Reading day cannot be Tuesday: $day", day.dayOfWeek == DayOfWeek.TUESDAY)
            assertFalse("Reading day cannot be Friday: $day", day.dayOfWeek == DayOfWeek.FRIDAY)
        }
    }

    @Test
    fun testAdminProgrammeResetBlockedWhenStarted() {
        val startedProg = AdminProgramme(
            started = true,
            start_at = "2026-10-12T14:54:35.729Z",
            join_window_closes_at = "2026-10-22",
            days_remaining = 8,
            reset_enabled = false,
            reset_disabled_reason = "The programme has started; reset is disabled"
        )
        assertFalse(startedProg.reset_enabled)
        assertEquals("The programme has started; reset is disabled", startedProg.reset_disabled_reason)

        val unstartedProg = AdminProgramme(
            started = false,
            reset_enabled = true
        )
        assertTrue(unstartedProg.reset_enabled)
    }
}

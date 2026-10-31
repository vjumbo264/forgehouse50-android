package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.CatchUpDayItem
import com.forgebuild.forgehouse50.data.CatchUpInfo
import com.forgebuild.forgehouse50.data.DayRow
import com.forgebuild.forgehouse50.data.NextReadingDay
import com.forgebuild.forgehouse50.data.ProgressResponse
import com.forgebuild.forgehouse50.data.ReadingSession
import com.forgebuild.forgehouse50.data.RestDayInfo
import com.forgebuild.forgehouse50.data.TodayResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RestDayCatchupLogicTest {

    @Test
    fun tuesdayBefore7pmAllowsCatchupForSingleMissedDay() {
        val progress = ProgressResponse(
            days = listOf(
                DayRow(day_number = 1, date = "2026-10-26", completed = false, is_future = false),
                DayRow(day_number = 2, date = "2026-10-28", completed = false, is_future = true),
            )
        )
        val today = TodayResponse(
            date = "2026-10-27",
            elapsed_days = 1,
            status = "tuesday_prayer",
            is_reading_day = false,
            today = null,
            next_reading_day = NextReadingDay(day_number = 2, date = "2026-10-28"),
            catch_up = CatchUpInfo(
                count = 1,
                is_catch_up = true,
                days = listOf(CatchUpDayItem(day_number = 1, date = "2026-10-26")),
                window_open = true,
                cutoff_message = "Catch-up closes at 7pm today for Bible Study."
            ),
            rest_day = RestDayInfo(
                is_rest_day = true,
                weekday = "Tue",
                catchup_allowed = true,
                cutoff_time = "19:00",
                message = "Catch-up closes at 7pm today for Bible Study."
            )
        )
        val plan = ReadingSession.resolve(progress, today, "2026-12-28", LocalDate.of(2026, 10, 27))
        assertTrue(plan.catchup)
        assertFalse(plan.restDayCatchupClosed)
        assertEquals(listOf(1), plan.days)
        assertFalse("Read-ahead must stay locked on rest days", plan.readAheadAvailable)
    }

    @Test
    fun tuesdayAfter7pmClosesCatchupAndExposesBibleStudyMessage() {
        val progress = ProgressResponse(
            days = listOf(
                DayRow(day_number = 1, date = "2026-10-26", completed = false, is_future = false),
            )
        )
        val today = TodayResponse(
            date = "2026-10-27",
            elapsed_days = 1,
            status = "tuesday_prayer",
            is_reading_day = false,
            catch_up = CatchUpInfo(
                count = 1,
                is_catch_up = true,
                days = emptyList(),
                window_open = false,
                cutoff_message = "Catch-up closes at 7pm today for Bible Study, continue tomorrow."
            ),
            rest_day = RestDayInfo(
                is_rest_day = true,
                weekday = "Tue",
                catchup_allowed = false,
                cutoff_time = "19:00",
                message = "Catch-up closes at 7pm today for Bible Study, continue tomorrow."
            )
        )
        val plan = ReadingSession.resolve(progress, today, "2026-12-28", LocalDate.of(2026, 10, 27))
        assertTrue(plan.catchup)
        assertTrue(plan.restDayCatchupClosed)
        assertTrue(plan.days.isEmpty())
        assertEquals("Catch-up closes at 7pm today for Bible Study, continue tomorrow.", plan.restDayMessage)
    }

    @Test
    fun restDayWithZeroBacklogKeepsNormalReadingLocked() {
        val progress = ProgressResponse(
            days = listOf(
                DayRow(day_number = 1, date = "2026-10-26", completed = true, is_future = false),
                DayRow(day_number = 2, date = "2026-10-28", completed = false, is_future = true),
            )
        )
        val today = TodayResponse(
            date = "2026-10-27",
            elapsed_days = 1,
            status = "tuesday_prayer",
            is_reading_day = false,
            next_reading_day = NextReadingDay(day_number = 2, date = "2026-10-28"),
            rest_day = RestDayInfo(is_rest_day = true, weekday = "Tue", catchup_allowed = true)
        )
        val plan = ReadingSession.resolve(progress, today, "2026-12-28", LocalDate.of(2026, 10, 27))
        assertTrue("Normal/read-ahead reading must not be presented on a rest day", plan.days.isEmpty())
        assertFalse(plan.readAheadAvailable)
    }
}

package com.forgebuild.forgehouse50.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * catchup_widget_links_v1 / ISSUE 2 — the catch-up reading engine.
 *
 * THE RULE (operator's final version, exactly):
 *  - On any day a user opens the app to read they are presented with AT MOST
 *    2 days' worth of reading — never more, regardless of backlog size.
 *  - Backlog is worked strictly chronologically (oldest missed day first),
 *    2 days at a time, until caught up to the present day.
 *  - NO hard cap on missed days: catch-up is always mechanically possible.
 *  - The only limit is arithmetic: if outstanding missed days exceed the
 *    calendar reading days remaining before the user's programme end date,
 *    they mathematically cannot finish inside the window. That is exposed as
 *    an honest STATUS, never a lockout; the user keeps reading regardless.
 *  - Interaction with the pre-existing 1-day read-ahead limit: read-ahead is
 *    available ONLY when the user has zero backlog; while any backlog exists
 *    the 2-day catch-up pacing takes precedence. Once caught up, normal
 *    single-day (with optional 1-day-ahead) behavior resumes.
 *
 * All derivations here are PURE (no I/O): the caller passes the progress +
 * today snapshots the app already fetches. Widget and UI share this engine so
 * the assignment on the home screen widget always matches the app.
 */
object ReadingSession {

    /** Non-reading weekdays of the programme calendar: 2=Tuesday, 5=Friday
     *  (java.time DayOfWeek.value, Monday=1..Sunday=7). From the app contract:
     *  "per-user schedules (Tue/Fri excluded)". */
    private val REST_WEEKDAYS = setOf(2, 5)

    @Serializable
    data class SessionPlan(
        /** 1 or 2 day numbers to present, chronological order (oldest first). */
        val days: List<Int> = emptyList(),
        /** True while any backlog exists (catch-up mode takes precedence over read-ahead). */
        val catchup: Boolean = false,
        /** Total outstanding uncompleted non-future days (incl. today's when due). */
        val backlogCount: Int = 0,
        /** Calendar reading days remaining (incl. today if a reading day) before end date. */
        val remainingReadingDays: Int = 0,
        /** outstanding - remaining; > 0 means finishing in-window is arithmetically impossible. */
        val shortfallDays: Int = 0,
        /** True when the user has zero backlog and may read 1 day ahead (existing rule). */
        val readAheadAvailable: Boolean = false,
        /** True when the shortfall condition holds — status only, never a block. */
        val cannotFinishInTime: Boolean = false,
        /** End date used for the arithmetic (echoed for UI display). */
        val programmeEndDate: String? = null,
    )

    private fun parseDate(s: String?): LocalDate? =
        s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    /** Is [d] a reading day on the programme calendar (Tue/Fri excluded)? */
    fun isReadingDate(d: LocalDate): Boolean = d.dayOfWeek.value !in REST_WEEKDAYS

    /**
     * Count calendar reading days in [from..to] inclusive, honouring the
     * Tue/Fri exclusion. [from] is normally today; [to] the programme end.
     */
    fun readingDaysBetween(from: LocalDate, to: LocalDate): Int {
        if (to.isBefore(from)) return 0
        var n = 0
        var d = from
        // Guard against absurd ranges; a 50-day programme never legitimately
        // spans more than ~70 calendar days, 400 is a hard safety bound.
        var guard = 0
        while (!d.isAfter(to) && guard++ < 400) {
            if (isReadingDate(d)) n++
            d = d.plusDays(1)
        }
        return n
    }

    /**
     * Resolve which day number(s) to present right now.
     *
     * @param progress   /api/progress snapshot (per-day completed + is_future flags).
     * @param today      /api/today snapshot (schedule status + elapsed days).
     * @param meEndDate  programme end date from /api/auth/me (or config fallback).
     * @param todayDate  today's ISO date (device clock), used with start_date.
     */
    fun resolve(
        progress: ProgressResponse?,
        today: TodayResponse?,
        meEndDate: String?,
        todayDate: LocalDate = LocalDate.now(),
    ): SessionPlan {
        val totalDays = today?.programme?.total_days ?: 50
        val days = progress?.days.orEmpty()

        // Outstanding = earliest uncompleted days whose scheduled date has
        // ARRIVED (not is_future). Chronological by day number.
        val outstanding = days
            .filter { !it.completed && !it.is_future && it.day_number in 1..totalDays }
            .sortedBy { it.day_number }
            .map { it.day_number }

        // The backend's /api/today block is authoritative for "what is due
        // today" when /api/progress hasn't materialised the day yet; make sure
        // today's assigned day is always eligible for presentation.
        val todayDue = today?.today?.takeIf { t ->
            !t.completed && t.day_number in 1..totalDays && outstanding.none { it == t.day_number }
        }?.day_number

        val allOutstanding = (outstanding + listOfNotNull(todayDue)).sorted()

        // No progress data at all yet: fall back to the backend's own "today".
        if (allOutstanding.isEmpty()) {
            val fallback = today?.today?.takeIf { !it.completed }?.day_number
                ?: today?.next_reading_day?.takeIf {
                    // zero backlog + read-ahead allowance (existing rule):
                    // tomorrow's day is presentable at most 1 day ahead.
                    it.day_number <= (today?.elapsed_days ?: 0) + 1
                }?.day_number
            return SessionPlan(
                days = listOfNotNull(fallback),
                catchup = false,
                backlogCount = 0,
                readAheadAvailable = true,
                programmeEndDate = meEndDate,
            )
        }

        val backlog = allOutstanding.size
        val inCatchup = backlog > 1

        // Arithmetic-only status: remaining reading days before the end date.
        val end = parseDate(meEndDate)
        val remaining = if (end != null) readingDaysBetween(todayDate, end) else Int.MAX_VALUE
        val shortfall = if (end != null) (backlog - remaining).coerceAtLeast(0) else 0

        // Present at most the 2 oldest outstanding days — never more.
        val present = allOutstanding.take(2)

        return SessionPlan(
            days = present,
            catchup = inCatchup,
            backlogCount = backlog,
            remainingReadingDays = if (end != null) remaining else 0,
            shortfallDays = shortfall,
            readAheadAvailable = !inCatchup,
            cannotFinishInTime = shortfall > 0,
            programmeEndDate = meEndDate,
        )
    }

    /** Human-readable short status for Progress/Home, per the operator's wording. */
    fun statusLine(plan: SessionPlan): String = when {
        plan.cannotFinishInTime ->
            "You need ${plan.shortfallDays} more day" + (if (plan.shortfallDays == 1) "" else "s") +
                " than you have remaining to fully catch up before the programme ends. " +
                "Keep reading — you can still complete all 50 days at your own pace."
        plan.catchup ->
            "Catch-up mode: ${plan.backlogCount} days outstanding — presented 2 at a time, oldest first."
        else -> ""
    }
}

/** Chapter-view gating for the finish action (ISSUE 4), kept UI-agnostic so the
 *  widget/tests can reason about it too. */
class ChapterViewTracker(
    /** chapters as "Book:chapter" keys, in presentation order */
    private val chapters: List<Pair<String, Int>>,
) {
    private val viewed = mutableSetOf<Pair<String, Int>>()
    val total: Int get() = chapters.size
    val viewedCount: Int get() = viewed.size
    val allViewed: Boolean get() = viewed.size >= chapters.size && chapters.isNotEmpty()

    fun markViewed(chapter: Pair<String, Int>) {
        if (chapter in chapters) viewed.add(chapter)
    }

    fun unviewedRemaining(): Int = (chapters.size - viewed.size).coerceAtLeast(0)

    /** Gate hint text, e.g. "View all 6 chapters to finish (3/6 viewed)". */
    fun gateHint(): String =
        "View all $total chapters to finish ($viewedCount/$total viewed)"
}

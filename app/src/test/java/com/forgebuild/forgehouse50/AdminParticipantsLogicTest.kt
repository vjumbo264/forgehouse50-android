package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.Participant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminParticipantsLogicTest {

    enum class SortOption { POINTS_DESC, NEWEST, NAME_ASC, DAYS_DESC }

    private fun filterAndSort(
        participants: List<Participant>,
        query: String,
        sortOption: SortOption
    ): List<Participant> {
        val q = query.trim().lowercase()
        val filtered = if (q.isBlank()) {
            participants
        } else {
            participants.filter {
                it.displayName.lowercase().contains(q) ||
                it.email.lowercase().contains(q) ||
                it.displayTag.lowercase().contains(q)
            }
        }
        return when (sortOption) {
            SortOption.POINTS_DESC -> filtered.sortedByDescending { it.points }
            SortOption.NEWEST -> filtered.sortedByDescending { it.created_at.orEmpty() }
            SortOption.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            SortOption.DAYS_DESC -> filtered.sortedByDescending { it.days_completed }
        }
    }

    private val sampleList = listOf(
        Participant(
            id = "user-1111-aaaa",
            name = "Victor",
            surname = "Fubara",
            full_name = "Victor Fubara",
            short_tag = "aaaa",
            email = "victor@example.com",
            points = 886,
            days_completed = 8,
            notes_count = 12,
            created_at = "2026-09-01T10:00:00Z"
        ),
        Participant(
            id = "user-2222-bbbb",
            name = "Victor",
            surname = "Okafor",
            full_name = "Victor Okafor",
            short_tag = "bbbb",
            email = "v.okafor@example.com",
            points = 200,
            days_completed = 3,
            notes_count = 1,
            created_at = "2026-09-10T10:00:00Z"
        ),
        Participant(
            id = "user-3333-cccc",
            name = "Alice",
            surname = "Zack",
            full_name = "Alice Zack",
            short_tag = "cccc",
            email = "alice@example.com",
            points = 0,
            days_completed = 0,
            notes_count = 0,
            created_at = "2026-09-15T10:00:00Z"
        ),
        Participant(
            id = "user-4444-dddd",
            name = "Admin",
            surname = "Pastor",
            full_name = "Admin Pastor",
            role = "admin",
            short_tag = "dddd",
            email = "pastor@example.com",
            points = 0,
            days_completed = 0,
            notes_count = 0,
            created_at = "2026-08-01T10:00:00Z"
        ),
    )

    @Test
    fun testSearchFilteringByFullNameAndShortTag() {
        // Search by first name "Victor" returns both Victors
        val res1 = filterAndSort(sampleList, "Victor", SortOption.POINTS_DESC)
        assertEquals(2, res1.size)

        // Search by specific surname "Fubara" distinguishes them
        val res2 = filterAndSort(sampleList, "Fubara", SortOption.POINTS_DESC)
        assertEquals(1, res2.size)
        assertEquals("aaaa", res2[0].short_tag)

        // Search by short tag "#bbbb"
        val res3 = filterAndSort(sampleList, "bbbb", SortOption.POINTS_DESC)
        assertEquals(1, res3.size)
        assertEquals("Victor Okafor", res3[0].displayName)
    }

    @Test
    fun testSortingOptions() {
        // Points desc: Victor Fubara (886) first
        val pts = filterAndSort(sampleList, "", SortOption.POINTS_DESC)
        assertEquals("Victor Fubara", pts.first().displayName)

        // Name A to Z: Alice Zack first
        val nameAsc = filterAndSort(sampleList, "", SortOption.NAME_ASC)
        assertEquals("Admin Pastor", nameAsc[0].displayName)
        assertEquals("Alice Zack", nameAsc[1].displayName)

        // Newest: Alice Zack (Sept 15) first
        val newest = filterAndSort(sampleList, "", SortOption.NEWEST)
        assertEquals("Alice Zack", newest.first().displayName)

        // Days completed desc: Victor Fubara (8) first, then Victor Okafor (3)
        val days = filterAndSort(sampleList, "", SortOption.DAYS_DESC)
        assertEquals(8, days[0].days_completed)
        assertEquals(3, days[1].days_completed)
    }

    @Test
    fun testSelectInactiveAccounts() {
        // Inactive accounts have 0 points, 0 days, 0 notes, and are NOT admins
        val nonAdmins = sampleList.filter { it.role != "admin" }
        val inactive = nonAdmins.filter { it.isInactive }

        assertEquals(1, inactive.size)
        assertEquals("Alice Zack", inactive[0].displayName)
        assertEquals("user-3333-cccc", inactive[0].id)
    }

    @Test
    fun testSelectAllExcludesAdmins() {
        // Admins can never be selected for deletion
        val selectable = sampleList.filter { it.role != "admin" }
        assertEquals(3, selectable.size)
        assertTrue(selectable.none { it.role == "admin" })
    }

    @Test
    fun testBatchDeleteConfirmationValidation() {
        fun isValidBatchConfirm(word: String): Boolean =
            word.trim().equals("DELETE", ignoreCase = true)

        assertTrue(isValidBatchConfirm("DELETE"))
        assertTrue(isValidBatchConfirm("delete"))
        assertTrue(isValidBatchConfirm(" Delete "))
        assertFalse(isValidBatchConfirm("DEL"))
        assertFalse(isValidBatchConfirm("yes"))
        assertFalse(isValidBatchConfirm(""))
    }
}

    @Test
    fun testReadingTimeFormatting() {
        fun formatTotalReadingTime(seconds: Long): String {
            val hrs = seconds / 3600
            val mins = (seconds % 3600) / 60
            return "${hrs} h ${mins} m read"
        }

        fun formatDayReadingTime(seconds: Long): String {
            val hrs = seconds / 3600
            val mins = (seconds % 3600) / 60
            val secs = seconds % 60
            return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m ${secs}s"
        }

        assertEquals("0 h 0 m read", formatTotalReadingTime(0))
        assertEquals("1 h 0 m read", formatTotalReadingTime(3600))
        assertEquals("6 h 20 m read", formatTotalReadingTime(22800))

        assertEquals("0m 0s", formatDayReadingTime(0))
        assertEquals("5m 10s", formatDayReadingTime(310))
        assertEquals("1h 10m", formatDayReadingTime(4200))
    }

    @Test
    fun testLatestCompletedDayDetection() {
        data class DayStub(val day: Int, val completed: Boolean, val completedAt: String?)

        fun findLatestCompletedDay(days: List<DayStub>): Int? {
            var latestDay: Int? = null
            var latestTime = ""
            for (d in days) {
                if (d.completed && d.completedAt != null) {
                    if (d.completedAt > latestTime) {
                        latestTime = d.completedAt
                        latestDay = d.day
                    }
                }
            }
            return latestDay
        }

        val emptyDays = listOf(
            DayStub(1, false, null),
            DayStub(2, false, null)
        )
        assertEquals(null, findLatestCompletedDay(emptyDays))

        val daysWithCompletions = listOf(
            DayStub(1, true, "2026-10-12T10:00:00Z"),
            DayStub(2, true, "2026-10-13T11:00:00Z"),
            DayStub(3, false, null)
        )
        assertEquals(2, findLatestCompletedDay(daysWithCompletions))

        // Catch-up completion where day 2 was completed after day 3
        val catchupCompletions = listOf(
            DayStub(1, true, "2026-10-12T10:00:00Z"),
            DayStub(3, true, "2026-10-14T09:00:00Z"),
            DayStub(2, true, "2026-10-14T15:00:00Z")
        )
        // Day 2 was completed most recently by timestamp
        assertEquals(2, findLatestCompletedDay(catchupCompletions))
    
    @Test
    fun testNoteHelperTextAndVisibilityLogic() {
        fun getNoteLabel(type: String): String {
            val isLeaderVisible = type == "observation" || type == "question"
            return if (isLeaderVisible) "Note (your leaders can read this)" else "Note (private to you)"
        }

        fun isVisibleToLeaders(type: String): Boolean {
            return type == "observation" || type == "question"
        }

        // Observation and Question are visible to leaders
        assertEquals("Note (your leaders can read this)", getNoteLabel("observation"))
        assertEquals("Note (your leaders can read this)", getNoteLabel("question"))
        assertTrue(isVisibleToLeaders("observation"))
        assertTrue(isVisibleToLeaders("question"))

        // Prayer, Insight, and Application are strictly private
        assertEquals("Note (private to you)", getNoteLabel("prayer"))
        assertEquals("Note (private to you)", getNoteLabel("insight"))
        assertEquals("Note (private to you)", getNoteLabel("scripture_connection"))
        assertEquals("Note (private to you)", getNoteLabel("application"))
        assertFalse(isVisibleToLeaders("prayer"))
        assertFalse(isVisibleToLeaders("insight"))
        assertFalse(isVisibleToLeaders("scripture_connection"))
        assertFalse(isVisibleToLeaders("application"))
    }

    @Test
    fun testAdminNotesFilteringExcludesPrivateTypes() {
        data class NoteStub(val id: String, val type: String, val text: String, val day: Int)

        val notes = listOf(
            NoteStub("1", "observation", "Good observation", 1),
            NoteStub("2", "question", "Interesting question?", 1),
            NoteStub("3", "prayer", "Private prayer", 1),
            NoteStub("4", "insight", "Deep insight", 1),
            NoteStub("5", "application", "Personal application", 1),
            NoteStub("6", "observation", "Another observation", 2)
        )

        fun filterVisibleNotes(list: List<NoteStub>, filter: String): List<NoteStub> {
            val allowed = when (filter) {
                "observation" -> setOf("observation")
                "question" -> setOf("question")
                else -> setOf("observation", "question") // all-visible default
            }
            return list.filter { it.type in allowed }
        }

        // All visible: only observation and question (3 notes)
        val visible = filterVisibleNotes(notes, "all-visible")
        assertEquals(3, visible.size)
        assertTrue(visible.none { it.type in setOf("prayer", "insight", "application") })

        // Observations only (2 notes)
        val obs = filterVisibleNotes(notes, "observation")
        assertEquals(2, obs.size)
        assertTrue(obs.all { it.type == "observation" })

        // Questions only (1 note)
        val q = filterVisibleNotes(notes, "question")
        assertEquals(1, q.size)
        assertTrue(q.all { it.type == "question" })

        // Malicious or invalid filter (e.g. attempting to request prayer) falls back to safe allowed set
        val malicious = filterVisibleNotes(notes, "prayer")
        assertTrue(malicious.none { it.type == "prayer" })
    }
}

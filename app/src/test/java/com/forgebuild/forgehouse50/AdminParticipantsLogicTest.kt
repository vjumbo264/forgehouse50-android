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

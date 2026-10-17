package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.ui.changelog.ChangelogEntry
import com.forgebuild.forgehouse50.update.UpdateLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogLogicTest {

    private val sampleEntries = listOf(
        ChangelogEntry(
            version = "v31",
            date = "2026-10-17",
            items = listOf(
                "Redesigned Reader Experience: Uncluttered, compact header and unified bottom dock",
                "Calm Reading Counters: Real-time verses-left counter and chapter indicators",
                "Two-Mode App Updater: Choose in-app download or browser link",
                "Prayer and Application Sharing: Send reflections to leadership with reply timestamps"
            )
        ),
        ChangelogEntry(
            version = "v30",
            date = "2026-10-17",
            items = listOf(
                "Notes and Insights Repair: Seamless saving and editing across all note types",
                "Synchronized Notes Counter: Instant home screen note counter updates"
            )
        ),
        ChangelogEntry(
            version = "v29",
            date = "2026-10-17",
            items = listOf(
                "Bible Translations Overhaul: Fixed offline footnote parsing for all 12 versions",
                "Unified Bible Translations Screen: Single screen with live progress"
            )
        ),
        ChangelogEntry(
            version = "v28",
            date = "2026-10-17",
            items = listOf(
                "Community Answers on Home: Inspiring leadership replies visible to all",
                "Interactive Scripture Pickers: Pick book, chapter, and verse with ease"
            )
        )
    )

    @Test
    fun testSinceLastSeenLogic() {
        // If user last saw v28, items from v31, v30, v29 are included
        val sinceV28 = UpdateLogic.computeSinceLastSeen(sampleEntries, "v28")
        assertEquals(8, sinceV28.size)
        assertTrue(sinceV28.any { it.contains("Redesigned Reader") })
        assertTrue(sinceV28.any { it.contains("Notes and Insights") })
        assertTrue(sinceV28.any { it.contains("Bible Translations") })
        assertFalse(sinceV28.any { it.contains("Interactive Scripture Pickers") }) // from v28, not newer

        // If user last saw v30, only v31 items are returned
        val sinceV30 = UpdateLogic.computeSinceLastSeen(sampleEntries, "v30")
        assertEquals(4, sinceV30.size)
        assertTrue(sinceV30.all { it.contains("Reader") || it.contains("Counters") || it.contains("Updater") || it.contains("Sharing") })

        // If user already saw current latest v31, nothing is returned
        val sinceV31 = UpdateLogic.computeSinceLastSeen(sampleEntries, "v31")
        assertTrue(sinceV31.isEmpty())

        // If null or blank, nothing is returned
        assertTrue(UpdateLogic.computeSinceLastSeen(sampleEntries, null).isEmpty())
        assertTrue(UpdateLogic.computeSinceLastSeen(sampleEntries, "").isEmpty())
    }

    @Test
    fun testAdminWordFilter() {
        val mixedItems = listOf(
            "Reader redesign and bottom dock",
            "Admin inbox moderation controls",
            "Delete user from database",
            "Prayer and application sharing"
        )
        val filtered = UpdateLogic.filterChangelogItems(mixedItems)
        assertEquals(2, filtered.size)
        assertEquals("Reader redesign and bottom dock", filtered[0])
        assertEquals("Prayer and application sharing", filtered[1])

        // Verify none contains "admin"
        assertTrue(filtered.none { it.lowercase().contains("admin") })
    }

    @Test
    fun testVersionParsing() {
        assertEquals(31, UpdateLogic.parseVersionCode("v31"))
        assertEquals(28, UpdateLogic.parseVersionCode("forgehouse50-v28"))
        assertEquals(1, UpdateLogic.parseVersionCode("1"))
        assertEquals(0, UpdateLogic.parseVersionCode("invalid"))
    }
}

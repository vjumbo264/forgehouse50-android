package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.WatTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicHomeScreenLogicTest {

    enum class HomeCardKey {
        HEADER,
        UPDATE_CARD,
        ANNOUNCEMENT_CARD,
        TODAY_READING_CARD,
        COMMUNITY_CARD,
        STATS_ROW,
        NOTES_ACTION,
        WHATSAPP_GROUP,
        SUPPORT_PLAY_STORE,
        SUPPORT_WHATSAPP,
        ADMIN_DASHBOARD
    }

    data class DynamicHomeState(
        val isUpdateActive: Boolean,
        val hasAnnouncement: Boolean,
        val hasCommunityPost: Boolean,
        val isAdmin: Boolean,
    ) {
        fun computeActiveCardKeys(): List<HomeCardKey> {
            val list = mutableListOf<HomeCardKey>()
            list.add(HomeCardKey.HEADER)
            if (isUpdateActive) list.add(HomeCardKey.UPDATE_CARD)
            if (hasAnnouncement) list.add(HomeCardKey.ANNOUNCEMENT_CARD)
            list.add(HomeCardKey.TODAY_READING_CARD)
            if (hasCommunityPost) list.add(HomeCardKey.COMMUNITY_CARD)
            list.add(HomeCardKey.STATS_ROW)
            list.add(HomeCardKey.NOTES_ACTION)
            list.add(HomeCardKey.WHATSAPP_GROUP)
            list.add(HomeCardKey.SUPPORT_PLAY_STORE)
            list.add(HomeCardKey.SUPPORT_WHATSAPP)
            if (isAdmin) list.add(HomeCardKey.ADMIN_DASHBOARD)
            return list
        }
    }

    @Test
    fun testPriorityOrderWithAllCardsActive() {
        val state = DynamicHomeState(
            isUpdateActive = true,
            hasAnnouncement = true,
            hasCommunityPost = true,
            isAdmin = true
        )
        val keys = state.computeActiveCardKeys()
        assertEquals(HomeCardKey.HEADER, keys[0])
        assertEquals(HomeCardKey.UPDATE_CARD, keys[1])
        assertEquals(HomeCardKey.ANNOUNCEMENT_CARD, keys[2])
        assertEquals(HomeCardKey.TODAY_READING_CARD, keys[3])
        assertEquals(HomeCardKey.COMMUNITY_CARD, keys[4])
        assertEquals(HomeCardKey.STATS_ROW, keys[5])
        assertEquals(HomeCardKey.NOTES_ACTION, keys[6])
        assertEquals(HomeCardKey.WHATSAPP_GROUP, keys[7])
        assertEquals(HomeCardKey.SUPPORT_PLAY_STORE, keys[8])
        assertEquals(HomeCardKey.SUPPORT_WHATSAPP, keys[9])
        assertEquals(HomeCardKey.ADMIN_DASHBOARD, keys[10])
    }

    @Test
    fun testEmptyStatesRenderNothing() {
        val state = DynamicHomeState(
            isUpdateActive = false,
            hasAnnouncement = false,
            hasCommunityPost = false,
            isAdmin = false
        )
        val keys = state.computeActiveCardKeys()
        assertFalse(keys.contains(HomeCardKey.UPDATE_CARD))
        assertFalse(keys.contains(HomeCardKey.ANNOUNCEMENT_CARD))
        assertFalse(keys.contains(HomeCardKey.COMMUNITY_CARD))
        assertFalse(keys.contains(HomeCardKey.ADMIN_DASHBOARD))
        // Today's reading card is the first main card below header
        assertEquals(HomeCardKey.HEADER, keys[0])
        assertEquals(HomeCardKey.TODAY_READING_CARD, keys[1])
        assertEquals(HomeCardKey.STATS_ROW, keys[2])
    }

    @Test
    fun testDynamicCardInsertionDoesNotDisturbRelativePriority() {
        // Initial state without announcement or update
        val initial = DynamicHomeState(
            isUpdateActive = false,
            hasAnnouncement = false,
            hasCommunityPost = true,
            isAdmin = false
        )
        val initialKeys = initial.computeActiveCardKeys()
        val todayIdxInitial = initialKeys.indexOf(HomeCardKey.TODAY_READING_CARD)
        val commIdxInitial = initialKeys.indexOf(HomeCardKey.COMMUNITY_CARD)
        assertTrue(commIdxInitial > todayIdxInitial)

        // New announcement appears: expands space above Today reading card
        val withAnnouncement = initial.copy(hasAnnouncement = true)
        val newKeys = withAnnouncement.computeActiveCardKeys()
        val annIdx = newKeys.indexOf(HomeCardKey.ANNOUNCEMENT_CARD)
        val todayIdxNew = newKeys.indexOf(HomeCardKey.TODAY_READING_CARD)
        val commIdxNew = newKeys.indexOf(HomeCardKey.COMMUNITY_CARD)

        assertTrue("Announcement must appear above Today reading", annIdx < todayIdxNew)
        assertTrue("Community card must remain below Today reading", commIdxNew > todayIdxNew)
    }

    @Test
    fun testCommunityCardReplyWatTimeFormat() {
        val replyIso = "2026-10-17T15:02:00.000Z"
        val formatted = WatTimeFormatter.formatRepliedTime(replyIso)
        assertTrue("Must start with Replied", formatted.startsWith("Replied"))
        assertTrue("Must contain formatted time", formatted.contains("17 Oct") && formatted.contains("4:02 pm"))
    }
}

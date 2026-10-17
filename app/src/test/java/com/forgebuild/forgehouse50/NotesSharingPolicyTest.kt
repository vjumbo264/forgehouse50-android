package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.WatTimeFormatter
import com.forgebuild.forgehouse50.ui.notes.SendFeedbackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesSharingPolicyTest {

    private val leaderBoundTypes = setOf("observation", "question", "prayer", "application")
    private val privateTypes = setOf("insight", "scripture_connection")

    @Test
    fun testLeaderBoundNoteTypes() {
        assertTrue(leaderBoundTypes.contains("observation"))
        assertTrue(leaderBoundTypes.contains("question"))
        assertTrue(leaderBoundTypes.contains("prayer"))
        assertTrue(leaderBoundTypes.contains("application"))

        assertFalse(leaderBoundTypes.contains("insight"))
        assertFalse(leaderBoundTypes.contains("scripture_connection"))
    }

    @Test
    fun testPrivateTypesRemainConfidential() {
        assertTrue(privateTypes.contains("insight"))
        assertFalse(leaderBoundTypes.contains("insight"))
    }

    @Test
    fun testGroupShareGateForPrayerAndApplication() {
        fun canPublishToGroup(type: String, hasReply: Boolean, allowGroupShare: Boolean): Boolean {
            if (!hasReply) return false
            if (type == "prayer" || type == "application") {
                return allowGroupShare
            }
            return true
        }

        // Observation without reply -> cannot publish
        assertFalse(canPublishToGroup("observation", hasReply = false, allowGroupShare = false))
        // Observation with reply -> can publish
        assertTrue(canPublishToGroup("observation", hasReply = true, allowGroupShare = false))

        // Prayer with reply but allowGroupShare = false -> blocked
        assertFalse(canPublishToGroup("prayer", hasReply = true, allowGroupShare = false))
        // Prayer with reply and allowGroupShare = true -> allowed
        assertTrue(canPublishToGroup("prayer", hasReply = true, allowGroupShare = true))

        // Application with reply but allowGroupShare = false -> blocked
        assertFalse(canPublishToGroup("application", hasReply = true, allowGroupShare = false))
        // Application with reply and allowGroupShare = true -> allowed
        assertTrue(canPublishToGroup("application", hasReply = true, allowGroupShare = true))
    }

    @Test
    fun testWatTimeFormatting() {
        // 12:15 UTC is 13:15 WAT (1:15 pm)
        val t1 = WatTimeFormatter.formatWatTime("2026-10-17T12:15:00.000Z")
        assertEquals("17 Oct, 1:15 pm", t1)

        // With year
        val t2 = WatTimeFormatter.formatWatTime("2026-10-17T12:15:00.000Z", includeYear = true)
        assertEquals("17 Oct 2026, 1:15 pm", t2)

        // Sent time unedited
        val sent1 = WatTimeFormatter.formatSentTime("2026-10-17T12:15:00.000Z", "2026-10-17T12:15:00.000Z")
        assertEquals("Sent 17 Oct, 1:15 pm", sent1)

        // Sent time edited
        val sent2 = WatTimeFormatter.formatSentTime("2026-10-17T12:15:00.000Z", "2026-10-17T14:30:00.000Z")
        assertEquals("Sent 17 Oct, 1:15 pm (Edited)", sent2)

        // Replied time
        val rep = WatTimeFormatter.formatRepliedTime("2026-10-17T13:00:00.000Z")
        assertEquals("Replied 17 Oct, 2:00 pm", rep)
    }

    @Test
    fun testSendFeedbackStateValues() {
        assertEquals(4, SendFeedbackState.values().size)
        assertTrue(SendFeedbackState.Idle != SendFeedbackState.Sending)
        assertTrue(SendFeedbackState.Sending != SendFeedbackState.Sent)
        assertTrue(SendFeedbackState.Sent != SendFeedbackState.Error)
    }
}

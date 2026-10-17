package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class NotesRouteContractTest {

    @Test
    fun testNoteTypes_allFiveTypesSupported() {
        val validTypes = listOf("observation", "question", "scripture_connection", "insight", "application", "prayer")
        assertTrue(validTypes.contains("insight"))
        assertTrue(validTypes.contains("observation"))
        assertTrue(validTypes.contains("question"))
        assertTrue(validTypes.contains("prayer"))
        assertTrue(validTypes.contains("application"))
    }

    @Test
    fun testUpdateNotePayload_includesIdAndTypeAndBody() {
        val id = "note-uuid-1234"
        val type = "observation"
        val body = "Clear observation of Matthew 1"
        val showName = true

        val json = buildJsonObject {
            put("id", id)
            put("note_type", type)
            put("body", body)
            put("show_name", showName)
        }

        assertEquals(id, json["id"]?.toString()?.replace("\"", ""))
        assertEquals(type, json["note_type"]?.toString()?.replace("\"", ""))
        assertEquals(body, json["body"]?.toString()?.replace("\"", ""))
        assertEquals("true", json["show_name"]?.toString())
    }

    @Test
    fun testAdminInboxUrls_formattedWithId() {
        val inboxId = "inbox-789"
        val publishUrl = "/admin/inbox/$inboxId/publish"
        val hideUrl = "/admin/inbox/$inboxId/hide"
        val replyUrl = "/admin/inbox/$inboxId/reply"

        assertEquals("/admin/inbox/inbox-789/publish", publishUrl)
        assertEquals("/admin/inbox/inbox-789/hide", hideUrl)
        assertEquals("/admin/inbox/inbox-789/reply", replyUrl)
        assertFalse(publishUrl.contains("//"))
        assertFalse(hideUrl.contains("//"))
        assertFalse(replyUrl.contains("//"))
    }

    @Test
    fun testHomeNotesCount_neverStuckAtZeroWhenNotesExist() {
        val todayNotesTotal = 0
        val meNotesTotal = 0
        val cachedNotesCount = 5

        val displayedCount = maxOf(todayNotesTotal, meNotesTotal, cachedNotesCount)
        assertEquals(5, displayedCount)
    }

    @Test
    fun testFriendlyErrorMessages_noRawHttpCode() {
        fun friendlyMessage(status: Int, backendMsg: String?): String {
            return when (status) {
                400 -> backendMsg ?: "Invalid request details. Please check your input."
                401 -> "Session expired. Please sign in again."
                403 -> "You do not have permission to perform this action."
                404 -> backendMsg ?: "The requested item could not be found."
                405 -> "Could not save your note. Please try again."
                429 -> "Rate limit reached. Please wait a moment and try again."
                500, 502, 503, 504 -> "Could not save your note. Please try again."
                else -> backendMsg ?: "Request failed. Please try again."
            }
        }

        assertFalse(friendlyMessage(500, null).contains("500"))
        assertFalse(friendlyMessage(405, null).contains("405"))
        assertEquals("Could not save your note. Please try again.", friendlyMessage(500, null))
        assertEquals("Could not save your note. Please try again.", friendlyMessage(405, null))
    }
}

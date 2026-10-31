package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.AnnouncementLatestResponse
import com.forgebuild.forgehouse50.data.AnnouncementsResponse
import com.forgebuild.forgehouse50.ui.AppJson
import com.forgebuild.forgehouse50.ui.announcements.shouldOfferAnnouncementCollapse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementsLogicTest {

    @Test
    fun standardPastoralAnnouncementsAreNeverCollapsedOrTruncated() {
        val pastorMessage = "Good afternoon everyone. This is to bring to your notice that about 17 of you " +
            "are not keeping up with your reading times. And, as you delay, it will keep piling up. " +
            "So far, only one person has done his/her reading for today. I'm encouraging you to read your Bible today."
        assertFalse(shouldOfferAnnouncementCollapse(pastorMessage))
        assertFalse(shouldOfferAnnouncementCollapse("Paragraph 1.\n\nParagraph 2.\n\nParagraph 3."))
    }

    @Test
    fun extraLongAnnouncementsOfferInlineExpandCollapseToggle() {
        val huge = "Please take note of this detailed programme schedule update. ".repeat(30)
        assertTrue(shouldOfferAnnouncementCollapse(huge))
    }

    @Test
    fun decodesLatestAndArchiveResponsesIncludingRetiredStatus() {
        val latestPayload = """
            {
              "ok": true,
              "total_active": 0,
              "total_archived": 2,
              "total_all": 2,
              "announcement": null
            }
        """.trimIndent()
        val decodedLatest = AppJson.decodeFromString(AnnouncementLatestResponse.serializer(), latestPayload)
        assertNull(decodedLatest.announcement)
        assertEquals(2, decodedLatest.total_archived)
        assertEquals(2, decodedLatest.total_all)

        val archivePayload = """
            {
              "ok": true,
              "announcements": [
                {
                  "id": "a1",
                  "title": "LACK OF CONSISTENCY",
                  "body": "Full pastoral notice body.",
                  "status": "archived",
                  "created_at": "2026-10-29T11:27:35.664Z"
                }
              ],
              "next_cursor": null,
              "has_more": false
            }
        """.trimIndent()
        val decodedArchive = AppJson.decodeFromString(AnnouncementsResponse.serializer(), archivePayload)
        assertEquals(1, decodedArchive.announcements.size)
        assertNotNull(decodedArchive.announcements.first())
        assertEquals("archived", decodedArchive.announcements.first().status)
    }
}

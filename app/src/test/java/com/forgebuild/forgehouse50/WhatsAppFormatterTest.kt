package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.LeaderInboxItem
import com.forgebuild.forgehouse50.data.LeaderInboxReply
import com.forgebuild.forgehouse50.data.WhatsAppFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WhatsAppFormatterTest {

    @Test
    fun testAnonymousItemFormatting() {
        val item = LeaderInboxItem(
            id = "1",
            type = "observation",
            reference = "Matthew 5:3",
            text = "Blessed are the poor in spirit, for theirs is the kingdom of heaven.",
            show_name = false,
            author_full_name = "Secret Author",
            reply = LeaderInboxReply(id = "r1", text = "True humility is recognizing our need for God.")
        )

        val formatted = WhatsAppFormatter.formatItem(item)

        assertTrue(formatted.contains("*Observation*"))
        assertTrue(formatted.contains("_Matthew 5:3_"))
        assertTrue(formatted.contains("> Blessed are the poor in spirit"))
        assertTrue(formatted.contains("*Reply:*\nTrue humility is recognizing our need for God."))
        assertTrue(formatted.contains("*Author:* *Anonymous*"))
        assertFalse(formatted.contains("Secret Author"))
    }

    @Test
    fun testNamedQuestionItemFormatting() {
        val item = LeaderInboxItem(
            id = "2",
            type = "question",
            reference = "Acts 2:4",
            text = "What was the significance of speaking in tongues?",
            show_name = true,
            author_full_name = "David Okonkwo",
            reply = LeaderInboxReply(id = "r2", text = "It symbolized the universal gospel reaching all nations.")
        )

        val formatted = WhatsAppFormatter.formatItem(item)

        assertTrue(formatted.contains("*Question*"))
        assertTrue(formatted.contains("_Acts 2:4_"))
        assertTrue(formatted.contains("> What was the significance"))
        assertTrue(formatted.contains("*Reply:*\nIt symbolized the universal gospel"))
        assertTrue(formatted.contains("*Author:* David Okonkwo"))
    }

    @Test
    fun testBatchFormattingWithHeaderAndSeparator() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.OCTOBER, 17, 12, 0, 0)
        }
        val item1 = LeaderInboxItem(
            id = "1",
            type = "observation",
            reference = "Matthew 5:3",
            text = "Obs text",
            show_name = false,
            reply = LeaderInboxReply(text = "Leader reply 1")
        )
        val item2 = LeaderInboxItem(
            id = "2",
            type = "question",
            reference = "Romans 8:28",
            text = "Question text",
            show_name = true,
            author_full_name = "Sarah Adeyemi",
            reply = LeaderInboxReply(text = "Leader reply 2")
        )

        val batch = WhatsAppFormatter.formatBatch(listOf(item1, item2), cal.time)

        assertTrue(batch.startsWith("*ForgeHouse 50 — Community Highlights*\n17 Oct 2026\n"))
        assertTrue(batch.contains("───────────────────────────────"))
        assertTrue(batch.contains("*Author:* *Anonymous*"))
        assertTrue(batch.contains("*Author:* Sarah Adeyemi"))
    }
}

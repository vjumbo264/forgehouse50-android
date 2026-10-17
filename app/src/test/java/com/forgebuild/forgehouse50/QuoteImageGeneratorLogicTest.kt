package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.LeaderInboxItem
import com.forgebuild.forgehouse50.data.LeaderReply
import com.forgebuild.forgehouse50.ui.admin.QuoteImageGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteImageGeneratorLogicTest {

    @Test
    fun testGoldenCardDimensions() {
        assertEquals(1080, QuoteImageGenerator.CARD_WIDTH)
        assertEquals(1350, QuoteImageGenerator.CARD_HEIGHT)
        assertEquals(3, QuoteImageGenerator.PALETTES.size)
    }

    @Test
    fun testAnonymityRules() {
        val anonItem = LeaderInboxItem(
            id = "inbox-1",
            author_full_name = "Brother John",
            show_name = false,
            text = "Great insight"
        )
        assertEquals("Anonymous", QuoteImageGenerator.authorDisplayName(anonItem))

        val namedItem = LeaderInboxItem(
            id = "inbox-2",
            author_full_name = "Sister Mary",
            show_name = true,
            text = "Prayer request"
        )
        assertEquals("Sister Mary", QuoteImageGenerator.authorDisplayName(namedItem))
    }

    @Test
    fun testEligibilityRules() {
        // Unanswered note -> false
        val unanswered = LeaderInboxItem(
            id = "1",
            type = "observation",
            reply = null,
            status = "new"
        )
        assertFalse(QuoteImageGenerator.canShareAsImage(unanswered))

        // Answered observation -> true
        val answeredObs = LeaderInboxItem(
            id = "2",
            type = "observation",
            reply = LeaderReply(text = "Amen"),
            status = "answered"
        )
        assertTrue(QuoteImageGenerator.canShareAsImage(answeredObs))

        // Insight -> ALWAYS false (strict confidentiality)
        val insight = LeaderInboxItem(
            id = "3",
            type = "insight",
            reply = LeaderReply(text = "Private reflection"),
            status = "answered"
        )
        assertFalse("Insight notes must never be shared as quote images", QuoteImageGenerator.canShareAsImage(insight))

        // Prayer without group share permission -> false
        val prayerNoShare = LeaderInboxItem(
            id = "4",
            type = "prayer",
            allow_group_share = false,
            reply = LeaderReply(text = "Praying with you"),
            status = "answered"
        )
        assertFalse(QuoteImageGenerator.canShareAsImage(prayerNoShare))

        // Prayer WITH group share permission -> true
        val prayerWithShare = LeaderInboxItem(
            id = "5",
            type = "prayer",
            allow_group_share = true,
            reply = LeaderReply(text = "Praying with you"),
            status = "answered"
        )
        assertTrue(QuoteImageGenerator.canShareAsImage(prayerWithShare))

        // Application without group share permission -> false
        val appNoShare = LeaderInboxItem(
            id = "6",
            type = "application",
            allow_group_share = false,
            reply = LeaderReply(text = "Well applied"),
            status = "answered"
        )
        assertFalse(QuoteImageGenerator.canShareAsImage(appNoShare))

        // Application WITH group share permission -> true
        val appWithShare = LeaderInboxItem(
            id = "7",
            type = "application",
            allow_group_share = true,
            reply = LeaderReply(text = "Well applied"),
            status = "answered"
        )
        assertTrue(QuoteImageGenerator.canShareAsImage(appWithShare))
    }

    @Test
    fun testShortTextSingleCard() {
        val item = LeaderInboxItem(
            id = "short-1",
            type = "observation",
            reference = "Matthew 5:3",
            text = "Blessed are the poor in spirit, for theirs is the kingdom of heaven.",
            reply = LeaderReply(text = "True spiritual humility is the doorway to God's presence.")
        )
        val cards = QuoteImageGenerator.splitTextToCards(item, globalIndex = 0)
        assertEquals(1, cards.size)
        assertEquals(1, cards[0].pageIndex)
        assertEquals(1, cards[0].totalPages)
        assertEquals(0, cards[0].variantIndex)
    }

    @Test
    fun testLongTextContinuationCards() {
        val longQuote = "A".repeat(350)
        val longReply = "B".repeat(400)
        val item = LeaderInboxItem(
            id = "long-1",
            type = "question",
            reference = "Romans 8:28",
            text = longQuote,
            reply = LeaderReply(text = longReply)
        )
        val cards = QuoteImageGenerator.splitTextToCards(item, globalIndex = 1)
        assertEquals(2, cards.size)
        assertEquals(1, cards[0].pageIndex)
        assertEquals(2, cards[0].totalPages)
        assertEquals(2, cards[1].pageIndex)
        assertEquals(2, cards[1].totalPages)
        assertEquals(1, cards[0].variantIndex)
        assertEquals(1, cards[1].variantIndex)
    }

    @Test
    fun testCyclingPalettes() {
        val item = LeaderInboxItem(id = "item", type = "observation", reply = LeaderReply(text = "ok"))
        val c0 = QuoteImageGenerator.splitTextToCards(item, 0)
        val c1 = QuoteImageGenerator.splitTextToCards(item, 1)
        val c2 = QuoteImageGenerator.splitTextToCards(item, 2)
        val c3 = QuoteImageGenerator.splitTextToCards(item, 3)

        assertEquals(0, c0[0].variantIndex)
        assertEquals(1, c1[0].variantIndex)
        assertEquals(2, c2[0].variantIndex)
        assertEquals(0, c3[0].variantIndex) // cycles back to 0
    }
}

package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.ChatMessageDto
import com.forgebuild.forgehouse50.ui.community.CommunityChatLogic
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityChatLogicTest {

    private fun msg(id: String, createdAt: String, text: String = id) = ChatMessageDto(
        id = id,
        channel_type = "group",
        sender_id = "u1",
        author_name = "Grace O.",
        author_role = "member",
        text = text,
        created_at = createdAt,
    )

    @Test
    fun mergeOlderMessages_prependsChronologicallyAndDeduplicates() {
        val existing = listOf(
            msg("m3", "2026-10-31T10:03:00.000Z"),
            msg("m4", "2026-10-31T10:04:00.000Z"),
        )
        val older = listOf(
            msg("m1", "2026-10-31T10:01:00.000Z"),
            msg("m2", "2026-10-31T10:02:00.000Z"),
            msg("m3", "2026-10-31T10:03:00.000Z"),
        )

        val merged = CommunityChatLogic.mergeOlderMessages(existing, older)
        assertEquals(listOf("m1", "m2", "m3", "m4"), merged.map { it.id })
    }

    @Test
    fun applyIncrementalDelta_appendsNewAndRemovesModeratorDeletedIds() {
        val existing = listOf(
            msg("m1", "2026-10-31T10:01:00.000Z"),
            msg("m2", "2026-10-31T10:02:00.000Z"),
            msg("m3", "2026-10-31T10:03:00.000Z"),
        )
        val incoming = listOf(
            msg("m4", "2026-10-31T10:04:00.000Z"),
        )

        val updated = CommunityChatLogic.applyIncrementalDelta(
            existing = existing,
            incoming = incoming,
            deletedIds = listOf("m2"),
        )
        assertEquals(listOf("m1", "m3", "m4"), updated.map { it.id })
    }

    @Test
    fun canComposeDirect_enforcesAdminInitiatedAndMutePolicy() {
        // Member cannot compose before admin starts thread
        assertFalse(CommunityChatLogic.canComposeDirect(isAdmin = false, isMuted = false, threadStartedByAdmin = false))
        // Member can reply once admin started thread
        assertTrue(CommunityChatLogic.canComposeDirect(isAdmin = false, isMuted = false, threadStartedByAdmin = true))
        // Muted member cannot compose even in started thread
        assertFalse(CommunityChatLogic.canComposeDirect(isAdmin = false, isMuted = true, threadStartedByAdmin = true))
        // Admin can always initiate or reply
        assertTrue(CommunityChatLogic.canComposeDirect(isAdmin = true, isMuted = false, threadStartedByAdmin = false))
    }

    @Test
    fun canComposeGroup_blocksMutedMemberOnly() {
        assertTrue(CommunityChatLogic.canComposeGroup(isAdmin = false, isMuted = false))
        assertFalse(CommunityChatLogic.canComposeGroup(isAdmin = false, isMuted = true))
        assertTrue(CommunityChatLogic.canComposeGroup(isAdmin = true, isMuted = true))
    }

    @Test
    fun formatUnreadBadge_handlesZeroNormalAndOverflow() {
        assertNull(CommunityChatLogic.formatUnreadBadge(0))
        assertNull(CommunityChatLogic.formatUnreadBadge(-3))
        assertEquals("4", CommunityChatLogic.formatUnreadBadge(4))
        assertEquals("99", CommunityChatLogic.formatUnreadBadge(99))
        assertEquals("99+", CommunityChatLogic.formatUnreadBadge(120))
    }

    @Test
    fun sanitizeChatError_rethrowsCancellationAndStripsRawInternals() {
        var rethrown = false
        try {
            CommunityChatLogic.sanitizeChatError(CancellationException("StandaloneCoroutine was cancelled"))
        } catch (_: CancellationException) {
            rethrown = true
        }
        assertTrue(rethrown)

        val friendly = CommunityChatLogic.sanitizeChatError(
            RuntimeException("java.net.SocketTimeoutException: failed to connect"),
            "Could not send message.",
        )
        assertFalse(friendly.contains("SocketTimeoutException"))
    }
}

package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.ChatDeliveryState
import com.forgebuild.forgehouse50.data.ChatMessageDto
import com.forgebuild.forgehouse50.data.ChatReplyPreviewDto
import com.forgebuild.forgehouse50.data.ChatThreadDto
import com.forgebuild.forgehouse50.ui.community.CommunityChatLogic
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityChatLogicTest {

    private fun msg(
        id: String,
        createdAt: String,
        text: String = "msg-$id",
        isMe: Boolean = false,
        role: String = "member",
        isDeleted: Boolean = false,
        clientMsgId: String? = null,
        replyTo: ChatReplyPreviewDto? = null,
    ) = ChatMessageDto(
        id = id,
        sender_id = if (isMe) "user-me" else "user-other",
        is_me = isMe,
        author_name = if (role == "admin") "Pastor Dave" else "Ada O.",
        author_role = role,
        text = text,
        is_deleted = isDeleted,
        client_msg_id = clientMsgId,
        reply_to = replyTo,
        created_at = createdAt,
    )

    @Test
    fun `k1 adaptive polling cadence starts at 1_5s and backs off when idle`() {
        assertEquals(1_500L, CommunityChatLogic.nextForegroundPollDelayMs(0L))
        assertEquals(1_500L, CommunityChatLogic.nextForegroundPollDelayMs(29_999L))
        assertEquals(3_000L, CommunityChatLogic.nextForegroundPollDelayMs(30_000L))
        assertEquals(3_000L, CommunityChatLogic.nextForegroundPollDelayMs(119_999L))
        assertEquals(6_000L, CommunityChatLogic.nextForegroundPollDelayMs(120_000L))
    }

    @Test
    fun `k1 mergeOlderMessages prepends older page and deduplicates`() {
        val existing = listOf(
            msg("m3", "2026-10-31T10:03:00Z"),
            msg("m4", "2026-10-31T10:04:00Z"),
        )
        val older = listOf(
            msg("m1", "2026-10-31T10:01:00Z"),
            msg("m2", "2026-10-31T10:02:00Z"),
            msg("m3", "2026-10-31T10:03:00Z"),
        )
        val merged = CommunityChatLogic.mergeOlderMessages(existing, older)
        assertEquals(listOf("m1", "m2", "m3", "m4"), merged.map { it.id })
    }

    @Test
    fun `k1 applyIncrementalDelta replaces matching optimistic message and removes deletedIds`() {
        val optimistic = CommunityChatLogic.createOptimisticMessage(
            clientMsgId = "cid-1",
            channelType = "group",
            threadId = null,
            senderId = "user-me",
            authorName = "Ada O.",
            authorRole = "member",
            text = "Amen!",
            createdAt = "2026-10-31T10:03:00Z",
        )
        val existing = listOf(
            msg("m1", "2026-10-31T10:01:00Z"),
            msg("m2", "2026-10-31T10:02:00Z"),
            optimistic,
        )
        val incoming = listOf(
            msg("m3", "2026-10-31T10:03:01Z", text = "Amen!", isMe = true, clientMsgId = "cid-1"),
        )
        val updated = CommunityChatLogic.applyIncrementalDelta(existing, incoming, deletedIds = listOf("m2"))
        assertEquals(listOf("m1", "m3"), updated.map { it.id })
        assertEquals(ChatDeliveryState.SENT, updated.last().delivery_state)
    }

    @Test
    fun `k2 empty thread cursor normalizes to non-null EPOCH_CURSOR preventing reload loop`() {
        val cursor = CommunityChatLogic.normalizeCursor(null)
        assertEquals(CommunityChatLogic.EPOCH_CURSOR, cursor)
        val state = CommunityChatLogic.onLoadSuccess(CommunityChatLogic.ThreadLoadState(isLoading = true))
        assertFalse(CommunityChatLogic.shouldAttemptInitialOnPoll(cursor, state, 10_000L))
    }

    @Test
    fun `k2 failed thread load uses bounded backoff and stops retrying after MAX_AUTO_LOAD_RETRIES`() {
        var state = CommunityChatLogic.ThreadLoadState()
        assertTrue(CommunityChatLogic.shouldAttemptInitialOnPoll(null, state, 0L))

        state = CommunityChatLogic.onLoadFailure(state, "Network error", nowMs = 1_000L)
        assertEquals(1, state.attemptCount)
        assertFalse(CommunityChatLogic.shouldAttemptInitialOnPoll(null, state, 2_000L)) // within 2s backoff
        assertTrue(CommunityChatLogic.shouldAttemptInitialOnPoll(null, state, 3_000L))

        state = CommunityChatLogic.onLoadFailure(state, "Network error", nowMs = 3_000L)
        assertEquals(2, state.attemptCount)
        assertFalse(CommunityChatLogic.shouldAttemptInitialOnPoll(null, state, 6_000L)) // within 4s backoff
        assertTrue(CommunityChatLogic.shouldAttemptInitialOnPoll(null, state, 7_000L))

        state = CommunityChatLogic.onLoadFailure(state, "Network error", nowMs = 7_000L)
        assertEquals(3, state.attemptCount)
        assertFalse(state.canAutoRetry)
        // Must NEVER auto-retry again regardless of elapsed time (no endless loop)
        assertFalse(CommunityChatLogic.shouldAttemptInitialOnPoll(null, state, 999_999L))
    }

    @Test
    fun `k3 optimistic send lifecycle transitions SENDING to SENT or FAILED and supports pending roundtrip`() {
        val opt = CommunityChatLogic.createOptimisticMessage(
            clientMsgId = "cid-99",
            channelType = "direct",
            threadId = "user-me",
            senderId = "user-me",
            authorName = "Ada O.",
            authorRole = "member",
            text = "Hello Pastor",
            createdAt = "2026-10-31T10:00:00Z",
        )
        assertEquals(ChatDeliveryState.SENDING, opt.delivery_state)

        val pending = CommunityChatLogic.toPendingOutgoing(opt)!!
        val restored = CommunityChatLogic.fromPendingOutgoing(pending, "user-me")
        assertEquals(ChatDeliveryState.FAILED, restored.delivery_state)
        assertEquals("cid-99", restored.client_msg_id)

        val failedList = CommunityChatLogic.markOptimisticFailed(listOf(opt), "cid-99", "Offline")
        assertEquals(ChatDeliveryState.FAILED, failedList.single().delivery_state)
        assertEquals("Offline", failedList.single().local_error)

        val retrying = CommunityChatLogic.markOptimisticRetrying(failedList, "cid-99")
        assertEquals(ChatDeliveryState.SENDING, retrying.single().delivery_state)

        val serverMsg = msg("srv-1", "2026-10-31T10:00:01Z", text = "Hello Pastor", isMe = true, clientMsgId = "cid-99")
        val sentList = CommunityChatLogic.markOptimisticSent(retrying, "cid-99", serverMsg)
        assertEquals(1, sentList.size)
        assertEquals("srv-1", sentList.single().id)
        assertEquals(ChatDeliveryState.SENT, sentList.single().delivery_state)
    }

    @Test
    fun `k4 swipe to reply and tap quote jump target resolution`() {
        val normal = msg("m1", "2026-10-31T10:00:00Z")
        val deleted = msg("m2", "2026-10-31T10:01:00Z", isDeleted = true)
        val sending = CommunityChatLogic.createOptimisticMessage("c1", "group", null, "u1", "Ada", "member", "Hi")

        assertTrue(CommunityChatLogic.canSwipeToReply(normal))
        assertFalse(CommunityChatLogic.canSwipeToReply(deleted))
        assertFalse(CommunityChatLogic.canSwipeToReply(sending))

        val list = listOf(normal, deleted)
        assertEquals(0, CommunityChatLogic.findReplyTargetIndex(list, "m1"))
        assertEquals(-1, CommunityChatLogic.findReplyTargetIndex(list, "missing"))
    }

    @Test
    fun `k5 member can delete own sent message and deleted text never leaks to non-admins`() {
        val mine = msg("m1", "2026-10-31T10:00:00Z", text = "Secret typo", isMe = true)
        val theirs = msg("m2", "2026-10-31T10:01:00Z", text = "Reply", isMe = false, replyTo = ChatReplyPreviewDto("m1", "Ada O.", "member", "Secret typo"))

        assertTrue(CommunityChatLogic.canDeleteMessage(mine, isAdminViewer = false))
        assertFalse(CommunityChatLogic.canDeleteMessage(theirs, isAdminViewer = false))
        assertTrue(CommunityChatLogic.canDeleteMessage(theirs, isAdminViewer = true))

        val tombstoned = CommunityChatLogic.applyLocalSoftDelete(
            existing = listOf(mine, theirs),
            messageId = "m1",
            isAdminViewer = false,
            deletedAt = "2026-10-31T10:02:00Z",
        )
        assertTrue(tombstoned[0].is_deleted)
        assertEquals(CommunityChatLogic.DELETED_MESSAGE_PLACEHOLDER, CommunityChatLogic.displayMessageText(tombstoned[0], isAdminViewer = false))
        assertEquals(CommunityChatLogic.DELETED_MESSAGE_PLACEHOLDER, CommunityChatLogic.displayReplyPreviewText(tombstoned[1].reply_to!!, isAdminViewer = false))
        assertFalse(CommunityChatLogic.canDeleteMessage(tombstoned[0], isAdminViewer = false))
    }

    @Test
    fun `k6 shared leadership inbox lets members compose immediately unless muted`() {
        assertTrue(CommunityChatLogic.canComposeDirect(isAdmin = false, isMuted = false))
        assertFalse(CommunityChatLogic.canComposeDirect(isAdmin = false, isMuted = true))
        assertTrue(CommunityChatLogic.canComposeDirect(isAdmin = true, isMuted = false, hasActiveThread = true))
        assertFalse(CommunityChatLogic.canComposeDirect(isAdmin = true, isMuted = false, hasActiveThread = false))
        assertEquals(
            "With Pastor Dave & leaders",
            CommunityChatLogic.memberLeadershipSubtitle(listOf(ChatThreadDto(subtitle = "With Pastor Dave & leaders"))),
        )
        assertEquals("ForgeHouse Leadership", CommunityChatLogic.memberDirectHeaderTitle())
        // Empty thread or after manual retry resets attemptCount so member can recover from failed load (FH-014)
        var failed = CommunityChatLogic.onLoadFailure(
            CommunityChatLogic.ThreadLoadState(),
            "Network error",
            nowMs = 1_000L,
        )
        failed = CommunityChatLogic.onLoadFailure(failed, "Network error", nowMs = 3_000L)
        failed = CommunityChatLogic.onLoadFailure(failed, "Network error", nowMs = 7_000L)
        assertFalse(CommunityChatLogic.shouldAttemptInitialOnPoll(null, failed, 999_999L))
        val recovered = CommunityChatLogic.onLoadSuccess(failed)
        assertEquals(0, recovered.attemptCount)
        assertNull(recovered.errorMessage)
    }

    @Test
    fun `unread badge formatter caps at 99+`() {
        assertNull(CommunityChatLogic.formatUnreadBadge(0))
        assertNull(CommunityChatLogic.formatUnreadBadge(-3))
        assertEquals("4", CommunityChatLogic.formatUnreadBadge(4))
        assertEquals("99", CommunityChatLogic.formatUnreadBadge(99))
        assertEquals("99+", CommunityChatLogic.formatUnreadBadge(120))
    }

    @Test(expected = CancellationException::class)
    fun `sanitizeChatError always rethrows CancellationException`() {
        CommunityChatLogic.sanitizeChatError(CancellationException("StandaloneCoroutine was cancelled"))
    }

    @Test
    fun `sanitizeChatError maps technical coroutine and network failures to friendly copy`() {
        val friendly = CommunityChatLogic.sanitizeChatError(
            IllegalStateException("Unable to resolve host \"forgehouse50.pages.dev\"")
        )
        assertFalse(friendly.contains("Unable to resolve host", ignoreCase = true))
        assertTrue(friendly.contains("internet", ignoreCase = true) || friendly.contains("try again", ignoreCase = true))
    }
}

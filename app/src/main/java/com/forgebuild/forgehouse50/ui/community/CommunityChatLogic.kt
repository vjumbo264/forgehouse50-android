package com.forgebuild.forgehouse50.ui.community

import com.forgebuild.forgehouse50.data.ChatDeliveryState
import com.forgebuild.forgehouse50.data.ChatMessageDto
import com.forgebuild.forgehouse50.data.ChatReplyPreviewDto
import com.forgebuild.forgehouse50.data.ChatThreadDto
import com.forgebuild.forgehouse50.data.PendingChatOutgoing
import com.forgebuild.forgehouse50.data.ReaderErrorSanitizer
import kotlinx.coroutines.CancellationException
import java.time.Instant

/**
 * Pure state-transition and validation helpers for Community Chat (i9 + k1..k6).
 * Kept Android-free so JUnit tests can verify adaptive sync cadence, bounded
 * retry/backoff, shared leadership inbox, optimistic send + ticks, swipe-to-reply
 * jump targets, and soft-delete tombstone safety.
 */
object CommunityChatLogic {
    const val MAX_MESSAGE_CHARS = 1000
    const val EPOCH_CURSOR = "1970-01-01T00:00:00.000Z"
    const val DELETED_MESSAGE_PLACEHOLDER = "This message was deleted"

    // k1 adaptive foreground polling cadence
    const val POLL_FAST_MS = 1_500L
    const val POLL_MEDIUM_MS = 3_000L
    const val POLL_IDLE_MS = 6_000L
    const val NON_CHAT_UNREAD_POLL_MS = 15_000L
    const val FOREGROUND_POLL_INTERVAL_MS = POLL_FAST_MS

    // k2 bounded backoff for failed/empty loads (never endless loop)
    const val MAX_AUTO_LOAD_RETRIES = 3

    data class ThreadLoadState(
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val attemptCount: Int = 0,
        val hasLoadedOnce: Boolean = false,
        val nextRetryAllowedAtMs: Long = 0L,
    ) {
        val canAutoRetry: Boolean
            get() = errorMessage == null || attemptCount < MAX_AUTO_LOAD_RETRIES
    }

    /**
     * Adaptive foreground chat polling cadence (k1):
     * - 1.5s for 30s after opening chat or sending/receiving a message
     * - 3s between 30s and 2m idle
     * - 6s after 2m idle
     */
    fun nextForegroundPollDelayMs(idleMillis: Long): Long = when {
        idleMillis < 30_000L -> POLL_FAST_MS
        idleMillis < 120_000L -> POLL_MEDIUM_MS
        else -> POLL_IDLE_MS
    }

    /**
     * Bounded exponential backoff in ms for initial-load failures (k2).
     * attempt=1 -> 2s, attempt=2 -> 4s, attempt=3 -> 8s.
     */
    fun backoffDelayMs(attempt: Int): Long = when {
        attempt <= 1 -> 2_000L
        attempt == 2 -> 4_000L
        else -> 8_000L
    }

    fun onLoadSuccess(prev: ThreadLoadState): ThreadLoadState = ThreadLoadState(
        isLoading = false,
        errorMessage = null,
        attemptCount = 0,
        hasLoadedOnce = true,
        nextRetryAllowedAtMs = 0L,
    )

    fun onLoadFailure(
        prev: ThreadLoadState,
        errorMessage: String,
        nowMs: Long,
    ): ThreadLoadState {
        val nextAttempts = prev.attemptCount + 1
        return ThreadLoadState(
            isLoading = false,
            errorMessage = errorMessage,
            attemptCount = nextAttempts,
            hasLoadedOnce = prev.hasLoadedOnce,
            nextRetryAllowedAtMs = nowMs + backoffDelayMs(nextAttempts),
        )
    }

    /**
     * Determines whether a background poll tick may attempt a full initial load
     * when no cursor exists yet (k2). Stops permanently after MAX_AUTO_LOAD_RETRIES
     * so a failed or empty load NEVER loops endlessly.
     */
    fun shouldAttemptInitialOnPoll(
        cursor: String?,
        loadState: ThreadLoadState,
        nowMs: Long,
    ): Boolean {
        if (!cursor.isNullOrBlank()) return false
        if (loadState.isLoading) return false
        if (loadState.hasLoadedOnce && loadState.errorMessage == null) return false
        if (loadState.attemptCount >= MAX_AUTO_LOAD_RETRIES) return false
        return nowMs >= loadState.nextRetryAllowedAtMs
    }

    /** Always normalizes empty-thread cursors to EPOCH_CURSOR so polls never repeat full loads. */
    fun normalizeCursor(serverCursor: String?, fallback: String? = null): String =
        serverCursor?.takeIf { it.isNotBlank() }
            ?: fallback?.takeIf { it.isNotBlank() }
            ?: EPOCH_CURSOR

    /**
     * Prepends an older chronological page (`?before=`) ahead of [existing],
     * deduplicating by message ID and maintaining oldest-first chronological order.
     */
    fun mergeOlderMessages(
        existing: List<ChatMessageDto>,
        older: List<ChatMessageDto>,
    ): List<ChatMessageDto> {
        if (older.isEmpty()) return existing
        val map = LinkedHashMap<String, ChatMessageDto>(older.size + existing.size)
        for (m in older) {
            if (m.id.isNotBlank()) map[m.id] = m
        }
        for (m in existing) {
            if (m.id.isNotBlank() && !map.containsKey(m.id)) {
                map[m.id] = m
            }
        }
        return sortChronological(map.values)
    }

    /**
     * Merges incremental `/sync` or `?since=` updates into [existing]:
     * - Replaces any matching optimistic message (`client_msg_id`) with its server-confirmed row.
     * - Updates existing messages in-place (e.g. soft-delete tombstones, k5).
     * - Removes any IDs in [deletedIds].
     * - Preserves local `SENDING` / `FAILED` optimistic rows not yet acked.
     */
    fun applyIncrementalDelta(
        existing: List<ChatMessageDto>,
        incoming: List<ChatMessageDto>,
        deletedIds: List<String> = emptyList(),
    ): List<ChatMessageDto> {
        if (incoming.isEmpty() && deletedIds.isEmpty()) return existing
        val deletedSet = deletedIds.toHashSet()
        val incomingClientIds = incoming.mapNotNull { it.client_msg_id?.takeIf(String::isNotBlank) }.toHashSet()
        val map = LinkedHashMap<String, ChatMessageDto>(existing.size + incoming.size)

        for (m in existing) {
            if (m.id.isBlank() || m.id in deletedSet) continue
            val cid = m.client_msg_id
            if (m.delivery_state != ChatDeliveryState.SENT && !cid.isNullOrBlank() && cid in incomingClientIds) {
                continue
            }
            map[m.id] = m
        }
        for (m in incoming) {
            if (m.id.isBlank() || m.id in deletedSet) continue
            map[m.id] = m.copy(delivery_state = ChatDeliveryState.SENT, local_error = null)
        }
        return sortChronological(map.values)
    }

    private fun sortChronological(items: Iterable<ChatMessageDto>): List<ChatMessageDto> =
        items.sortedWith(
            compareBy<ChatMessageDto> {
                if (it.delivery_state == ChatDeliveryState.SENT) 0 else 1
            }.thenBy { it.created_at }.thenBy { it.id }
        )

    // ── Optimistic Send & Delivery Ticks (k3) ────────────────────────────────

    fun createOptimisticMessage(
        clientMsgId: String,
        channelType: String,
        threadId: String?,
        senderId: String,
        authorName: String,
        authorRole: String,
        text: String,
        replyTo: ChatMessageDto? = null,
        createdAt: String = Instant.now().toString(),
    ): ChatMessageDto = ChatMessageDto(
        id = "local-$clientMsgId",
        channel_type = channelType,
        thread_id = threadId,
        sender_id = senderId,
        is_me = true,
        author_name = authorName.ifBlank { "You" },
        author_role = authorRole,
        text = text,
        client_msg_id = clientMsgId,
        reply_to = replyTo?.let {
            ChatReplyPreviewDto(
                id = it.id,
                author_name = it.author_name,
                author_role = it.author_role,
                text = if (it.is_deleted) DELETED_MESSAGE_PLACEHOLDER else it.text.take(180),
                is_deleted = it.is_deleted,
            )
        },
        created_at = createdAt,
        updated_at = createdAt,
        delivery_state = ChatDeliveryState.SENDING,
        local_error = null,
    )

    fun markOptimisticSent(
        existing: List<ChatMessageDto>,
        clientMsgId: String,
        serverMessage: ChatMessageDto,
    ): List<ChatMessageDto> {
        val filtered = existing.filterNot {
            it.id == "local-$clientMsgId" ||
                (it.client_msg_id == clientMsgId && it.delivery_state != ChatDeliveryState.SENT)
        }
        return applyIncrementalDelta(filtered, listOf(serverMessage), emptyList())
    }

    fun markOptimisticFailed(
        existing: List<ChatMessageDto>,
        clientMsgId: String,
        error: String,
    ): List<ChatMessageDto> = existing.map { m ->
        if (m.id == "local-$clientMsgId" || m.client_msg_id == clientMsgId) {
            m.copy(delivery_state = ChatDeliveryState.FAILED, local_error = error)
        } else {
            m
        }
    }

    fun markOptimisticRetrying(
        existing: List<ChatMessageDto>,
        clientMsgId: String,
    ): List<ChatMessageDto> = existing.map { m ->
        if (m.id == "local-$clientMsgId" || m.client_msg_id == clientMsgId) {
            m.copy(delivery_state = ChatDeliveryState.SENDING, local_error = null)
        } else {
            m
        }
    }

    fun removeOptimisticMessage(
        existing: List<ChatMessageDto>,
        clientMsgId: String,
    ): List<ChatMessageDto> = existing.filterNot {
        it.id == "local-$clientMsgId" || it.client_msg_id == clientMsgId
    }

    fun toPendingOutgoing(
        msg: ChatMessageDto,
    ): PendingChatOutgoing? {
        val cid = msg.client_msg_id?.takeIf { it.isNotBlank() } ?: return null
        return PendingChatOutgoing(
            client_msg_id = cid,
            channel_type = msg.channel_type,
            thread_id = msg.thread_id,
            text = msg.text,
            reply_to_id = msg.reply_to?.id,
            reply_preview = msg.reply_to,
            created_at = msg.created_at,
            author_name = msg.author_name,
            author_role = msg.author_role,
        )
    }

    fun fromPendingOutgoing(
        pending: PendingChatOutgoing,
        senderId: String,
    ): ChatMessageDto = ChatMessageDto(
        id = "local-${pending.client_msg_id}",
        channel_type = pending.channel_type,
        thread_id = pending.thread_id,
        sender_id = senderId,
        is_me = true,
        author_name = pending.author_name,
        author_role = pending.author_role,
        text = pending.text,
        client_msg_id = pending.client_msg_id,
        reply_to = pending.reply_preview,
        created_at = pending.created_at,
        updated_at = pending.created_at,
        delivery_state = ChatDeliveryState.FAILED,
        local_error = "Waiting for network — tap Retry",
    )

    // ── Swipe-to-Reply & Jump (k4 + j5) ──────────────────────────────────────

    fun canSwipeToReply(msg: ChatMessageDto): Boolean =
        !msg.is_deleted && msg.delivery_state == ChatDeliveryState.SENT && msg.id.isNotBlank()

    fun findReplyTargetIndex(messages: List<ChatMessageDto>, replyToId: String?): Int {
        if (replyToId.isNullOrBlank()) return -1
        return messages.indexOfFirst { it.id == replyToId }
    }

    // ── Soft Delete & Safe Display (k5) ──────────────────────────────────────

    fun canDeleteMessage(msg: ChatMessageDto, isAdminViewer: Boolean): Boolean {
        if (msg.is_deleted || msg.delivery_state != ChatDeliveryState.SENT || msg.id.isBlank()) {
            return false
        }
        return isAdminViewer || msg.is_me
    }

    fun displayMessageText(msg: ChatMessageDto, isAdminViewer: Boolean): String {
        if (!msg.is_deleted) return msg.text
        if (isAdminViewer && msg.text.isNotBlank() && msg.text != DELETED_MESSAGE_PLACEHOLDER) {
            return "[Deleted] ${msg.text}"
        }
        return DELETED_MESSAGE_PLACEHOLDER
    }

    fun displayReplyPreviewText(reply: ChatReplyPreviewDto, isAdminViewer: Boolean): String {
        if (!reply.is_deleted) return reply.text
        if (isAdminViewer && reply.text.isNotBlank() && reply.text != DELETED_MESSAGE_PLACEHOLDER) {
            return "[Deleted] ${reply.text}"
        }
        return DELETED_MESSAGE_PLACEHOLDER
    }

    fun applyLocalSoftDelete(
        existing: List<ChatMessageDto>,
        messageId: String,
        serverMessage: ChatMessageDto? = null,
        isAdminViewer: Boolean = false,
        deletedAt: String = Instant.now().toString(),
    ): List<ChatMessageDto> = existing.map { m ->
        val updatedSelf = if (m.id == messageId) {
            serverMessage?.copy(delivery_state = ChatDeliveryState.SENT, local_error = null)
                ?: m.copy(
                    is_deleted = true,
                    deleted_at = deletedAt,
                    text = if (isAdminViewer) m.text else DELETED_MESSAGE_PLACEHOLDER,
                )
        } else {
            m
        }
        val r = updatedSelf.reply_to
        if (r != null && r.id == messageId) {
            updatedSelf.copy(
                reply_to = r.copy(
                    is_deleted = true,
                    text = if (isAdminViewer) r.text else DELETED_MESSAGE_PLACEHOLDER,
                )
            )
        } else {
            updatedSelf
        }
    }

    // ── Shared Leadership Inbox & Badges (k6) ────────────────────────────────

    fun formatUnreadBadge(count: Int): String? = when {
        count <= 0 -> null
        count > 99 -> "99+"
        else -> count.toString()
    }

    /**
     * Members can ALWAYS compose in their shared ForgeHouse Leadership inbox
     * unless muted (k6). Admins can compose in any selected member thread.
     */
    fun canComposeDirect(
        isAdmin: Boolean,
        isMuted: Boolean,
        hasActiveThread: Boolean = true,
    ): Boolean {
        if (isAdmin) return hasActiveThread
        return !isMuted
    }

    fun canComposeGroup(
        isAdmin: Boolean,
        isMuted: Boolean,
    ): Boolean {
        if (isAdmin) return true
        return !isMuted
    }

    fun memberDirectHeaderTitle(): String = "ForgeHouse Leadership"

    fun memberLeadershipSubtitle(threads: List<ChatThreadDto>): String =
        threads.firstOrNull()?.subtitle?.takeIf { it.isNotBlank() }
            ?: "Private chat with pastors & leaders"

    fun sanitizeChatError(
        error: Throwable,
        fallback: String = "Could not complete chat action. Please try again.",
    ): String {
        if (error is CancellationException) throw error
        return ReaderErrorSanitizer.toUserMessage(error, fallback) ?: fallback
    }
}

package com.forgebuild.forgehouse50.ui.community

import com.forgebuild.forgehouse50.data.ChatMessageDto
import com.forgebuild.forgehouse50.data.ReaderErrorSanitizer
import kotlinx.coroutines.CancellationException

/**
 * Pure state-transition and validation helpers for Community Chat (i9).
 * Kept Android-free so JUnit tests can verify pagination, delta merges,
 * soft-delete propagation, direct-thread initiation rules, and badge labels.
 */
object CommunityChatLogic {
    const val MAX_MESSAGE_CHARS = 1000
    const val FOREGROUND_POLL_INTERVAL_MS = 4_000L

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
        return map.values.sortedWith(compareBy<ChatMessageDto> { it.created_at }.thenBy { it.id })
    }

    /**
     * Applies an incremental `?since=` poll response:
     * - Removes any messages whose IDs appear in [deletedIds]
     * - Appends/updates [incoming] messages deduplicated by ID
     * - Preserves oldest-at-top / newest-at-bottom chronological order
     */
    fun applyIncrementalDelta(
        existing: List<ChatMessageDto>,
        incoming: List<ChatMessageDto>,
        deletedIds: List<String>,
    ): List<ChatMessageDto> {
        if (incoming.isEmpty() && deletedIds.isEmpty()) return existing
        val deletedSet = deletedIds.toHashSet()
        val map = LinkedHashMap<String, ChatMessageDto>(existing.size + incoming.size)
        for (m in existing) {
            if (m.id.isNotBlank() && m.id !in deletedSet) {
                map[m.id] = m
            }
        }
        for (m in incoming) {
            if (m.id.isNotBlank() && m.id !in deletedSet) {
                map[m.id] = m
            }
        }
        return map.values.sortedWith(compareBy<ChatMessageDto> { it.created_at }.thenBy { it.id })
    }

    fun formatUnreadBadge(count: Int): String? = when {
        count <= 0 -> null
        count > 99 -> "99+"
        else -> count.toString()
    }

    /**
     * Members can compose in Direct Messages ONLY after an admin has started
     * their private thread, and only when not muted. Admins can always compose.
     */
    fun canComposeDirect(
        isAdmin: Boolean,
        isMuted: Boolean,
        threadStartedByAdmin: Boolean,
    ): Boolean {
        if (isAdmin) return true
        return !isMuted && threadStartedByAdmin
    }

    fun canComposeGroup(
        isAdmin: Boolean,
        isMuted: Boolean,
    ): Boolean {
        if (isAdmin) return true
        return !isMuted
    }

    fun sanitizeChatError(error: Throwable, fallback: String = "Could not complete chat action. Please try again."): String {
        if (error is CancellationException) throw error
        return ReaderErrorSanitizer.toUserMessage(error, fallback) ?: fallback
    }
}

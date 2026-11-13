package com.forgebuild.forgehouse50.work

import com.forgebuild.forgehouse50.data.ChatMessageDto
import com.forgebuild.forgehouse50.data.ChatSendResponse
import com.forgebuild.forgehouse50.data.ChatSyncResponse
import com.forgebuild.forgehouse50.data.PendingChatOutgoing
import com.forgebuild.forgehouse50.ui.community.CommunityChatLogic
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/**
 * Pure, JVM-testable business logic for WhatsApp-style background chat notifications (k7).
 * Uses zero Firebase/FCM dependencies — extends the existing KeepAliveService polling loop.
 */
object ChatNotificationLogic {
    // Separate user-configurable notification channels (Amendment A2)
    const val CHANNEL_CHAT_GROUP = "chat_group"
    const val CHANNEL_CHAT_DIRECT = "chat_direct"
    const val CHANNEL_REMINDERS = "daily_reading"
    const val CHANNEL_UPDATES = "app_updates"

    const val NOTIF_GROUP_CHAT = "fh50_chat_messages"
    const val SUMMARY_NOTIFICATION_ID = 5099
    const val GROUP_NOTIFICATION_ID = 5101
    private const val DIRECT_NOTIFICATION_ID_BASE = 5200

    // Tuned for Cloudflare Workers Free tier (~150 active members, docs/CHAT_ARCHITECTURE.md)
    const val DAY_INTERVAL_MS = 300_000L       // 5 min (06:00-23:00 Africa/Lagos)
    const val NIGHT_INTERVAL_MS = 900_000L     // 15 min (23:00-06:00 Africa/Lagos)
    const val BURST_INTERVAL_MS = 30_000L      // 30s active burst
    const val BURST_WINDOW_MS = 120_000L       // 2 min active burst window
    const val MAX_LINES_PER_CONVERSATION = 8

    const val ACTION_INLINE_REPLY = "com.forgebuild.forgehouse50.ACTION_CHAT_INLINE_REPLY"
    const val ACTION_MARK_READ = "com.forgebuild.forgehouse50.ACTION_CHAT_MARK_READ"
    const val ACTION_RESTART_KEEPALIVE = "com.forgebuild.forgehouse50.ACTION_RESTART_KEEPALIVE"

    const val EXTRA_CHANNEL_TYPE = "extra_channel_type"
    const val EXTRA_THREAD_ID = "extra_thread_id"
    const val EXTRA_CONVERSATION_KEY = "extra_conversation_key"
    const val EXTRA_LATEST_TS = "extra_latest_ts"
    const val EXTRA_TITLE = "extra_title"
    const val KEY_REMOTE_REPLY_TEXT = "key_remote_reply_text"

    private val AGGRESSIVE_OEM_TOKENS = setOf(
        "tecno", "infinix", "itel", "transsion",
        "xiaomi", "redmi", "poco",
        "oppo", "realme", "vivo", "iqoo", "oneplus",
        "huawei", "honor",
    )

    data class NotificationEntry(
        val id: String,
        val senderName: String,
        val senderRole: String,
        val text: String,
        val createdAt: String,
        val timestampMs: Long,
        val isFromMe: Boolean = false,
    )

    data class ConversationBatch(
        val conversationKey: String,
        val notificationId: Int,
        val channelType: String,
        val threadId: String?,
        val title: String,
        val deepLinkUri: String,
        val messages: List<ChatMessageDto>,
    )

    data class FilteredChatNotifications(
        val batches: List<ConversationBatch>,
        val deletedIds: Set<String>,
        val suppressedConversationKeys: Set<String>,
    )

    data class BackgroundCycleOutcome(
        val stoppedLoggedOut: Boolean = false,
        val notifiedBatches: List<ConversationBatch> = emptyList(),
        val deletedIds: Set<String> = emptySet(),
        val pendingDrainedCount: Int = 0,
        val triggeredBurst: Boolean = false,
        val nextDelayMs: Long = DAY_INTERVAL_MS,
        val hadNetworkError: Boolean = false,
    )

    interface BackgroundChatGateway {
        suspend fun baselineCursor(): ChatSyncResponse
        suspend fun syncBackground(groupSince: String?, directSince: String?): ChatSyncResponse
        suspend fun sendPending(item: PendingChatOutgoing): ChatSendResponse
    }

    interface BackgroundChatStorage {
        var isLoggedIn: Boolean
        var userId: String?
        var isAdmin: Boolean
        var bgGroupCursor: String?
        var bgDirectCursor: String?
        var bgBurstUntilMs: Long
        var muteGroupNotifications: Boolean
        fun loadPendingOutgoing(): List<PendingChatOutgoing>
        fun removePendingOutgoing(clientMsgId: String)
        fun clearChatBackgroundState()
    }

    fun directNotificationId(threadId: String): Int =
        DIRECT_NOTIFICATION_ID_BASE + ((threadId.hashCode() and 0x7FFFFFFF) % 500)

    fun conversationKey(channelType: String, threadId: String?): String =
        if (channelType == "group") "group" else "direct:${threadId.orEmpty()}"

    fun isWithinDaylightLagos(
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getTimeZone("Africa/Lagos"),
    ): Boolean {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in 6 until 23
    }

    fun nextBackgroundIntervalMs(
        nowMs: Long,
        burstUntilMs: Long,
        timeZone: TimeZone = TimeZone.getTimeZone("Africa/Lagos"),
    ): Long = when {
        nowMs < burstUntilMs -> BURST_INTERVAL_MS
        isWithinDaylightLagos(nowMs, timeZone) -> DAY_INTERVAL_MS
        else -> NIGHT_INTERVAL_MS
    }

    fun parseTimestampMillis(iso: String, fallbackMs: Long): Long =
        runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(fallbackMs)

    fun buildDeepLinkUri(channelType: String, threadId: String?): String =
        if (channelType == "group") {
            "fh50://community?tab=group"
        } else if (!threadId.isNullOrBlank()) {
            "fh50://community?tab=direct&thread_id=$threadId"
        } else {
            "fh50://community?tab=direct"
        }

    fun buildSenderDisplayName(msg: ChatMessageDto): String {
        val base = msg.author_name.ifBlank { if (msg.author_role == "admin") "Leader" else "Member" }
        return if (msg.author_role == "admin" && !base.contains("Leader", ignoreCase = true)) {
            "$base (Leader)"
        } else {
            base
        }
    }

    fun buildConversationTitle(
        channelType: String,
        firstIncoming: ChatMessageDto?,
        isAdminViewer: Boolean,
    ): String = when {
        channelType == "group" -> "Community Group Chat"
        !isAdminViewer -> "ForgeHouse Leadership"
        else -> firstIncoming?.thread_title?.takeIf { it.isNotBlank() }
            ?: firstIncoming?.author_name?.takeIf { it.isNotBlank() }
            ?: "Member"
    }

    fun isThreadForegroundActive(
        channelType: String,
        threadId: String?,
        isAdminViewer: Boolean,
        activeChannel: String?,
        activeThreadId: String?,
    ): Boolean {
        if (activeChannel == null || activeChannel != channelType) return false
        if (channelType == "group") return true
        return if (!isAdminViewer) {
            true
        } else {
            !threadId.isNullOrBlank() && threadId == activeThreadId
        }
    }

    fun filterNotifiableMessages(
        sync: ChatSyncResponse,
        selfUserId: String,
        isAdmin: Boolean,
        muteGroupNotifications: Boolean,
        activeForegroundChannel: String?,
        activeForegroundThreadId: String?,
    ): FilteredChatNotifications {
        val deletedIds = HashSet<String>()
        for (m in sync.group_messages) {
            if (m.is_deleted && m.id.isNotBlank()) deletedIds.add(m.id)
        }
        for (m in sync.direct_messages) {
            if (m.is_deleted && m.id.isNotBlank()) deletedIds.add(m.id)
        }

        val suppressedKeys = HashSet<String>()
        val batches = mutableListOf<ConversationBatch>()

        val groupForeground = isThreadForegroundActive(
            channelType = "group",
            threadId = null,
            isAdminViewer = isAdmin,
            activeChannel = activeForegroundChannel,
            activeThreadId = activeForegroundThreadId,
        )
        if (groupForeground) {
            suppressedKeys.add("group")
        } else if (!muteGroupNotifications) {
            val validGroup = sync.group_messages.filter { m ->
                m.id.isNotBlank() &&
                    !m.is_me &&
                    (selfUserId.isBlank() || m.sender_id != selfUserId) &&
                    !m.is_deleted &&
                    m.text.isNotBlank() &&
                    m.text != CommunityChatLogic.DELETED_MESSAGE_PLACEHOLDER
            }
            if (validGroup.isNotEmpty()) {
                batches += ConversationBatch(
                    conversationKey = "group",
                    notificationId = GROUP_NOTIFICATION_ID,
                    channelType = "group",
                    threadId = null,
                    title = "Community Group Chat",
                    deepLinkUri = buildDeepLinkUri("group", null),
                    messages = validGroup,
                )
            }
        }

        // Direct messages are NEVER muted by muteGroupNotifications (Amendment A2).
        val directGroups = LinkedHashMap<String, MutableList<ChatMessageDto>>()
        for (m in sync.direct_messages) {
            if (m.id.isBlank() || m.is_me || (selfUserId.isNotBlank() && m.sender_id == selfUserId)) continue
            if (m.is_deleted || m.text.isBlank() || m.text == CommunityChatLogic.DELETED_MESSAGE_PLACEHOLDER) continue
            val tId = m.thread_id?.takeIf { it.isNotBlank() } ?: if (isAdmin) m.sender_id else selfUserId
            if (tId.isBlank()) continue
            directGroups.getOrPut(tId) { mutableListOf() }.add(m)
        }

        for ((threadId, msgs) in directGroups) {
            val key = conversationKey("direct", threadId)
            val directForeground = isThreadForegroundActive(
                channelType = "direct",
                threadId = threadId,
                isAdminViewer = isAdmin,
                activeChannel = activeForegroundChannel,
                activeThreadId = activeForegroundThreadId,
            )
            if (directForeground) {
                suppressedKeys.add(key)
                continue
            }
            val title = buildConversationTitle("direct", msgs.firstOrNull(), isAdmin)
            batches += ConversationBatch(
                conversationKey = key,
                notificationId = directNotificationId(threadId),
                channelType = "direct",
                threadId = threadId,
                title = title,
                deepLinkUri = buildDeepLinkUri("direct", threadId),
                messages = msgs,
            )
        }

        return FilteredChatNotifications(
            batches = batches,
            deletedIds = deletedIds,
            suppressedConversationKeys = suppressedKeys,
        )
    }

    fun appendAndScrubEntries(
        existing: List<NotificationEntry>,
        incoming: List<ChatMessageDto>,
        deletedIds: Set<String>,
        fallbackNowMs: Long,
    ): List<NotificationEntry> {
        val byId = LinkedHashMap<String, NotificationEntry>()
        for (e in existing) {
            if (e.id !in deletedIds) byId[e.id] = e
        }
        for (m in incoming) {
            if (m.id.isBlank() || m.id in deletedIds || m.is_deleted) continue
            if (m.text.isBlank() || m.text == CommunityChatLogic.DELETED_MESSAGE_PLACEHOLDER) continue
            byId[m.id] = NotificationEntry(
                id = m.id,
                senderName = buildSenderDisplayName(m),
                senderRole = m.author_role,
                text = m.text,
                createdAt = m.created_at,
                timestampMs = parseTimestampMillis(m.created_at, fallbackNowMs),
                isFromMe = m.is_me,
            )
        }
        return byId.values
            .sortedWith(compareBy<NotificationEntry> { it.timestampMs }.thenBy { it.id })
            .takeLast(MAX_LINES_PER_CONVERSATION)
    }

    fun buildOfflineFallbackOutgoing(
        clientMsgId: String,
        channelType: String,
        threadId: String?,
        text: String,
        authorName: String,
        authorRole: String,
        createdAt: String,
    ): PendingChatOutgoing = PendingChatOutgoing(
        client_msg_id = clientMsgId,
        channel_type = channelType,
        thread_id = threadId,
        text = text.trim().take(CommunityChatLogic.MAX_MESSAGE_CHARS),
        reply_to_id = null,
        reply_preview = null,
        created_at = createdAt,
        author_name = authorName.ifBlank { "You" },
        author_role = authorRole.ifBlank { "member" },
    )

    fun isAggressiveBackgroundOem(manufacturer: String, brand: String = ""): Boolean {
        val combined = "${manufacturer.lowercase()} ${brand.lowercase()}"
        return AGGRESSIVE_OEM_TOKENS.any { token -> combined.contains(token) }
    }

    /**
     * Executes one KeepAliveService background polling + pending-drain cycle (k7).
     * Handles cursor bootstrap without historical spam, logout cleanup, transient errors,
     * and 30s active burst activation.
     */
    suspend fun runBackgroundCycle(
        gateway: BackgroundChatGateway,
        storage: BackgroundChatStorage,
        activeForegroundChannel: String?,
        activeForegroundThreadId: String?,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getTimeZone("Africa/Lagos"),
        onNotify: (FilteredChatNotifications) -> Unit = {},
        onClearAll: () -> Unit = {},
    ): BackgroundCycleOutcome {
        if (!storage.isLoggedIn) {
            storage.clearChatBackgroundState()
            onClearAll()
            return BackgroundCycleOutcome(stoppedLoggedOut = true, nextDelayMs = 0L)
        }

        var drained = 0
        var triggerBurst = false

        val pending = storage.loadPendingOutgoing()
        for (item in pending) {
            try {
                gateway.sendPending(item)
                storage.removePendingOutgoing(item.client_msg_id)
                drained++
                triggerBurst = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Keep queued in PendingChatStore; retry on next cycle
            }
        }

        var hadError = false
        var notified = emptyList<ConversationBatch>()
        var deletedIds = emptySet<String>()

        try {
            val gCur = storage.bgGroupCursor
            val dCur = storage.bgDirectCursor
            if (gCur.isNullOrBlank() || dCur.isNullOrBlank()) {
                // First run after login/install: establish baseline cursor silently without notification flood
                val base = gateway.baselineCursor()
                val cursor = base.server_time.takeIf { it.isNotBlank() } ?: Instant.ofEpochMilli(nowMs).toString()
                if (storage.bgGroupCursor.isNullOrBlank()) storage.bgGroupCursor = cursor
                if (storage.bgDirectCursor.isNullOrBlank()) storage.bgDirectCursor = cursor
            } else {
                val sync = gateway.syncBackground(gCur, dCur)
                sync.group_last_ts?.takeIf { it.isNotBlank() }?.let { storage.bgGroupCursor = it }
                sync.direct_last_ts?.takeIf { it.isNotBlank() }?.let { storage.bgDirectCursor = it }

                val filtered = filterNotifiableMessages(
                    sync = sync,
                    selfUserId = storage.userId.orEmpty(),
                    isAdmin = storage.isAdmin,
                    muteGroupNotifications = storage.muteGroupNotifications,
                    activeForegroundChannel = activeForegroundChannel,
                    activeForegroundThreadId = activeForegroundThreadId,
                )
                notified = filtered.batches
                deletedIds = filtered.deletedIds
                if (filtered.batches.isNotEmpty() || filtered.deletedIds.isNotEmpty() || filtered.suppressedConversationKeys.isNotEmpty()) {
                    onNotify(filtered)
                }
                if (filtered.batches.isNotEmpty()) {
                    triggerBurst = true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            hadError = true
        }

        if (triggerBurst) {
            val targetUntil = nowMs + BURST_WINDOW_MS
            if (targetUntil > storage.bgBurstUntilMs) {
                storage.bgBurstUntilMs = targetUntil
            }
        }

        val nextDelay = nextBackgroundIntervalMs(nowMs, storage.bgBurstUntilMs, timeZone)
        return BackgroundCycleOutcome(
            stoppedLoggedOut = false,
            notifiedBatches = notified,
            deletedIds = deletedIds,
            pendingDrainedCount = drained,
            triggeredBurst = triggerBurst,
            nextDelayMs = nextDelay,
            hadNetworkError = hadError,
        )
    }
}

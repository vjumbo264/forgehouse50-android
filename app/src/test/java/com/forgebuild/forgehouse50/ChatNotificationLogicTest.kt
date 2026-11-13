package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.ChatMessageDto
import com.forgebuild.forgehouse50.data.ChatSendResponse
import com.forgebuild.forgehouse50.data.ChatSyncResponse
import com.forgebuild.forgehouse50.data.PendingChatOutgoing
import com.forgebuild.forgehouse50.ui.community.CommunityChatLogic
import com.forgebuild.forgehouse50.work.ChatNotificationLogic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.time.ZoneId

class ChatNotificationLogicTest {

    private fun msg(
        id: String,
        channel: String = "group",
        threadId: String? = null,
        senderId: String = "u-other",
        authorName: String = "Ada O.",
        authorRole: String = "member",
        text: String = "Grace and peace",
        isMe: Boolean = false,
        isDeleted: Boolean = false,
        createdAt: String = "2026-11-13T10:00:00Z",
        threadTitle: String? = null,
    ) = ChatMessageDto(
        id = id,
        channel_type = channel,
        thread_id = threadId,
        sender_id = senderId,
        is_me = isMe,
        author_name = authorName,
        author_role = authorRole,
        text = text,
        is_deleted = isDeleted,
        created_at = createdAt,
        thread_title = threadTitle,
    )

    @Test
    fun `nextBackgroundIntervalMs uses 5m day, 15m night Lagos, and 30s active burst`() {
        val lagos = ZoneId.of("Africa/Lagos")
        val day14h = ZonedDateTime.of(2026, 11, 13, 14, 0, 0, 0, lagos).toInstant().toEpochMilli()
        val night02h = ZonedDateTime.of(2026, 11, 13, 2, 0, 0, 0, lagos).toInstant().toEpochMilli()
        val night23h = ZonedDateTime.of(2026, 11, 13, 23, 30, 0, 0, lagos).toInstant().toEpochMilli()

        assertEquals(300_000L, ChatNotificationLogic.nextBackgroundIntervalMs(day14h, burstUntilMs = 0L))
        assertEquals(900_000L, ChatNotificationLogic.nextBackgroundIntervalMs(night02h, burstUntilMs = 0L))
        assertEquals(900_000L, ChatNotificationLogic.nextBackgroundIntervalMs(night23h, burstUntilMs = 0L))
        assertEquals(30_000L, ChatNotificationLogic.nextBackgroundIntervalMs(night02h, burstUntilMs = night02h + 60_000L))
    }

    @Test
    fun `filterNotifiableMessages groups per conversation, suppresses foreground thread, and never mutes leader DMs`() {
        val sync = ChatSyncResponse(
            group_messages = listOf(
                msg("g1", channel = "group", senderId = "u1", text = "Morning everyone!"),
                msg("g-me", channel = "group", senderId = "self", text = "My own", isMe = true),
            ),
            direct_messages = listOf(
                msg("d1", channel = "direct", threadId = "u-self", senderId = "leader-1", authorName = "Pastor Dave", authorRole = "admin", text = "How is your reading going?", threadTitle = "ForgeHouse Leadership"),
                msg("d2", channel = "direct", threadId = "u-other-thread", senderId = "u-other-thread", authorName = "Bola", authorRole = "member", text = "Question on Mark 4", threadTitle = "Bola"),
            ),
        )

        // When group chat is muted, group batch is omitted, but direct messages from leaders ALWAYS notify
        val mutedGroupResult = ChatNotificationLogic.filterNotifiableMessages(
            sync = sync,
            selfUserId = "u-self",
            isAdmin = false,
            muteGroupNotifications = true,
            activeForegroundChannel = null,
            activeForegroundThreadId = null,
        )
        assertTrue(mutedGroupResult.batches.none { it.channelType == "group" })
        val directBatch = mutedGroupResult.batches.first { it.channelType == "direct" }
        assertEquals("fh50://community?tab=direct&thread_id=u-self", directBatch.deepLinkUri)
        assertEquals("ForgeHouse Leadership", directBatch.title)

        // When user has group chat open in foreground, group is suppressed while direct still notifies
        val fgGroupResult = ChatNotificationLogic.filterNotifiableMessages(
            sync = sync,
            selfUserId = "u-self",
            isAdmin = false,
            muteGroupNotifications = false,
            activeForegroundChannel = "group",
            activeForegroundThreadId = null,
        )
        assertTrue("group" in fgGroupResult.suppressedConversationKeys)
        assertTrue(fgGroupResult.batches.none { it.channelType == "group" })
        assertTrue(fgGroupResult.batches.any { it.channelType == "direct" })

        // Admin viewing thread u-self suppresses u-self, still receives Bola's thread notification
        val fgAdminDirect = ChatNotificationLogic.filterNotifiableMessages(
            sync = sync,
            selfUserId = "admin-1",
            isAdmin = true,
            muteGroupNotifications = false,
            activeForegroundChannel = "direct",
            activeForegroundThreadId = "u-self",
        )
        assertTrue("direct:u-self" in fgAdminDirect.suppressedConversationKeys)
        assertEquals(listOf("group", "direct:u-other-thread"), fgAdminDirect.batches.map { it.conversationKey })
        assertNotEquals(
            ChatNotificationLogic.directNotificationId("u-self"),
            ChatNotificationLogic.directNotificationId("u-other-thread"),
        )
    }

    @Test
    fun `deleted messages never appear in notifications and scrub previously cached notification entries`() {
        val syncWithDeleted = ChatSyncResponse(
            group_messages = listOf(
                msg("g-del", channel = "group", text = "Private mistake", isDeleted = true),
                msg("g-ok", channel = "group", text = "Encouragement"),
            ),
        )
        val filtered = ChatNotificationLogic.filterNotifiableMessages(
            sync = syncWithDeleted,
            selfUserId = "u-self",
            isAdmin = false,
            muteGroupNotifications = false,
            activeForegroundChannel = null,
            activeForegroundThreadId = null,
        )
        assertTrue("g-del" in filtered.deletedIds)
        val batchMsgs = filtered.batches.single().messages
        assertEquals(listOf("g-ok"), batchMsgs.map { it.id })

        val priorEntries = listOf(
            ChatNotificationLogic.NotificationEntry("g-del", "Ada", "member", "Private mistake", "2026-11-13T10:00:00Z", 1_000L),
            ChatNotificationLogic.NotificationEntry("g-ok", "Ada", "member", "Encouragement", "2026-11-13T10:01:00Z", 2_000L),
        )
        val scrubbed = ChatNotificationLogic.appendAndScrubEntries(priorEntries, emptyList(), setOf("g-del"), 3_000L)
        assertEquals(listOf("g-ok"), scrubbed.map { it.id })
        assertFalse(scrubbed.any { it.text.contains("Private mistake") })
    }

    @Test
    fun `offline inline reply fallback constructs valid queued outgoing item`() {
        val queued = ChatNotificationLogic.buildOfflineFallbackOutgoing(
            clientMsgId = "cid-notif-1",
            channelType = "direct",
            threadId = "u-self",
            text = "  Thank you Pastor!  ",
            authorName = "Chidi",
            authorRole = "member",
            createdAt = "2026-11-13T12:00:00Z",
        )
        assertEquals("Thank you Pastor!", queued.text)
        assertEquals("direct", queued.channel_type)
        assertEquals("u-self", queued.thread_id)
        assertEquals("cid-notif-1", queued.client_msg_id)
    }

    @Test
    fun `runBackgroundCycle initializes baseline silently, drains pending queue, notifies new msgs, and clears on logout`() = runBlocking {
        val pendingQueue = mutableListOf(
            PendingChatOutgoing(
                client_msg_id = "c-queued",
                channel_type = "group",
                text = "Queued reply",
                created_at = "2026-11-13T09:50:00Z",
            )
        )
        var clearedCalled = false
        val notifiedLog = mutableListOf<ChatNotificationLogic.FilteredChatNotifications>()

        val storage = object : ChatNotificationLogic.BackgroundChatStorage {
            override var isLoggedIn: Boolean = true
            override var userId: String? = "u-self"
            override var isAdmin: Boolean = false
            override var bgGroupCursor: String? = null
            override var bgDirectCursor: String? = null
            override var bgBurstUntilMs: Long = 0L
            override var muteGroupNotifications: Boolean = false
            override fun loadPendingOutgoing(): List<PendingChatOutgoing> = pendingQueue.toList()
            override fun removePendingOutgoing(clientMsgId: String) { pendingQueue.removeAll { it.client_msg_id == clientMsgId } }
            override fun clearChatBackgroundState() {
                bgGroupCursor = null
                bgDirectCursor = null
                bgBurstUntilMs = 0L
            }
        }

        val gateway = object : ChatNotificationLogic.BackgroundChatGateway {
            var nextSync = ChatSyncResponse(server_time = "2026-11-13T10:00:00Z")
            override suspend fun baselineCursor(): ChatSyncResponse =
                ChatSyncResponse(server_time = "2026-11-13T10:00:00Z")
            override suspend fun syncBackground(groupSince: String?, directSince: String?): ChatSyncResponse = nextSync
            override suspend fun sendPending(item: PendingChatOutgoing): ChatSendResponse =
                ChatSendResponse(ok = true, message = msg("srv-${item.client_msg_id}"))
        }

        val now1 = ZonedDateTime.of(2026, 11, 13, 10, 0, 0, 0, ZoneId.of("Africa/Lagos")).toInstant().toEpochMilli()

        // Cycle 1: initializes baseline cursors silently without spamming old messages, drains offline queue -> 30s burst
        val out1 = ChatNotificationLogic.runBackgroundCycle(
            gateway = gateway,
            storage = storage,
            activeForegroundChannel = null,
            activeForegroundThreadId = null,
            nowMs = now1,
            onNotify = { notifiedLog += it },
            onClearAll = { clearedCalled = true },
        )
        assertFalse(out1.stoppedLoggedOut)
        assertEquals(1, out1.pendingDrainedCount)
        assertTrue(pendingQueue.isEmpty())
        assertEquals("2026-11-13T10:00:00Z", storage.bgGroupCursor)
        assertEquals("2026-11-13T10:00:00Z", storage.bgDirectCursor)
        assertTrue(notifiedLog.isEmpty())
        assertEquals(30_000L, out1.nextDelayMs)

        // Cycle 2: after burst ends, receives new leader DM -> updates cursor, notifies, triggers burst again
        val now2 = now1 + 150_000L
        gateway.nextSync = ChatSyncResponse(
            server_time = "2026-11-13T10:02:30Z",
            group_last_ts = "2026-11-13T10:00:00Z",
            direct_last_ts = "2026-11-13T10:02:20Z",
            direct_messages = listOf(
                msg("d-new", channel = "direct", threadId = "u-self", senderId = "leader-1", authorName = "Pastor Dave", authorRole = "admin", text = "Welcome!")
            ),
        )
        val out2 = ChatNotificationLogic.runBackgroundCycle(
            gateway = gateway,
            storage = storage,
            activeForegroundChannel = null,
            activeForegroundThreadId = null,
            nowMs = now2,
            onNotify = { notifiedLog += it },
            onClearAll = { clearedCalled = true },
        )
        assertEquals(1, out2.notifiedBatches.size)
        assertEquals("2026-11-13T10:02:20Z", storage.bgDirectCursor)
        assertEquals(30_000L, out2.nextDelayMs)
        assertEquals(1, notifiedLog.size)

        // Cycle 3: user logs out -> clears cursors, invokes onClearAll, stops loop
        storage.isLoggedIn = false
        val out3 = ChatNotificationLogic.runBackgroundCycle(
            gateway = gateway,
            storage = storage,
            activeForegroundChannel = null,
            activeForegroundThreadId = null,
            nowMs = now2 + 5_000L,
            onNotify = { notifiedLog += it },
            onClearAll = { clearedCalled = true },
        )
        assertTrue(out3.stoppedLoggedOut)
        assertTrue(clearedCalled)
        assertNull(storage.bgGroupCursor)
        assertNull(storage.bgDirectCursor)
    }

    @Test
    fun `isAggressiveBackgroundOem flags Tecno, Infinix, Itel, Xiaomi and skips Pixel`() {
        assertTrue(ChatNotificationLogic.isAggressiveBackgroundOem("TECNO", "TECNO SPARK"))
        assertTrue(ChatNotificationLogic.isAggressiveBackgroundOem("INFINIX"))
        assertTrue(ChatNotificationLogic.isAggressiveBackgroundOem("itel"))
        assertTrue(ChatNotificationLogic.isAggressiveBackgroundOem("Xiaomi", "Redmi"))
        assertFalse(ChatNotificationLogic.isAggressiveBackgroundOem("Google", "Pixel 8"))
    }
}

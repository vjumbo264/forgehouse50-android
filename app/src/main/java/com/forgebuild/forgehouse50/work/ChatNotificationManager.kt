package com.forgebuild.forgehouse50.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.forgebuild.forgehouse50.MainActivity
import com.forgebuild.forgehouse50.R
import com.forgebuild.forgehouse50.data.ChatMessageDto
import com.forgebuild.forgehouse50.data.SessionStore
import com.forgebuild.forgehouse50.work.ChatNotificationLogic.NotificationEntry
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages grouped MessagingStyle notifications with inline RemoteInput reply and
 * Mark as Read actions across separate user-configurable channels (k7).
 */
object ChatNotificationManager {
    private val conversationHistory = ConcurrentHashMap<String, List<NotificationEntry>>()
    private val conversationMeta = ConcurrentHashMap<String, ConversationMeta>()

    private data class ConversationMeta(
        val notificationId: Int,
        val channelType: String,
        val threadId: String?,
        val title: String,
        val deepLinkUri: String,
        val latestTimestamp: String?,
    )

    fun ensureAllChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                ChatNotificationLogic.CHANNEL_CHAT_GROUP,
                "Community Group Chat",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Messages in the ForgeHouse 50 community group chat"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                ChatNotificationLogic.CHANNEL_CHAT_DIRECT,
                "Private Leader Messages",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Direct messages between members and ForgeHouse pastors & leaders"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                ChatNotificationLogic.CHANNEL_REMINDERS,
                "Daily Reading & Quotes",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Daily reading reminders and encouraging spiritual quotes"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                ChatNotificationLogic.CHANNEL_UPDATES,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications when a new version of ForgeHouse 50 is available"
            }
        )
    }

    fun applyFilteredUpdates(context: Context, filtered: ChatNotificationLogic.FilteredChatNotifications) {
        ensureAllChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val session = SessionStore(context)
        val selfName = session.userName?.takeIf { it.isNotBlank() } ?: "You"
        val nowMs = System.currentTimeMillis()

        // 1. Dismiss any conversation currently open in foreground
        for (suppressedKey in filtered.suppressedConversationKeys) {
            clearConversation(context, suppressedKey)
        }

        // 2. Scrub any soft-deleted messages from already displayed notifications (never leak deleted text)
        if (filtered.deletedIds.isNotEmpty()) {
            val batchKeys = filtered.batches.map { it.conversationKey }.toSet()
            for ((key, existing) in conversationHistory.entries.toList()) {
                if (existing.any { it.id in filtered.deletedIds }) {
                    val scrubbed = ChatNotificationLogic.appendAndScrubEntries(
                        existing = existing,
                        incoming = emptyList(),
                        deletedIds = filtered.deletedIds,
                        fallbackNowMs = nowMs,
                    )
                    if (scrubbed.isEmpty()) {
                        clearConversation(context, key)
                    } else {
                        conversationHistory[key] = scrubbed
                        if (key !in batchKeys) {
                            conversationMeta[key]?.let { meta ->
                                postConversationNotification(context, nm, key, meta, scrubbed, selfName)
                            }
                        }
                    }
                }
            }
        }

        // 3. Append new batches and post/update grouped MessagingStyle notifications
        for (batch in filtered.batches) {
            val prev = conversationHistory[batch.conversationKey].orEmpty()
            val updated = ChatNotificationLogic.appendAndScrubEntries(
                existing = prev,
                incoming = batch.messages,
                deletedIds = filtered.deletedIds,
                fallbackNowMs = nowMs,
            )
            if (updated.isEmpty()) continue
            conversationHistory[batch.conversationKey] = updated
            val meta = ConversationMeta(
                notificationId = batch.notificationId,
                channelType = batch.channelType,
                threadId = batch.threadId,
                title = batch.title,
                deepLinkUri = batch.deepLinkUri,
                latestTimestamp = batch.messages.lastOrNull()?.created_at ?: updated.lastOrNull()?.createdAt,
            )
            conversationMeta[batch.conversationKey] = meta
            postConversationNotification(context, nm, batch.conversationKey, meta, updated, selfName)
        }

        postOrClearSummary(context, nm)
    }

    fun appendReplyEcho(
        context: Context,
        conversationKey: String,
        channelType: String,
        threadId: String?,
        title: String,
        replyText: String,
        createdAt: String,
        queuedOffline: Boolean = false,
    ) {
        ensureAllChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val session = SessionStore(context)
        val selfName = session.userName?.takeIf { it.isNotBlank() } ?: "You"
        val prev = conversationHistory[conversationKey].orEmpty()
        val nowMs = System.currentTimeMillis()
        val displayReply = if (queuedOffline) "$replyText (Queued — will send when online)" else replyText
        val echo = NotificationEntry(
            id = "local-reply-$nowMs",
            senderName = selfName,
            senderRole = if (session.isAdmin) "admin" else "member",
            text = displayReply,
            createdAt = createdAt,
            timestampMs = nowMs,
            isFromMe = true,
        )
        val updated = (prev + echo).takeLast(ChatNotificationLogic.MAX_LINES_PER_CONVERSATION)
        conversationHistory[conversationKey] = updated
        val meta = conversationMeta[conversationKey] ?: ConversationMeta(
            notificationId = if (channelType == "group") {
                ChatNotificationLogic.GROUP_NOTIFICATION_ID
            } else {
                ChatNotificationLogic.directNotificationId(threadId.orEmpty())
            },
            channelType = channelType,
            threadId = threadId,
            title = title,
            deepLinkUri = ChatNotificationLogic.buildDeepLinkUri(channelType, threadId),
            latestTimestamp = createdAt,
        )
        conversationMeta[conversationKey] = meta
        postConversationNotification(context, nm, conversationKey, meta, updated, selfName)
        postOrClearSummary(context, nm)
    }

    fun clearConversation(context: Context, conversationKey: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val meta = conversationMeta.remove(conversationKey)
        conversationHistory.remove(conversationKey)
        val id = meta?.notificationId ?: when {
            conversationKey == "group" -> ChatNotificationLogic.GROUP_NOTIFICATION_ID
            conversationKey.startsWith("direct:") -> {
                val tid = conversationKey.removePrefix("direct:")
                if (tid.isNotBlank()) ChatNotificationLogic.directNotificationId(tid) else null
            }
            else -> null
        }
        if (id != null) {
            runCatching { nm.cancel(id) }
        }
        postOrClearSummary(context, nm)
    }

    fun cancelAll(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (meta in conversationMeta.values) {
            runCatching { nm.cancel(meta.notificationId) }
        }
        conversationHistory.clear()
        conversationMeta.clear()
        runCatching { nm.cancel(ChatNotificationLogic.GROUP_NOTIFICATION_ID) }
        runCatching { nm.cancel(ChatNotificationLogic.SUMMARY_NOTIFICATION_ID) }
    }

    private fun postConversationNotification(
        context: Context,
        nm: NotificationManager,
        conversationKey: String,
        meta: ConversationMeta,
        entries: List<NotificationEntry>,
        selfName: String,
    ) {
        val channelId = if (meta.channelType == "group") {
            ChatNotificationLogic.CHANNEL_CHAT_GROUP
        } else {
            ChatNotificationLogic.CHANNEL_CHAT_DIRECT
        }

        val mePerson = Person.Builder()
            .setName(selfName)
            .setKey("self")
            .build()

        val style = NotificationCompat.MessagingStyle(mePerson)
            .setConversationTitle(meta.title)
            .setGroupConversation(meta.channelType == "group")

        for (e in entries) {
            val senderPerson = if (e.isFromMe) {
                mePerson
            } else {
                Person.Builder()
                    .setName(e.senderName)
                    .setKey(e.senderName)
                    .build()
            }
            style.addMessage(e.text, e.timestampMs, senderPerson)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(meta.deepLinkUri)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPi = PendingIntent.getActivity(
            context,
            meta.notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val remoteInput = RemoteInput.Builder(ChatNotificationLogic.KEY_REMOTE_REPLY_TEXT)
            .setLabel("Reply")
            .build()

        val replyIntent = Intent(context, ChatNotificationActionReceiver::class.java).apply {
            action = ChatNotificationLogic.ACTION_INLINE_REPLY
            putExtra(ChatNotificationLogic.EXTRA_CHANNEL_TYPE, meta.channelType)
            putExtra(ChatNotificationLogic.EXTRA_THREAD_ID, meta.threadId ?: "")
            putExtra(ChatNotificationLogic.EXTRA_CONVERSATION_KEY, conversationKey)
            putExtra(ChatNotificationLogic.EXTRA_TITLE, meta.title)
            putExtra(ChatNotificationLogic.EXTRA_LATEST_TS, meta.latestTimestamp ?: "")
        }
        val replyFlags = if (Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val replyPi = PendingIntent.getBroadcast(
            context,
            meta.notificationId * 10 + 1,
            replyIntent,
            replyFlags,
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPi,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        val readIntent = Intent(context, ChatNotificationActionReceiver::class.java).apply {
            action = ChatNotificationLogic.ACTION_MARK_READ
            putExtra(ChatNotificationLogic.EXTRA_CHANNEL_TYPE, meta.channelType)
            putExtra(ChatNotificationLogic.EXTRA_THREAD_ID, meta.threadId ?: "")
            putExtra(ChatNotificationLogic.EXTRA_CONVERSATION_KEY, conversationKey)
            putExtra(ChatNotificationLogic.EXTRA_LATEST_TS, meta.latestTimestamp ?: "")
        }
        val readPi = PendingIntent.getBroadcast(
            context,
            meta.notificationId * 10 + 2,
            readIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Mark as read",
            readPi,
        ).build()

        val priority = if (meta.channelType == "direct") {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(priority)
            .setGroup(ChatNotificationLogic.NOTIF_GROUP_CHAT)
            .setContentIntent(tapPi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .addAction(replyAction)
            .addAction(markReadAction)
            .build()

        runCatching { nm.notify(meta.notificationId, notification) }
    }

    private fun postOrClearSummary(context: Context, nm: NotificationManager) {
        val activeCount = conversationHistory.size
        if (activeCount <= 1) {
            runCatching { nm.cancel(ChatNotificationLogic.SUMMARY_NOTIFICATION_ID) }
            return
        }
        val totalMsgs = conversationHistory.values.sumOf { it.size }
        val inbox = NotificationCompat.InboxStyle()
            .setBigContentTitle("$totalMsgs unread messages across $activeCount chats")
        for ((key, list) in conversationHistory) {
            val title = conversationMeta[key]?.title ?: "Chat"
            val last = list.lastOrNull() ?: continue
            inbox.addLine("$title: ${last.senderName}: ${last.text}")
        }
        val summaryTap = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("fh50://community?tab=group")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val summaryPi = PendingIntent.getActivity(
            context,
            ChatNotificationLogic.SUMMARY_NOTIFICATION_ID,
            summaryTap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val summary = NotificationCompat.Builder(context, ChatNotificationLogic.CHANNEL_CHAT_DIRECT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ForgeHouse 50 Chat")
            .setContentText("$totalMsgs new messages across $activeCount conversations")
            .setStyle(inbox)
            .setGroup(ChatNotificationLogic.NOTIF_GROUP_CHAT)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(summaryPi)
            .build()
        runCatching { nm.notify(ChatNotificationLogic.SUMMARY_NOTIFICATION_ID, summary) }
    }
}

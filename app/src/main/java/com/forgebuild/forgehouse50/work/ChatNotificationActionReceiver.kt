package com.forgebuild.forgehouse50.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.forgebuild.forgehouse50.ui.community.PendingChatStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Handles inline Reply (RemoteInput), Mark as Read, and KeepAliveService restart actions (k7).
 */
class ChatNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ChatNotificationLogic.ACTION_RESTART_KEEPALIVE -> {
                val repo = RepositoryProvider.get(context)
                if (repo.session.isLoggedIn) {
                    KeepAliveService.start(context)
                }
            }

            ChatNotificationLogic.ACTION_MARK_READ -> {
                val pendingResult = goAsync()
                val channelType = intent.getStringExtra(ChatNotificationLogic.EXTRA_CHANNEL_TYPE) ?: "group"
                val threadId = intent.getStringExtra(ChatNotificationLogic.EXTRA_THREAD_ID)?.takeIf { it.isNotBlank() }
                val conversationKey = intent.getStringExtra(ChatNotificationLogic.EXTRA_CONVERSATION_KEY)
                    ?: ChatNotificationLogic.conversationKey(channelType, threadId)
                val latestTs = intent.getStringExtra(ChatNotificationLogic.EXTRA_LATEST_TS)?.takeIf { it.isNotBlank() }

                ChatNotificationManager.clearConversation(context, conversationKey)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repo = RepositoryProvider.get(context)
                        if (repo.session.isLoggedIn) {
                            runCatching {
                                repo.api.markChatRead(
                                    channelType = channelType,
                                    threadId = if (channelType == "direct") threadId else null,
                                    timestamp = latestTs,
                                )
                            }
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ChatNotificationLogic.ACTION_INLINE_REPLY -> {
                val replyText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(ChatNotificationLogic.KEY_REMOTE_REPLY_TEXT)
                    ?.toString()
                    ?.trim()
                if (replyText.isNullOrBlank()) return

                val pendingResult = goAsync()
                val channelType = intent.getStringExtra(ChatNotificationLogic.EXTRA_CHANNEL_TYPE) ?: "group"
                val threadId = intent.getStringExtra(ChatNotificationLogic.EXTRA_THREAD_ID)?.takeIf { it.isNotBlank() }
                val conversationKey = intent.getStringExtra(ChatNotificationLogic.EXTRA_CONVERSATION_KEY)
                    ?: ChatNotificationLogic.conversationKey(channelType, threadId)
                val title = intent.getStringExtra(ChatNotificationLogic.EXTRA_TITLE) ?: "Chat"
                val latestTs = intent.getStringExtra(ChatNotificationLogic.EXTRA_LATEST_TS)?.takeIf { it.isNotBlank() }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repo = RepositoryProvider.get(context)
                        if (!repo.session.isLoggedIn) {
                            ChatNotificationManager.cancelAll(context)
                            return@launch
                        }
                        val clientMsgId = UUID.randomUUID().toString()
                        val nowIso = Instant.now().toString()
                        val sentOk = try {
                            if (channelType == "group") {
                                repo.api.sendGroupMessage(text = replyText, clientMsgId = clientMsgId)
                                runCatching { repo.api.markChatRead("group", timestamp = latestTs) }
                            } else {
                                val target = if (repo.session.isAdmin) threadId else null
                                repo.api.sendDirectMessage(
                                    text = replyText,
                                    targetUserId = target,
                                    clientMsgId = clientMsgId,
                                )
                                runCatching {
                                    repo.api.markChatRead(
                                        channelType = "direct",
                                        threadId = threadId ?: repo.session.userId,
                                        timestamp = latestTs,
                                    )
                                }
                            }
                            true
                        } catch (_: Throwable) {
                            val fallbackItem = ChatNotificationLogic.buildOfflineFallbackOutgoing(
                                clientMsgId = clientMsgId,
                                channelType = channelType,
                                threadId = if (channelType == "direct") (threadId ?: repo.session.userId) else null,
                                text = replyText,
                                authorName = repo.session.userName ?: "You",
                                authorRole = if (repo.session.isAdmin) "admin" else "member",
                                createdAt = nowIso,
                            )
                            PendingChatStore.upsert(repo.session, fallbackItem)
                            false
                        }
                        repo.session.bgChatBurstUntilMs = System.currentTimeMillis() + ChatNotificationLogic.BURST_WINDOW_MS
                        ChatNotificationManager.appendReplyEcho(
                            context = context,
                            conversationKey = conversationKey,
                            channelType = channelType,
                            threadId = threadId,
                            title = title,
                            replyText = replyText,
                            createdAt = nowIso,
                            queuedOffline = !sentOk,
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}

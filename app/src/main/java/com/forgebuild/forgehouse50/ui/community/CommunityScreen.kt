package com.forgebuild.forgehouse50.ui.community

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forgebuild.forgehouse50.data.ChatDeliveryState
import com.forgebuild.forgehouse50.data.ChatDirectoryMemberDto
import com.forgebuild.forgehouse50.data.ChatMessageDto
import com.forgebuild.forgehouse50.data.ChatThreadDto
import com.forgebuild.forgehouse50.data.ChatUnreadSummary
import com.forgebuild.forgehouse50.data.CommunityPost
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.WatTimeFormatter
import com.forgebuild.forgehouse50.ui.ExpressiveLoading
import com.forgebuild.forgehouse50.ui.UserAvatar
import com.forgebuild.forgehouse50.ui.noteTypeLabel
import com.forgebuild.forgehouse50.work.ChatNotificationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Tracks which chat conversation is currently visible in the foreground so
 * background notifications (k7) can suppress alerts for the active thread.
 */
object ActiveChatForegroundTracker {
    @Volatile var activeChannel: String? = null // "group" | "direct" | null
    @Volatile var activeThreadId: String? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    repo: Repository,
    onBack: () -> Unit,
    initialTab: Int = 0,
    initialThreadId: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isAdmin = repo.session.isAdmin
    val sessionToken = repo.session.sessionToken
    val selfUserId = repo.session.userId.orEmpty()
    val selfName = repo.session.userName?.takeIf { it.isNotBlank() } ?: "You"
    val selfRole = if (isAdmin) "admin" else "member"

    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var lastActivityAtMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }

    // Shared chat metadata
    var isMuted by remember { mutableStateOf(false) }
    var unreadSummary by remember { mutableStateOf(ChatUnreadSummary()) }

    // Group chat state
    var groupMessages by remember { mutableStateOf<List<ChatMessageDto>>(emptyList()) }
    var groupHasOlder by remember { mutableStateOf(false) }
    var groupOldestTs by remember { mutableStateOf<String?>(null) }
    var groupLastTs by remember { mutableStateOf<String?>(null) }
    var groupLoadState by remember { mutableStateOf(CommunityChatLogic.ThreadLoadState(isLoading = true)) }
    var groupLoadingOlder by remember { mutableStateOf(false) }
    var groupDraft by remember { mutableStateOf("") }
    var groupReplyTarget by remember { mutableStateOf<ChatMessageDto?>(null) }
    val groupListState = rememberLazyListState()

    // Direct chat state (k2 + k6 shared leadership inbox)
    var threads by remember { mutableStateOf<List<ChatThreadDto>>(emptyList()) }
    var memberDirectory by remember { mutableStateOf<List<ChatDirectoryMemberDto>>(emptyList()) }
    var threadsLoadState by remember { mutableStateOf(CommunityChatLogic.ThreadLoadState(isLoading = true)) }
    var activeDirectThreadId by remember {
        mutableStateOf<String?>(
            if (isAdmin) initialThreadId?.takeIf { it.isNotBlank() }
            else repo.session.userId
        )
    }
    var activeDirectTitle by remember {
        mutableStateOf(if (isAdmin) "Direct Message" else CommunityChatLogic.memberDirectHeaderTitle())
    }
    var activeDirectSubtitle by remember {
        mutableStateOf(if (isAdmin) "Visible only to leadership and this member" else "Private chat with pastors & leaders")
    }
    var directMessages by remember { mutableStateOf<List<ChatMessageDto>>(emptyList()) }
    var directHasOlder by remember { mutableStateOf(false) }
    var directOldestTs by remember { mutableStateOf<String?>(null) }
    var directLastTs by remember { mutableStateOf<String?>(null) }
    var directLoadState by remember { mutableStateOf(CommunityChatLogic.ThreadLoadState(isLoading = false)) }
    var directLoadingOlder by remember { mutableStateOf(false) }
    var directDraft by remember { mutableStateOf("") }
    var directReplyTarget by remember { mutableStateOf<ChatMessageDto?>(null) }
    var showMemberPicker by remember { mutableStateOf(false) }
    val directListState = rememberLazyListState()

    // Moderation & delete state (k5)
    var deleteConfirmTarget by remember { mutableStateOf<ChatMessageDto?>(null) }
    var mutedUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Reflections feed state
    var posts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var hasMorePosts by remember { mutableStateOf(false) }
    var postsLoading by remember { mutableStateOf(true) }
    var postsLoadingMore by remember { mutableStateOf(false) }
    val postsListState = rememberLazyListState()

    // Lifecycle tracking
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    fun restorePendingMessagesFor(channelType: String, threadId: String?): List<ChatMessageDto> {
        val pending = PendingChatStore.loadAll(repo.session)
        return pending.filter {
            it.channel_type == channelType &&
                (channelType == "group" || it.thread_id == threadId || (!isAdmin && it.thread_id == null))
        }.map { CommunityChatLogic.fromPendingOutgoing(it, selfUserId) }
    }

    suspend fun refreshThreadsMetadata(userInitiated: Boolean = false) {
        if (!userInitiated && threadsLoadState.attemptCount >= CommunityChatLogic.MAX_AUTO_LOAD_RETRIES && threadsLoadState.errorMessage != null) {
            return
        }
        threadsLoadState = threadsLoadState.copy(
            isLoading = true,
            attemptCount = if (userInitiated) 0 else threadsLoadState.attemptCount,
        )
        try {
            val res = repo.api.chatThreads()
            isMuted = res.is_muted
            unreadSummary = res.unread
            threads = res.threads
            memberDirectory = res.directory
            mutedUserIds = (res.directory.filter { it.is_muted }.map { it.user_id } +
                res.threads.filter { it.is_muted }.map { it.user_id }).toSet()
            if (!isAdmin) {
                val own = res.threads.firstOrNull()
                if (own != null) {
                    activeDirectThreadId = own.thread_id
                }
                activeDirectTitle = CommunityChatLogic.memberDirectHeaderTitle()
                activeDirectSubtitle = CommunityChatLogic.memberLeadershipSubtitle(res.threads)
            } else if (!activeDirectThreadId.isNullOrBlank()) {
                val matched = res.threads.firstOrNull { it.thread_id == activeDirectThreadId }
                    ?: res.directory.firstOrNull { it.user_id == activeDirectThreadId }?.let {
                        ChatThreadDto(thread_id = it.user_id, user_id = it.user_id, title = it.title)
                    }
                if (matched != null && matched.title.isNotBlank()) {
                    activeDirectTitle = matched.title
                }
            }
            threadsLoadState = CommunityChatLogic.onLoadSuccess(threadsLoadState)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            val msg = CommunityChatLogic.sanitizeChatError(e, "Could not load direct threads.")
            threadsLoadState = CommunityChatLogic.onLoadFailure(threadsLoadState, msg, System.currentTimeMillis())
        }
    }

    suspend fun loadGroupInitial(scrollToBottom: Boolean = true, userInitiated: Boolean = false) {
        groupLoadState = groupLoadState.copy(
            isLoading = true,
            errorMessage = if (userInitiated) null else groupLoadState.errorMessage,
            attemptCount = if (userInitiated) 0 else groupLoadState.attemptCount,
        )
        try {
            val res = repo.api.chatGroupMessages(limit = 50)
            val pending = restorePendingMessagesFor("group", null)
            groupMessages = CommunityChatLogic.applyIncrementalDelta(pending, res.messages)
            groupHasOlder = res.has_older
            groupOldestTs = res.oldest_timestamp
            groupLastTs = CommunityChatLogic.normalizeCursor(res.last_timestamp)
            isMuted = res.is_muted
            groupLoadState = CommunityChatLogic.onLoadSuccess(groupLoadState)
            lastActivityAtMs = System.currentTimeMillis()
            if (scrollToBottom && groupMessages.isNotEmpty()) {
                groupListState.scrollToItem(groupMessages.lastIndex)
            }
            val latestTs = res.messages.lastOrNull()?.created_at
            if (!latestTs.isNullOrBlank() && selectedTab == 0) {
                runCatching { repo.api.markChatRead("group", timestamp = latestTs) }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            val msg = CommunityChatLogic.sanitizeChatError(e, "Could not load group chat.")
            groupLoadState = CommunityChatLogic.onLoadFailure(groupLoadState, msg, System.currentTimeMillis())
        }
    }

    suspend fun loadDirectInitial(
        threadId: String?,
        scrollToBottom: Boolean = true,
        userInitiated: Boolean = false,
    ) {
        if (isAdmin && threadId.isNullOrBlank()) return
        directLoadState = directLoadState.copy(
            isLoading = true,
            errorMessage = if (userInitiated) null else directLoadState.errorMessage,
            attemptCount = if (userInitiated) 0 else directLoadState.attemptCount,
        )
        try {
            val res = repo.api.chatDirectMessages(threadId = if (isAdmin) threadId else null, limit = 50)
            val effectiveThreadId = res.thread_id ?: threadId ?: selfUserId
            val pending = restorePendingMessagesFor("direct", effectiveThreadId)
            directMessages = CommunityChatLogic.applyIncrementalDelta(pending, res.messages)
            directHasOlder = res.has_older
            directOldestTs = res.oldest_timestamp
            directLastTs = CommunityChatLogic.normalizeCursor(res.last_timestamp)
            isMuted = res.is_muted
            if (!isAdmin) {
                activeDirectTitle = CommunityChatLogic.memberDirectHeaderTitle()
            } else if (!res.thread_title.isNullOrBlank()) {
                activeDirectTitle = res.thread_title
            }
            directLoadState = CommunityChatLogic.onLoadSuccess(directLoadState)
            lastActivityAtMs = System.currentTimeMillis()
            if (scrollToBottom && directMessages.isNotEmpty()) {
                directListState.scrollToItem(directMessages.lastIndex)
            }
            val latestTs = res.messages.lastOrNull()?.created_at
            if (!latestTs.isNullOrBlank() && selectedTab == 1) {
                runCatching { repo.api.markChatRead("direct", threadId = effectiveThreadId, timestamp = latestTs) }
            }
            refreshThreadsMetadata()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            val msg = CommunityChatLogic.sanitizeChatError(e, "Could not load private messages.")
            directLoadState = CommunityChatLogic.onLoadFailure(directLoadState, msg, System.currentTimeMillis())
        }
    }

    suspend fun syncForegroundTick() {
        val nowMs = System.currentTimeMillis()
        if (selectedTab == 0) {
            val gCursor = groupLastTs
            if (gCursor.isNullOrBlank()) {
                if (CommunityChatLogic.shouldAttemptInitialOnPoll(gCursor, groupLoadState, nowMs)) {
                    loadGroupInitial(scrollToBottom = groupMessages.isEmpty())
                }
                return
            }
            try {
                val nearBottom = groupMessages.isEmpty() ||
                    (groupListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= (groupMessages.lastIndex - 2)
                val sync = repo.api.chatSync(groupSince = gCursor)
                isMuted = sync.is_muted
                unreadSummary = sync.unread
                groupLastTs = CommunityChatLogic.normalizeCursor(sync.group_last_ts, gCursor)
                if (sync.group_messages.isNotEmpty()) {
                    val prevCount = groupMessages.size
                    groupMessages = CommunityChatLogic.applyIncrementalDelta(groupMessages, sync.group_messages)
                    lastActivityAtMs = nowMs
                    if (nearBottom && groupMessages.isNotEmpty() && groupMessages.size >= prevCount) {
                        groupListState.animateScrollToItem(groupMessages.lastIndex)
                    }
                    val newestTs = sync.group_messages.lastOrNull { !it.is_me }?.created_at
                    if (!newestTs.isNullOrBlank()) {
                        runCatching { repo.api.markChatRead("group", timestamp = newestTs) }
                    }
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
            }
        } else if (selectedTab == 1) {
            if (isAdmin && activeDirectThreadId.isNullOrBlank()) {
                if (CommunityChatLogic.shouldAttemptInitialOnPoll(null, threadsLoadState, nowMs) || threadsLoadState.errorMessage == null) {
                    refreshThreadsMetadata()
                }
                return
            }
            val dCursor = directLastTs
            if (dCursor.isNullOrBlank()) {
                if (CommunityChatLogic.shouldAttemptInitialOnPoll(dCursor, directLoadState, nowMs)) {
                    loadDirectInitial(activeDirectThreadId, scrollToBottom = directMessages.isEmpty())
                }
                return
            }
            try {
                val nearBottom = directMessages.isEmpty() ||
                    (directListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= (directMessages.lastIndex - 2)
                val sync = repo.api.chatSync(
                    directSince = dCursor,
                    threadId = if (isAdmin) activeDirectThreadId else null,
                )
                isMuted = sync.is_muted
                unreadSummary = sync.unread
                directLastTs = CommunityChatLogic.normalizeCursor(sync.direct_last_ts, dCursor)
                if (sync.direct_messages.isNotEmpty()) {
                    val prevCount = directMessages.size
                    directMessages = CommunityChatLogic.applyIncrementalDelta(directMessages, sync.direct_messages)
                    lastActivityAtMs = nowMs
                    if (nearBottom && directMessages.isNotEmpty() && directMessages.size >= prevCount) {
                        directListState.animateScrollToItem(directMessages.lastIndex)
                    }
                    val newestTs = sync.direct_messages.lastOrNull { !it.is_me }?.created_at
                    val effThread = if (isAdmin) activeDirectThreadId else selfUserId
                    if (!newestTs.isNullOrBlank() && !effThread.isNullOrBlank()) {
                        runCatching { repo.api.markChatRead("direct", threadId = effThread, timestamp = newestTs) }
                    }
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
            }
        }
    }

    suspend fun loadGroupOlder() {
        val oldest = groupOldestTs ?: return
        if (groupLoadingOlder || !groupHasOlder) return
        groupLoadingOlder = true
        try {
            val res = repo.api.chatGroupMessages(before = oldest, limit = 50)
            val prependedCount = res.messages.size
            groupMessages = CommunityChatLogic.mergeOlderMessages(groupMessages, res.messages)
            groupHasOlder = res.has_older
            groupOldestTs = res.oldest_timestamp ?: groupMessages.firstOrNull()?.created_at
            if (prependedCount > 0) {
                groupListState.scrollToItem(prependedCount.coerceAtMost(groupMessages.lastIndex.coerceAtLeast(0)))
            }
        } catch (e: Throwable) {
            snackbarHostState.showSnackbar(CommunityChatLogic.sanitizeChatError(e, "Could not load older messages."))
        } finally {
            groupLoadingOlder = false
        }
    }

    suspend fun loadDirectOlder(threadId: String?) {
        val oldest = directOldestTs ?: return
        if (directLoadingOlder || !directHasOlder) return
        directLoadingOlder = true
        try {
            val res = repo.api.chatDirectMessages(
                threadId = if (isAdmin) threadId else null,
                before = oldest,
                limit = 50,
            )
            val prependedCount = res.messages.size
            directMessages = CommunityChatLogic.mergeOlderMessages(directMessages, res.messages)
            directHasOlder = res.has_older
            directOldestTs = res.oldest_timestamp ?: directMessages.firstOrNull()?.created_at
            if (prependedCount > 0) {
                directListState.scrollToItem(prependedCount.coerceAtMost(directMessages.lastIndex.coerceAtLeast(0)))
            }
        } catch (e: Throwable) {
            snackbarHostState.showSnackbar(CommunityChatLogic.sanitizeChatError(e, "Could not load older messages."))
        } finally {
            directLoadingOlder = false
        }
    }

    suspend fun loadReflectionsInitial() {
        postsLoading = true
        try {
            val res = repo.api.communityPosts()
            posts = res.posts
            nextCursor = res.next_cursor
            hasMorePosts = res.has_more
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
        } finally {
            postsLoading = false
        }
    }

    suspend fun loadReflectionsMore() {
        if (postsLoadingMore || !hasMorePosts || nextCursor == null) return
        postsLoadingMore = true
        try {
            val res = repo.api.communityPosts(nextCursor)
            posts = posts + res.posts
            nextCursor = res.next_cursor
            hasMorePosts = res.has_more
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
        } finally {
            postsLoadingMore = false
        }
    }

    suspend fun sendGroupOptimistic(
        text: String,
        replyTarget: ChatMessageDto?,
        existingClientId: String? = null,
    ) {
        val cid = existingClientId ?: UUID.randomUUID().toString()
        val optimistic = if (existingClientId == null) {
            CommunityChatLogic.createOptimisticMessage(
                clientMsgId = cid,
                channelType = "group",
                threadId = null,
                senderId = selfUserId,
                authorName = selfName,
                authorRole = selfRole,
                text = text,
                replyTo = replyTarget,
            ).also { msg ->
                groupMessages = groupMessages + msg
                CommunityChatLogic.toPendingOutgoing(msg)?.let { PendingChatStore.upsert(repo.session, it) }
                if (groupMessages.isNotEmpty()) {
                    groupListState.animateScrollToItem(groupMessages.lastIndex)
                }
            }
        } else {
            groupMessages = CommunityChatLogic.markOptimisticRetrying(groupMessages, cid)
            groupMessages.firstOrNull { it.client_msg_id == cid }
        }
        lastActivityAtMs = System.currentTimeMillis()
        try {
            val replyId = replyTarget?.id ?: optimistic?.reply_to?.id
            val res = repo.api.sendGroupMessage(text = text, replyToId = replyId, clientMsgId = cid)
            PendingChatStore.remove(repo.session, cid)
            res.message?.let { sent ->
                groupMessages = CommunityChatLogic.markOptimisticSent(groupMessages, cid, sent)
                groupLastTs = CommunityChatLogic.normalizeCursor(sent.created_at, groupLastTs)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            val errMsg = CommunityChatLogic.sanitizeChatError(e, "Failed to send")
            groupMessages = CommunityChatLogic.markOptimisticFailed(groupMessages, cid, errMsg)
        }
    }

    suspend fun sendDirectOptimistic(
        text: String,
        targetThreadId: String?,
        replyTarget: ChatMessageDto?,
        existingClientId: String? = null,
    ) {
        val cid = existingClientId ?: UUID.randomUUID().toString()
        val effectiveThreadId = if (isAdmin) targetThreadId else selfUserId.ifBlank { null }
        val optimistic = if (existingClientId == null) {
            CommunityChatLogic.createOptimisticMessage(
                clientMsgId = cid,
                channelType = "direct",
                threadId = effectiveThreadId,
                senderId = selfUserId,
                authorName = selfName,
                authorRole = selfRole,
                text = text,
                replyTo = replyTarget,
            ).also { msg ->
                directMessages = directMessages + msg
                CommunityChatLogic.toPendingOutgoing(msg)?.let { PendingChatStore.upsert(repo.session, it) }
                if (directMessages.isNotEmpty()) {
                    directListState.animateScrollToItem(directMessages.lastIndex)
                }
            }
        } else {
            directMessages = CommunityChatLogic.markOptimisticRetrying(directMessages, cid)
            directMessages.firstOrNull { it.client_msg_id == cid }
        }
        lastActivityAtMs = System.currentTimeMillis()
        try {
            val replyId = replyTarget?.id ?: optimistic?.reply_to?.id
            val res = repo.api.sendDirectMessage(
                text = text,
                targetUserId = if (isAdmin) effectiveThreadId else null,
                replyToId = replyId,
                clientMsgId = cid,
            )
            PendingChatStore.remove(repo.session, cid)
            res.message?.let { sent ->
                directMessages = CommunityChatLogic.markOptimisticSent(directMessages, cid, sent)
                directLastTs = CommunityChatLogic.normalizeCursor(sent.created_at, directLastTs)
            }
            refreshThreadsMetadata()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            val errMsg = CommunityChatLogic.sanitizeChatError(e, "Failed to send")
            directMessages = CommunityChatLogic.markOptimisticFailed(directMessages, cid, errMsg)
        }
    }

    fun jumpToMessage(
        messages: List<ChatMessageDto>,
        listState: androidx.compose.foundation.lazy.LazyListState,
        replyToId: String,
    ) {
        val idx = CommunityChatLogic.findReplyTargetIndex(messages, replyToId)
        scope.launch {
            if (idx >= 0) {
                listState.animateScrollToItem(idx)
                highlightedMessageId = replyToId
                delay(1_600L)
                if (highlightedMessageId == replyToId) {
                    highlightedMessageId = null
                }
            } else {
                snackbarHostState.showSnackbar("Original message is older — tap 'Load older messages' above.")
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isResumed = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ActiveChatForegroundTracker.activeChannel = null
            ActiveChatForegroundTracker.activeThreadId = null
        }
    }

    // Track active thread for foreground notification suppression (k7)
    LaunchedEffect(isResumed, selectedTab, activeDirectThreadId) {
        if (!isResumed) {
            ActiveChatForegroundTracker.activeChannel = null
            ActiveChatForegroundTracker.activeThreadId = null
        } else when (selectedTab) {
            0 -> {
                ActiveChatForegroundTracker.activeChannel = "group"
                ActiveChatForegroundTracker.activeThreadId = null
                ChatNotificationManager.clearConversation(context, "group")
            }
            1 -> {
                val effTid = if (isAdmin) activeDirectThreadId else selfUserId
                ActiveChatForegroundTracker.activeChannel = "direct"
                ActiveChatForegroundTracker.activeThreadId = effTid
                if (!effTid.isNullOrBlank()) {
                    ChatNotificationManager.clearConversation(context, "direct:$effTid")
                }
            }
            else -> {
                ActiveChatForegroundTracker.activeChannel = null
                ActiveChatForegroundTracker.activeThreadId = null
            }
        }
    }

    // Initial load on open
    LaunchedEffect(Unit) {
        launch { refreshThreadsMetadata(userInitiated = true) }
        launch { loadGroupInitial(scrollToBottom = true, userInitiated = true) }
        if (!isAdmin) {
            launch { loadDirectInitial(null, scrollToBottom = true, userInitiated = true) }
        }
        launch { loadReflectionsInitial() }
    }

    // Load direct thread when switched to Direct tab or selected member thread changes
    LaunchedEffect(selectedTab, activeDirectThreadId) {
        lastActivityAtMs = System.currentTimeMillis()
        if (selectedTab == 1) {
            if (isAdmin) {
                if (!activeDirectThreadId.isNullOrBlank()) {
                    loadDirectInitial(activeDirectThreadId, scrollToBottom = true, userInitiated = true)
                } else {
                    refreshThreadsMetadata(userInitiated = true)
                }
            } else if (!directLoadState.hasLoadedOnce && !directLoadState.isLoading) {
                loadDirectInitial(null, scrollToBottom = directMessages.isEmpty(), userInitiated = true)
            }
        } else if (selectedTab == 0) {
            refreshThreadsMetadata()
        }
    }

    // Adaptive foreground-only /sync polling while CommunityScreen is visible & RESUMED (k1)
    LaunchedEffect(isResumed, selectedTab, activeDirectThreadId) {
        if (!isResumed) return@LaunchedEffect
        if (selectedTab == 0 && !groupLoadState.isLoading) {
            syncForegroundTick()
        } else if (selectedTab == 1 && !directLoadState.isLoading) {
            syncForegroundTick()
        }
        while (isActive && isResumed) {
            val idleMs = (System.currentTimeMillis() - lastActivityAtMs).coerceAtLeast(0L)
            delay(CommunityChatLogic.nextForegroundPollDelayMs(idleMs))
            syncForegroundTick()
        }
    }

    val shouldLoadMorePosts by remember {
        derivedStateOf {
            val total = postsListState.layoutInfo.totalItemsCount
            val lastVisible = postsListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            selectedTab == 2 && hasMorePosts && total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMorePosts) {
        if (shouldLoadMorePosts) loadReflectionsMore()
    }

    if (deleteConfirmTarget != null) {
        val target = deleteConfirmTarget!!
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text("Delete message?") },
            text = {
                Text(
                    if (target.is_me) {
                        "Delete this message for everyone in the conversation?"
                    } else {
                        "Remove this message by ${target.author_name} for everyone in the conversation?"
                    }
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val toDelete = target
                        deleteConfirmTarget = null
                        scope.launch {
                            try {
                                val res = repo.api.deleteChatMessage(toDelete.id)
                                val deletedAt = res.deleted_at ?: toDelete.deleted_at ?: ""
                                groupMessages = CommunityChatLogic.applyLocalSoftDelete(
                                    existing = groupMessages,
                                    messageId = toDelete.id,
                                    serverMessage = res.message,
                                    isAdminViewer = isAdmin,
                                    deletedAt = deletedAt,
                                )
                                directMessages = CommunityChatLogic.applyLocalSoftDelete(
                                    existing = directMessages,
                                    messageId = toDelete.id,
                                    serverMessage = res.message,
                                    isAdminViewer = isAdmin,
                                    deletedAt = deletedAt,
                                )
                                refreshThreadsMetadata()
                            } catch (e: Throwable) {
                                snackbarHostState.showSnackbar(
                                    CommunityChatLogic.sanitizeChatError(e, "Could not delete message.")
                                )
                            }
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showMemberPicker && isAdmin) {
        AdminMemberPickerDialog(
            members = memberDirectory,
            loading = threadsLoadState.isLoading,
            errorMessage = threadsLoadState.errorMessage,
            sessionToken = sessionToken,
            onRetry = { scope.launch { refreshThreadsMetadata(userInitiated = true) } },
            onDismiss = { showMemberPicker = false },
            onSelectMember = { member ->
                showMemberPicker = false
                directMessages = emptyList()
                directLastTs = null
                directOldestTs = null
                directLoadState = CommunityChatLogic.ThreadLoadState(isLoading = true)
                activeDirectTitle = member.title
                activeDirectThreadId = member.user_id
                selectedTab = 1
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Community") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedTab == 1 && isAdmin && activeDirectThreadId != null) {
                                activeDirectThreadId = null
                                directReplyTarget = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                refreshThreadsMetadata(userInitiated = true)
                                when (selectedTab) {
                                    0 -> loadGroupInitial(scrollToBottom = false, userInitiated = true)
                                    1 -> loadDirectInitial(activeDirectThreadId, scrollToBottom = false, userInitiated = true)
                                    2 -> loadReflectionsInitial()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        TabLabelWithBadge(
                            label = "Group Chat",
                            badge = CommunityChatLogic.formatUnreadBadge(
                                if (selectedTab == 0) 0 else unreadSummary.group_unread
                            ),
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        TabLabelWithBadge(
                            label = "Direct",
                            badge = CommunityChatLogic.formatUnreadBadge(unreadSummary.direct_unread),
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Reflections", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }

            when (selectedTab) {
                0 -> {
                    ChatConversationPane(
                        messages = groupMessages,
                        loading = groupLoadState.isLoading,
                        loadError = groupLoadState.errorMessage,
                        onRetryLoad = { scope.launch { loadGroupInitial(scrollToBottom = true, userInitiated = true) } },
                        hasOlder = groupHasOlder,
                        loadingOlder = groupLoadingOlder,
                        onLoadOlder = { scope.launch { loadGroupOlder() } },
                        listState = groupListState,
                        highlightedMessageId = highlightedMessageId,
                        onJumpToReply = { replyId -> jumpToMessage(groupMessages, groupListState, replyId) },
                        emptyTitle = "Community Group Chat",
                        emptySubtitle = "Encourage one another and discuss what you are reading today.",
                        draft = groupDraft,
                        onDraftChange = { if (it.length <= CommunityChatLogic.MAX_MESSAGE_CHARS) groupDraft = it },
                        replyTarget = groupReplyTarget,
                        onClearReply = { groupReplyTarget = null },
                        onReplyClick = { groupReplyTarget = it },
                        canCompose = CommunityChatLogic.canComposeGroup(isAdmin, isMuted),
                        disabledReason = if (isMuted) {
                            "You have been muted by an administrator and can read messages only."
                        } else {
                            ""
                        },
                        onSend = {
                            val trimmed = groupDraft.trim()
                            if (trimmed.isEmpty()) return@ChatConversationPane
                            val reply = groupReplyTarget
                            groupDraft = ""
                            groupReplyTarget = null
                            scope.launch { sendGroupOptimistic(trimmed, reply) }
                        },
                        onRetrySend = { failedMsg ->
                            val cid = failedMsg.client_msg_id ?: return@ChatConversationPane
                            scope.launch { sendGroupOptimistic(failedMsg.text, null, existingClientId = cid) }
                        },
                        onCancelFailedSend = { failedMsg ->
                            val cid = failedMsg.client_msg_id ?: return@ChatConversationPane
                            PendingChatStore.remove(repo.session, cid)
                            groupMessages = CommunityChatLogic.removeOptimisticMessage(groupMessages, cid)
                        },
                        isAdmin = isAdmin,
                        mutedUserIds = mutedUserIds,
                        sessionToken = sessionToken,
                        onDeleteMessage = { deleteConfirmTarget = it },
                        onAdminToggleMute = { senderId, targetName, currentlyMuted ->
                            scope.launch {
                                try {
                                    val res = repo.api.adminSetChatMute(senderId, !currentlyMuted)
                                    mutedUserIds = if (res.is_muted) mutedUserIds + senderId else mutedUserIds - senderId
                                    snackbarHostState.showSnackbar(
                                        if (res.is_muted) "$targetName muted" else "$targetName unmuted"
                                    )
                                } catch (e: Throwable) {
                                    snackbarHostState.showSnackbar(
                                        CommunityChatLogic.sanitizeChatError(e, "Could not update mute setting.")
                                    )
                                }
                            }
                        },
                        onAdminMessagePrivately = { msg ->
                            if (msg.author_role != "admin" && msg.sender_id.isNotBlank()) {
                                directMessages = emptyList()
                                directLastTs = null
                                directOldestTs = null
                                directLoadState = CommunityChatLogic.ThreadLoadState(isLoading = true)
                                activeDirectTitle = msg.author_name
                                activeDirectThreadId = msg.sender_id
                                selectedTab = 1
                            }
                        },
                    )
                }

                1 -> {
                    if (isAdmin && activeDirectThreadId == null) {
                        AdminDirectThreadsOverview(
                            threads = threads,
                            loading = threadsLoadState.isLoading,
                            errorMessage = threadsLoadState.errorMessage,
                            onRetry = { scope.launch { refreshThreadsMetadata(userInitiated = true) } },
                            sessionToken = sessionToken,
                            onStartNewMessage = { showMemberPicker = true },
                            onOpenThread = { thread ->
                                directMessages = emptyList()
                                directLastTs = null
                                directOldestTs = null
                                directLoadState = CommunityChatLogic.ThreadLoadState(isLoading = true)
                                activeDirectTitle = thread.title
                                activeDirectThreadId = thread.thread_id
                            },
                        )
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = if (isAdmin) "Private thread with $activeDirectTitle" else activeDirectTitle,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = if (isAdmin) {
                                                "Shared leadership inbox · visible to leaders and this member"
                                            } else {
                                                activeDirectSubtitle
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (isAdmin && !activeDirectThreadId.isNullOrBlank()) {
                                        val memberId = activeDirectThreadId!!
                                        val memberMuted = memberId in mutedUserIds
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        val res = repo.api.adminSetChatMute(memberId, !memberMuted)
                                                        mutedUserIds = if (res.is_muted) mutedUserIds + memberId else mutedUserIds - memberId
                                                        snackbarHostState.showSnackbar(
                                                            if (res.is_muted) "$activeDirectTitle muted" else "$activeDirectTitle unmuted"
                                                        )
                                                    } catch (e: Throwable) {
                                                        snackbarHostState.showSnackbar(
                                                            CommunityChatLogic.sanitizeChatError(e, "Could not update mute setting.")
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                if (memberMuted) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (memberMuted) "Unmute" else "Mute")
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                            ChatConversationPane(
                                messages = directMessages,
                                loading = directLoadState.isLoading,
                                loadError = directLoadState.errorMessage,
                                onRetryLoad = {
                                    scope.launch { loadDirectInitial(activeDirectThreadId, scrollToBottom = true, userInitiated = true) }
                                },
                                hasOlder = directHasOlder,
                                loadingOlder = directLoadingOlder,
                                onLoadOlder = { scope.launch { loadDirectOlder(activeDirectThreadId) } },
                                listState = directListState,
                                highlightedMessageId = highlightedMessageId,
                                onJumpToReply = { replyId -> jumpToMessage(directMessages, directListState, replyId) },
                                emptyTitle = if (isAdmin) "Start private conversation" else "Message ForgeHouse Leadership",
                                emptySubtitle = if (isAdmin) {
                                    "Send a private pastoral note to $activeDirectTitle."
                                } else {
                                    "Send a private message to pastors and leaders. Any leader can reply here."
                                },
                                draft = directDraft,
                                onDraftChange = { if (it.length <= CommunityChatLogic.MAX_MESSAGE_CHARS) directDraft = it },
                                replyTarget = directReplyTarget,
                                onClearReply = { directReplyTarget = null },
                                onReplyClick = { directReplyTarget = it },
                                canCompose = CommunityChatLogic.canComposeDirect(
                                    isAdmin = isAdmin,
                                    isMuted = isMuted,
                                    hasActiveThread = !isAdmin || !activeDirectThreadId.isNullOrBlank(),
                                ),
                                disabledReason = if (!isAdmin && isMuted) {
                                    "You have been muted by an administrator and can read messages only."
                                } else {
                                    ""
                                },
                                onSend = {
                                    val trimmed = directDraft.trim()
                                    if (trimmed.isEmpty()) return@ChatConversationPane
                                    val reply = directReplyTarget
                                    val targetId = if (isAdmin) activeDirectThreadId else null
                                    directDraft = ""
                                    directReplyTarget = null
                                    scope.launch { sendDirectOptimistic(trimmed, targetId, reply) }
                                },
                                onRetrySend = { failedMsg ->
                                    val cid = failedMsg.client_msg_id ?: return@ChatConversationPane
                                    val targetId = if (isAdmin) activeDirectThreadId else null
                                    scope.launch { sendDirectOptimistic(failedMsg.text, targetId, null, existingClientId = cid) }
                                },
                                onCancelFailedSend = { failedMsg ->
                                    val cid = failedMsg.client_msg_id ?: return@ChatConversationPane
                                    PendingChatStore.remove(repo.session, cid)
                                    directMessages = CommunityChatLogic.removeOptimisticMessage(directMessages, cid)
                                },
                                isAdmin = isAdmin,
                                mutedUserIds = mutedUserIds,
                                sessionToken = sessionToken,
                                onDeleteMessage = { deleteConfirmTarget = it },
                                onAdminToggleMute = null,
                                onAdminMessagePrivately = null,
                            )
                        }
                    }
                }

                2 -> {
                    ReflectionsFeedPane(
                        posts = posts,
                        loading = postsLoading,
                        loadingMore = postsLoadingMore,
                        listState = postsListState,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabLabelWithBadge(label: String, badge: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!badge.isNullOrBlank()) {
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatConversationPane(
    messages: List<ChatMessageDto>,
    loading: Boolean,
    loadError: String?,
    onRetryLoad: () -> Unit,
    hasOlder: Boolean,
    loadingOlder: Boolean,
    onLoadOlder: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    highlightedMessageId: String?,
    onJumpToReply: (String) -> Unit,
    emptyTitle: String,
    emptySubtitle: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    replyTarget: ChatMessageDto?,
    onClearReply: () -> Unit,
    onReplyClick: (ChatMessageDto) -> Unit,
    canCompose: Boolean,
    disabledReason: String,
    onSend: () -> Unit,
    onRetrySend: (ChatMessageDto) -> Unit,
    onCancelFailedSend: (ChatMessageDto) -> Unit,
    isAdmin: Boolean,
    mutedUserIds: Set<String>,
    sessionToken: String?,
    onDeleteMessage: (ChatMessageDto) -> Unit,
    onAdminToggleMute: ((senderId: String, authorName: String, currentlyMuted: Boolean) -> Unit)?,
    onAdminMessagePrivately: ((ChatMessageDto) -> Unit)?,
) {
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                loading && messages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ExpressiveLoading()
                    }
                }
                loadError != null && messages.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(44.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                loadError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))
                            FilledTonalButton(onClick = onRetryLoad) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
                messages.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(emptyTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                emptySubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (hasOlder) {
                            item(key = "load_older_header") {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    OutlinedButton(onClick = onLoadOlder, enabled = !loadingOlder) {
                                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (loadingOlder) "Loading..." else "Load older messages")
                                    }
                                }
                            }
                        }

                        items(messages, key = { it.id }) { msg ->
                            ChatMessageBubble(
                                msg = msg,
                                isAdminViewer = isAdmin,
                                isHighlighted = msg.id == highlightedMessageId,
                                isSenderMuted = msg.sender_id in mutedUserIds,
                                sessionToken = sessionToken,
                                onReply = { onReplyClick(msg) },
                                onJumpToReply = onJumpToReply,
                                onDelete = if (CommunityChatLogic.canDeleteMessage(msg, isAdmin)) {
                                    { onDeleteMessage(msg) }
                                } else null,
                                onRetrySend = { onRetrySend(msg) },
                                onCancelFailedSend = { onCancelFailedSend(msg) },
                                onToggleMute = if (onAdminToggleMute != null && isAdmin && msg.author_role != "admin" && msg.sender_id.isNotBlank()) {
                                    { onAdminToggleMute(msg.sender_id, msg.author_name, msg.sender_id in mutedUserIds) }
                                } else null,
                                onMessagePrivately = if (onAdminMessagePrivately != null && isAdmin && msg.author_role != "admin" && msg.sender_id.isNotBlank()) {
                                    { onAdminMessagePrivately(msg) }
                                } else null,
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        if (!canCompose) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = disabledReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (replyTarget != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "Replying to ${replyTarget.author_name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = CommunityChatLogic.displayMessageText(replyTarget, isAdmin),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = onClearReply, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            placeholder = { Text("Write a message...") },
                            maxLines = 4,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = onSend,
                            enabled = draft.isNotBlank(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (draft.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    msg: ChatMessageDto,
    isAdminViewer: Boolean,
    isHighlighted: Boolean,
    isSenderMuted: Boolean,
    sessionToken: String?,
    onReply: () -> Unit,
    onJumpToReply: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onRetrySend: () -> Unit,
    onCancelFailedSend: () -> Unit,
    onToggleMute: (() -> Unit)?,
    onMessagePrivately: (() -> Unit)?,
) {
    val isLeader = msg.author_role == "admin"
    val timeLabel = remember(msg.created_at) { WatTimeFormatter.formatWatTime(msg.created_at) }
    val canSwipe = CommunityChatLogic.canSwipeToReply(msg)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val maxDragPx = with(density) { 72.dp.toPx() }
    val triggerThresholdPx = with(density) { 48.dp.toPx() }
    val offsetX = remember(msg.id) { Animatable(0f) }
    var hapticFired by remember(msg.id) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (offsetX.value > 8f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Swipe to reply",
                    tint = if (offsetX.value >= triggerThresholdPx) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isHighlighted -> MaterialTheme.colorScheme.secondaryContainer
                    msg.is_me -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    isLeader -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                },
            ),
            border = BorderStroke(
                width = if (isHighlighted) 2.dp else 1.dp,
                color = when {
                    isHighlighted -> MaterialTheme.colorScheme.primary
                    isLeader -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(
                    if (canSwipe) {
                        Modifier.pointerInput(msg.id) {
                            detectHorizontalDragGestures(
                                onDragStart = { hapticFired = false },
                                onHorizontalDrag = { _, dragAmount ->
                                    val next = (offsetX.value + dragAmount).coerceIn(0f, maxDragPx)
                                    scope.launch { offsetX.snapTo(next) }
                                    if (!hapticFired && next >= triggerThresholdPx) {
                                        hapticFired = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                                onDragEnd = {
                                    val triggered = offsetX.value >= triggerThresholdPx
                                    scope.launch {
                                        offsetX.animateTo(
                                            0f,
                                            spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                        )
                                    }
                                    if (triggered) onReply()
                                },
                                onDragCancel = {
                                    scope.launch { offsetX.animateTo(0f) }
                                },
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (isLeader) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else {
                        UserAvatar(
                            avatarId = msg.avatar_id,
                            userId = msg.sender_id,
                            pictureVersion = msg.picture_version,
                            sessionToken = sessionToken,
                            size = 34.dp,
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = msg.author_name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            if (isLeader) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    Text(
                                        text = "Leader",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            } else if (isAdminViewer && isSenderMuted) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                ) {
                                    Text(
                                        text = "Muted",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (timeLabel.isNotBlank()) {
                                Text(
                                    text = timeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (msg.is_me && !msg.is_deleted) {
                                Spacer(Modifier.width(6.dp))
                                when (msg.delivery_state) {
                                    ChatDeliveryState.SENDING -> Icon(
                                        Icons.Filled.Schedule,
                                        contentDescription = "Sending",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    ChatDeliveryState.SENT -> Icon(
                                        Icons.Filled.Done,
                                        contentDescription = "Sent",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    ChatDeliveryState.FAILED -> Icon(
                                        Icons.Filled.ErrorOutline,
                                        contentDescription = "Failed",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (canSwipe) {
                        IconButton(onClick = onReply, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.Reply,
                                contentDescription = "Reply",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (isAdminViewer) {
                        if (onMessagePrivately != null) {
                            IconButton(onClick = onMessagePrivately, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.MailOutline,
                                    contentDescription = "Direct message member",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (onToggleMute != null) {
                            IconButton(onClick = onToggleMute, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    if (isSenderMuted) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                    contentDescription = if (isSenderMuted) "Unmute member" else "Mute member",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Delete message",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                if (msg.reply_to != null && !msg.is_deleted) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = msg.reply_to.id.isNotBlank()) {
                                onJumpToReply(msg.reply_to.id)
                            },
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = msg.reply_to.author_name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = CommunityChatLogic.displayReplyPreviewText(msg.reply_to, isAdminViewer),
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = if (msg.reply_to.is_deleted) FontStyle.Italic else FontStyle.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = CommunityChatLogic.displayMessageText(msg, isAdminViewer),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = if (msg.is_deleted) FontStyle.Italic else FontStyle.Normal,
                    color = if (msg.is_deleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )

                if (msg.delivery_state == ChatDeliveryState.FAILED) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = msg.local_error ?: "Not sent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRetrySend, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text("Retry")
                        }
                        TextButton(onClick = onCancelFailedSend, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminDirectThreadsOverview(
    threads: List<ChatThreadDto>,
    loading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    sessionToken: String?,
    onStartNewMessage: () -> Unit,
    onOpenThread: (ChatThreadDto) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Leadership Inbox",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            FilledTonalButton(onClick = onStartNewMessage) {
                Icon(Icons.Filled.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Message member")
            }
        }

        Spacer(Modifier.height(12.dp))

        when {
            loading && threads.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExpressiveLoading()
                }
            }
            errorMessage != null && threads.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = onRetry) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
            }
            threads.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.MailOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "No private threads yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap 'Message member' to send a private pastoral message to any participant.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(threads, key = { it.thread_id }) { thread ->
                        val timeLabel = remember(thread.latest_timestamp) {
                            WatTimeFormatter.formatWatTime(thread.latest_timestamp)
                        }
                        Card(
                            onClick = { onOpenThread(thread) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UserAvatar(
                                    avatarId = thread.avatar_id,
                                    userId = thread.user_id,
                                    pictureVersion = thread.picture_version,
                                    sessionToken = sessionToken,
                                    size = 42.dp,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = thread.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (timeLabel.isNotBlank()) {
                                            Text(
                                                text = timeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = thread.latest_message ?: "No messages yet",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        val badge = CommunityChatLogic.formatUnreadBadge(thread.unread_count)
                                        if (badge != null) {
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary,
                                            ) {
                                                Text(
                                                    text = badge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminMemberPickerDialog(
    members: List<ChatDirectoryMemberDto>,
    loading: Boolean,
    errorMessage: String?,
    sessionToken: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onSelectMember: (ChatDirectoryMemberDto) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(members, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) members else members.filter { it.title.lowercase().contains(q) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start private message") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search member name...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                when {
                    loading && members.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            ExpressiveLoading()
                        }
                    }
                    errorMessage != null && members.isEmpty() -> {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onRetry) { Text("Retry") }
                        }
                    }
                    filtered.isEmpty() -> {
                        Text(
                            "No matching members found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.height(300.dp)) {
                            items(filtered, key = { it.user_id }) { m ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectMember(m) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    UserAvatar(
                                        avatarId = m.avatar_id,
                                        userId = m.user_id,
                                        pictureVersion = m.picture_version,
                                        sessionToken = sessionToken,
                                        size = 34.dp,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        m.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (m.is_muted) {
                                        Text(
                                            "Muted",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun ReflectionsFeedPane(
    posts: List<CommunityPost>,
    loading: Boolean,
    loadingMore: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    if (loading && posts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ExpressiveLoading()
        }
    } else if (posts.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "No answered reflections yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "When leaders reply to observations and questions, answered posts will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(posts, key = { it.id }) { post ->
                CommunityPostCard(post)
            }
            if (loadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        ExpressiveLoading()
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityPostCard(
    post: CommunityPost,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (post.type.equals("question", ignoreCase = true)) Icons.Filled.Help else Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = noteTypeLabel(post.type),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = post.reference,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = post.author_name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = post.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (post.reply.text.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Leader Reply",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = post.reply.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

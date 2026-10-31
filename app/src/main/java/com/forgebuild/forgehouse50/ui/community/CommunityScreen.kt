package com.forgebuild.forgehouse50.ui.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    repo: Repository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isAdmin = repo.session.isAdmin
    val sessionToken = repo.session.sessionToken

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Group Chat, 1 = Direct, 2 = Reflections

    // Shared chat metadata
    var isMuted by remember { mutableStateOf(false) }
    var unreadSummary by remember { mutableStateOf(ChatUnreadSummary()) }

    // Group chat state
    var groupMessages by remember { mutableStateOf<List<ChatMessageDto>>(emptyList()) }
    var groupHasOlder by remember { mutableStateOf(false) }
    var groupOldestTs by remember { mutableStateOf<String?>(null) }
    var groupLastTs by remember { mutableStateOf<String?>(null) }
    var groupLoading by remember { mutableStateOf(true) }
    var groupLoadingOlder by remember { mutableStateOf(false) }
    var groupDraft by remember { mutableStateOf("") }
    var groupReplyTarget by remember { mutableStateOf<ChatMessageDto?>(null) }
    var groupSending by remember { mutableStateOf(false) }
    val groupListState = rememberLazyListState()

    // Direct chat state
    var threads by remember { mutableStateOf<List<ChatThreadDto>>(emptyList()) }
    var memberDirectory by remember { mutableStateOf<List<ChatDirectoryMemberDto>>(emptyList()) }
    var threadsLoading by remember { mutableStateOf(true) }
    var activeDirectThreadId by remember { mutableStateOf<String?>(if (isAdmin) null else repo.session.userId) }
    var activeDirectTitle by remember { mutableStateOf("Pastor Dave") }
    var directStartedByAdmin by remember { mutableStateOf(false) }
    var directMessages by remember { mutableStateOf<List<ChatMessageDto>>(emptyList()) }
    var directHasOlder by remember { mutableStateOf(false) }
    var directOldestTs by remember { mutableStateOf<String?>(null) }
    var directLastTs by remember { mutableStateOf<String?>(null) }
    var directLoading by remember { mutableStateOf(false) }
    var directLoadingOlder by remember { mutableStateOf(false) }
    var directDraft by remember { mutableStateOf("") }
    var directReplyTarget by remember { mutableStateOf<ChatMessageDto?>(null) }
    var directSending by remember { mutableStateOf(false) }
    var showMemberPicker by remember { mutableStateOf(false) }
    val directListState = rememberLazyListState()

    // Moderation state
    var deleteConfirmTarget by remember { mutableStateOf<ChatMessageDto?>(null) }
    var mutedUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Reflections feed state (preserved)
    var posts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var hasMorePosts by remember { mutableStateOf(false) }
    var postsLoading by remember { mutableStateOf(true) }
    var postsLoadingMore by remember { mutableStateOf(false) }
    val postsListState = rememberLazyListState()

    // Track foreground lifecycle so polling runs ONLY while screen is visible & resumed
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    suspend fun refreshThreadsMetadata() {
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
                    activeDirectTitle = own.title
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
        } finally {
            threadsLoading = false
        }
    }

    suspend fun loadGroupInitial(scrollToBottom: Boolean = true) {
        try {
            val res = repo.api.chatGroupMessages(limit = 50)
            groupMessages = res.messages
            groupHasOlder = res.has_older
            groupOldestTs = res.oldest_timestamp
            groupLastTs = res.last_timestamp
            isMuted = res.is_muted
            if (scrollToBottom && res.messages.isNotEmpty()) {
                groupListState.scrollToItem(res.messages.lastIndex)
            }
        } catch (e: Throwable) {
            val msg = CommunityChatLogic.sanitizeChatError(e, "Could not load group chat.")
            snackbarHostState.showSnackbar(msg)
        } finally {
            groupLoading = false
        }
    }

    suspend fun pollGroupSince() {
        val cursor = groupLastTs
        if (cursor.isNullOrBlank()) {
            loadGroupInitial(scrollToBottom = groupMessages.isEmpty())
            return
        }
        try {
            val nearBottom = groupMessages.isEmpty() ||
                (groupListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= (groupMessages.lastIndex - 2)
            val res = repo.api.chatGroupMessages(since = cursor, limit = 50)
            isMuted = res.is_muted
            if (res.messages.isNotEmpty() || res.deleted_ids.isNotEmpty()) {
                groupMessages = CommunityChatLogic.applyIncrementalDelta(
                    existing = groupMessages,
                    incoming = res.messages,
                    deletedIds = res.deleted_ids,
                )
                if (!res.last_timestamp.isNullOrBlank()) {
                    groupLastTs = res.last_timestamp
                }
                if (nearBottom && res.messages.isNotEmpty() && groupMessages.isNotEmpty()) {
                    groupListState.animateScrollToItem(groupMessages.lastIndex)
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
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

    suspend fun loadDirectInitial(threadId: String?, scrollToBottom: Boolean = true) {
        if (isAdmin && threadId.isNullOrBlank()) return
        directLoading = true
        try {
            val res = repo.api.chatDirectMessages(threadId = if (isAdmin) threadId else null, limit = 50)
            directMessages = res.messages
            directHasOlder = res.has_older
            directOldestTs = res.oldest_timestamp
            directLastTs = res.last_timestamp
            directStartedByAdmin = res.thread_started_by_admin
            isMuted = res.is_muted
            if (!res.thread_title.isNullOrBlank()) {
                activeDirectTitle = res.thread_title
            }
            if (scrollToBottom && res.messages.isNotEmpty()) {
                directListState.scrollToItem(res.messages.lastIndex)
            }
            refreshThreadsMetadata()
        } catch (e: Throwable) {
            snackbarHostState.showSnackbar(CommunityChatLogic.sanitizeChatError(e, "Could not load conversation."))
        } finally {
            directLoading = false
        }
    }

    suspend fun pollDirectSince(threadId: String?) {
        if (isAdmin && threadId.isNullOrBlank()) {
            refreshThreadsMetadata()
            return
        }
        val cursor = directLastTs
        if (cursor.isNullOrBlank()) {
            loadDirectInitial(threadId, scrollToBottom = directMessages.isEmpty())
            return
        }
        try {
            val nearBottom = directMessages.isEmpty() ||
                (directListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= (directMessages.lastIndex - 2)
            val res = repo.api.chatDirectMessages(
                threadId = if (isAdmin) threadId else null,
                since = cursor,
                limit = 50,
            )
            directStartedByAdmin = res.thread_started_by_admin
            isMuted = res.is_muted
            if (res.messages.isNotEmpty() || res.deleted_ids.isNotEmpty()) {
                directMessages = CommunityChatLogic.applyIncrementalDelta(
                    existing = directMessages,
                    incoming = res.messages,
                    deletedIds = res.deleted_ids,
                )
                if (!res.last_timestamp.isNullOrBlank()) {
                    directLastTs = res.last_timestamp
                }
                if (nearBottom && res.messages.isNotEmpty() && directMessages.isNotEmpty()) {
                    directListState.animateScrollToItem(directMessages.lastIndex)
                }
                refreshThreadsMetadata()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isResumed = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Initial load when screen opens
    LaunchedEffect(Unit) {
        launch { refreshThreadsMetadata() }
        launch { loadGroupInitial(scrollToBottom = true) }
        if (!isAdmin) {
            launch { loadDirectInitial(null, scrollToBottom = true) }
        }
        launch { loadReflectionsInitial() }
    }

    // Load active direct thread whenever selected
    LaunchedEffect(selectedTab, activeDirectThreadId) {
        if (selectedTab == 1) {
            if (isAdmin) {
                if (!activeDirectThreadId.isNullOrBlank()) {
                    loadDirectInitial(activeDirectThreadId, scrollToBottom = true)
                } else {
                    refreshThreadsMetadata()
                }
            } else {
                loadDirectInitial(null, scrollToBottom = directMessages.isEmpty())
            }
        } else if (selectedTab == 0) {
            refreshThreadsMetadata()
        }
    }

    // Foreground-only cursor polling while CommunityScreen is visible & RESUMED
    LaunchedEffect(isResumed, selectedTab, activeDirectThreadId) {
        if (!isResumed) return@LaunchedEffect
        // Immediate catch-up refresh on resume
        if (selectedTab == 0 && !groupLoading) {
            pollGroupSince()
            refreshThreadsMetadata()
        } else if (selectedTab == 1 && !directLoading) {
            pollDirectSince(activeDirectThreadId)
        }
        while (isActive && isResumed) {
            delay(CommunityChatLogic.FOREGROUND_POLL_INTERVAL_MS)
            if (selectedTab == 0) {
                pollGroupSince()
                refreshThreadsMetadata()
            } else if (selectedTab == 1) {
                pollDirectSince(activeDirectThreadId)
            }
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
                Text("Remove this message by ${target.author_name} for everyone in the conversation?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteConfirmTarget = null
                        scope.launch {
                            try {
                                repo.api.adminDeleteChatMessage(target.id)
                                groupMessages = CommunityChatLogic.applyIncrementalDelta(
                                    groupMessages,
                                    emptyList(),
                                    listOf(target.id),
                                )
                                directMessages = CommunityChatLogic.applyIncrementalDelta(
                                    directMessages,
                                    emptyList(),
                                    listOf(target.id),
                                )
                                snackbarHostState.showSnackbar("Message deleted")
                            } catch (e: Throwable) {
                                snackbarHostState.showSnackbar(
                                    CommunityChatLogic.sanitizeChatError(e, "Could not delete message.")
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (showMemberPicker && isAdmin) {
        AdminMemberPickerDialog(
            members = memberDirectory,
            sessionToken = sessionToken,
            onDismiss = { showMemberPicker = false },
            onSelectMember = { member ->
                showMemberPicker = false
                directMessages = emptyList()
                directLastTs = null
                directOldestTs = null
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
                                refreshThreadsMetadata()
                                when (selectedTab) {
                                    0 -> loadGroupInitial(scrollToBottom = false)
                                    1 -> loadDirectInitial(activeDirectThreadId, scrollToBottom = false)
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
                        loading = groupLoading,
                        hasOlder = groupHasOlder,
                        loadingOlder = groupLoadingOlder,
                        onLoadOlder = { scope.launch { loadGroupOlder() } },
                        listState = groupListState,
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
                        sending = groupSending,
                        onSend = {
                            val trimmed = groupDraft.trim()
                            if (trimmed.isEmpty() || groupSending) return@ChatConversationPane
                            groupSending = true
                            val replyId = groupReplyTarget?.id
                            scope.launch {
                                try {
                                    val res = repo.api.sendGroupMessage(trimmed, replyId)
                                    groupDraft = ""
                                    groupReplyTarget = null
                                    res.message?.let { sent ->
                                        groupMessages = CommunityChatLogic.applyIncrementalDelta(
                                            groupMessages,
                                            listOf(sent),
                                            emptyList(),
                                        )
                                        groupLastTs = sent.created_at
                                        if (groupMessages.isNotEmpty()) {
                                            groupListState.animateScrollToItem(groupMessages.lastIndex)
                                        }
                                    }
                                } catch (e: Throwable) {
                                    snackbarHostState.showSnackbar(
                                        CommunityChatLogic.sanitizeChatError(e, "Could not send message.")
                                    )
                                } finally {
                                    groupSending = false
                                }
                            }
                        },
                        isAdmin = isAdmin,
                        mutedUserIds = mutedUserIds,
                        sessionToken = sessionToken,
                        onAdminDelete = { deleteConfirmTarget = it },
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
                            loading = threadsLoading,
                            sessionToken = sessionToken,
                            onStartNewMessage = { showMemberPicker = true },
                            onOpenThread = { thread ->
                                directMessages = emptyList()
                                directLastTs = null
                                directOldestTs = null
                                activeDirectTitle = thread.title
                                activeDirectThreadId = thread.thread_id
                            },
                        )
                    } else if (!isAdmin && !directLoading && !directStartedByAdmin && directMessages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.MailOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "No direct messages yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Private messages from Pastor Dave or programme leadership will appear here so you can reply privately.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            // Private thread header banner
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
                                            text = if (isAdmin) "Private thread with $activeDirectTitle" else "Private thread with $activeDirectTitle",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "Visible only to leadership and this member",
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
                                loading = directLoading,
                                hasOlder = directHasOlder,
                                loadingOlder = directLoadingOlder,
                                onLoadOlder = { scope.launch { loadDirectOlder(activeDirectThreadId) } },
                                listState = directListState,
                                emptyTitle = "Start private conversation",
                                emptySubtitle = "Send a private pastoral note to $activeDirectTitle.",
                                draft = directDraft,
                                onDraftChange = { if (it.length <= CommunityChatLogic.MAX_MESSAGE_CHARS) directDraft = it },
                                replyTarget = directReplyTarget,
                                onClearReply = { directReplyTarget = null },
                                onReplyClick = { directReplyTarget = it },
                                canCompose = CommunityChatLogic.canComposeDirect(isAdmin, isMuted, directStartedByAdmin),
                                disabledReason = when {
                                    !isAdmin && isMuted -> "You have been muted by an administrator and can read messages only."
                                    !isAdmin && !directStartedByAdmin -> "Direct conversations are started by an administrator."
                                    else -> ""
                                },
                                sending = directSending,
                                onSend = {
                                    val trimmed = directDraft.trim()
                                    if (trimmed.isEmpty() || directSending) return@ChatConversationPane
                                    directSending = true
                                    val replyId = directReplyTarget?.id
                                    val targetId = if (isAdmin) activeDirectThreadId else null
                                    scope.launch {
                                        try {
                                            val res = repo.api.sendDirectMessage(
                                                text = trimmed,
                                                targetUserId = targetId,
                                                replyToId = replyId,
                                            )
                                            directDraft = ""
                                            directReplyTarget = null
                                            directStartedByAdmin = true
                                            res.message?.let { sent ->
                                                directMessages = CommunityChatLogic.applyIncrementalDelta(
                                                    directMessages,
                                                    listOf(sent),
                                                    emptyList(),
                                                )
                                                directLastTs = sent.created_at
                                                if (directMessages.isNotEmpty()) {
                                                    directListState.animateScrollToItem(directMessages.lastIndex)
                                                }
                                            }
                                            refreshThreadsMetadata()
                                        } catch (e: Throwable) {
                                            snackbarHostState.showSnackbar(
                                                CommunityChatLogic.sanitizeChatError(e, "Could not send direct message.")
                                            )
                                        } finally {
                                            directSending = false
                                        }
                                    }
                                },
                                isAdmin = isAdmin,
                                mutedUserIds = mutedUserIds,
                                sessionToken = sessionToken,
                                onAdminDelete = { deleteConfirmTarget = it },
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
    hasOlder: Boolean,
    loadingOlder: Boolean,
    onLoadOlder: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    emptyTitle: String,
    emptySubtitle: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    replyTarget: ChatMessageDto?,
    onClearReply: () -> Unit,
    onReplyClick: (ChatMessageDto) -> Unit,
    canCompose: Boolean,
    disabledReason: String,
    sending: Boolean,
    onSend: () -> Unit,
    isAdmin: Boolean,
    mutedUserIds: Set<String>,
    sessionToken: String?,
    onAdminDelete: (ChatMessageDto) -> Unit,
    onAdminToggleMute: ((senderId: String, authorName: String, currentlyMuted: Boolean) -> Unit)?,
    onAdminMessagePrivately: ((ChatMessageDto) -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (loading && messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExpressiveLoading()
                }
            } else if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            emptyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            emptySubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (hasOlder) {
                        item(key = "load_older_header") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                OutlinedButton(
                                    onClick = onLoadOlder,
                                    enabled = !loadingOlder,
                                ) {
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
                            isSenderMuted = msg.sender_id in mutedUserIds,
                            sessionToken = sessionToken,
                            onReply = { onReplyClick(msg) },
                            onDelete = { onAdminDelete(msg) },
                            onToggleMute = if (onAdminToggleMute != null && msg.author_role != "admin" && msg.sender_id.isNotBlank()) {
                                { onAdminToggleMute(msg.sender_id, msg.author_name, msg.sender_id in mutedUserIds) }
                            } else null,
                            onMessagePrivately = if (onAdminMessagePrivately != null && msg.author_role != "admin" && msg.sender_id.isNotBlank()) {
                                { onAdminMessagePrivately(msg) }
                            } else null,
                        )
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
                                        text = replyTarget.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = onClearReply,
                                    modifier = Modifier.size(28.dp),
                                ) {
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
                            enabled = draft.isNotBlank() && !sending,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (draft.isNotBlank() && !sending) {
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
    isSenderMuted: Boolean,
    sessionToken: String?,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onToggleMute: (() -> Unit)?,
    onMessagePrivately: (() -> Unit)?,
) {
    val isLeader = msg.author_role == "admin"
    val timeLabel = remember(msg.created_at) { WatTimeFormatter.formatWatTime(msg.created_at) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                msg.is_me -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                isLeader -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = BorderStroke(
            1.dp,
            if (isLeader) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        modifier = Modifier.fillMaxWidth(),
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
                    if (timeLabel.isNotBlank()) {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                IconButton(onClick = onReply, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Reply",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
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

            if (msg.reply_to != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            text = msg.reply_to.author_name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = msg.reply_to.text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AdminDirectThreadsOverview(
    threads: List<ChatThreadDto>,
    loading: Boolean,
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
                "Private Member Threads",
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

        if (loading && threads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExpressiveLoading()
            }
        } else if (threads.isEmpty()) {
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
        } else {
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
                                        thread.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (thread.is_muted) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "(Muted)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (timeLabel.isNotBlank()) {
                                        Text(
                                            timeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = thread.latest_message ?: "",
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

@Composable
private fun AdminMemberPickerDialog(
    members: List<ChatDirectoryMemberDto>,
    sessionToken: String?,
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
                if (filtered.isEmpty()) {
                    Text(
                        "No matching members found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
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

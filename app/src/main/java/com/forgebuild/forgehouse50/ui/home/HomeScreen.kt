package com.forgebuild.forgehouse50.ui.home

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.Announcement
import com.forgebuild.forgehouse50.data.AppCache
import com.forgebuild.forgehouse50.data.CommunityPost
import com.forgebuild.forgehouse50.data.ReadingSession
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.WatTimeFormatter
import com.forgebuild.forgehouse50.ui.ActionPillRow
import com.forgebuild.forgehouse50.ui.ExpressiveButton
import com.forgebuild.forgehouse50.ui.ExpressiveButtonLoader
import com.forgebuild.forgehouse50.ui.ExpressiveLinearProgress
import com.forgebuild.forgehouse50.ui.JoinWhatsAppGroupButton
import com.forgebuild.forgehouse50.ui.SupportPlayStoreCard
import com.forgebuild.forgehouse50.ui.SupportWhatsAppButton
import com.forgebuild.forgehouse50.ui.announcements.formatAnnouncementDate
import com.forgebuild.forgehouse50.update.UpdateChecker
import com.forgebuild.forgehouse50.update.UpdateManager
import com.forgebuild.forgehouse50.update.UpdateUiState
import com.forgebuild.forgehouse50.widget.ForgeHouseWidgetProvider
import com.forgebuild.forgehouse50.widget.WidgetRefreshWorker

/**
 * Dynamic Home / Today dashboard (Item 5).
 *
 * Single lazy, scrollable list of independent cards with intrinsic heights in priority order:
 * 1. Update-ready card (if active)
 * 2. Announcement (if active)
 * 3. Today\x27s reading (with catch-up, calm placeholder skeleton on initial load)
 * 4. Community (latest answered item, showing reply and WAT reply time)
 * 5. Three stat cards (Points, Days, Streak)
 * 6. Action rows: My notes, WhatsApp group, Support, Admin
 *
 * Each dynamic card opens up space with animateItem() and AnimatedVisibility/animateContentSize.
 * Empty states render nothing (no empty boxes).
 */
@Composable
fun HomeScreen(
    repo: Repository,
    reloadTick: Int,
    onOpenRead: (Int) -> Unit,
    onOpenSession: (List<Int>) -> Unit = {},
    onOpenQuiz: (Int) -> Unit,
    onOpenNotes: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenCommunity: () -> Unit = {},
    onOpenAnnouncements: () -> Unit = {},
) {
    val context = LocalContext.current

    var showNotesPrivacyNotice by remember {
        mutableStateOf(repo.session.userId != null && !repo.session.hasNotesPrivacyNoticeBeenAcknowledged())
    }
    if (showNotesPrivacyNotice) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    "Notes Sharing Update",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Observations, Questions, Prayers and Applications are sent to your leaders. They may reply, and may share some with the group, using your name only if you allow it. Insights stay private. If you delete a note, your leaders keep the copy you sent.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        repo.session.acknowledgeNotesPrivacyNotice()
                        showNotesPrivacyNotice = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Synchronous cache read for instantaneous first-frame render
    var today by remember { mutableStateOf(AppCache.getToday(context)) }
    var me by remember { mutableStateOf(AppCache.getMe(context)) }
    var progress by remember { mutableStateOf(AppCache.getProgress(context)) }
    var plan by remember {
        val t = today
        mutableStateOf(
            if (t != null) ReadingSession.resolve(progress, t, me?.programme?.programme_end_date)
            else null
        )
    }
    var latestCommunityPost by remember { mutableStateOf<CommunityPost?>(null) }
    var latestAnnouncement by remember { mutableStateOf<Announcement?>(null) }

    val block = today?.today
    var quizDone by remember(block?.day_number) {
        val d = block?.day_number
        mutableStateOf(if (d != null) AppCache.isQuizDone(context, d) ?: false else false)
    }
    var refreshing by remember { mutableStateOf(false) }

    // Background refresh & reconcile
    LaunchedEffect(reloadTick) {
        refreshing = (today == null)
        runCatching { repo.api.today() }.onSuccess { fresh ->
            today = fresh
            AppCache.saveToday(context, fresh)
        }
        runCatching { repo.api.me() }.onSuccess { fresh ->
            me = fresh
            AppCache.saveMe(context, fresh)
            repo.session.userId = fresh.id
            repo.session.userRole = fresh.role
            repo.session.userName = fresh.name
        }
        runCatching { repo.api.progress() }.onSuccess { fresh ->
            progress = fresh
            AppCache.saveProgress(context, fresh)
        }
        runCatching { repo.api.notes() }.onSuccess { freshNotes ->
            AppCache.saveNotes(context, freshNotes)
        }
        val curDay = today?.today?.day_number
        if (curDay != null) {
            runCatching { repo.api.quiz(curDay) }.onSuccess {
                val done = it.attempt != null
                quizDone = done
                AppCache.setQuizDone(context, curDay, done)
                AppCache.saveQuiz(context, curDay, it)
            }
        }
        refreshing = false

        runCatching { repo.api.communityLatest() }.onSuccess {
            latestCommunityPost = it.post
        }
        runCatching { repo.api.announcementLatest() }.onSuccess {
            latestAnnouncement = it.announcement
        }

        // Update home widget with latest snapshot
        WidgetRefreshWorker.refreshWidgetState(context, repo)
        ForgeHouseWidgetProvider.updateAll(context)
    }

    // Recompute plan whenever any constituent state updates
    LaunchedEffect(today, progress, me) {
        val t = today ?: return@LaunchedEffect
        plan = ReadingSession.resolve(progress, t, me?.programme?.programme_end_date)
    }

    val updateState by UpdateManager.uiState.collectAsState()
    val isUpdateVisible = updateState is UpdateUiState.ReadyToInstall ||
            updateState is UpdateUiState.PermissionRequired ||
            updateState is UpdateUiState.Downloading

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Priority 0: Header (greeting, date, refreshing spinner)
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().animateItem(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Hello${me?.name?.let { ", $it" } ?: ""}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        today?.date ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (refreshing) {
                    ExpressiveButtonLoader()
                }
            }
        }

        // Priority 1: Update-ready card (Item 3 & Item 5)
        item(key = "update_card") {
            AnimatedVisibility(
                visible = isUpdateVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier.animateItem()
            ) {
                when (val s = updateState) {
                    is UpdateUiState.ReadyToInstall -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.SystemUpdate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Update ready",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Version ${s.update.versionName} is downloaded. Tap to install.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    )
                                }
                                ExpressiveButton(
                                    onClick = { UpdateManager.triggerInstall(context) },
                                ) {
                                    Text("Install")
                                }
                            }
                        }
                    }
                    is UpdateUiState.PermissionRequired -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.SystemUpdate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Permission required",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Version ${s.update.versionName} is downloaded. Grant permission to install.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                                    )
                                }
                                ExpressiveButton(
                                    onClick = { UpdateChecker.openUnknownAppSourcesSettings(context) },
                                ) {
                                    Text("Settings")
                                }
                            }
                        }
                    }
                    is UpdateUiState.Downloading -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "Downloading update…",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "${(s.progress * 100).toInt()}%\職",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                ExpressiveLinearProgress(
                                    progress = { s.progress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        // Priority 2: Announcement card (Item 5 & Item 9)
        item(key = "announcement_card") {
            AnimatedVisibility(
                visible = latestAnnouncement != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier.animateItem()
            ) {
                if (latestAnnouncement != null) {
                    val ann = latestAnnouncement!!
                    val seenKey = "seen_announcement_" + ann.id
                    var isUnread by remember(ann.id) {
                        mutableStateOf(!context.getSharedPreferences("fh50_announcements", Context.MODE_PRIVATE).getBoolean(seenKey, false))
                    }
                    Card(
                        onClick = {
                            context.getSharedPreferences("fh50_announcements", Context.MODE_PRIVATE).edit().putBoolean(seenKey, true).apply()
                            isUnread = false
                            onOpenAnnouncements()
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth().animateContentSize()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Announcement",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (isUnread) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(6.dp)) {}
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    formatAnnouncementDate(ann.created_at),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                ann.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                ann.body,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Priority 3: Today\x27s reading (with catch-up) (Item 5)
        item(key = "today_reading_card") {
            Box(Modifier.fillMaxWidth().animateItem().animateContentSize()) {
                Crossfade(targetState = (today != null), label = "today_crossfade") { isLoaded ->
                    if (!isLoaded || today == null) {
                        TodayReadingSkeleton()
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            Column(Modifier.padding(22.dp)) {
                                if (today?.is_reading_day == true && block != null) {
                                    val a = block.assignment
                                    Text(
                                        "Day ${block.day_number} of ${today?.programme?.total_days ?: 50}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        a?.summary ?: "Today\x27s reading",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (a != null) {
                                        Spacer(Modifier.height(4.dp))
                                        val posStorage = remember { com.forgebuild.forgehouse50.data.PrefsReaderPositionStorage(context) }
                                        val savedPos = if (!block.completed && block.reading_seconds > 0) {
                                            posStorage.getPosition(repo.session.userId ?: "local", block.day_number, repo.session.translationId ?: com.forgebuild.forgehouse50.data.Repository.KJV_ID)
                                        } else null
                                        val chapterLabel = if (savedPos != null && a.chapter_count > 1) {
                                            "Chapter ${(savedPos.chapterIdx + 1).coerceIn(1, a.chapter_count)} of ${a.chapter_count} · about ${a.est_minutes} min"
                                        } else {
                                            "${a.chapter_count} chapter${if (a.chapter_count > 1) "s" else ""} · about ${a.est_minutes} min"
                                        }
                                        Text(
                                            chapterLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.height(20.dp))
                                    if (block.completed) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Text("Reading complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                        Spacer(Modifier.height(14.dp))
                                        if (quizDone) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                                Spacer(Modifier.width(10.dp))
                                                Text("Quiz done — day fully complete", style = MaterialTheme.typography.bodyLarge)
                                            }
                                        } else {
                                            Button(
                                                onClick = { onOpenQuiz(block.day_number) },
                                                shape = CircleShape,
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                            ) {
                                                Row(
                                                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Filled.Quiz, null, Modifier.size(20.dp))
                                                        Spacer(Modifier.width(10.dp))
                                                        Text("Take today\x27s quiz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                    }
                                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    } else {
                                        // Primary action button (Catch-up or Start/Continue reading)
                                        val pl = plan
                                        Button(
                                            onClick = {
                                                if (pl != null && pl.catchup && pl.days.isNotEmpty()) onOpenSession(pl.days)
                                                else onOpenRead(block.day_number)
                                            },
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.fillMaxWidth().height(54.dp),
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Filled.MenuBook, null, Modifier.size(20.dp))
                                                    Spacer(Modifier.width(10.dp))
                                                    Text(
                                                        if (pl != null && pl.catchup)
                                                            "Catch up: days ${pl.days.joinToString(" + ")}"
                                                        else if (block.reading_seconds > 0) "Continue reading"
                                                        else "Start reading",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
                                            }
                                        }
                                        if (pl != null && pl.catchup) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                ReadingSession.statusLine(pl),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                } else {
                                    Text("Rest day", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    val next = today?.next_reading_day
                                    Text(
                                        if (next?.assignment != null)
                                            "Next reading day ${next.day_number} (${next.date}): ${next.assignment.summary}"
                                        else "No reading scheduled today.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Priority 4: Community card (latest answered item, Item 5 & Item 8)
        item(key = "community_card") {
            AnimatedVisibility(
                visible = latestCommunityPost != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier.animateItem()
            ) {
                if (latestCommunityPost != null) {
                    val post = latestCommunityPost!!
                    val replyWatTime = WatTimeFormatter.formatRepliedTime(post.reply.created_at)
                    Card(
                        onClick = onOpenCommunity,
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth().animateContentSize()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "From the community",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.weight(1f))
                                if (replyWatTime.isNotBlank()) {
                                    Text(
                                        replyWatTime,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)) {
                                    Text(
                                        post.type.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(post.reference, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("·  " + post.author_name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(post.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
                            if (post.reply.text.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Leader Reply:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                            if (replyWatTime.isNotBlank()) {
                                                Text(replyWatTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(post.reply.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Priority 5: Stats row (Points, Days, Streak)
        item(key = "stats_row") {
            val stats = today?.stats
            Row(
                Modifier.fillMaxWidth().animateItem(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PremiumStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Star,
                    iconColor = MaterialTheme.colorScheme.primary,
                    iconBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    value = "${stats?.points ?: me?.stats?.points ?: 0}",
                    label = "Points",
                )
                PremiumStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.DateRange,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    iconBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    value = "${stats?.days_completed ?: me?.stats?.days_completed ?: 0} / ${today?.programme?.total_days ?: 50}",
                    label = "Days",
                )
                PremiumStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Whatshot,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                    iconBg = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    value = "${stats?.streak_current ?: me?.stats?.streak_current ?: 0}",
                    label = "Streak",
                )
            }
        }

        // Priority 6: Action row - My notes
        item(key = "notes_action") {
            val cachedNotesCount = AppCache.getNotes(context)?.notes?.size ?: 0
            val notesTotal = maxOf(today?.stats?.notes_total ?: 0, me?.stats?.notes_total ?: 0, cachedNotesCount)
            Box(Modifier.animateItem()) {
                ActionPillRow(
                    icon = Icons.Filled.Edit,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    label = "My notes ($notesTotal)",
                    onClick = onOpenNotes,
                )
            }
        }

        // Priority 7: Action row - Join WhatsApp Group
        item(key = "whatsapp_group") {
            Box(Modifier.animateItem()) {
                JoinWhatsAppGroupButton(context)
            }
        }

        // Priority 8: Support Play Store card
        item(key = "support_play_store") {
            Box(Modifier.animateItem()) {
                SupportPlayStoreCard()
            }
        }

        // Priority 9: Support ForgeHouse Global
        item(key = "support_whatsapp") {
            Box(Modifier.animateItem()) {
                SupportWhatsAppButton(context)
            }
        }

        // Priority 10: Admin dashboard
        if (repo.session.isAdmin || me?.role == "admin") {
            item(key = "admin_dashboard") {
                Box(Modifier.animateItem()) {
                    ActionPillRow(
                        icon = Icons.Filled.AdminPanelSettings,
                        iconTint = MaterialTheme.colorScheme.error,
                        iconContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        label = "Admin dashboard",
                        onClick = onOpenAdmin,
                    )
                }
            }
        }
    }
}

/**
 * Calm placeholder skeleton for Today\x27s reading card of final height, preventing layout jumps on initial load.
 */
@Composable
fun TodayReadingSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier
                .padding(22.dp)
                .fillMaxWidth(),
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                modifier = Modifier.width(80.dp).height(16.dp),
            ) {}
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(0.75f).height(28.dp),
            ) {}
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                modifier = Modifier.width(160.dp).height(16.dp),
            ) {}
            Spacer(Modifier.height(28.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {}
        }
    }
}

/**
 * Premium pill-shaped stat card with soft circular chip, bold large numeral, and label.
 */
@Composable
private fun PremiumStatCard(
    modifier: Modifier,
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    value: String,
    label: String,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = iconBg,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

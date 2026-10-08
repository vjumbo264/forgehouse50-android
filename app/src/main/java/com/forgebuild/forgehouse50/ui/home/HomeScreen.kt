package com.forgebuild.forgehouse50.ui.home

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Star
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
import com.forgebuild.forgehouse50.data.AppCache
import com.forgebuild.forgehouse50.data.MeResponse
import com.forgebuild.forgehouse50.data.ProgressResponse
import com.forgebuild.forgehouse50.data.ReadingSession
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.TodayResponse
import com.forgebuild.forgehouse50.ui.ActionPillRow
import com.forgebuild.forgehouse50.ui.ExpressiveButtonLoader
import com.forgebuild.forgehouse50.ui.JoinWhatsAppGroupButton
import com.forgebuild.forgehouse50.ui.SupportWhatsAppButton
import com.forgebuild.forgehouse50.widget.ForgeHouseWidgetProvider
import com.forgebuild.forgehouse50.widget.WidgetRefreshWorker

/**
 * Home / Today dashboard.
 *
 * Cache-first (Issue 1): Synchronous initialization from [AppCache] inside remember
 * guarantees real, accurate data renders immediately on the very first frame —
 * zero zeroes, zero placeholders, zero flicker, even completely offline.
 *
 * Premium visual pass (Issue 2): Elevated confidence matching reference mockup:
 * pill stat cards with soft circular icon chips and bold large numerals,
 * full-width pill action rows with icon chips and chevrons, generous 24dp/28dp radii.
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
) {
    val context = LocalContext.current

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

        // Update home widget with latest snapshot
        WidgetRefreshWorker.refreshWidgetState(context, repo)
        ForgeHouseWidgetProvider.updateAll(context)
    }

    // Recompute plan whenever any constituent state updates
    LaunchedEffect(today, progress, me) {
        val t = today ?: return@LaunchedEffect
        plan = ReadingSession.resolve(progress, t, me?.programme?.programme_end_date)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
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

        Spacer(Modifier.height(20.dp))

        // Three Stat Cards (Pills with soft icon chips and bold large numerals)
        val stats = today?.stats
        Row(
            Modifier.fillMaxWidth(),
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

        Spacer(Modifier.height(24.dp))

        // Today's Reading Card (Elevated container with bold typography)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(Modifier.padding(22.dp)) {
                if (today == null) {
                    Text("Loading today's plan…", style = MaterialTheme.typography.bodyMedium)
                } else if (today?.is_reading_day == true && block != null) {
                    val a = block.assignment
                    Text(
                        "Day ${block.day_number} of ${today?.programme?.total_days ?: 50}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        a?.summary ?: "Today's reading",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (a != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${a.chapter_count} chapters · about ${a.est_minutes} min",
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
                                        Text("Take today's quiz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

        Spacer(Modifier.height(20.dp))

        // Action Row 1: My notes
        ActionPillRow(
            icon = Icons.Filled.Edit,
            iconTint = MaterialTheme.colorScheme.primary,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            label = "My notes (${me?.stats?.notes_total ?: 0})",
            onClick = onOpenNotes,
        )

        Spacer(Modifier.height(12.dp))

        // Action Row 2: Join WhatsApp Group
        JoinWhatsAppGroupButton(context)

        Spacer(Modifier.height(12.dp))

        // Action Row 3: Support ForgeHouse Global
        SupportWhatsAppButton(context)

        if (repo.session.isAdmin || me?.role == "admin") {
            Spacer(Modifier.height(12.dp))
            ActionPillRow(
                icon = Icons.Filled.AdminPanelSettings,
                iconTint = MaterialTheme.colorScheme.error,
                iconContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                label = "Admin dashboard",
                onClick = onOpenAdmin,
            )
        }

        Spacer(Modifier.height(24.dp))
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

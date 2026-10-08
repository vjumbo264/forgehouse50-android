package com.forgebuild.forgehouse50.ui.progress

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.AppCache
import com.forgebuild.forgehouse50.data.ProgressResponse
import com.forgebuild.forgehouse50.data.ReadingSession
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.ExpressiveLoading
import com.forgebuild.forgehouse50.ui.formatDurationShort

/**
 * Progress: 50-day grid with completion + quiz state, cache-first.
 * Renders immediately from AppCache synchronously on first frame composition,
 * eliminating any loading spinner or zero-state flash.
 */
@Composable
fun ProgressScreen(repo: Repository, onOpenDay: (Int) -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableStateOf(AppCache.getProgress(context)) }
    var today by remember { mutableStateOf(AppCache.getToday(context)) }
    var me by remember { mutableStateOf(AppCache.getMe(context)) }

    var catchupStatus by remember {
        val initialPlan = if (progress != null && today != null) {
            ReadingSession.resolve(progress, today!!, me?.programme?.programme_end_date)
        } else null
        mutableStateOf(if (initialPlan != null) ReadingSession.statusLine(initialPlan) else "")
    }

    LaunchedEffect(Unit) {
        runCatching { repo.api.progress() }.onSuccess {
            progress = it
            AppCache.saveProgress(context, it)
        }
        runCatching { repo.api.today() }.onSuccess {
            today = it
            AppCache.saveToday(context, it)
        }
        runCatching { repo.api.me() }.onSuccess {
            me = it
            AppCache.saveMe(context, it)
            repo.session.userId = it.id
            repo.session.userName = it.name
            repo.session.userRole = it.role
        }
    }

    LaunchedEffect(progress, today, me) {
        val pr = progress ?: return@LaunchedEffect
        val t = today ?: return@LaunchedEffect
        val plan = ReadingSession.resolve(pr, t, me?.programme?.programme_end_date)
        catchupStatus = ReadingSession.statusLine(plan)
    }

    val p = progress
    if (p == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Progress", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "${p.totals.reading_days_completed} of ${p.programme.total_days} reading days · " +
                        "${p.totals.chapters_completed} of ${p.programme.total_chapters} chapters",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (p.totals.percent_completed / 100f).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Streak ${p.totals.streak_current} (best ${p.totals.streak_longest}) · " +
                        formatDurationShort(p.totals.reading_seconds_total) + " read",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (catchupStatus.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        catchupStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (catchupStatus.startsWith("You need")) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(p.days, key = { it.day_number }) { d ->
                Card(
                    onClick = { if (!d.is_future) onOpenDay(d.day_number) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = if (d.is_future) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (d.completed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            null,
                            tint = if (d.completed) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Day ${d.day_number} — ${d.assignment}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (d.completed) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (d.is_future) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                buildString {
                                    append(d.date)
                                    if (d.quiz_taken) append(" · quiz ${d.quiz_score}/${d.quiz_total}")
                                    if (d.notes_count > 0) append(" · ${d.notes_count} notes")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

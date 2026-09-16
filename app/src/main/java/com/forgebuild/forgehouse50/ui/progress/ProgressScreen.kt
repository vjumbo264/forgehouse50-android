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
import com.forgebuild.forgehouse50.data.ProgressResponse
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.AppJson
import com.forgebuild.forgehouse50.ui.formatDurationShort
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.forgebuild.forgehouse50.ui.ExpressiveLoading

/** Progress: 50-day grid with completion + quiz state, cache-first. */
@Composable
fun ProgressScreen(repo: Repository, onOpenDay: (Int) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fh50_cache", Context.MODE_PRIVATE) }
    var progress by remember { mutableStateOf<ProgressResponse?>(null) }

    LaunchedEffect(Unit) {
        prefs.getString("progress", null)?.let { c ->
            runCatching { AppJson.decodeFromString<ProgressResponse>(c) }.getOrNull()
        }?.let { progress = it }
        runCatching { repo.api.progress() }.onSuccess {
            progress = it
            prefs.edit().putString("progress", AppJson.encodeToString(ProgressResponse.serializer(), it)).apply()
        }
    }

    val p = progress
    if (p == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Progress", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("${p.totals.reading_days_completed} of ${p.programme.total_days} reading days · " +
                    "${p.totals.chapters_completed} of ${p.programme.total_chapters} chapters",
                    style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (p.totals.percent_completed / 100f).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Streak ${p.totals.streak_current} (best ${p.totals.streak_longest}) · " +
                    formatDurationShort(p.totals.reading_seconds_total) + " read",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(p.days, key = { it.day_number }) { d ->
                Card(
                    onClick = { if (!d.is_future) onOpenDay(d.day_number) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (d.is_future) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (d.completed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            null,
                            tint = if (d.completed) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Day ${d.day_number} — ${d.assignment}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (d.is_future) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface)
                            Text(
                                buildString {
                                    append(d.date)
                                    if (d.quiz_taken) append(" · quiz ${d.quiz_score}/${d.quiz_total}")
                                    if (d.notes_count > 0) append(" · ${d.notes_count} notes")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

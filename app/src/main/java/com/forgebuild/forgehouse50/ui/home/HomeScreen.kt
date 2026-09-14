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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.forgebuild.forgehouse50.data.MeResponse
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.TodayResponse
import com.forgebuild.forgehouse50.ui.AppJson
import com.forgebuild.forgehouse50.ui.formatDurationShort

/**
 * Home / Today dashboard. Cache-first per the Engine pattern: renders
 * instantly from the last-known snapshot, then refreshes from the live API
 * in the background and recomposes — never a blocking reload.
 */
@Composable
fun HomeScreen(
    repo: Repository,
    reloadTick: Int,
    onOpenRead: (Int) -> Unit,
    onOpenQuiz: (Int) -> Unit,
    onOpenNotes: () -> Unit,
    onOpenAdmin: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fh50_cache", Context.MODE_PRIVATE) }
    var today by remember { mutableStateOf<TodayResponse?>(null) }
    var me by remember { mutableStateOf<MeResponse?>(null) }
    var quizDone by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(reloadTick) {
        // 1) instant cached render
        prefs.getString("today", null)?.let { cached ->
            runCatching { AppJson.decodeFromString<TodayResponse>(cached) }.getOrNull()
        }?.let { today = it }
        // 2) background refresh + reconcile
        refreshing = true
        runCatching { repo.api.today() }.onSuccess { fresh ->
            today = fresh
            prefs.edit().putString("today", AppJson.encodeToString(TodayResponse.serializer(), fresh)).apply()
        }
        runCatching { repo.api.me() }.onSuccess {
            me = it
            repo.session.userRole = it.role
            repo.session.userName = it.name
        }
        refreshing = false
    }

    val block = today?.today
    LaunchedEffect(block?.day_number, block?.completed) {
        quizDone = false
        val d = block?.day_number
        if (d != null) {
            runCatching { repo.api.quiz(d) }.onSuccess { quizDone = it.attempt != null }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Hello${me?.name?.let { ", $it" } ?: ""}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    today?.date ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }

        Spacer(Modifier.height(20.dp))

        // Stats strip
        val stats = today?.stats
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatChip(Modifier.weight(1f), "Points", "${stats?.points ?: 0}")
            StatChip(Modifier.weight(1f), "Days", "${stats?.days_completed ?: 0} / ${today?.programme?.total_days ?: 50}")
            StatChip(Modifier.weight(1f), "Streak", "${stats?.streak_current ?: 0}")
        }

        Spacer(Modifier.height(24.dp))

        // Today card
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(20.dp)) {
                if (today == null) {
                    Text("Loading today's plan…", style = MaterialTheme.typography.bodyMedium)
                } else if (today?.is_reading_day == true && block != null) {
                    val a = block.assignment
                    Text(
                        "Day ${block.day_number} of ${today?.programme?.total_days ?: 50}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        a?.summary ?: "Today's reading",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (a != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${a.chapter_count} chapters · about ${a.est_minutes} min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    if (block.completed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Reading complete", style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(12.dp))
                        if (quizDone) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Quiz done — day fully complete", style = MaterialTheme.typography.bodyLarge)
                            }
                        } else {
                            Button(onClick = { onOpenQuiz(block.day_number) }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Quiz, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Take today's quiz")
                            }
                        }
                    } else {
                        Button(onClick = { onOpenRead(block.day_number) }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.MenuBook, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if ((block.reading_seconds) > 0) "Continue reading" else "Start reading")
                        }
                    }
                } else {
                    Text("Rest day", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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

        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = onOpenNotes, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("My notes (${me?.stats?.notes_total ?: 0})")
        }

        if (repo.session.isAdmin || me?.role == "admin") {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenAdmin, Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AdminPanelSettings, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Admin dashboard")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatChip(modifier: Modifier, label: String, value: String) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

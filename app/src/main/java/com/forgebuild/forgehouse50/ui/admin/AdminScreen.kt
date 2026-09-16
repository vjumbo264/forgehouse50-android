package com.forgebuild.forgehouse50.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.AdminProgramme
import com.forgebuild.forgehouse50.data.AdminStats
import com.forgebuild.forgehouse50.data.Participant
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.formatDurationShort
import kotlinx.coroutines.launch

/** Admin dashboard: stats, participants (points/completion), programme
 *  control (Start Programme). Rendered only for admin-flagged users. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(repo: Repository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var stats by remember { mutableStateOf<AdminStats?>(null) }
    var programme by remember { mutableStateOf<AdminProgramme?>(null) }
    var participants by remember { mutableStateOf<List<Participant>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var adjustTarget by remember { mutableStateOf<Participant?>(null) }
    var confirmStart by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { repo.api.adminStats() }.onSuccess { stats = it }
        runCatching { repo.api.adminProgramme() }.onSuccess { programme = it }
        runCatching { repo.api.adminParticipants() }.onSuccess { participants = it.participants }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Admin") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Stats") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Participants") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Programme") })
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            when (tab) {
                0 -> StatsTab(stats)
                1 -> ParticipantsTab(participants, onAdjust = { adjustTarget = it })
                else -> ProgrammeTab(programme, onStart = { confirmStart = true })
            }
        }
    }

    adjustTarget?.let { p ->
        var delta by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        var adjusting by remember { mutableStateOf(false) }   // ISSUE 4: loading state
        AlertDialog(
            onDismissRequest = { adjustTarget = null },
            title = { Text("Adjust points — ${p.name}") },
            text = {
                Column {
                    Text("Current: ${p.points} points", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(delta, { if (it.matches(Regex("-?\\d*"))) delta = it },
                        label = { Text("Points (+/-)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !adjusting,
                    onClick = {
                        val pts = delta.toIntOrNull() ?: return@TextButton
                        scope.launch {
                            adjusting = true                      // ISSUE 4: show loading
                            message = "Saving…"
                            runCatching { repo.api.adminAdjustPoints(p.id, pts, reason) }
                                .onSuccess {
                                    // ISSUE 4: explicit success vs error, and the
                                    // points change is re-fetched from the server so
                                    // the admin sees the real new total, not a guess.
                                    message = if (it.ok) "Saved ✓ ${p.name} adjusted by $pts points"
                                              else "Failed: ${it.error ?: "not saved"}"
                                    if (it.ok) runCatching { repo.api.adminParticipants() }
                                        .onSuccess { r -> participants = r.participants }
                                }
                                .onFailure { message = "Failed: ${it.message}" }
                            adjusting = false
                            adjustTarget = null
                        }
                    },
                ) {
                    if (adjusting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Apply")
                }
            },
            dismissButton = { TextButton(onClick = { adjustTarget = null }) { Text("Cancel") } },
        )
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Start the programme?") },
            text = { Text("This starts the 50-day programme for all registered participants and closes the join window. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStart = false
                    scope.launch {
                        runCatching { repo.api.adminStartProgramme() }.onSuccess {
                            message = if (it.ok) "Programme started." else it.error
                            runCatching { repo.api.adminProgramme() }.onSuccess { p -> programme = p }
                        }
                    }
                }) { Text("Start programme") }
            },
            dismissButton = { TextButton(onClick = { confirmStart = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatsTab(stats: AdminStats?) {
    if (stats == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Today: ${stats.today_date}", style = MaterialTheme.typography.bodyMedium)
        StatRow("Participants", "${stats.active_participants} active / ${stats.total_participants} total")
        StatRow("Completed today", "${stats.completed_today}")
        StatRow("Average completion", "%.0f%%".format(stats.average_completion_percent))
        StatRow("Chapters completed", "${stats.total_chapters_completed}")
        StatRow("Behind schedule", "${stats.users_behind_schedule}")
        StatRow("Quiz attempts", "${stats.quiz_attempts_total} (avg %.0f%%)".format(stats.quiz_avg_score_pct))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ParticipantsTab(participants: List<Participant>, onAdjust: (Participant) -> Unit) {
    if (participants.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    LazyColumn(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(participants, key = { it.id }) { p ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                        Text("${p.points} pts · ${p.days_completed} days · ${p.notes_count} notes · ${p.role}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onAdjust(p) }) { Text("Points") }
                }
            }
        }
    }
}

@Composable
private fun ProgrammeTab(programme: AdminProgramme?, onStart: () -> Unit) {
    if (programme == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    Column(Modifier.padding(20.dp)) {
        StatRow("Status", when {
            programme.concluded -> "Concluded"
            programme.started -> "Running"
            else -> "Not started"
        })
        Spacer(Modifier.height(8.dp))
        StatRow("Registration", if (programme.registration_open) "Open (closes ${programme.join_window_closes_at ?: "—"})" else "Closed")
        StatRow("Eligible participants", "${programme.eligible_participants}")
        programme.end_date_preview?.let { StatRow("Projected end", it) }
        Spacer(Modifier.height(16.dp))
        if (!programme.started) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start programme") }
        }
    }
}

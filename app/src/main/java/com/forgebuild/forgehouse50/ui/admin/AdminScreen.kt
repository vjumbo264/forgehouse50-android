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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.AdminProgramme
import com.forgebuild.forgehouse50.data.AdminStats
import com.forgebuild.forgehouse50.data.AppCache
import com.forgebuild.forgehouse50.data.Participant
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.ExpressiveButton
import com.forgebuild.forgehouse50.ui.ExpressiveButtonLoader
import com.forgebuild.forgehouse50.ui.ExpressiveLoading
import kotlinx.coroutines.launch

/**
 * Admin dashboard: stats, participants (points/completion/deletion), programme
 * control (Start Programme, End Testing Phase), and Admin Password management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(repo: Repository, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var stats by remember { mutableStateOf(AppCache.getAdminStats(context)) }
    var programme by remember { mutableStateOf(AppCache.getAdminProgramme(context)) }
    var participants by remember { mutableStateOf<List<Participant>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var adjustTarget by remember { mutableStateOf<Participant?>(null) }
    var deleteTarget by remember { mutableStateOf<Participant?>(null) }
    var confirmStart by remember { mutableStateOf(false) }
    var confirmEndTesting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { repo.api.adminStats() }.onSuccess { stats = it; AppCache.saveAdminStats(context, it) }
        runCatching { repo.api.adminProgramme() }.onSuccess { programme = it; AppCache.saveAdminProgramme(context, it) }
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
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Password") })
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            when (tab) {
                0 -> StatsTab(stats)
                1 -> ParticipantsTab(
                    participants = participants,
                    onAdjust = { adjustTarget = it },
                    onDelete = { deleteTarget = it }
                )
                2 -> ProgrammeTab(programme, onStart = { confirmStart = true }, onEndTesting = { confirmEndTesting = true })
                else -> PasswordTab(repo = repo, onMessage = { message = it })
            }
        }
    }

    adjustTarget?.let { p ->
        var delta by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        var adjusting by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adjustTarget = null },
            title = { Text("Adjust points — ${p.name}") },
            text = {
                Column {
                    Text("Current: ${p.points} points", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = delta,
                        onValueChange = { delta = it },
                        label = { Text("Delta (e.g. +10 or -5)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason (required)") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !adjusting && (delta.toIntOrNull() != null) && reason.isNotBlank(),
                    onClick = {
                        val pts = delta.toIntOrNull() ?: return@TextButton
                        scope.launch {
                            adjusting = true
                            runCatching { repo.api.adminAdjustPoints(p.id, pts, reason) }
                                .onSuccess {
                                    message = if (it.ok) "Points updated for ${p.name}." else it.error
                                    runCatching { repo.api.adminParticipants() }.onSuccess { list -> participants = list.participants }
                                }
                            adjusting = false
                            adjustTarget = null
                        }
                    },
                ) {
                    if (adjusting) ExpressiveButtonLoader()
                    else Text("Apply")
                }
            },
            dismissButton = { TextButton(onClick = { adjustTarget = null }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { p ->
        var confirmText by remember { mutableStateOf("") }
        var deleting by remember { mutableStateOf(false) }
        var deleteError by remember { mutableStateOf<String?>(null) }
        val canConfirm = confirmText.trim().equals(p.name.trim(), ignoreCase = true) ||
                         confirmText.trim().equals(p.id.trim(), ignoreCase = true)

        AlertDialog(
            onDismissRequest = { if (!deleting) deleteTarget = null },
            title = { Text("Delete participant?") },
            text = {
                Column {
                    Text(
                        "This permanently removes ${p.name}'s progress, points, notes and badges. This cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Type '${p.name}' to confirm:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it; deleteError = null },
                        placeholder = { Text(p.name) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    deleteError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            deleting = true
                            deleteError = null
                            try {
                                val res = repo.api.adminDeleteUser(p.id, confirmText.trim())
                                if (res.ok) {
                                    message = "Participant ${p.name} permanently deleted."
                                    deleteTarget = null
                                    runCatching { repo.api.adminParticipants() }.onSuccess { participants = it.participants }
                                    runCatching { repo.api.adminStats() }.onSuccess { stats = it; AppCache.saveAdminStats(context, it) }
                                } else {
                                    deleteError = res.error ?: "Deletion failed"
                                }
                            } catch (e: Exception) {
                                deleteError = e.message ?: "Deletion failed"
                            } finally {
                                deleting = false
                            }
                        }
                    },
                    enabled = canConfirm && !deleting,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    if (deleting) ExpressiveButtonLoader() else Text("Delete permanently")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTarget = null },
                    enabled = !deleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmEndTesting) {
        AlertDialog(
            onDismissRequest = { confirmEndTesting = false },
            title = { Text("End testing phase & reset?") },
            text = { Text("This permanently deletes every non-admin account and ALL their data (progress, notes, points, quiz attempts, sessions). Admin accounts and shared data (assignments, quiz bank, programme settings) are kept. The programme is NOT started by this action.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEndTesting = false
                    scope.launch {
                        runCatching { repo.api.adminEndTesting() }.onSuccess {
                            message = if (it.ok) "Testing phase ended." else it.error
                            runCatching { repo.api.adminProgramme() }.onSuccess { p -> programme = p }
                        }
                    }
                }) { Text("End testing & reset") }
            },
            dismissButton = { TextButton(onClick = { confirmEndTesting = false }) { Text("Cancel") } },
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
    if (stats == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }; return }
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
private fun ParticipantsTab(
    participants: List<Participant>,
    onAdjust: (Participant) -> Unit,
    onDelete: (Participant) -> Unit
) {
    if (participants.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }; return }
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
                    if (p.role != "admin") {
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = { onDelete(p) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgrammeTab(programme: AdminProgramme?, onStart: () -> Unit, onEndTesting: () -> Unit) {
    if (programme == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }; return }
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
        ExpressiveButton(onClick = onEndTesting, modifier = Modifier.fillMaxWidth()) { Text("End Testing Phase & Reset") }
        Spacer(Modifier.height(8.dp))
        if (!programme.started) {
            ExpressiveButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start programme") }
        }
    }
}

@Composable
private fun PasswordTab(repo: Repository, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Change Admin Password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Changing the password invalidates all other active admin sessions. Minimum 12 characters, mixing letters and numbers or symbols.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = currentPassword,
            onValueChange = { currentPassword = it; error = null; success = null },
            label = { Text("Current password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showCurrent = !showCurrent }) {
                    Icon(
                        if (showCurrent) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showCurrent) "Hide password" else "Show password"
                    )
                }
            }
        )

        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it; error = null; success = null },
            label = { Text("New password (min 12 chars)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showNew = !showNew }) {
                    Icon(
                        if (showNew) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showNew) "Hide password" else "Show password"
                    )
                }
            }
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; error = null; success = null },
            label = { Text("Confirm new password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        success?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))
        val canSubmit = currentPassword.isNotBlank() &&
                        newPassword.length >= 12 &&
                        newPassword == confirmPassword &&
                        !busy

        ExpressiveButton(
            onClick = {
                if (newPassword != confirmPassword) {
                    error = "New passwords do not match"
                    return@ExpressiveButton
                }
                scope.launch {
                    busy = true
                    error = null
                    success = null
                    try {
                        val res = repo.api.adminChangePassword(currentPassword, newPassword)
                        if (res.ok) {
                            success = "Password successfully updated. Other sessions invalidated."
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                            onMessage("Admin password updated successfully.")
                        } else {
                            error = res.error ?: "Password change failed"
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Password change failed"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = canSubmit,
            busy = busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Update password")
        }
    }
}

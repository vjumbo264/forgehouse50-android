package com.forgebuild.forgehouse50.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import com.forgebuild.forgehouse50.data.ParticipantDetail
import com.forgebuild.forgehouse50.data.ParticipantDayRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.forgebuild.forgehouse50.ui.AvatarAssets
import com.forgebuild.forgehouse50.ui.UserAvatar
import com.forgebuild.forgehouse50.ui.ExpressiveButton
import com.forgebuild.forgehouse50.ui.ExpressiveButtonLoader
import com.forgebuild.forgehouse50.ui.ExpressiveLoading
import kotlinx.coroutines.launch

enum class ParticipantSortOption(val label: String) {
    POINTS_DESC("Points"),
    NEWEST("Newest"),
    NAME_ASC("Name A–Z"),
    DAYS_DESC("Days"),
}

/**
 * Admin dashboard: stats, participants (points/completion/batch-deletion), programme
 * control (Start Programme, End Testing Phase), and Admin Password management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(repo: Repository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by remember { mutableIntStateOf(0) }
    var stats by remember { mutableStateOf(AppCache.getAdminStats(context)) }
    var programme by remember { mutableStateOf(AppCache.getAdminProgramme(context)) }
    var participants by remember { mutableStateOf<List<Participant>>(emptyList()) }
    var participantsLoading by remember { mutableStateOf(true) }

    var adjustTarget by remember { mutableStateOf<Participant?>(null) }
    var singleDeleteTarget by remember { mutableStateOf<Participant?>(null) }
    var batchDeleteOpen by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var inspectingParticipantId by remember { mutableStateOf<String?>(null) }

    var confirmStart by remember { mutableStateOf(false) }
    var confirmEndTesting by remember { mutableStateOf(false) }

    suspend fun refreshParticipants() {
        participantsLoading = true
        runCatching { repo.api.adminParticipants() }.onSuccess {
            participants = it.participants
            participantsLoading = false
        }.onFailure {
            participantsLoading = false
            scope.launch { snackbarHostState.showSnackbar(it.message ?: "Could not load participants") }
        }
    }

    suspend fun refreshStats() {
        runCatching { repo.api.adminStats() }.onSuccess {
            stats = it
            AppCache.saveAdminStats(context, it)
        }
    }

    suspend fun refreshProgramme() {
        runCatching { repo.api.adminProgramme() }.onSuccess {
            programme = it
            AppCache.saveAdminProgramme(context, it)
        }
    }

    LaunchedEffect(Unit) {
        refreshStats()
        refreshProgramme()
        refreshParticipants()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "${selectedIds.size} selected" else "Admin") },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = { selectionMode = false; selectedIds = emptySet() }) {
                            Icon(Icons.Filled.Close, "Cancel selection")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
                actions = {
                    if (tab == 1 && !selectionMode) {
                        TextButton(onClick = { selectionMode = true }) {
                            Text("Select")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (tab == 1 && selectionMode && selectedIds.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${selectedIds.size} participant${if (selectedIds.size > 1) "s" else ""} selected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(
                            onClick = { batchDeleteOpen = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete (${selectedIds.size})")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ScrollableTabRow guarantees tabs fit on one line with no wrapping
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Stats") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Participants") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Leaders' Inbox") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Announcements") })
                Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text("Programme") })
                Tab(selected = tab == 5, onClick = { tab = 5 }, text = { Text("Password") })
            }

            if (inspectingParticipantId != null) {
                ParticipantDetailScreen(
                    userId = inspectingParticipantId!!,
                    repo = repo,
                    scope = scope,
                    snackbarHostState = snackbarHostState,
                    onBack = { inspectingParticipantId = null },
                    onParticipantChanged = { scope.launch { refreshParticipants() } }
                )
            } else {
                when (tab) {
                    0 -> StatsTab(stats)
                    1 -> ParticipantsTab(
                        participants = participants,
                        loading = participantsLoading,
                        sessionToken = repo.session.sessionToken,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onToggleSelect = { id ->
                            selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
                        },
                        onSelectAll = { selectAll ->
                            val nonAdmins = participants.filter { it.role != "admin" }.map { it.id }.toSet()
                            selectedIds = if (selectAll) nonAdmins else emptySet()
                        },
                        onSelectInactive = {
                            val inactive = participants.filter { it.role != "admin" && it.isInactive }.map { it.id }.toSet()
                            selectedIds = inactive
                            scope.launch {
                                snackbarHostState.showSnackbar("${inactive.size} inactive account(s) selected.")
                            }
                        },
                        onOpenDetail = { p -> inspectingParticipantId = p.id },
                        onAdjust = { adjustTarget = it },
                        onDelete = { singleDeleteTarget = it }
                    )
                    2 -> LeadersInboxTab(
                        repo = repo,
                        participants = participants,
                        snackbarHostState = snackbarHostState,
                    )
                    3 -> AdminAnnouncementsTab(
                        repo = repo,
                        snackbarHostState = snackbarHostState,
                    )
                    4 -> ProgrammeTab(
                        programme = programme,
                        onStart = { confirmStart = true },
                        onEndTesting = { confirmEndTesting = true }
                    )
                    else -> PasswordTab(
                        repo = repo,
                        onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                }
            }
        }
    }

    // Points adjustment dialog
    adjustTarget?.let { p ->
        var delta by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        var adjusting by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adjustTarget = null },
            title = { Text("Adjust points — ${p.displayName}") },
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
                                    val msg = if (it.ok) "Points updated for ${p.displayName}." else (it.error ?: "Update failed")
                                    snackbarHostState.showSnackbar(msg)
                                    refreshParticipants()
                                }
                                .onFailure {
                                    snackbarHostState.showSnackbar(it.message ?: "Failed to update points")
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

    // Single delete dialog (No typing required)
    singleDeleteTarget?.let { p ->
        var deleting by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!deleting) singleDeleteTarget = null },
            title = { Text("Delete ${p.displayName}?") },
            text = {
                Text(
                    "This permanently removes ${p.displayName}'s progress, points, notes and badges. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            deleting = true
                            try {
                                val res = repo.api.adminDeleteUser(p.id, confirm = true)
                                if (res.ok) {
                                    snackbarHostState.showSnackbar("Participant ${p.displayName} permanently deleted.")
                                    singleDeleteTarget = null
                                    refreshParticipants()
                                    refreshStats()
                                } else {
                                    snackbarHostState.showSnackbar(res.error ?: "Deletion failed")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(e.message ?: "Deletion failed")
                            } finally {
                                deleting = false
                            }
                        }
                    },
                    enabled = !deleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    if (deleting) ExpressiveButtonLoader() else Text("Delete permanently")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { singleDeleteTarget = null },
                    enabled = !deleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Batch delete confirmation dialog (Requires typing word DELETE)
    if (batchDeleteOpen) {
        var confirmWord by remember { mutableStateOf("") }
        var batchDeleting by remember { mutableStateOf(false) }
        val canConfirm = confirmWord.trim().equals("DELETE", ignoreCase = true)
        val selectedParticipants = remember(selectedIds, participants) {
            participants.filter { selectedIds.contains(it.id) }
        }
        val namesSummary = remember(selectedParticipants) {
            val names = selectedParticipants.take(4).map { it.displayName }
            val rest = selectedParticipants.size - names.size
            if (rest > 0) names.joinToString(", ") + " and $rest more" else names.joinToString(", ")
        }

        AlertDialog(
            onDismissRequest = { if (!batchDeleting) batchDeleteOpen = false },
            title = { Text("Delete ${selectedIds.size} participants?") },
            text = {
                Column {
                    Text(
                        "This permanently removes $namesSummary along with all progress, points, notes and badges. This cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Type 'DELETE' to confirm:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = confirmWord,
                        onValueChange = { confirmWord = it },
                        placeholder = { Text("DELETE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            batchDeleting = true
                            try {
                                val res = repo.api.adminBatchDelete(selectedIds.toList(), "DELETE")
                                if (res.ok) {
                                    val msg = buildString {
                                        append("${res.deleted_count} deleted")
                                        if (res.skipped_count > 0) append(", ${res.skipped_count} skipped")
                                        if (res.failed_count > 0) append(", ${res.failed_count} failed")
                                    }
                                    snackbarHostState.showSnackbar(msg)
                                    batchDeleteOpen = false
                                    selectionMode = false
                                    selectedIds = emptySet()
                                    refreshParticipants()
                                    refreshStats()
                                } else {
                                    snackbarHostState.showSnackbar(res.error ?: "Batch deletion failed")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(e.message ?: "Batch deletion failed")
                            } finally {
                                batchDeleting = false
                            }
                        }
                    },
                    enabled = canConfirm && !batchDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    if (batchDeleting) ExpressiveButtonLoader() else Text("Delete permanently")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { batchDeleteOpen = false },
                    enabled = !batchDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // End testing phase dialog
    if (confirmEndTesting) {
        AlertDialog(
            onDismissRequest = { confirmEndTesting = false },
            title = { Text("End testing phase & reset?") },
            text = { Text("This permanently deletes every non-admin account and ALL their data (progress, notes, points, quiz attempts, sessions). Admin accounts and shared data (assignments, quiz bank, programme settings) are kept. The programme is NOT started by this action.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        confirmEndTesting = false
                        scope.launch {
                            runCatching { repo.api.adminEndTesting() }.onSuccess {
                                snackbarHostState.showSnackbar(if (it.ok) "Testing phase ended." else (it.error ?: "Action failed"))
                                refreshProgramme()
                                refreshParticipants()
                                refreshStats()
                            }
                        }
                    }
                ) { Text("End testing & reset") }
            },
            dismissButton = { TextButton(onClick = { confirmEndTesting = false }) { Text("Cancel") } },
        )
    }

    // Start programme dialog
    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Start the programme?") },
            text = { Text("This starts the 50-day programme for all registered participants and closes the join window. This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    confirmStart = false
                    scope.launch {
                        runCatching { repo.api.adminStartProgramme() }.onSuccess {
                            snackbarHostState.showSnackbar(if (it.ok) "Programme started." else (it.error ?: "Action failed"))
                            refreshProgramme()
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
    if (stats == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Overview — ${stats.today_date}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ParticipantsTab(
    participants: List<Participant>,
    loading: Boolean,
    sessionToken: String? = null,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onSelectInactive: () -> Unit,
    onOpenDetail: (Participant) -> Unit,
    onAdjust: (Participant) -> Unit,
    onDelete: (Participant) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(ParticipantSortOption.POINTS_DESC) }

    val filteredList = remember(participants, searchQuery, sortOption) {
        val q = searchQuery.trim().lowercase()
        val filtered = if (q.isBlank()) {
            participants
        } else {
            participants.filter {
                it.displayName.lowercase().contains(q) ||
                it.email.lowercase().contains(q) ||
                it.displayTag.lowercase().contains(q)
            }
        }
        when (sortOption) {
            ParticipantSortOption.POINTS_DESC -> filtered.sortedByDescending { it.points }
            ParticipantSortOption.NEWEST -> filtered.sortedByDescending { it.created_at.orEmpty() }
            ParticipantSortOption.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            ParticipantSortOption.DAYS_DESC -> filtered.sortedByDescending { it.days_completed }
        }
    }

    val nonAdminsCount = remember(participants) { participants.count { it.role != "admin" } }
    val allSelected = remember(selectedIds, nonAdminsCount) {
        nonAdminsCount > 0 && selectedIds.size >= nonAdminsCount
    }

    Column(Modifier.fillMaxSize()) {
        // Controls header: count, search, sort
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${participants.size} participant${if (participants.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (selectionMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onSelectInactive) {
                            Text("Select inactive")
                        }
                        TextButton(onClick = { onSelectAll(!allSelected) }) {
                            Text(if (allSelected) "Deselect all" else "Select all")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by name, tag, or email") },
                leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, "Clear search", Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Sort row chips
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Sort, "Sort", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                ParticipantSortOption.entries.forEach { option ->
                    FilterChip(
                        selected = sortOption == option,
                        onClick = { sortOption = option },
                        label = { Text(option.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        if (loading && participants.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }
        } else if (filteredList.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isNotBlank()) "No participants match '$searchQuery'." else "No participants found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList, key = { it.id }) { p ->
                    ParticipantCard(
                        p = p,
                        selectionMode = selectionMode,
                        isSelected = selectedIds.contains(p.id),
                        sessionToken = sessionToken,
                        onToggleSelect = { onToggleSelect(p.id) },
                        onOpenDetail = { onOpenDetail(p) },
                        onAdjust = { onAdjust(p) },
                        onDelete = { onDelete(p) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantCard(
    p: Participant,
    selectionMode: Boolean,
    isSelected: Boolean,
    sessionToken: String? = null,
    onToggleSelect: () -> Unit,
    onOpenDetail: () -> Unit,
    onAdjust: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isProtectedAdmin = p.role == "admin"

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
            if (selectionMode) {
                if (!isProtectedAdmin) onToggleSelect()
            } else {
                onOpenDetail()
            }
        }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    enabled = !isProtectedAdmin,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Avatar with photo support
            UserAvatar(
                avatarId = p.avatar_id,
                userId = p.id,
                pictureVersion = p.picture_version,
                sessionToken = sessionToken,
                size = 42.dp,
                contentDescription = null,
                modifier = Modifier.clip(CircleShape)
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = p.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = p.displayTag,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    if (p.role == "admin") {
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Admin",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                val readingTimeFormatted = remember(p.reading_seconds) {
                    val hrs = p.reading_seconds / 3600
                    val mins = (p.reading_seconds % 3600) / 60
                    "${hrs} h ${mins} m read"
                }
                Text(
                    text = "${p.points} pts · ${p.days_completed} days · $readingTimeFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!selectionMode) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Filled.MoreVert, "Actions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Award or remove points") },
                            onClick = {
                                menuExpanded = false
                                onAdjust()
                            }
                        )
                        if (!isProtectedAdmin) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgrammeTab(programme: AdminProgramme?, onStart: () -> Unit, onEndTesting: () -> Unit) {
    if (programme == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Programme controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        // Contract: "Started Mon 12 Oct 2026 · Joining closes Wed 21 Oct 2026 · 8 days left"
        val statusLine = programme.status_summary ?: when {
            programme.concluded -> "Concluded"
            programme.started -> "Started ${programme.start_formatted ?: (programme.start_at?.take(10) ?: "")} · Joining closes ${programme.join_window_last_day_formatted ?: (programme.join_window_closes_at?.take(10) ?: "")} · ${programme.days_remaining} days left"
            else -> "Not started"
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        StatRow("Status", when {
            programme.concluded -> "Concluded"
            programme.started -> "Running"
            else -> "Not started"
        })
        Spacer(Modifier.height(8.dp))
        StatRow("Start date", programme.start_formatted ?: (programme.start_at?.take(10) ?: "Not set"))
        Spacer(Modifier.height(8.dp))
        StatRow("Join window closes", programme.join_window_last_day_formatted ?: (programme.join_window_closes_at?.take(10) ?: "Not set"))
        Spacer(Modifier.height(8.dp))
        StatRow("Days remaining", if (programme.started) "${programme.days_remaining} days left" else "10 days")
        Spacer(Modifier.height(8.dp))
        StatRow("Programme end date", programme.programme_end_date?.take(10) ?: (programme.end_date_preview?.take(10) ?: "2026-12-28"))
        Spacer(Modifier.height(8.dp))
        StatRow("Registration", if (programme.registration_open) "Open" else "Closed")
        Spacer(Modifier.height(20.dp))

        if (!programme.started) {
            ExpressiveButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("Start programme")
            }
            Spacer(Modifier.height(12.dp))
        }

        val resetEnabled = programme.reset_enabled && !programme.started
        OutlinedButton(
            onClick = onEndTesting,
            enabled = resetEnabled,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            border = BorderStroke(
                1.dp,
                if (resetEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("End testing phase & reset")
        }
        if (!resetEnabled) {
            Spacer(Modifier.height(6.dp))
            Text(
                programme.reset_disabled_reason ?: "The programme has started; reset is disabled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PasswordTab(repo: Repository, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var currentVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Change admin password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Changing the password invalidates all other active admin sessions. Minimum 12 characters, mixing letters and numbers or symbols.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = currentPassword,
            onValueChange = { currentPassword = it; localError = null },
            label = { Text("Current password") },
            singleLine = true,
            visualTransformation = if (currentVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { currentVisible = !currentVisible }) {
                    Icon(if (currentVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it; localError = null },
            label = { Text("New password (min 12 chars)") },
            singleLine = true,
            visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { newVisible = !newVisible }) {
                    Icon(if (newVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; localError = null },
            label = { Text("Confirm new password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        localError?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))

        ExpressiveButton(
            onClick = {
                if (newPassword.length < 12) {
                    localError = "New password must be at least 12 characters long"
                    return@ExpressiveButton
                }
                if (newPassword != confirmPassword) {
                    localError = "New passwords do not match"
                    return@ExpressiveButton
                }
                scope.launch {
                    busy = true
                    localError = null
                    try {
                        val res = repo.api.adminChangePassword(currentPassword, newPassword)
                        if (res.ok) {
                            onMessage("Admin password updated. Other sessions invalidated.")
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                        } else {
                            localError = res.error ?: "Password change failed"
                        }
                    } catch (e: Exception) {
                        localError = e.message ?: "Network error"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) ExpressiveButtonLoader() else Text("Update password")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParticipantDetailScreen(
    userId: String,
    repo: Repository,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onParticipantChanged: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<ParticipantDetail?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    var showRemovePictureDialog by remember { mutableStateOf(false) }
    var removingPicture by remember { mutableStateOf(false) }

    fun loadDetail() {
        loading = true
        errorMessage = null
        scope.launch {
            try {
                val res = repo.api.adminParticipantDetail(userId)
                if (res.participant != null) {
                    detail = res.participant
                } else {
                    errorMessage = "Failed to load participant details."
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load details."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(userId) {
        loadDetail()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(detail?.displayName ?: "Participant Detail") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { loadDetail() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        )

        if (loading && detail == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExpressiveLoading()
            }
        } else if (errorMessage != null && detail == null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { loadDetail() }) { Text("Retry") }
                }
            }
        } else if (detail != null) {
            val d = detail!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Card
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(
                                    avatarId = d.avatar_id,
                                    userId = d.id,
                                    pictureVersion = d.picture_version,
                                    sessionToken = repo.session.sessionToken,
                                    size = 56.dp,
                                    contentDescription = null,
                                    modifier = Modifier.clip(CircleShape)
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(d.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(d.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(12.dp))
                            // Metrics Grid
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Total Points", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${d.total_points} pts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Days Completed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${d.days_completed} / 50", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Total Reading", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(d.totalReadingTimeFormatted, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Average Reading", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(d.avgReadingTimeFormatted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Column {
                                    Text("Notes (Counts Only)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${d.notes_count} (${d.observations_count} obs, ${d.questions_count} q)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Reset Latest Day Action
                            if (d.latest_completed_day != null) {
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { showResetDialog = true },
                                    enabled = !resetting,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (resetting) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Resetting Day ${d.latest_completed_day}...")
                                    } else {
                                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Reset Latest Day (Day ${d.latest_completed_day})", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (d.picture_version != null) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { showRemovePictureDialog = true },
                                        enabled = !removingPicture,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (removingPicture) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Removing picture...")
                                        } else {
                                            Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Remove picture", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Days Section Title
                item {
                    Text("Reading Days History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                // Days List
                items(d.days, key = { it.day_number }) { day ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (day.is_latest_completed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Day ${day.day_number}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (day.is_latest_completed) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                "Latest",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                if (day.date != null) {
                                    Text(day.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (day.completed) "Completed · ${day.readingTimeFormatted}" else if (day.reading_seconds > 0) "In Progress · ${day.readingTimeFormatted}" else "Pending",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (day.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Quiz: ${day.quiz_score ?: "—"}  |  Points: ${day.points_earned}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Remove Picture Confirmation Dialog (Item 7d)
        if (showRemovePictureDialog && detail != null) {
            val d = detail!!
            AlertDialog(
                onDismissRequest = { if (!removingPicture) showRemovePictureDialog = false },
                title = { Text("Remove picture") },
                text = {
                    Text("Remove ${d.displayName}'s picture? They will go back to their illustration avatar.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            removingPicture = true
                            scope.launch {
                                try {
                                    val res = repo.api.adminRemoveProfilePicture(d.id)
                                    if (res.ok) {
                                        snackbarHostState.showSnackbar("Picture removed for ${d.displayName}")
                                        showRemovePictureDialog = false
                                        loadDetail()
                                        onParticipantChanged()
                                    } else {
                                        snackbarHostState.showSnackbar(res.error ?: "Failed to remove picture.")
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "Failed to remove picture.")
                                } finally {
                                    removingPicture = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove picture")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showRemovePictureDialog = false },
                        enabled = !removingPicture
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Confirmation Dialog
        if (showResetDialog && detail?.latest_completed_day != null) {
            val targetDay = detail!!.latest_completed_day!!
            AlertDialog(
                onDismissRequest = { if (!resetting) showResetDialog = false },
                title = { Text("Reset Day $targetDay for ${detail!!.displayName}?") },
                text = {
                    Text(
                        "This will reverse all points awarded for Day $targetDay (reading, quiz, and streak bonus). The user will have to re-read for at least 5 minutes and retake the quiz. This action is audited."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            resetting = true
                            scope.launch {
                                try {
                                    val res = repo.api.adminResetLatestDay(userId)
                                    if (res.ok) {
                                        snackbarHostState.showSnackbar(
                                            "Day ${res.day_number} reset successfully: ${res.points_reversed} points reversed."
                                        )
                                        showResetDialog = false
                                        loadDetail()
                                        onParticipantChanged()
                                    } else {
                                        snackbarHostState.showSnackbar(res.error ?: "Failed to reset day.")
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "Failed to reset day.")
                                } finally {
                                    resetting = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset Day")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showResetDialog = false },
                        enabled = !resetting
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminNotesTab(
    repo: Repository,
    participants: List<Participant>,
) {
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<com.forgebuild.forgehouse50.data.AdminNoteItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var typeFilter by remember { mutableStateOf("all-visible") }
    var selectedUserId by remember { mutableStateOf("") }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    var userMenuOpen by remember { mutableStateOf(false) }
    var dayMenuOpen by remember { mutableStateOf(false) }

    fun refreshNotes() {
        scope.launch {
            loading = true
            runCatching {
                repo.api.adminNotes(
                    type = typeFilter,
                    userId = selectedUserId.ifBlank { null },
                    day = selectedDay,
                    q = query.ifBlank { null }
                )
            }.onSuccess {
                notes = it.notes
            }.onFailure {
                notes = emptyList()
            }
            loading = false
        }
    }

    LaunchedEffect(typeFilter, selectedUserId, selectedDay) {
        refreshNotes()
    }

    LaunchedEffect(query) {
        kotlinx.coroutines.delay(300)
        refreshNotes()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search notes or references") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // Type filter row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = typeFilter == "all-visible",
                onClick = { typeFilter = "all-visible" },
                label = { Text("All Visible") }
            )
            FilterChip(
                selected = typeFilter == "observation",
                onClick = { typeFilter = "observation" },
                label = { Text("Observations") }
            )
            FilterChip(
                selected = typeFilter == "question",
                onClick = { typeFilter = "question" },
                label = { Text("Questions") }
            )
        }

        Spacer(Modifier.height(6.dp))

        // Dropdown filter chips for Person and Day
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box {
                val selectedUser = participants.firstOrNull { it.id == selectedUserId }
                FilterChip(
                    selected = selectedUserId.isNotBlank(),
                    onClick = { userMenuOpen = true },
                    label = { Text(selectedUser?.full_name?.ifBlank { null } ?: selectedUser?.name ?: "All People") }
                )
                DropdownMenu(
                    expanded = userMenuOpen,
                    onDismissRequest = { userMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All People") },
                        onClick = {
                            selectedUserId = ""
                            userMenuOpen = false
                        }
                    )
                    participants.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.full_name?.ifBlank { null } ?: p.name) },
                            onClick = {
                                selectedUserId = p.id
                                userMenuOpen = false
                            }
                        )
                    }
                }
            }

            Box {
                FilterChip(
                    selected = selectedDay != null,
                    onClick = { dayMenuOpen = true },
                    label = { Text(if (selectedDay != null) "Day $selectedDay" else "All Days") }
                )
                DropdownMenu(
                    expanded = dayMenuOpen,
                    onDismissRequest = { dayMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Days") },
                        onClick = {
                            selectedDay = null
                            dayMenuOpen = false
                        }
                    )
                    (1..50).forEach { d ->
                        DropdownMenuItem(
                            text = { Text("Day $d") },
                            onClick = {
                                selectedDay = d
                                dayMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (loading && notes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No observation or question notes found matching the filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(notes, key = { it.id }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = item.author_full_name.ifBlank { item.author_name.ifBlank { "Participant" } },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = item.reference.ifBlank { "${item.book} ${item.chapter}" },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (item.day != null) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Text(
                                                "Day ${item.day}",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = if (item.type == "observation") MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = if (item.type == "observation") "Observation" else "Question",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.type == "observation") MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = item.text.ifBlank { item.body },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (item.created_at.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = item.created_at.take(10),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

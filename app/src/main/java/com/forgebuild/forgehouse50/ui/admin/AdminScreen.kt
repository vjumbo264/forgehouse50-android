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
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Programme") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Password") })
            }

            when (tab) {
                0 -> StatsTab(stats)
                1 -> ParticipantsTab(
                    participants = participants,
                    loading = participantsLoading,
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
                    onAdjust = { adjustTarget = it },
                    onDelete = { singleDeleteTarget = it }
                )
                2 -> ProgrammeTab(
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
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onSelectInactive: () -> Unit,
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
                        onToggleSelect = { onToggleSelect(p.id) },
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
    onToggleSelect: () -> Unit,
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
            .clickable(enabled = selectionMode && !isProtectedAdmin) { onToggleSelect() }
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

            // Avatar
            Image(
                painter = painterResource(AvatarAssets.resFor(p.avatar_id)),
                contentDescription = null,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
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

                Text(
                    text = "${p.points} pts · ${p.days_completed} days · ${p.notes_count} notes",
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
        StatRow("Status", when {
            programme.concluded -> "Concluded"
            programme.started -> "Running"
            else -> "Not started"
        })
        Spacer(Modifier.height(8.dp))
        StatRow("Start date", programme.start_at?.take(10) ?: "Not set")
        Spacer(Modifier.height(8.dp))
        StatRow("Join window closes", programme.join_window_closes_at?.take(10) ?: "Not set")
        Spacer(Modifier.height(8.dp))
        StatRow("Programme end date", programme.programme_end_date?.take(10) ?: "Pending")
        Spacer(Modifier.height(8.dp))
        StatRow("Registration", if (programme.registration_open) "Open" else "Closed")

        Spacer(Modifier.height(20.dp))
        if (!programme.started) {
            ExpressiveButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("Start programme")
            }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedButton(
            onClick = onEndTesting,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("End testing phase & reset")
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

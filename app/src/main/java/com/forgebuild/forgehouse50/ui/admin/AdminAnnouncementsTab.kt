package com.forgebuild.forgehouse50.ui.admin

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.Announcement
import com.forgebuild.forgehouse50.data.AnnouncementLatestResponse
import com.forgebuild.forgehouse50.data.AppCache
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.announcements.TodayAnnouncementCard
import com.forgebuild.forgehouse50.ui.announcements.formatAnnouncementDate
import kotlinx.coroutines.launch
import java.time.Instant

private const val MAX_TITLE_CHARS = 120
private const val MAX_BODY_CHARS = 4000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAnnouncementsTab(
    repo: Repository,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Announcement>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var composerOpen by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Announcement?>(null) }
    var itemTitle by remember { mutableStateOf("") }
    var itemBody by remember { mutableStateOf("") }
    var showComposerPreview by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    var deletingItem by remember { mutableStateOf<Announcement?>(null) }
    var showMemberPreviewCard by remember { mutableStateOf(true) }

    fun syncTodayCache(allItems: List<Announcement>) {
        val activeItems = allItems.filter { it.status == "active" }
        val archivedItems = allItems.filter { it.status == "archived" }
        AppCache.saveLatestAnnouncement(
            context,
            AnnouncementLatestResponse(
                ok = true,
                total_active = activeItems.size,
                total_archived = archivedItems.size,
                total_all = allItems.size,
                announcement = activeItems.firstOrNull()
            )
        )
        AppCache.saveAnnouncements(context, allItems)
    }

    fun refresh() {
        scope.launch {
            loading = true
            runCatching { repo.api.adminAnnouncements() }.onSuccess {
                items = it.announcements
                syncTodayCache(it.announcements)
            }.onFailure {
                items = emptyList()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    if (composerOpen) {
        AlertDialog(
            onDismissRequest = { if (!busy) composerOpen = false },
            title = {
                Text(
                    if (editingItem != null) "Edit Announcement" else "Post Announcement",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = { showComposerPreview = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (!showComposerPreview) "Write (Selected)" else "Write")
                        }
                        OutlinedButton(
                            onClick = { showComposerPreview = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (showComposerPreview) "Preview (On)" else "Member Preview")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (showComposerPreview) {
                        Text(
                            "How members see this on Today:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        TodayAnnouncementCard(
                            ann = Announcement(
                                id = editingItem?.id ?: "preview",
                                title = itemTitle.ifBlank { "Announcement title" },
                                body = itemBody.ifBlank { "Your full announcement message will appear here without truncation." },
                                status = "active",
                                created_at = editingItem?.created_at ?: Instant.now().toString()
                            ),
                            isUnread = true,
                            totalCount = (items.size + if (editingItem == null) 1 else 0).coerceAtLeast(1),
                            showArchiveAction = true,
                        )
                    } else {
                        OutlinedTextField(
                            value = itemTitle,
                            onValueChange = { if (it.length <= MAX_TITLE_CHARS) itemTitle = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${itemTitle.length} / $MAX_TITLE_CHARS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End)
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = itemBody,
                            onValueChange = { if (it.length <= MAX_BODY_CHARS) itemBody = it },
                            label = { Text("Full message (shown in full on Today)") },
                            minLines = 5,
                            maxLines = 12,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${itemBody.length} / $MAX_BODY_CHARS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            val r = if (editingItem == null) {
                                runCatching {
                                    repo.api.adminCreateAnnouncement(itemTitle.trim(), itemBody.trim(), "active")
                                }
                            } else {
                                runCatching {
                                    repo.api.adminUpdateAnnouncement(
                                        editingItem!!.id,
                                        itemTitle.trim(),
                                        itemBody.trim(),
                                        editingItem!!.status
                                    )
                                }
                            }
                            r.onSuccess {
                                composerOpen = false
                                refresh()
                                snackbarHostState.showSnackbar(
                                    if (editingItem == null) "Announcement posted to Today screen." else "Announcement updated."
                                )
                            }.onFailure {
                                snackbarHostState.showSnackbar(it.message ?: "Failed to save announcement")
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && itemTitle.isNotBlank() && itemBody.isNotBlank()
                ) {
                    Text(if (editingItem == null) "Post Live" else "Save Changes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { composerOpen = false },
                    enabled = !busy
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (deletingItem != null) {
        val target = deletingItem!!
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("Delete permanently?") },
            text = {
                Text("This permanently deletes "${target.title}" from both Today and the Past Announcements archive. To remove it from Today while keeping it in the archive, use Retire instead.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val r = runCatching { repo.api.adminDeleteAnnouncement(target.id) }
                            deletingItem = null
                            r.onSuccess {
                                refresh()
                                snackbarHostState.showSnackbar("Announcement deleted.")
                            }.onFailure {
                                snackbarHostState.showSnackbar(it.message ?: "Failed to delete announcement")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val currentActive = items.firstOrNull { it.status == "active" }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingItem = null
                    itemTitle = ""
                    itemBody = ""
                    showComposerPreview = false
                    composerOpen = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Post Announcement")
            }
        }
    ) { padding ->
        if (loading && items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                item(key = "member_preview_header") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Visibility,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Live Member View (Today Screen)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(onClick = { showMemberPreviewCard = !showMemberPreviewCard }) {
                                    Text(if (showMemberPreviewCard) "Hide" else "Show")
                                }
                            }
                            if (showMemberPreviewCard) {
                                Spacer(Modifier.height(8.dp))
                                if (currentActive != null) {
                                    TodayAnnouncementCard(
                                        ann = currentActive,
                                        isUnread = true,
                                        totalCount = items.size.coerceAtLeast(1),
                                        showArchiveAction = true,
                                    )
                                } else {
                                    Text(
                                        "No active announcement on Today screen right now. Members can still view ${items.size} archived announcement(s).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (items.isEmpty()) {
                    item(key = "empty_state") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Campaign,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No announcements posted yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    items(items, key = { it.id }) { ann ->
                        val isActive = ann.status == "active"
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(
                                1.dp,
                                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Text(
                                            text = if (isActive) "Active on Today" else "Retired (Archive only)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        formatAnnouncementDate(ann.created_at),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    ann.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    ann.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            editingItem = ann
                                            itemTitle = ann.title
                                            itemBody = ann.body
                                            showComposerPreview = false
                                            composerOpen = true
                                        }
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit")
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            val nextStatus = if (isActive) "archived" else "active"
                                            scope.launch {
                                                val r = runCatching {
                                                    repo.api.adminUpdateAnnouncement(ann.id, ann.title, ann.body, nextStatus)
                                                }
                                                r.onSuccess {
                                                    refresh()
                                                    snackbarHostState.showSnackbar(
                                                        if (nextStatus == "archived") "Retired to Past Announcements archive."
                                                        else "Republished to Today screen."
                                                    )
                                                }.onFailure {
                                                    snackbarHostState.showSnackbar(it.message ?: "Could not update status")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            if (isActive) Icons.Filled.Archive else Icons.Filled.Unarchive,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isActive) "Retire" else "Republish")
                                    }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { deletingItem = ann }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error
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

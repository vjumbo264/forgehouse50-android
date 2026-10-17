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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.Announcement
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.announcements.formatAnnouncementDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAnnouncementsTab(
    repo: Repository,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Announcement>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var composerOpen by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Announcement?>(null) }
    var itemTitle by remember { mutableStateOf("") }
    var itemBody by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    var deletingItem by remember { mutableStateOf<Announcement?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            runCatching { repo.api.adminAnnouncements() }.onSuccess {
                items = it.announcements
            }.onFailure {
                items = emptyList()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    // Composer / Edit Dialog
    if (composerOpen) {
        AlertDialog(
            onDismissRequest = { if (!busy) composerOpen = false },
            title = {
                Text(
                    if (editingItem != null) "Edit Announcement" else "New Announcement",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = itemTitle,
                        onValueChange = { if (it.length <= 80) itemTitle = it },
                        label = { Text("Title (max 80 chars)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${itemTitle.length} / 80",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = itemBody,
                        onValueChange = { if (it.length <= 1000) itemBody = it },
                        label = { Text("Announcement message (max 1000 chars)") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${itemBody.length} / 1000",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            val r = if (editingItem == null) {
                                runCatching { repo.api.adminCreateAnnouncement(itemTitle.trim(), itemBody.trim()) }
                            } else {
                                runCatching { repo.api.adminUpdateAnnouncement(editingItem!!.id, itemTitle.trim(), itemBody.trim()) }
                            }
                            r.onSuccess {
                                composerOpen = false
                                refresh()
                                snackbarHostState.showSnackbar("Announcement saved.")
                            }.onFailure {
                                snackbarHostState.showSnackbar(it.message ?: "Failed to save announcement")
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && itemTitle.isNotBlank() && itemBody.isNotBlank()
                ) {
                    Text("Save Announcement")
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

    // Delete Confirmation Dialog
    if (deletingItem != null) {
        val target = deletingItem!!
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("Delete announcement?") },
            text = { Text("Are you sure you want to delete \"${target.title}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val r = runCatching { repo.api.adminDeleteAnnouncement(target.id) }
                            r.onSuccess {
                                deletingItem = null
                                refresh()
                                snackbarHostState.showSnackbar("Announcement deleted.")
                            }.onFailure {
                                snackbarHostState.showSnackbar(it.message ?: "Failed to delete")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingItem = null
                    itemTitle = ""
                    itemBody = ""
                    composerOpen = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New Announcement")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Programme Announcements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (loading && items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No announcements posted yet. Tap + to write one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize().padding(bottom = 70.dp)
                ) {
                    items(items, key = { it.id }) { ann ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        ann.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            editingItem = ann
                                            itemTitle = ann.title
                                            itemBody = ann.body
                                            composerOpen = true
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { deletingItem = ann },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                Text(
                                    formatAnnouncementDate(ann.created_at),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(Modifier.height(6.dp))
                                Text(
                                    ann.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

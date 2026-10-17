package com.forgebuild.forgehouse50.ui.admin

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgebuild.forgehouse50.data.LeaderInboxItem
import com.forgebuild.forgehouse50.data.Participant
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.WhatsAppFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LeadersInboxTab(
    repo: Repository,
    participants: List<Participant>,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<LeaderInboxItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var statusFilter by remember { mutableStateOf("all") }
    var typeFilter by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var replyTargetItem by remember { mutableStateOf<LeaderInboxItem?>(null) }
    var replyText by remember { mutableStateOf("") }
    var replyBusy by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            runCatching {
                repo.api.adminInbox(
                    status = statusFilter,
                    type = typeFilter.ifBlank { null },
                    q = query.ifBlank { null }
                )
            }.onSuccess {
                items = it.items
            }.onFailure {
                items = emptyList()
            }
            loading = false
        }
    }

    LaunchedEffect(statusFilter, typeFilter) {
        refresh()
    }

    LaunchedEffect(query) {
        delay(300)
        refresh()
    }

    // Reply Dialog
    if (replyTargetItem != null) {
        val target = replyTargetItem!!
        AlertDialog(
            onDismissRequest = { if (!replyBusy) replyTargetItem = null },
            title = {
                Text(
                    if (target.reply != null) "Edit Leader Reply" else "Write Leader Reply",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "${target.author_full_name} · ${target.reference}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        target.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("Reply from leadership...") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            replyBusy = true
                            val r = runCatching { repo.api.adminInboxReply(target.id, replyText) }
                            r.onSuccess {
                                replyTargetItem = null
                                refresh()
                                snackbarHostState.showSnackbar("Reply saved.")
                            }.onFailure {
                                snackbarHostState.showSnackbar(it.message ?: "Failed to save reply")
                            }
                            replyBusy = false
                        }
                    },
                    enabled = !replyBusy && replyText.isNotBlank()
                ) {
                    Text("Save Reply")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { replyTargetItem = null },
                    enabled = !replyBusy
                ) {
                    Text("Cancel")
                }
            }
        )
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
            label = { Text("Search author, note, or reference") },
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

        // Status & Type Filters
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("all" to "All Status", "new" to "Unanswered", "answered" to "Answered", "published" to "Published", "hidden" to "Hidden").forEach { (k, label) ->
                FilterChip(
                    selected = statusFilter == k,
                    onClick = { statusFilter = k },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
            listOf("" to "All Types", "observation" to "Observations", "question" to "Questions").forEach { (k, label) ->
                FilterChip(
                    selected = typeFilter == k,
                    onClick = { typeFilter = k },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Multi-select & Copy for WhatsApp Bar
        val answeredItems = items.filter { it.reply != null || it.status == "answered" || it.status == "published" }
        if (answeredItems.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedIds.isNotEmpty() && selectedIds.size == answeredItems.size,
                        onCheckedChange = { all ->
                            selectedIds = if (all) answeredItems.map { it.id }.toSet() else emptySet()
                        }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (selectedIds.isEmpty()) "Select for WhatsApp" else "${selectedIds.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            val selectedList = items.filter { it.id in selectedIds }
                            if (selectedList.isEmpty()) {
                                scope.launch { snackbarHostState.showSnackbar("Select at least one answered note to copy.") }
                                return@Button
                            }
                            val formatted = WhatsAppFormatter.formatBatch(selectedList)
                            clipboardManager.setText(AnnotatedString(formatted))
                            Toast.makeText(context, "Copied for WhatsApp", Toast.LENGTH_SHORT).show()
                            scope.launch { snackbarHostState.showSnackbar("Copied for WhatsApp") }
                        },
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (loading && items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No items found matching the filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.id }) { item ->
                    val isSelected = item.id in selectedIds
                    val isAnswered = item.reply != null || item.status == "answered" || item.status == "published"
                    val isPublished = item.status == "published"
                    val isHidden = item.status == "hidden"

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            // Top Row: Selection checkbox (if answered), Author info, Status Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isAnswered) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }

                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            item.author_full_name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (!item.show_name) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                                            ) {
                                                Text(
                                                    "Anon in group",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        "${item.type.replaceFirstChar { it.uppercase() }} · ${item.reference}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Status badge
                                Surface(
                                    shape = CircleShape,
                                    color = when (item.status) {
                                        "published" -> MaterialTheme.colorScheme.tertiaryContainer
                                        "answered" -> MaterialTheme.colorScheme.secondaryContainer
                                        "hidden" -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                ) {
                                    Text(
                                        text = when (item.status) {
                                            "published" -> "Published"
                                            "answered" -> "Answered"
                                            "hidden" -> "Hidden"
                                            else -> "New"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when (item.status) {
                                            "published" -> MaterialTheme.colorScheme.onTertiaryContainer
                                            "answered" -> MaterialTheme.colorScheme.onSecondaryContainer
                                            "hidden" -> MaterialTheme.colorScheme.onErrorContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Note text
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Existing reply block
                            if (item.reply != null && item.reply.text.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(
                                            "Leader Reply:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            item.reply.text,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Action buttons row: Reply, Publish/Unpublish, Hide/Unhide
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        replyText = item.reply?.text.orEmpty()
                                        replyTargetItem = item
                                    },
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (item.reply != null) "Edit Reply" else "Reply", style = MaterialTheme.typography.labelSmall)
                                }

                                if (isAnswered) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                val nextPub = !isPublished
                                                val r = runCatching { repo.api.adminInboxPublish(item.id, nextPub) }
                                                r.onSuccess {
                                                    refresh()
                                                    snackbarHostState.showSnackbar(if (nextPub) "Published to community" else "Unpublished")
                                                }.onFailure {
                                                    snackbarHostState.showSnackbar(it.message ?: "Action failed")
                                                }
                                            }
                                        },
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isPublished) "Unpublish" else "Publish", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val nextHide = !isHidden
                                            val r = runCatching { repo.api.adminInboxHide(item.id, nextHide) }
                                            r.onSuccess {
                                                refresh()
                                                snackbarHostState.showSnackbar(if (nextHide) "Hidden from inbox" else "Unhidden")
                                            }.onFailure {
                                                snackbarHostState.showSnackbar(it.message ?: "Action failed")
                                            }
                                        }
                                    },
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        if (isHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isHidden) "Unhide" else "Hide", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

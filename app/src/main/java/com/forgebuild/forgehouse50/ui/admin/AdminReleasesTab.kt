package com.forgebuild.forgehouse50.ui.admin

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.forgebuild.forgehouse50.data.AdminReleaseAuditEntry
import com.forgebuild.forgehouse50.data.AdminReleaseItem
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.runSuspendCatching
import com.forgebuild.forgehouse50.update.UpdateManager
import kotlinx.coroutines.launch

@Composable
fun AdminReleasesTab(
    repo: Repository,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var releases by remember { mutableStateOf<List<AdminReleaseItem>>(emptyList()) }
    var auditLog by remember { mutableStateOf<List<AdminReleaseAuditEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busyVersion by remember { mutableStateOf<Int?>(null) }
    var confirmEveryoneTarget by remember { mutableStateOf<AdminReleaseItem?>(null) }

    suspend fun loadReleases() {
        loading = true
        runSuspendCatching { repo.api.adminReleases() }
            .onSuccess { res ->
                releases = res.releases
                auditLog = res.audit_log
                loading = false
            }
            .onFailure { err ->
                loading = false
                snackbarHostState.showSnackbar(err.message ?: "Could not load app releases")
            }
    }

    fun applyRollout(versionCode: Int, action: String, confirmWord: String? = null) {
        scope.launch {
            busyVersion = versionCode
            runSuspendCatching {
                repo.api.adminUpdateRelease(
                    versionCode = versionCode,
                    action = action,
                    confirm = confirmWord,
                )
            }.onSuccess {
                busyVersion = null
                val label = when (action) {
                    "admins_only" -> "Released v$versionCode to admins"
                    "everyone" -> "Released v$versionCode to everyone"
                    else -> "Pulled v$versionCode back to hold"
                }
                loadReleases()
                UpdateManager.check(context, repo.session, force = true)
                snackbarHostState.showSnackbar(label)
            }.onFailure { err ->
                busyVersion = null
                snackbarHostState.showSnackbar(err.message ?: "Failed to update rollout state")
            }
        }
    }

    LaunchedEffect(Unit) {
        loadReleases()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Controlled Update Rollouts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "New builds start on hold. Test with admins first, then release to everyone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { scope.launch { loadReleases() } }, enabled = !loading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh releases")
            }
        }

        if (loading && releases.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (releases.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Text(
                                "No builds synced yet. Tap Refresh to sync GitHub releases.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                items(releases, key = { it.version_code }) { rel ->
                    val isBusy = busyVersion == rel.version_code
                    val (stateText, containerCol, contentCol) = when (rel.rollout_state) {
                        "everyone" -> Triple(
                            "Released to everyone",
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        "admins_only" -> Triple(
                            "Admins only",
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        else -> Triple(
                            "On hold",
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val sizeMb = if (rel.size_bytes > 0) "%.1f MB".format(rel.size_bytes / (1024f * 1024f)) else ""

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.SystemUpdate,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${rel.version_name} (code ${rel.version_code})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Surface(
                                    color = containerCol,
                                    contentColor = contentCol,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        stateText,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            val metaParts = buildList {
                                if (sizeMb.isNotBlank()) add(sizeMb)
                                if (!rel.updated_by.isNullOrBlank()) add("by ${rel.updated_by}")
                                if (rel.updated_at.isNotBlank()) add(rel.updated_at.take(16).replace('T', ' '))
                            }
                            if (metaParts.isNotEmpty()) {
                                Text(
                                    metaParts.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (rel.changelog.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                rel.changelog.take(4).forEach { line ->
                                    Text(
                                        "• $line",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilledTonalButton(
                                        onClick = { applyRollout(rel.version_code, "admins_only") },
                                        enabled = !isBusy && rel.rollout_state != "admins_only",
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Release to admins")
                                    }
                                    OutlinedButton(
                                        onClick = { applyRollout(rel.version_code, "hold") },
                                        enabled = !isBusy && rel.rollout_state != "hold",
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Pull back to hold")
                                    }
                                }
                                Button(
                                    onClick = { confirmEveryoneTarget = rel },
                                    enabled = !isBusy && rel.rollout_state != "everyone",
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Release to everyone")
                                }
                            }
                        }
                    }
                }

                if (auditLog.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Recent Rollout Changes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(auditLog, key = { it.id }) { entry ->
                        Text(
                            "${entry.created_at.take(16).replace('T', ' ')} — ${entry.summary ?: entry.action}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    confirmEveryoneTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmEveryoneTarget = null },
            title = { Text("Release ${target.version_name} to everyone?") },
            text = {
                Text(
                    "Every ForgeHouse 50 participant will be prompted to download and install ${target.version_name} (code ${target.version_code}). Make sure admins have tested this build first.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val vc = target.version_code
                        confirmEveryoneTarget = null
                        applyRollout(vc, "everyone", confirmWord = "RELEASE_TO_EVERYONE")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("Confirm release to everyone")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEveryoneTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

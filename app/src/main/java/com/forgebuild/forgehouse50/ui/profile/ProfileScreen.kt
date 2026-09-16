package com.forgebuild.forgehouse50.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import com.forgebuild.forgehouse50.data.local.DownloadedTranslation
import com.forgebuild.forgehouse50.data.MeResponse
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.AvatarAssets
import com.forgebuild.forgehouse50.ui.formatBytes
import com.forgebuild.forgehouse50.ui.formatDurationShort
import com.forgebuild.forgehouse50.work.KeepAliveService
import com.forgebuild.forgehouse50.work.ReminderWorker
import kotlinx.coroutines.launch

// combined_fixes_v1 Issue 2: bundled avatar assets — all 41, offline, no fetch.
private val AVATAR_IDS = AvatarAssets.IDS

/** Profile: stats, badges, illustration avatar picker (same asset set as the
 *  web app), downloaded-translation manager, sign out. */
@Composable
fun ProfileScreen(repo: Repository, onSignedOut: () -> Unit, onManageTranslations: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<MeResponse?>(null) }
    var pickingAvatar by remember { mutableStateOf(false) }
    var translations by remember { mutableStateOf<List<DownloadedTranslation>>(emptyList()) }
    var totalBytes by remember { mutableStateOf(0L) }

    suspend fun refreshTranslations() {
        translations = repo.downloadedTranslations()
        totalBytes = repo.translationsTotalSizeBytes()
    }

    LaunchedEffect(Unit) {
        runCatching { repo.api.me() }.onSuccess { me = it }
        refreshTranslations()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(AvatarAssets.resFor(me?.avatar_id)),
                contentDescription = "Avatar",
                modifier = Modifier.size(64.dp).clip(CircleShape).clickable { pickingAvatar = true },
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text((listOfNotNull(me?.name, me?.surname?.takeIf { it.isNotBlank() }).joinToString(" ")).ifBlank { repo.session.userName ?: "" }, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Text(me?.email ?: "", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { pickingAvatar = true }) { Text("Change avatar") }
            }
        }
        Spacer(Modifier.height(16.dp))
        val s = me?.stats
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("${s?.points ?: 0} points · ${s?.days_completed ?: 0} days · ${s?.chapters ?: 0} chapters",
                    style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text("Streak ${s?.streak_current ?: 0} (best ${s?.streak_longest ?: 0}) · " +
                    formatDurationShort(s?.reading_seconds ?: 0) + " read · ${s?.notes_total ?: 0} notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val badges = me?.badges ?: emptyList()
        if (badges.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Badges", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            badges.forEach { b ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${b.name} — ${b.description}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // leaderboard_audio_removal_offline_bible_v1 / ISSUE 5d: Downloaded
        // content is now about OFFLINE BIBLE TRANSLATIONS only (audio is gone).
        // KJV is bundled (not listed). Shows total storage + per-translation
        // removal that frees it.
        Text("Offline Bible translations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("King James Version is bundled with the app — always available offline.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("Downloaded translations use ${formatBytes(totalBytes)} on this device.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (translations.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("No other translations downloaded yet.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Spacer(Modifier.height(8.dp))
            translations.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name.ifBlank { t.translation }, style = MaterialTheme.typography.bodyMedium)
                        Text("${t.chapterCount} chapters · ${formatBytes(t.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            runCatching { repo.removeTranslation(t.translation) }
                            refreshTranslations()
                        }
                    }) { Icon(Icons.Filled.Delete, "Remove ${t.name}", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onManageTranslations, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.LibraryBooks, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Manage translations")
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            scope.launch {
                runCatching { repo.api.logout() }
                ReminderWorker.cancel(context)
                KeepAliveService.stop(context)
                repo.session.clear()
                onSignedOut()
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        Spacer(Modifier.height(32.dp))
    }

    if (pickingAvatar) {
        AlertDialog(
            onDismissRequest = { pickingAvatar = false },
            title = { Text("Choose your avatar") },
            text = {
                LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AVATAR_IDS) { id ->
                        Image(painter = painterResource(AvatarAssets.resFor(id)), contentDescription = id,
                            modifier = Modifier.size(48.dp).clip(CircleShape).clickable {
                                scope.launch {
                                    runCatching { repo.api.setAvatar(id) }.onSuccess {
                                        me = me?.copy(avatar_id = id)
                                    }
                                    pickingAvatar = false
                                }
                            }, contentScale = ContentScale.Crop)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingAvatar = false }) { Text("Cancel") } },
        )
    }
}

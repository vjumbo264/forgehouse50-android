package com.forgebuild.forgehouse50.ui.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.forgebuild.forgehouse50.data.MeResponse
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.formatBytes
import com.forgebuild.forgehouse50.ui.formatDurationShort
import com.forgebuild.forgehouse50.work.ReminderWorker
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

private val AVATAR_IDS = (1..30).map { "avatar-%02d".format(it) }
private fun avatarUrl(id: String) = "https://forgehouse50.pages.dev/avatars/$id.png"

/** Profile: stats, badges, illustration avatar picker (same asset set as the
 *  web app), downloaded-content manager, sign out. */
@Composable
fun ProfileScreen(repo: Repository, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<MeResponse?>(null) }
    var pickingAvatar by remember { mutableStateOf(false) }
    var chaptersStored by remember { mutableStateOf(0) }
    var audioBytes by remember { mutableStateOf(0L) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { repo.api.me() }.onSuccess { me = it }
        chaptersStored = repo.scriptureChapterCount()
        audioBytes = repo.audioTotalSizeBytes()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = me?.avatar_id?.let { avatarUrl(it) },
                contentDescription = "Avatar",
                modifier = Modifier.size(64.dp).clip(CircleShape).clickable { pickingAvatar = true },
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(me?.name ?: repo.session.userName ?: "", style = MaterialTheme.typography.titleLarge,
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
        Text("Downloaded content", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("$chaptersStored chapters of scripture stored offline · ${formatBytes(audioBytes)} of audio",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { confirmClear = true }) { Text("Clear downloaded content") }
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            scope.launch {
                runCatching { repo.api.logout() }
                ReminderWorker.cancel(context)
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
                        AsyncImage(model = avatarUrl(id), contentDescription = id,
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
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear downloaded content?") },
            text = { Text("All offline scripture text and downloaded audio will be removed from this device. You can download it again any time.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        repo.clearOfflineContent()
                        chaptersStored = 0; audioBytes = 0
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep") } },
        )
    }
}

package com.forgebuild.forgehouse50.ui.read

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.Translation
import com.forgebuild.forgehouse50.ui.formatBytes
import kotlinx.coroutines.launch
import com.forgebuild.forgehouse50.ui.ExpressiveButtonLoader
import com.forgebuild.forgehouse50.ui.ExpressiveLinearProgress

/**
 * leaderboard_audio_removal_offline_bible_v1 / ISSUE 5b (Android).
 *
 * Manage Translations — reachable from the Read screen. KJV is bundled (no
 * download action, always available). Every other API translation is listed
 * with its estimated size and a Download action; downloading stores the full
 * New Testament on-device for fully-offline reading. Downloaded translations
 * can be removed to free storage. NO audio options anywhere (Issue 3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationsScreen(repo: Repository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var translations by remember { mutableStateOf<List<Translation>>(emptyList()) }
    var downloaded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sizes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var downloading by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refresh() {
        val dl = repo.downloadedTranslations()
        downloaded = dl.map { it.translation }.toSet() + Repository.KJV_ID
        sizes = dl.associate { it.translation to it.sizeBytes }
    }

    LaunchedEffect(Unit) {
        runCatching { repo.ensureBundledKjvImported() }
        refresh()
        runCatching { repo.api.translations() }.onSuccess {
            translations = it.translations.filter { t -> t.versewell && t.id != Repository.KJV_ID }
        }.onFailure { error = it.message }
        loading = false
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Bible translations") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            // Bundled KJV — always available, no download action.
            Card(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("King James Version", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Bundled — always available offline", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.CheckCircle, "Included", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Download for offline reading", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))

            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExpressiveButtonLoader()
                    Spacer(Modifier.width(8.dp))
                    Text("Loading available translations…", style = MaterialTheme.typography.bodySmall)
                }
            } else if (translations.isEmpty()) {
                Text("No other translations available right now.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(translations, key = { it.id }) { t ->
                        val isDownloaded = t.id in downloaded
                        val isDownloading = downloading == t.id
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        val sizeText = sizes[t.id]?.let { formatBytes(it) }
                                        Text(
                                            if (isDownloaded) "Downloaded${sizeText?.let { " · $it" } ?: ""}"
                                            else "Not downloaded",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    when {
                                        isDownloading -> ExpressiveButtonLoader()
                                        isDownloaded -> IconButton(onClick = {
                                            scope.launch {
                                                runCatching { repo.removeTranslation(t.id) }
                                                refresh()
                                            }
                                        }) { Icon(Icons.Filled.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
                                        else -> TextButton(onClick = {
                                            scope.launch {
                                                downloading = t.id; progress = 0f; error = null
                                                runCatching {
                                                    repo.downloadTranslation(t.id, t.name) { d, tot ->
                                                        progress = if (tot > 0) d.toFloat() / tot else 0f
                                                    }
                                                }.onFailure { error = it.message }
                                                downloading = null
                                                refresh()
                                            }
                                        }) {
                                            Icon(Icons.Filled.Download, null, modifier = Modifier.width(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Download")
                                        }
                                    }
                                }
                                if (isDownloading) {
                                    Spacer(Modifier.height(8.dp))
                                    // Part A/F: real M3 Expressive wavy progress, driven by genuine bytes.
                                    ExpressiveLinearProgress(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                    Text("Downloading… ${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

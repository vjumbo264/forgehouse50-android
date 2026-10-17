package com.forgebuild.forgehouse50.ui.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.Translation
import com.forgebuild.forgehouse50.data.TranslationDownloadState
import com.forgebuild.forgehouse50.data.local.DownloadedTranslation
import com.forgebuild.forgehouse50.ui.ExpressiveButtonLoader
import com.forgebuild.forgehouse50.ui.ExpressiveLinearProgress
import com.forgebuild.forgehouse50.ui.formatBytes
import kotlinx.coroutines.launch

/**
 * Item 1c — Single, unified Bible translations screen.
 * Replaces duplicate screens/entry points.
 * Features:
 * - "Downloaded" section: lists on-device versions, size, clear "Active" indicator, tap to activate, delete with confirmation.
 *   Bundled KJV marked "Built in" and cannot be deleted.
 * - "Available" section: lists available versions with language and size. Search bar if > 8 versions.
 * - Live M3 Expressive wavy progress indicator immediately upon tapping Download, byte progress, Cancel action.
 * - Persistent download queue (concurrency 2), survives leaving screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationsScreen(repo: Repository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var availableList by remember { mutableStateOf<List<Translation>>(emptyList()) }
    var downloadedList by remember { mutableStateOf<List<DownloadedTranslation>>(emptyList()) }
    var activeTranslationId by remember { mutableStateOf(repo.session.translationId ?: Repository.KJV_ID) }
    var searchQuery by remember { mutableStateOf("") }
    var loadingAvailable by remember { mutableStateOf(true) }
    var globalError by remember { mutableStateOf<String?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<DownloadedTranslation?>(null) }

    val downloadStates by repo.downloadManager.states.collectAsState()

    suspend fun refreshData() {
        downloadedList = repo.downloadedTranslations()
        activeTranslationId = repo.session.translationId ?: Repository.KJV_ID
    }

    LaunchedEffect(Unit) {
        runCatching { repo.ensureBundledKjvImported() }
        refreshData()
        runCatching { repo.api.translations() }
            .onSuccess { res ->
                availableList = res.translations.filter { t -> t.versewell && t.id != Repository.KJV_ID }
            }
            .onFailure { t ->
                globalError = t.message ?: "Could not load translations from server."
            }
        loadingAvailable = false
    }

    // Auto-refresh downloaded list when any download completes
    LaunchedEffect(downloadStates) {
        if (downloadStates.values.any { it is TranslationDownloadState.Done }) {
            refreshData()
        }
    }

    val downloadedIds = remember(downloadedList) {
        (downloadedList.map { it.translation } + Repository.KJV_ID).toSet()
    }

    val filteredAvailable = remember(availableList, downloadedIds, searchQuery) {
        val nonDownloaded = availableList.filter { it.id !in downloadedIds && repo.normalizeTranslation(it.id) !in downloadedIds }
        if (searchQuery.isBlank()) nonDownloaded
        else nonDownloaded.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.code.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bible translations", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Error banner if any
            globalError?.let { err ->
                item(key = "global_error") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { globalError = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Search bar if > 8 available translations
            if (availableList.size > 8) {
                item(key = "search_field") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search translations…", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    )
                }
            }

            // SECTION: DOWNLOADED
            item(key = "downloaded_header") {
                Text(
                    "Downloaded",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            // Built-in KJV item
            item(key = "downloaded_kjv") {
                val isKjvActive = activeTranslationId == Repository.KJV_ID || activeTranslationId == "kjv"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            repo.session.translationId = Repository.KJV_ID
                            activeTranslationId = Repository.KJV_ID
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isKjvActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "King James Version (KJV)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (isKjvActive) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "ACTIVE",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Built-in · Always available offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Built-in",
                            tint = if (isKjvActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Other downloaded items
            items(downloadedList, key = { "dl_${it.translation}" }) { item ->
                val normId = repo.normalizeTranslation(item.translation)
                val isActive = activeTranslationId == normId || activeTranslationId == item.translation
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            repo.session.translationId = normId
                            activeTranslationId = normId
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    item.name.ifBlank { item.translation.removePrefix("versewell-").uppercase() },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (isActive) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "ACTIVE",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Downloaded · ${formatBytes(item.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { deleteConfirmTarget = item }
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete ${item.name}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // SECTION: AVAILABLE
            item(key = "available_header") {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Available for Download",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            if (loadingAvailable) {
                item(key = "available_loading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ExpressiveButtonLoader()
                        Spacer(Modifier.width(10.dp))
                        Text("Loading available translations…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (filteredAvailable.isEmpty()) {
                item(key = "available_empty") {
                    Text(
                        if (searchQuery.isNotBlank()) "No translations match \"$searchQuery\"."
                        else "All available translations have been downloaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(filteredAvailable, key = { "avail_${it.id}" }) { t ->
                    val normId = repo.normalizeTranslation(t.id)
                    val dlState = downloadStates[normId] ?: TranslationDownloadState.Idle

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        t.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "English · Complete New Testament",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                when (dlState) {
                                    is TranslationDownloadState.Idle -> {
                                        FilledTonalButton(
                                            onClick = {
                                                repo.downloadManager.download(t.id, t.name)
                                            },
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Download,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Download")
                                        }
                                    }
                                    is TranslationDownloadState.Connecting -> {
                                        IconButton(
                                            onClick = { repo.downloadManager.cancel(t.id) }
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                                        }
                                    }
                                    is TranslationDownloadState.Progressing -> {
                                        IconButton(
                                            onClick = { repo.downloadManager.cancel(t.id) }
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                                        }
                                    }
                                    is TranslationDownloadState.Done -> {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                "Downloaded",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    is TranslationDownloadState.Failed -> {
                                        FilledTonalButton(
                                            onClick = { repo.downloadManager.retry(t.id, t.name) },
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Retry")
                                        }
                                    }
                                }
                            }

                            // Progress row when connecting or progressing
                            when (dlState) {
                                is TranslationDownloadState.Connecting -> {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Connecting…",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Waiting for download",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                is TranslationDownloadState.Progressing -> {
                                    Spacer(Modifier.height(8.dp))
                                    ExpressiveLinearProgress(
                                        progress = { dlState.progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Downloading… ${(dlState.progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (dlState.totalBytes > 0) {
                                            Text(
                                                "${formatBytes(dlState.bytesRead)} / ${formatBytes(dlState.totalBytes)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                is TranslationDownloadState.Failed -> {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        dlState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    deleteConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text("Remove Translation") },
            text = {
                Text(
                    "Are you sure you want to remove \"${target.name}\"? This will free ${formatBytes(target.sizeBytes)} of on-device storage. You can download it again anytime."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = target
                        deleteConfirmTarget = null
                        scope.launch {
                            repo.removeTranslation(toDelete.translation)
                            if (activeTranslationId == toDelete.translation || activeTranslationId == repo.normalizeTranslation(toDelete.translation)) {
                                repo.session.translationId = Repository.KJV_ID
                                activeTranslationId = Repository.KJV_ID
                            }
                            refreshData()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

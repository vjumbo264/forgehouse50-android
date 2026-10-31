package com.forgebuild.forgehouse50.ui.read

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.audio.AudioLogic
import com.forgebuild.forgehouse50.audio.BulkAudioEstimate
import com.forgebuild.forgehouse50.audio.TranslationAudioUiState
import com.forgebuild.forgehouse50.data.ReaderErrorSanitizer
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.Translation
import com.forgebuild.forgehouse50.data.TranslationDownloadState
import com.forgebuild.forgehouse50.data.local.DownloadedTranslation
import com.forgebuild.forgehouse50.data.runSuspendCatching
import com.forgebuild.forgehouse50.ui.ExpressiveButtonLoader
import com.forgebuild.forgehouse50.ui.ExpressiveLinearProgress
import com.forgebuild.forgehouse50.ui.formatBytes
import kotlinx.coroutines.launch

/**
 * Single, unified Bible translations screen with manual per-translation audio management (Item i10):
 * - "Downloaded" section: lists on-device text versions, size, "ACTIVE" indicator, tap to activate, delete text.
 *   Bundled KJV marked "Built-in" and cannot have its text deleted.
 * - "Available" section: lists available versions with search bar if > 8 versions.
 * - Per-translation Audio controls (ONLY for the 5 audio translations reported available by VerseWell):
 *   manual download with pre-download data-size estimate + Wi-Fi recommendation dialog, immediate live progress,
 *   pause/resume/cancel, SHA-256 skip-if-valid, on-device audio storage display, and audio deletion.
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

    // Audio confirmation dialogs: Pair(versionCodeUpper, displayName)
    var audioDownloadConfirmTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var audioDeleteConfirmTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val downloadStates by repo.downloadManager.states.collectAsState()
    val versionsCatalog by repo.audioManager.versionsCatalog.collectAsState()
    val audioManifests by repo.audioManager.manifests.collectAsState()
    val audioStates by repo.audioManager.translationStates.collectAsState()
    val localAudioByVersion by repo.audioManager.localChaptersByVersion.collectAsState()

    suspend fun refreshData() {
        downloadedList = repo.downloadedTranslations()
        activeTranslationId = repo.session.translationId ?: Repository.KJV_ID
        repo.audioManager.refreshLocalIndex()
    }

    LaunchedEffect(Unit) {
        runCatching { repo.ensureBundledKjvImported() }
        refreshData()
        launch { repo.audioManager.refreshVersionsCatalog() }
        runSuspendCatching { repo.api.translations() }
            .onSuccess { res ->
                availableList = res.translations.filter { t -> t.versewell && t.id != Repository.KJV_ID }
            }
            .onFailure { t ->
                globalError = ReaderErrorSanitizer.toUserMessage(t, "Could not load translations from server.")
            }
        loadingAvailable = false
    }

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

    @Composable
    fun MaybeAudioSection(translationIdOrCode: String, displayName: String) {
        val code = AudioLogic.normalizeVersionCode(translationIdOrCode)
        val localRows = localAudioByVersion[code].orEmpty()
        val isAudioSupported = AudioLogic.isTranslationAudioAvailable(
            translationIdOrCode = code,
            versionsCatalog = versionsCatalog,
            cachedManifest = audioManifests[code],
            localVerifiedChaptersCount = localRows.size,
        )
        if (!isAudioSupported) return

        val estimate = AudioLogic.estimateBulkDownload(
            versionCodeOrId = code,
            manifest = audioManifests[code],
            catalogAudio = versionsCatalog[code],
            localChapters = localRows,
        )
        val uiState = audioStates[code] ?: TranslationAudioUiState.Idle

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        Spacer(Modifier.height(10.dp))
        TranslationAudioSection(
            displayName = displayName,
            estimate = estimate,
            uiState = uiState,
            onRequestBulkDownload = {
                audioDownloadConfirmTarget = code to displayName
            },
            onResumeDownload = {
                repo.audioManager.startTranslationDownload(code)
            },
            onPauseDownload = {
                repo.audioManager.pauseTranslationDownload(code)
            },
            onCancelDownload = {
                repo.audioManager.cancelTranslationDownload(code)
            },
            onRequestDeleteAudio = {
                audioDeleteConfirmTarget = code to displayName
            },
        )
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
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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

                        MaybeAudioSection(Repository.KJV_ID, "King James Version (KJV)")
                    }
                }
            }

            // Other downloaded items
            items(downloadedList, key = { "dl_${it.translation}" }) { item ->
                val normId = repo.normalizeTranslation(item.translation)
                val isActive = activeTranslationId == normId || activeTranslationId == item.translation
                val labelName = item.name.ifBlank { item.translation.removePrefix("versewell-").uppercase() }
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
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        labelName,
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

                        MaybeAudioSection(normId, labelName)
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
                                            onClick = { repo.downloadManager.download(t.id, t.name) },
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Download")
                                        }
                                    }
                                    is TranslationDownloadState.Connecting, is TranslationDownloadState.Progressing -> {
                                        IconButton(onClick = { repo.downloadManager.cancel(t.id) }) {
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
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Retry")
                                        }
                                    }
                                }
                            }

                            when (dlState) {
                                is TranslationDownloadState.Connecting -> {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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

                            MaybeAudioSection(normId, t.name)
                        }
                    }
                }
            }
        }
    }

    // Delete text translation confirmation dialog
    deleteConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text("Remove Translation") },
            text = {
                Text(
                    "Are you sure you want to remove \"${target.name}\"? This will free ${formatBytes(target.sizeBytes)} of on-device text storage. You can download it again anytime."
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

    // Pre-download data size estimate + Wi-Fi recommendation dialog (Section 6.8)
    audioDownloadConfirmTarget?.let { (code, displayName) ->
        val est = AudioLogic.estimateBulkDownload(
            versionCodeOrId = code,
            manifest = audioManifests[code],
            catalogAudio = versionsCatalog[code],
            localChapters = localAudioByVersion[code].orEmpty(),
        )
        val sizeText = when {
            est.remainingBytes > 0L -> "~${AudioLogic.formatAudioBytes(est.remainingBytes)}"
            est.totalBytes > 0L -> "~${AudioLogic.formatAudioBytes(est.totalBytes)}"
            else -> "~380 MB"
        }
        AlertDialog(
            onDismissRequest = { audioDownloadConfirmTarget = null },
            title = { Text("Download audio") },
            text = {
                Text(
                    "Download offline New Testament audio for $displayName?\n\n" +
                        "Estimated data size: $sizeText (${est.remainingChapters} of ${est.totalChapters} chapters remaining).\n\n" +
                        "We recommend using Wi-Fi to save mobile data. You can pause or resume anytime, and chapters already on your phone are skipped automatically."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        audioDownloadConfirmTarget = null
                        repo.audioManager.startTranslationDownload(code)
                    }
                ) {
                    Text("Download audio")
                }
            },
            dismissButton = {
                TextButton(onClick = { audioDownloadConfirmTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete translation audio confirmation dialog
    audioDeleteConfirmTarget?.let { (code, displayName) ->
        val localBytes = localAudioByVersion[code].orEmpty().sumOf { it.sizeBytes }
        AlertDialog(
            onDismissRequest = { audioDeleteConfirmTarget = null },
            title = { Text("Delete audio") },
            text = {
                Text(
                    "Remove downloaded audio for $displayName? This frees ${AudioLogic.formatAudioBytes(localBytes)} of storage on your device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        audioDeleteConfirmTarget = null
                        scope.launch {
                            repo.audioManager.deleteTranslationAudio(code)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete audio")
                }
            },
            dismissButton = {
                TextButton(onClick = { audioDeleteConfirmTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TranslationAudioSection(
    displayName: String,
    estimate: BulkAudioEstimate,
    uiState: TranslationAudioUiState,
    onRequestBulkDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRequestDeleteAudio: () -> Unit,
) {
    val isAllOnDevice = estimate.totalChapters > 0 && estimate.downloadedChapters >= estimate.totalChapters
    val hasPartialOnDevice = estimate.downloadedChapters in 1 until estimate.totalChapters

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Headphones,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Audio narration",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val subtitle = when {
                    isAllOnDevice ->
                        "Complete offline · ${AudioLogic.formatAudioBytes(estimate.downloadedBytes)} (${estimate.downloadedChapters} chapters)"
                    hasPartialOnDevice ->
                        "${estimate.downloadedChapters} / ${estimate.totalChapters} chapters on device · ${AudioLogic.formatAudioBytes(estimate.downloadedBytes)}"
                    estimate.totalBytes > 0L ->
                        "Est. ${AudioLogic.formatAudioBytes(estimate.totalBytes)} · ${estimate.totalChapters} chapters"
                    else ->
                        "${estimate.totalChapters} chapters · Wi-Fi recommended"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (uiState) {
                is TranslationAudioUiState.Connecting, is TranslationAudioUiState.Downloading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPauseDownload, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Pause, contentDescription = "Pause audio download")
                        }
                        IconButton(onClick = onCancelDownload, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel audio download")
                        }
                    }
                }
                is TranslationAudioUiState.Paused, is TranslationAudioUiState.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(
                            onClick = onResumeDownload,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume")
                        }
                        if (estimate.downloadedBytes > 0L) {
                            IconButton(onClick = onRequestDeleteAudio, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete $displayName audio",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                else -> {
                    if (isAllOnDevice) {
                        IconButton(onClick = onRequestDeleteAudio, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete $displayName audio",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else if (hasPartialOnDevice) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(
                                onClick = onResumeDownload,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Resume")
                            }
                            IconButton(onClick = onRequestDeleteAudio, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete $displayName audio",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onRequestBulkDownload,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download audio")
                        }
                    }
                }
            }
        }

        when (uiState) {
            is TranslationAudioUiState.Connecting -> {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "Preparing audio manifest…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is TranslationAudioUiState.Downloading -> {
                val p = uiState.progress
                Spacer(Modifier.height(8.dp))
                ExpressiveLinearProgress(
                    progress = { p.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val chLabel = if (p.currentBook.isNotBlank()) {
                        "${p.chaptersDone}/${p.totalChapters} · ${p.currentBook} ${p.currentChapter}"
                    } else {
                        "${p.chaptersDone}/${p.totalChapters} chapters"
                    }
                    Text(
                        "${(p.fraction * 100).toInt()}% ($chLabel)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (p.totalBytes > 0L) {
                        Text(
                            "${AudioLogic.formatAudioBytes(p.bytesDownloaded)} / ${AudioLogic.formatAudioBytes(p.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            is TranslationAudioUiState.Paused -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Paused at ${uiState.chaptersDone} / ${uiState.totalChapters} chapters (${AudioLogic.formatAudioBytes(uiState.bytesDownloaded)} saved).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            is TranslationAudioUiState.Failed -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    uiState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> {}
        }
    }
}

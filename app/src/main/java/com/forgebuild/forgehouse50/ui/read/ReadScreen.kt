package com.forgebuild.forgehouse50.ui.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.Assignment
import com.forgebuild.forgehouse50.data.DayResponse
import com.forgebuild.forgehouse50.data.PassageResponse
import com.forgebuild.forgehouse50.data.Repository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Read screen (leaderboard_audio_removal_offline_bible_v1).
 *
 * ISSUE 3: the entire audio player (seek/skip-5s/play, per-chapter +
 * download-all-for-today) and all audio availability/network calls are
 * removed — audio no longer exists upstream.
 *
 * ISSUE 5: scripture resolves offline-first via [Repository] (bundled KJV or
 * a downloaded translation), so reading works with zero network once a
 * translation is on-device. The translation selector only offers translations
 * that are actually available on-device; a "Translations" action opens the
 * download manager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadScreen(
    repo: Repository,
    day: Int,
    onBack: () -> Unit,
    onOpenQuiz: (Int) -> Unit,
    onAddNote: (Int) -> Unit,
    onManageTranslations: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var dayData by remember { mutableStateOf<DayResponse?>(null) }
    var dayLoaded by remember { mutableStateOf(false) }
    var translation by remember { mutableStateOf(repo.session.translationId ?: Repository.KJV_ID) }
    var availableTranslations by remember { mutableStateOf<List<String>>(listOf(Repository.KJV_ID)) }
    var passageError by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(0) }
    var passage by remember { mutableStateOf<PassageResponse?>(null) }
    var loadingPassage by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(day) {
        // Import bundled KJV + discover which translations are on-device.
        runCatching { repo.ensureBundledKjvImported() }
        val onDevice = buildList {
            add(Repository.KJV_ID)
            repo.downloadedTranslations().forEach { add(it.translation) }
        }
        availableTranslations = onDevice
        val resolved = when {
            translation in onDevice -> translation
            else -> Repository.KJV_ID
        }
        translation = resolved
        repo.session.translationId = resolved
        runCatching { repo.api.day(day) }.onSuccess {
            dayData = it
            completed = it.progress.completed
        }.onFailure { error = it.message }
        dayLoaded = true
    }

    val assignments = dayData?.assignments ?: emptyList()
    val current: Assignment? = assignments.getOrNull(selected)
    val scroll = rememberScrollState()

    // Passage — keyed on the assignment identity + resolved translation, so it
    // always re-fires when its inputs are ready. Served from the on-device
    // store with no network call for bundled/downloaded translations.
    val assignmentKey = current?.let { "${it.book}:${it.chapter_start}:${it.chapter_end}" } ?: ""
    LaunchedEffect(assignmentKey, translation, reloadToken) {
        val a = current ?: return@LaunchedEffect
        if (translation.isBlank()) return@LaunchedEffect
        loadingPassage = true
        passageError = null
        runCatching { repo.getPassage(a.book, a.chapter_start, a.chapter_end, translation) }
            .onSuccess { passage = it }
            .onFailure { passage = null; passageError = it.message ?: "Could not load this passage." }
        loadingPassage = false
    }

    // Reading-time tracking: report 30s chunks while the screen is open.
    LaunchedEffect(day, completed) {
        if (!completed) {
            while (true) {
                delay(30_000)
                runCatching { repo.api.addReadingTime(day, 30) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Day $day") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onManageTranslations) {
                        Icon(Icons.Filled.LibraryBooks, "Manage translations")
                    }
                    IconButton(onClick = { onAddNote(day) }) { Icon(Icons.Filled.Edit, "Add note") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }

            // Translation selector — only translations available on-device
            // (bundled KJV + any downloaded). KJV is the default.
            if (availableTranslations.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableTranslations.forEach { t ->
                        FilterChip(
                            selected = translation == t,
                            onClick = { translation = t; repo.session.translationId = t },
                            label = { Text(t.removePrefix("versewell-").uppercase()) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Chapter chips (one per assignment)
            if (assignments.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    assignments.forEachIndexed { i, a ->
                        val label = if (a.chapter_start == a.chapter_end) "${a.book} ${a.chapter_start}"
                            else "${a.book} ${a.chapter_start}–${a.chapter_end}"
                        FilterChip(selected = selected == i, onClick = { selected = i }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Fixed-height clipped chapter pane
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .padding(horizontal = 24.dp),
            ) {
                when {
                    !dayLoaded || (loadingPassage && passage == null) -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    current == null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No reading assigned for day $day yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    passage == null -> {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(passageError ?: "Could not load this passage.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(onClick = { reloadToken++ }) { Text("Retry") }
                        }
                    }
                    else -> {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        val p = passage
                        if (p != null) {
                            Text(p.reference, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(12.dp))
                            var lastChapter = -1
                            p.verses.forEach { v ->
                                if (v.chapter != lastChapter) {
                                    lastChapter = v.chapter
                                    Spacer(Modifier.height(10.dp))
                                    Text("Chapter ${v.chapter}", style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    p.intros.firstOrNull { it.chapter == v.chapter }?.let { intro ->
                                        Spacer(Modifier.height(4.dp))
                                        Text(intro.text, style = MaterialTheme.typography.bodySmall,
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text("${v.verse}", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(28.dp).padding(top = 3.dp))
                                    Text(v.text, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            if (p.attribution.isNotBlank()) {
                                Spacer(Modifier.height(16.dp))
                                Text(p.attribution, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    }
                }
            }

            // Completion row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (completed) {
                    Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Reading complete", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { onOpenQuiz(day) }) { Text("Quiz") }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching { repo.api.completeDay(day) }.onSuccess { completed = true }
                            }
                        },
                        Modifier.fillMaxWidth(),
                    ) { Text("Mark day $day reading complete") }
                }
            }
        }
    }
}

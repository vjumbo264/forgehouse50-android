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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.forgebuild.forgehouse50.data.AudioAvailability
import com.forgebuild.forgehouse50.data.DayResponse
import com.forgebuild.forgehouse50.data.PassageResponse
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.media.AudioPlayerManager
import com.forgebuild.forgehouse50.ui.formatClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Read screen: fixed-height clipped chapter pane, seek/skip-5s audio
 * controls with audio-synced auto-scroll, single-chapter and
 * download-all-for-today persistence, completion + reading-time tracking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadScreen(
    repo: Repository,
    player: AudioPlayerManager,
    day: Int,
    onBack: () -> Unit,
    onOpenQuiz: (Int) -> Unit,
    onAddNote: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val playback by player.state.collectAsState()

    var dayData by remember { mutableStateOf<DayResponse?>(null) }
    // leaderboard_scripture_icon_fix_v1 / ISSUE 2: the passage spinner must
    // only show while the day response is still in flight or a passage fetch
    // is actually running. Previously loadingPassage initialised to true while
    // the passage effect early-returned until day data arrived — so if the
    // /read/day call ever failed, nothing ever flipped the flag false and the
    // screen spun forever ("stuck on loading").
    var dayLoaded by remember { mutableStateOf(false) }
    var translation by remember { mutableStateOf(repo.session.translationId ?: "versewell-kjv") }
    var selected by remember { mutableIntStateOf(0) }
    var passage by remember { mutableStateOf<PassageResponse?>(null) }
    var audio by remember { mutableStateOf<AudioAvailability?>(null) }
    var loadingPassage by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloading by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(day) {
        runCatching { repo.api.day(day) }.onSuccess {
            dayData = it
            completed = it.progress.completed
        }.onFailure { error = it.message }
        dayLoaded = true
        // Default translation: first VerseWell translation from the API.
        runCatching { repo.api.translations() }.onSuccess { t ->
            t.translations.firstOrNull { it.versewell }?.let { vw ->
                if (repo.session.translationId == null) {
                    translation = vw.id
                    repo.session.translationId = vw.id
                }
            }
        }
    }

    val assignments = dayData?.assignments ?: emptyList()
    val current: Assignment? = assignments.getOrNull(selected)
    val scroll = rememberScrollState()

    // Passage: persistent offline store — no network call once on-device.
    LaunchedEffect(day, selected, translation) {
        if (!dayLoaded) return@LaunchedEffect // spinner is gated on !dayLoaded below
        val a = current ?: return@LaunchedEffect
        loadingPassage = true
        runCatching { repo.getPassage(a.book, a.chapter_start, a.chapter_end, translation) }
            .onSuccess { passage = it; error = null }
            .onFailure { error = it.message }
        runCatching { repo.api.audioAvailability(a.book, a.chapter_start, a.chapter_end, translation) }
            .onSuccess { av ->
                audio = av
                val keys = av.available.map { "${it.chapter}" }.toSet()
                val present = mutableSetOf<String>()
                av.available.forEach { ch ->
                    if (repo.localAudioFile(translation, a.book, ch.chapter) != null) present.add("${ch.chapter}")
                }
                downloaded = keys intersect present
            }
        loadingPassage = false
    }

    // Position ticker for slider + auto-scroll.
    LaunchedEffect(playback.isPlaying) {
        if (playback.isPlaying) {
            while (true) { player.refresh(); delay(500) }
        }
    }

    // Audio-synced auto-scroll: while playing, keep the pane scrolling in
    // proportion to playback progress (paused = user scrolls freely).
    LaunchedEffect(playback.positionMs, playback.isPlaying) {
        if (playback.isPlaying && playback.durationMs > 0 && scroll.maxValue > 0) {
            val fraction = playback.positionMs.toFloat() / playback.durationMs
            val target = (scroll.maxValue * fraction).toInt() - scroll.viewportSize / 3
            scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue))
        }
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
                if (!dayLoaded || (loadingPassage && passage == null)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
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

            // Audio bar (when this assignment has VerseWell audio)
            val av = audio
            if (av != null && av.available.isNotEmpty() && current != null) {
                AudioBar(
                    repo = repo,
                    player = player,
                    playback = playback,
                    day = day,
                    translation = translation,
                    book = current.book,
                    availability = av,
                    downloaded = downloaded,
                    downloading = downloading,
                    onDownloading = { downloading = it },
                    onDownloaded = { downloaded = it },
                )
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

@Composable
private fun AudioBar(
    repo: Repository,
    player: AudioPlayerManager,
    playback: AudioPlayerManager.PlaybackUiState,
    day: Int,
    translation: String,
    book: String,
    availability: AudioAvailability,
    downloaded: Set<String>,
    downloading: Set<String>,
    onDownloading: (Set<String>) -> Unit,
    onDownloaded: (Set<String>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val first = availability.available.first()
    val absBase = "https://forgehouse50.pages.dev"

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val source = "$absBase${first.url}"
                val thisIsPlaying = playback.currentSource == source ||
                    playback.currentSource?.endsWith("${first.url.substringAfterLast('/')}") == true
                IconButton(onClick = {
                    if (thisIsPlaying && playback.isPlaying) player.pause()
                    else {
                        scope.launch {
                            // Prefer the persistent on-device copy (offline replay).
                            val local = repo.localAudioFile(translation, book, first.chapter)
                            player.play(local?.absolutePath ?: source, "$book ${first.chapter}")
                            runCatching { repo.api.markAudio(day) }
                        }
                    }
                }) {
                    Icon(
                        if (thisIsPlaying && playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (thisIsPlaying && playback.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { player.skipBack5s() }) { Icon(Icons.Filled.Replay5, "Back 5s") }
                IconButton(onClick = { player.skipForward5s() }) { Icon(Icons.Filled.Forward5, "Forward 5s") }
                Spacer(Modifier.weight(1f))
                // Single-chapter download (persist for offline)
                val key = "${first.chapter}"
                when {
                    key in downloaded -> Icon(Icons.Filled.DownloadDone, "Downloaded",
                        tint = MaterialTheme.colorScheme.primary)
                    key in downloading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else -> IconButton(onClick = {
                        scope.launch {
                            onDownloading(downloading + key)
                            runCatching { repo.ensureAudio(translation, book, first.chapter, source) }
                                .onSuccess { onDownloaded(downloaded + key) }
                            onDownloading(downloading - key)
                        }
                    }) { Icon(Icons.Filled.Download, "Download chapter") }
                }
            }
            if (playback.durationMs > 0) {
                Slider(
                    value = (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f),
                    onValueChange = { player.seekTo((it * playback.durationMs).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatClock(playback.positionMs), style = MaterialTheme.typography.labelSmall)
                    Text(formatClock(playback.durationMs), style = MaterialTheme.typography.labelSmall)
                }
            }
            // Download all for today
            if (availability.available.size > 1) {
                TextButton(onClick = {
                    scope.launch {
                        availability.available.forEach { ch ->
                            val k = "${ch.chapter}"
                            if (k !in downloaded && k !in downloading) {
                                onDownloading(downloading + k)
                                runCatching { repo.ensureAudio(translation, book, ch.chapter, "$absBase${ch.url}") }
                                    .onSuccess { onDownloaded(downloaded + k) }
                                onDownloading(downloading - k)
                            }
                        }
                    }
                }) { Text("Download all for today (${availability.available.size})") }
            }
        }
    }
}

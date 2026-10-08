package com.forgebuild.forgehouse50.ui.read
import com.forgebuild.forgehouse50.data.AppCache

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.forgebuild.forgehouse50.data.ChapterViewTracker
import com.forgebuild.forgehouse50.data.DayResponse
import com.forgebuild.forgehouse50.data.PassageResponse
import com.forgebuild.forgehouse50.data.Repository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.forgebuild.forgehouse50.ui.ExpressiveLoading
import com.forgebuild.forgehouse50.ui.ExpressiveButton

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
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var dayData by remember(day) { mutableStateOf(com.forgebuild.forgehouse50.data.AppCache.getDay(context, day)) }
    var dayLoaded by remember(day) { mutableStateOf(dayData != null) }
    var translation by remember { mutableStateOf(repo.session.translationId ?: Repository.KJV_ID) }
    var availableTranslations by remember { mutableStateOf<List<String>>(listOf(Repository.KJV_ID)) }
    var passageError by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(0) }
    var passage by remember { mutableStateOf<PassageResponse?>(null) }
    var loadingPassage by remember { mutableStateOf(false) }
    var completed by remember(day) { mutableStateOf(dayData?.progress?.completed ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Part D: Mark-Complete loading/feedback state.
    var completeBusy by remember { mutableStateOf(false) }
    // Issue 5: reader font scale, persisted across restarts via SessionStore.
    var fontScale by remember { mutableFloatStateOf(repo.session.fontScale) }
    // Issue 4: index into the day's flattened chapter list (one chapter shown at a time).
    var chapterIdx by remember { mutableIntStateOf(0) }
    // post_testing_polish_v1 ISSUE 4: exactly one footnote panel open at a time,
    // keyed by verse number; null = none open. Resets when the chapter changes.
    var openFootnoteVerse by remember { mutableStateOf<Int?>(null) }

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
            com.forgebuild.forgehouse50.data.AppCache.saveDay(context, day, it)
        }.onFailure { error = it.message }
        dayLoaded = true
    }

    val assignments = dayData?.assignments ?: emptyList()
    // Issue 4: flatten the day's assignments into an ordered (book, chapter) list;
    // exactly ONE chapter is fetched and rendered at a time, matching the website.
    val chapters = assignments.flatMap { a -> (a.chapter_start..a.chapter_end).map { a.book to it } }
    if (chapters.isNotEmpty() && chapterIdx > chapters.lastIndex) chapterIdx = 0
    val currentChapter: Pair<String, Int>? = chapters.getOrNull(chapterIdx)
    val scroll = rememberScrollState()

    var tracker by remember(day, chapters.size) { mutableStateOf(ChapterViewTracker(chapters)) }
    LaunchedEffect(day, chapters.size) {
        if (tracker.total != chapters.size) tracker = ChapterViewTracker(chapters)
    }

    var viewedTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(day, chapterIdx, chapters.size) {
        val ch = chapters.getOrNull(chapterIdx) ?: return@LaunchedEffect
        delay(1500)
        tracker.markViewed(ch)
        viewedTick++
    }
    @Suppress("UNUSED_EXPRESSION")
    viewedTick

    // Passage — keyed on the current chapter + resolved translation. Served from
    // the on-device store with no network call for bundled/downloaded translations.
    val chapterKey = currentChapter?.let { "${it.first}:${it.second}" } ?: ""
    LaunchedEffect(chapterKey, translation, reloadToken) {
        val cc = currentChapter ?: return@LaunchedEffect
        if (translation.isBlank()) return@LaunchedEffect
        loadingPassage = true
        passageError = null
        passage = null
        runCatching { repo.getPassage(cc.first, cc.second, cc.second, translation) }
            .onSuccess { passage = it }
            .onFailure { passage = null; passageError = it.message ?: "Could not load this chapter." }
        loadingPassage = false
        openFootnoteVerse = null
        scroll.scrollTo(0)
    }

    // Part C (2026-09-18): foreground reading-time tracking. Report 30s chunks
    // while the Read screen is composed/foreground. Failures now RETRY with the
    // pending seconds carried forward and surface as a soft banner after 3
    // consecutive failures instead of being silently dropped (the old
    // `runCatching{...}` swallowed every error, so a contract/endpoint failure
    // produced an all-zero leaderboard with no signal).
    var timeSyncError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(day) {
        var pending = 0
        var fails = 0
        while (isActive) {
            delay(30_000)
            pending += 30
            try {
                repo.api.addReadingTime(day, pending)
                pending = 0
                fails = 0
                timeSyncError = null
            } catch (e: Exception) {
                fails++
                if (fails >= 3) timeSyncError = "Reading time isn't syncing — check your connection."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Day $day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        currentChapter?.let {
                            Text("${it.first} ${it.second}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    // Premium font-size pill controls
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            TextButton(
                                onClick = {
                                    fontScale = (fontScale - 0.1f).coerceAtLeast(0.85f)
                                    repo.session.fontScale = fontScale
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) { Text("A\u2212", fontWeight = FontWeight.Bold) }
                            TextButton(
                                onClick = {
                                    fontScale = (fontScale + 0.1f).coerceAtMost(1.6f)
                                    repo.session.fontScale = fontScale
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) { Text("A+", fontWeight = FontWeight.Bold) }
                        }
                    }
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
            timeSyncError?.let {
                Text(it, color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp))
            }

            // Translation selector — only translations available on-device
            if (availableTranslations.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableTranslations.forEach { t ->
                        FilterChip(
                            selected = translation == t,
                            onClick = { translation = t; repo.session.translationId = t },
                            label = { Text(t.removePrefix("versewell-").uppercase(), fontWeight = FontWeight.SemiBold) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Previous/Next chapter navigation: calm chevron buttons with elevated center chip
            if (chapters.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { if (chapterIdx > 0) chapterIdx-- }, enabled = chapterIdx > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Previous")
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    ) {
                        Text(
                            currentChapter?.let { "${it.first} ${it.second}" } ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { if (chapterIdx < chapters.lastIndex) chapterIdx++ },
                        enabled = chapterIdx < chapters.lastIndex,
                    ) {
                        Text("Next")
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(20.dp))
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
                            ExpressiveLoading()
                        }
                    }
                    currentChapter == null -> {
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
                                            fontSize = MaterialTheme.typography.bodySmall.fontSize * fontScale,
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text("${v.verse}", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(28.dp).padding(top = 3.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(v.text, style = MaterialTheme.typography.bodyLarge,
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontScale)
                                        // Footnote marker: unmistakably interactive accent pill chip (streak_reader_support_fix_v1 / Issue 3)
                                        if (v.footnotes.isNotEmpty()) {
                                            val isOpen = openFootnoteVerse == v.verse
                                            Spacer(Modifier.height(4.dp))
                                            Surface(
                                                onClick = { openFootnoteVerse = if (isOpen) null else v.verse },
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = if (isOpen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                                tonalElevation = 2.dp,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                                ),
                                                modifier = Modifier.padding(vertical = 3.dp),
                                            ) {
                                                Row(
                                                    Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Notes,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(13.dp),
                                                    )
                                                    Text(
                                                        text = if (v.footnotes.size > 1) "${v.footnotes.size} Footnotes" else "Footnote",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }
                                            if (isOpen) {
                                                Spacer(Modifier.height(4.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    shape = MaterialTheme.shapes.large,
                                                    tonalElevation = 3.dp,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                                ) {
                                                    Column(Modifier.padding(14.dp)) {
                                                        Row(
                                                            Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                        ) {
                                                            Text(
                                                                "Footnote · Verse ${v.verse}",
                                                                style = MaterialTheme.typography.labelLarge,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary,
                                                            )
                                                            IconButton(
                                                                onClick = { openFootnoteVerse = null },
                                                                modifier = Modifier.size(24.dp),
                                                            ) {
                                                                Icon(
                                                                    Icons.Filled.Close,
                                                                    contentDescription = "Close footnote",
                                                                    modifier = Modifier.size(16.dp),
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                )
                                                            }
                                                        }
                                                        Spacer(Modifier.height(6.dp))
                                                        v.footnotes.forEach { note ->
                                                            Text(
                                                                note,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                            )
                                                            Spacer(Modifier.height(6.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
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

            // Completion row: enforced all-chapters-viewed gate (streak_reader_support_fix_v1 / Issue 5)
            val gateOpen = (viewedTick >= 0) && tracker.allViewed
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    if (completed) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Reading complete", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            ExpressiveButton(onClick = { onOpenQuiz(day) }) {
                                Icon(Icons.Filled.Quiz, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Quiz")
                            }
                        }
                    } else {
                        if (!gateOpen && tracker.total > 0) {
                            Text(
                                tracker.gateHint(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        ExpressiveButton(
                            onClick = {
                                if (completeBusy || !gateOpen) return@ExpressiveButton
                                scope.launch {
                                    completeBusy = true
                                    error = null
                                    runCatching { repo.api.completeDay(day, tracker.viewedKeys()) }
                                        .onSuccess {
                                            completed = true
                                            dayData?.let { cur ->
                                                val upd = cur.copy(progress = cur.progress.copy(completed = true))
                                                dayData = upd
                                                com.forgebuild.forgehouse50.data.AppCache.saveDay(context, day, upd)
                                            }
                                            com.forgebuild.forgehouse50.widget.WidgetRefreshWorker.refreshWidgetState(context, repo)
                                            com.forgebuild.forgehouse50.widget.ForgeHouseWidgetProvider.updateAll(context)
                                        }
                                        .onFailure { error = it.message ?: "Could not mark this day complete." }
                                    completeBusy = false
                                }
                            },
                            Modifier.fillMaxWidth(),
                            enabled = gateOpen,
                            busy = completeBusy,
                        ) { Text("Mark day $day reading complete") }
                    }
                }
            }
        }
    }
}

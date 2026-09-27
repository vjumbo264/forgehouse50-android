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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.ChapterViewTracker
import com.forgebuild.forgehouse50.data.DayResponse
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.ExpressiveButton
import com.forgebuild.forgehouse50.ui.ExpressiveLoading
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * catchup_widget_links_v1 / ISSUE 2 + ISSUE 4 — the multi-day reading session.
 *
 * Presented with 1 or 2 day numbers by the catch-up engine, this screen
 * paginates through day A's chapters, then day B's chapters (in catch-up), with
 * a PER-DAY completion gate: each day's "Finish Reading" stays visibly
 * disabled — with a "View all N chapters to finish (k/N viewed)" hint — until
 * every chapter of THAT day has actually been navigated to and displayed for a
 * minimum dwell. Completing day A unlocks day B's pane in the same session;
 * each day then has its own separate quiz, exactly as a normal single day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadSessionScreen(
    repo: Repository,
    days: List<Int>,
    onBack: () -> Unit,
    onOpenQuiz: (Int) -> Unit,
    onAddNote: (Int) -> Unit,
    onManageTranslations: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sessionDays = remember(days) { days.distinct().sorted().take(2) }

    var dayData by remember { mutableStateOf<Map<Int, DayResponse>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var translation by remember { mutableStateOf(repo.session.translationId ?: Repository.KJV_ID) }
    var availableTranslations by remember { mutableStateOf<List<String>>(listOf(Repository.KJV_ID)) }
    var completedDays by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var completeBusyDay by remember { mutableStateOf<Int?>(null) }
    var activeIdx by remember { mutableIntStateOf(0) }

    LaunchedEffect(sessionDays) {
        runCatching { repo.ensureBundledKjvImported() }
        availableTranslations = buildList {
            add(Repository.KJV_ID)
            repo.downloadedTranslations().forEach { add(it.translation) }
        }
        if (translation !in availableTranslations) translation = Repository.KJV_ID
        repo.session.translationId = translation
        val map = mutableMapOf<Int, DayResponse>()
        val done = mutableSetOf<Int>()
        var failed: String? = null
        for (d in sessionDays) {
            runCatching { repo.api.day(d) }
                .onSuccess { map[d] = it; if (it.progress.completed) done.add(d) }
                .onFailure { failed = it.message }
        }
        dayData = map
        completedDays = done
        loaded = true
        loadError = if (map.isEmpty()) failed else null
    }

    val activeDay = sessionDays.getOrNull(activeIdx)
    val activeData = activeDay?.let { dayData[it] }
    val chapters: List<Pair<String, Int>> = activeData?.assignments.orEmpty()
        .flatMap { a -> (a.chapter_start..a.chapter_end).map { a.book to it } }

    var chapterIdx by remember(activeDay) { mutableIntStateOf(0) }
    var tracker by remember(activeDay) { mutableStateOf(ChapterViewTracker(chapters)) }
    LaunchedEffect(activeDay, chapters.size) {
        if (tracker.total != chapters.size) tracker = ChapterViewTracker(chapters)
        chapterIdx = 0
    }

    // ISSUE 4 viewed-detection heuristic: a chapter counts as viewed once it has
    // been the DISPLAYED chapter for a short minimum dwell (1.5s) — requires
    // genuinely reaching every chapter via the Previous/Next navigation.
    var viewedTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(activeDay, chapterIdx, chapters.size) {
        val ch = chapters.getOrNull(chapterIdx) ?: return@LaunchedEffect
        delay(1500)
        tracker.markViewed(ch)
        viewedTick++
    }
    @Suppress("UNUSED_EXPRESSION")
    viewedTick // recomposition key for gate state

    LaunchedEffect(activeDay) {
        val d = activeDay ?: return@LaunchedEffect
        var pending = 0
        while (isActive) {
            delay(30000)
            pending += 30
            runCatching { repo.api.addReadingTime(d, pending) }.onSuccess { pending = 0 }
        }
    }

    val titleText = if (sessionDays.size > 1) {
        "Catch-up · Days " + sessionDays.joinToString(" + ")
    } else {
        "Day " + (sessionDays.firstOrNull()?.toString() ?: "")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleText) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onManageTranslations) {
                        Icon(Icons.Filled.LibraryBooks, "Manage translations")
                    }
                    if (activeDay != null) {
                        IconButton(onClick = { onAddNote(activeDay) }) {
                            Icon(Icons.Filled.Edit, "Add note")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val err = loadError
            if (err != null) {
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            if (sessionDays.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    sessionDays.forEachIndexed { i, d ->
                        val done = completedDays.contains(d)
                        val unlocked = i == 0 || completedDays.contains(sessionDays[i - 1])
                        FilterChip(
                            selected = activeIdx == i,
                            onClick = { if (unlocked) activeIdx = i },
                            enabled = unlocked,
                            label = { Text("Day " + d + if (done) " done" else "") },
                            leadingIcon = if (done) {
                                { Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

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

            if (!loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }
                return@Column
            }

            val day = activeDay
            if (day != null) {
                if (chapters.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { if (chapterIdx > 0) chapterIdx-- }, enabled = chapterIdx > 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Previous chapter")
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            chapters.getOrNull(chapterIdx)?.let { it.first + " " + it.second } ?: "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { if (chapterIdx < chapters.lastIndex) chapterIdx++ },
                            enabled = chapterIdx < chapters.lastIndex,
                        ) {
                            Text("Next chapter")
                            Spacer(Modifier.width(2.dp))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Box(Modifier.fillMaxWidth().weight(1f)) {
                    ChapterPane(
                        repo = repo,
                        chapter = chapters.getOrNull(chapterIdx),
                        translation = translation,
                        emptyMessage = "No reading assigned for day $day yet.",
                    )
                }

                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    if (completedDays.contains(day)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Day $day reading complete",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            ExpressiveButton(onClick = { onOpenQuiz(day) }) {
                                Icon(Icons.Filled.Quiz, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Quiz")
                            }
                        }
                        if (sessionDays.size > 1 && activeIdx == 0 && !completedDays.contains(sessionDays[1])) {
                            Spacer(Modifier.height(8.dp))
                            ExpressiveButton(onClick = { activeIdx = 1 }, Modifier.fillMaxWidth()) {
                                Text("Continue with day " + sessionDays[1])
                            }
                        }
                    } else {
                        val gateOpen = tracker.allViewed
                        if (!gateOpen && tracker.total > 0) {
                            Text(
                                tracker.gateHint(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        ExpressiveButton(
                            onClick = {
                                if (completeBusyDay != null || !gateOpen) return@ExpressiveButton
                                scope.launch {
                                    completeBusyDay = day
                                    loadError = null
                                    runCatching { repo.api.completeDay(day) }
                                        .onSuccess { completedDays = completedDays + day }
                                        .onFailure { loadError = it.message ?: "Could not mark this day complete." }
                                    completeBusyDay = null
                                }
                            },
                            Modifier.fillMaxWidth(),
                            enabled = gateOpen,
                            busy = completeBusyDay == day,
                        ) { Text("Finish day $day reading") }
                    }
                }
            }
        }
    }
}

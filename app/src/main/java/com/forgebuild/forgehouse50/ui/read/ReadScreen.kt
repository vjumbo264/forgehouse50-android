package com.forgebuild.forgehouse50.ui.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.AppCache
import com.forgebuild.forgehouse50.data.DayResponse
import com.forgebuild.forgehouse50.data.OfflineCompletionQueue
import com.forgebuild.forgehouse50.data.PassageResponse
import com.forgebuild.forgehouse50.data.PendingCompletion
import com.forgebuild.forgehouse50.data.PrefsReaderPositionStorage
import com.forgebuild.forgehouse50.data.PrefsTimerStorage
import com.forgebuild.forgehouse50.data.ReaderEncouragement
import com.forgebuild.forgehouse50.data.ReadingTimer
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.SavedReaderPosition
import com.forgebuild.forgehouse50.data.ScriptureReferenceUtils
import com.forgebuild.forgehouse50.data.Verse
import com.forgebuild.forgehouse50.ui.ExpressiveButton
import com.forgebuild.forgehouse50.ui.ExpressiveLinearProgress
import com.forgebuild.forgehouse50.ui.ExpressiveLoading
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Rebuilt Continuous Reader screen (Item 3):
 * - Continuous scroll across all chapters of the day (LazyColumn)
 * - Full-height content extending to bottom edge behind floating translucent bottom panel
 * - Monotonic active reading timer with idle detection (60s) and immediate touch resume
 * - Live status line with "Paused. Touch the screen to continue" on idle pause
 * - 26 sincere encouragement messages from minute 5 to 30, static after 30
 * - Chapter progress bar (0..100%) resetting per chapter
 * - "Chapter X of Y" indicators in compact top header
 * - Restores exact day, chapter, and scroll offset across navigation & restarts
 * - Less prominent chapter intro styling (muted, normal weight, subtle accent)
 * - Verse-level headings (heading_before) rendered as distinct section headings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadScreen(
    repo: Repository,
    day: Int = 1,
    sessionDays: List<Int> = listOf(day),
    onBack: () -> Unit,
    onOpenQuiz: (Int) -> Unit,
    onAddNote: (day: Int, book: String, chapter: Int, verse: String?, type: String?) -> Unit,
    onManageTranslations: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = repo.session.userId ?: "local"

    val days = remember(sessionDays, day) {
        val list = sessionDays.distinct().sorted()
        if (list.isEmpty()) listOf(day) else list
    }
    var activeIdx by remember(days) {
        val idx = days.indexOf(day).coerceAtLeast(0)
        mutableIntStateOf(idx)
    }
    val activeDay = days.getOrElse(activeIdx) { day }

    var dayData by remember(activeDay) { mutableStateOf(AppCache.getDay(context, activeDay)) }
    var dayLoaded by remember(activeDay) { mutableStateOf(dayData != null) }

    var translation by remember { mutableStateOf(repo.session.translationId ?: Repository.KJV_ID) }
    var availableTranslations by remember { mutableStateOf<List<String>>(listOf(Repository.KJV_ID)) }

    var reloadToken by remember { mutableIntStateOf(0) }
    var completedDays by remember(days) {
        val done = mutableSetOf<Int>()
        for (d in days) {
            if (AppCache.getDay(context, d)?.progress?.completed == true) {
                done.add(d)
            }
        }
        mutableStateOf<Set<Int>>(done)
    }

    var error by remember { mutableStateOf<String?>(null) }
    var completeBusy by remember { mutableStateOf(false) }
    var fontScale by remember { mutableFloatStateOf(repo.session.fontScale) }
    var showTranslationPicker by remember { mutableStateOf(false) }

    val posStorage = remember { PrefsReaderPositionStorage(context) }

    LaunchedEffect(activeDay) {
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

        runCatching { repo.api.day(activeDay) }.onSuccess {
            dayData = it
            if (it.progress.completed) completedDays = completedDays + activeDay
            AppCache.saveDay(context, activeDay, it)
        }.onFailure { error = it.message }
        dayLoaded = true
    }

    val assignments = dayData?.assignments ?: emptyList()
    val chapters = remember(assignments) {
        assignments.flatMap { a -> (a.chapter_start..a.chapter_end).map { a.book to it } }
    }

    // Active reading timer with monotonic clock & idle detection
    val timer = remember(activeDay, uid) {
        ReadingTimer(uid, activeDay, PrefsTimerStorage(context))
    }
    var timerTick by remember(activeDay) { mutableIntStateOf(timer.getReadingSeconds()) }
    var isIdlePaused by remember { mutableStateOf(false) }

    LaunchedEffect(timer) {
        timer.start()
        while (true) {
            delay(1000L)
            val s = timer.tick()
            timerTick = s
            isIdlePaused = timer.isIdlePaused()
            if (s % 5 == 0) timer.persist()
        }
    }

    DisposableEffect(timer) {
        onDispose {
            timer.pause()
        }
    }

    LaunchedEffect(uid) {
        if (uid != "local") {
            runCatching { OfflineCompletionQueue(context).processQueue(uid, repo.api) }
        }
    }

    // Passages cache for all chapters of the day
    val chapterPassages = remember(activeDay, translation, reloadToken) {
        mutableStateMapOf<Pair<String, Int>, PassageResponse>()
    }
    val chapterLoading = remember(activeDay, translation, reloadToken) {
        mutableStateMapOf<Pair<String, Int>, Boolean>()
    }
    val chapterErrors = remember(activeDay, translation, reloadToken) {
        mutableStateMapOf<Pair<String, Int>, String>()
    }

    LaunchedEffect(chapters, translation, reloadToken) {
        for (ch in chapters) {
            if (!chapterPassages.containsKey(ch)) {
                scope.launch {
                    chapterLoading[ch] = true
                    runCatching { repo.getPassage(ch.first, ch.second, ch.second, translation) }
                        .onSuccess {
                            chapterPassages[ch] = it
                            chapterLoading[ch] = false
                        }
                        .onFailure {
                            chapterErrors[ch] = it.message ?: "Could not load ${ch.first} ${ch.second}"
                            chapterLoading[ch] = false
                        }
                }
            }
        }
    }

    val listState = rememberLazyListState()

    // Restore saved reading position on entry
    var restoredPosition by remember(activeDay, translation) { mutableStateOf(false) }
    LaunchedEffect(chapters, activeDay, translation) {
        if (chapters.isNotEmpty() && !restoredPosition) {
            val saved = posStorage.getPosition(uid, activeDay, translation)
            if (saved != null) {
                runCatching {
                    listState.scrollToItem(saved.scrollItemIndex, saved.scrollOffsetPx)
                }
            }
            restoredPosition = true
        }
    }

    // Map each chapter to its item indices in LazyColumn
    data class ChapterItemBounds(
        val chapterIdx: Int,
        val book: String,
        val chapter: Int,
        val startIndex: Int,
        val itemCount: Int,
    )

    val chapterBoundsList by remember(chapters, chapterPassages.toMap()) {
        derivedStateOf {
            var currentIndex = 0
            chapters.mapIndexed { idx, ch ->
                val p = chapterPassages[ch]
                // 1 item for header, 1 item for intro (if present), verses (1 each), 1 item for attribution
                var count = 1 // header
                if (p != null) {
                    if (p.intros.isNotEmpty()) count += p.intros.size
                    count += p.verses.size
                    if (p.attribution.isNotBlank()) count += 1
                } else {
                    count += 1 // loading or error row
                }
                val bounds = ChapterItemBounds(
                    chapterIdx = idx,
                    book = ch.first,
                    chapter = ch.second,
                    startIndex = currentIndex,
                    itemCount = count,
                )
                currentIndex += count
                bounds
            }
        }
    }

    // Determine currently visible chapter and progress through it
    val currentChapterInfo by remember(chapterBoundsList) {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            val bounds = chapterBoundsList.lastOrNull { it.startIndex <= firstVisible }
                ?: chapterBoundsList.firstOrNull()
            bounds
        }
    }

    val currentChapterIdx = currentChapterInfo?.chapterIdx ?: 0
    val currentChapter = chapters.getOrNull(currentChapterIdx)

    val currentChapterProgress by remember(currentChapterInfo, listState) {
        derivedStateOf {
            val bounds = currentChapterInfo ?: return@derivedStateOf 0f
            if (bounds.itemCount <= 1) return@derivedStateOf 0f
            val offset = (listState.firstVisibleItemIndex - bounds.startIndex).coerceIn(0, bounds.itemCount - 1)
            offset.toFloat() / (bounds.itemCount - 1).toFloat()
        }
    }

    var finalChapterReached by remember(activeDay) { mutableStateOf(false) }
    LaunchedEffect(currentChapterIdx, chapters.size) {
        if (chapters.isNotEmpty() && currentChapterIdx >= chapters.lastIndex) {
            finalChapterReached = true
        }
    }

    // Check if the last chapter heading is visible
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    if (chapterBoundsList.isNotEmpty()) {
        val lastStart = chapterBoundsList.last().startIndex
        if (visibleItems.any { it.index >= lastStart }) {
            finalChapterReached = true
        }
    }

    // Save reading position continuously as user scrolls
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, currentChapterIdx) {
        if (dayLoaded && chapters.isNotEmpty()) {
            posStorage.setPosition(
                uid,
                activeDay,
                translation,
                SavedReaderPosition(
                    chapterIdx = currentChapterIdx,
                    scrollItemIndex = listState.firstVisibleItemIndex,
                    scrollOffsetPx = listState.firstVisibleItemScrollOffset,
                ),
            )
        }
    }

    var selectedVerses by remember(currentChapterIdx, activeIdx) { mutableStateOf<Set<Int>>(emptySet()) }
    var openFootnoteVerse by remember(activeDay) { mutableStateOf<Int?>(null) }

    // Sync reading time every 30 seconds
    LaunchedEffect(activeDay) {
        var pending = 0
        var fails = 0
        while (isActive) {
            delay(30_000)
            pending += 30
            try {
                repo.api.addReadingTime(activeDay, pending)
                pending = 0
                fails = 0
            } catch (e: Exception) {
                fails++
            }
        }
    }

    val isCurrentDone = completedDays.contains(activeDay)
    val hasMinTime = timerTick >= 300
    val canComplete = hasMinTime && finalChapterReached

    // Root layout: full viewport with root pointer input for active timer accrual & idle reset
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        timer.onInteraction()
                        isIdlePaused = false
                    }
                }
            },
    ) {
        // Continuous scrollable content extending to physical bottom edge
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 110.dp, bottom = 160.dp, start = 20.dp, end = 20.dp),
        ) {
            if (!dayLoaded) {
                item(key = "loading_day") {
                    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        ExpressiveLoading()
                    }
                }
            } else if (chapters.isEmpty()) {
                item(key = "empty_day") {
                    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No reading assigned for day $activeDay.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            } else {
                chapters.forEachIndexed { cIdx, ch ->
                    val passage = chapterPassages[ch]
                    val isLoading = chapterLoading[ch] == true
                    val chError = chapterErrors[ch]

                    // Chapter heading item
                    item(key = "ch_head_${ch.first}_${ch.second}") {
                        Column(Modifier.fillMaxWidth().padding(top = if (cIdx == 0) 8.dp else 36.dp, bottom = 12.dp)) {
                            if (cIdx > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(bottom = 28.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                            Text(
                                "${ch.first} ${ch.second}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Intros
                    passage?.intros?.forEachIndexed { iIdx, intro ->
                        item(key = "ch_intro_${ch.first}_${ch.second}_$iIdx") {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                            ) {
                                Row(Modifier.padding(14.dp)) {
                                    Box(
                                        Modifier
                                            .width(3.dp)
                                            .height(36.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                                RoundedCornerShape(2.dp),
                                            ),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        intro.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Loading indicator for this chapter
                    if (isLoading && passage == null) {
                        item(key = "ch_loading_${ch.first}_${ch.second}") {
                            Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                                ExpressiveLoading()
                            }
                        }
                    }

                    // Error row for this chapter
                    if (chError != null && passage == null) {
                        item(key = "ch_error_${ch.first}_${ch.second}") {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    chError,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(10.dp))
                                FilledTonalButton(onClick = { reloadToken++ }) { Text("Retry") }
                            }
                        }
                    }

                    // Verses
                    passage?.verses?.forEach { v ->
                        item(key = "v_${ch.first}_${ch.second}_${v.verse}") {
                            Column(Modifier.fillMaxWidth()) {
                                // Verse-level heading (Item 4)
                                if (!v.heading_before.isNullOrBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        v.heading_before,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                    )
                                }

                                val isSelected = selectedVerses.contains(v.verse)
                                Surface(
                                    onClick = {
                                        selectedVerses = if (isSelected) selectedVerses - v.verse else selectedVerses + v.verse
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp)) {
                                        Text(
                                            "${v.verse}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(28.dp).padding(top = 3.dp),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                v.text,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontScale,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )

                                            // Footnotes chip & expandable panel
                                            if (v.footnotes.isNotEmpty()) {
                                                val isOpen = openFootnoteVerse == v.verse
                                                Spacer(Modifier.height(4.dp))
                                                Surface(
                                                    onClick = { openFootnoteVerse = if (isOpen) null else v.verse },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = if (isOpen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                                    tonalElevation = 2.dp,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                                    ),
                                                    modifier = Modifier.padding(vertical = 2.dp),
                                                ) {
                                                    Box(
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.Notes,
                                                            contentDescription = "Footnotes",
                                                            modifier = Modifier.size(15.dp),
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
                                                                    "Footnotes · Verse ${v.verse}",
                                                                    style = MaterialTheme.typography.labelLarge,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                )
                                                                IconButton(
                                                                    onClick = { openFootnoteVerse = null },
                                                                    modifier = Modifier.size(24.dp),
                                                                ) {
                                                                    Icon(Icons.Filled.Close, "Close", modifier = Modifier.size(16.dp))
                                                                }
                                                            }
                                                            Spacer(Modifier.height(6.dp))
                                                            val cleanedNotes = v.footnotes.map { cleanFootnoteText(it) }
                                                            cleanedNotes.forEachIndexed { idx, noteText ->
                                                                if (idx > 0) {
                                                                    HorizontalDivider(
                                                                        modifier = Modifier.padding(vertical = 6.dp),
                                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                                    )
                                                                }
                                                                if (cleanedNotes.size > 1) {
                                                                    Row(Modifier.fillMaxWidth()) {
                                                                        Text(
                                                                            "${idx + 1}.",
                                                                            style = MaterialTheme.typography.bodyMedium,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = MaterialTheme.colorScheme.primary,
                                                                            modifier = Modifier.width(22.dp),
                                                                        )
                                                                        Text(
                                                                            noteText,
                                                                            style = MaterialTheme.typography.bodyMedium,
                                                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale,
                                                                            color = MaterialTheme.colorScheme.onSurface,
                                                                            modifier = Modifier.weight(1f),
                                                                        )
                                                                    }
                                                                } else {
                                                                    Text(
                                                                        noteText,
                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale,
                                                                        color = MaterialTheme.colorScheme.onSurface,
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Chapter attribution (if present)
                    if (passage != null && passage.attribution.isNotBlank()) {
                        item(key = "ch_attr_${ch.first}_${ch.second}") {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                passage.attribution,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Compact top bar + actions row + progress bar (Item 3d)
        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                // Title and primary controls row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }

                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            "${currentChapter?.first.orEmpty()} ${currentChapter?.second ?: 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            "Chapter ${currentChapterIdx + 1} of ${chapters.size}${if (days.size > 1) " · Day $activeDay" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }

                    // Chapter navigation shortcuts
                    IconButton(
                        onClick = {
                            if (currentChapterIdx > 0) {
                                val targetBounds = chapterBoundsList.getOrNull(currentChapterIdx - 1)
                                if (targetBounds != null) {
                                    scope.launch { listState.animateScrollToItem(targetBounds.startIndex) }
                                }
                            }
                        },
                        enabled = currentChapterIdx > 0,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous chapter")
                    }

                    IconButton(
                        onClick = {
                            if (currentChapterIdx < chapters.lastIndex) {
                                val targetBounds = chapterBoundsList.getOrNull(currentChapterIdx + 1)
                                if (targetBounds != null) {
                                    scope.launch { listState.animateScrollToItem(targetBounds.startIndex) }
                                }
                            }
                        },
                        enabled = currentChapterIdx < chapters.lastIndex,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next chapter")
                    }

                    // Translation chip
                    FilterChip(
                        selected = true,
                        onClick = { showTranslationPicker = true },
                        label = {
                            Text(
                                translation.removePrefix("versewell-").uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.LibraryBooks, null, modifier = Modifier.size(14.dp))
                        },
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )

                    // Font size controls
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(start = 2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    fontScale = (fontScale - 0.1f).coerceAtLeast(0.85f)
                                    repo.session.fontScale = fontScale
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            ) { Text("A\u2212", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }
                            TextButton(
                                onClick = {
                                    fontScale = (fontScale + 0.1f).coerceAtMost(1.6f)
                                    repo.session.fontScale = fontScale
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            ) { Text("A+", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }
                        }
                    }

                    // Note action
                    IconButton(onClick = {
                        val cur = currentChapter
                        val b = cur?.first.orEmpty()
                        val c = cur?.second ?: 0
                        val v = ScriptureReferenceUtils.formatVerseRange(selectedVerses)
                        selectedVerses = emptySet()
                        onAddNote(activeDay, b, c, v, null)
                    }) {
                        Icon(Icons.Filled.Edit, "Add note")
                    }
                }

                // Catch-up multi-day tabs (only when session has > 1 day)
                if (days.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        days.forEachIndexed { idx, d ->
                            FilterChip(
                                selected = activeIdx == idx,
                                onClick = { activeIdx = idx },
                                label = {
                                    Text("Day $d${if (completedDays.contains(d)) " (Done)" else ""}")
                                },
                            )
                        }
                    }
                }

                // Slim chapter progress indicator (resets to 0 when moving to next chapter)
                ExpressiveLinearProgress(
                    progress = { currentChapterProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Verse selection action bar (floats above bottom panel when verses are selected)
        if (selectedVerses.isNotEmpty()) {
            val rangeText = ScriptureReferenceUtils.formatVerseRange(selectedVerses).orEmpty()
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 100.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "v. $rangeText",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = {
                            val cur = currentChapter
                            val b = cur?.first.orEmpty()
                            val c = cur?.second ?: 0
                            val v = rangeText
                            selectedVerses = emptySet()
                            onAddNote(activeDay, b, c, v, "question")
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.HelpOutline, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Question", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = {
                            val cur = currentChapter
                            val b = cur?.first.orEmpty()
                            val c = cur?.second ?: 0
                            val v = rangeText
                            selectedVerses = emptySet()
                            onAddNote(activeDay, b, c, v, "observation")
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add note", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(
                        onClick = { selectedVerses = emptySet() },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Filled.Close, "Clear selection", Modifier.size(16.dp))
                    }
                }
            }
        }

        // Floating bottom panel: timer, encouragement line, completion button
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                if (isCurrentDone) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (days.size > 1) "Day $activeDay reading complete" else "Reading complete",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        ExpressiveButton(onClick = { onOpenQuiz(activeDay) }) {
                            Icon(Icons.Filled.Quiz, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Quiz")
                        }
                    }
                    if (days.size > 1 && activeIdx < days.lastIndex && !completedDays.contains(days[activeIdx + 1])) {
                        Spacer(Modifier.height(8.dp))
                        ExpressiveButton(
                            onClick = { activeIdx++ },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Continue with day ${days[activeIdx + 1]}")
                        }
                    }
                } else {
                    // Status & Encouragement Row
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (isIdlePaused) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.Filled.Pause,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Paused. Touch the screen to continue",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        } else if (hasMinTime) {
                            val encouragement = ReaderEncouragement.getMessage(timerTick)
                            Text(
                                encouragement ?: "Ready to mark complete",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            val helperText = if (!finalChapterReached) {
                                "Reach the last chapter to complete"
                            } else {
                                timer.formattedRemaining()
                            }
                            Text(
                                helperText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Text(
                            timer.formattedTimer(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Complete Day Button
                    ExpressiveButton(
                        onClick = {
                            if (completeBusy || !canComplete) return@ExpressiveButton
                            scope.launch {
                                completeBusy = true
                                error = null
                                val sec = timer.getReadingSeconds()
                                val firstOpened = timer.getFirstOpenedAt()
                                val res = runCatching {
                                    repo.api.completeDay(
                                        n = activeDay,
                                        readingSeconds = sec,
                                        firstOpenedAt = firstOpened,
                                        finalChapterReached = true,
                                    )
                                }
                                if (res.isSuccess) {
                                    timer.clear()
                                    posStorage.clear(uid, activeDay)
                                    completedDays = completedDays + activeDay
                                    dayData?.let { cur ->
                                        val upd = cur.copy(progress = cur.progress.copy(completed = true))
                                        dayData = upd
                                        AppCache.saveDay(context, activeDay, upd)
                                    }
                                    com.forgebuild.forgehouse50.widget.WidgetRefreshWorker.refreshWidgetState(context, repo)
                                    com.forgebuild.forgehouse50.widget.ForgeHouseWidgetProvider.updateAll(context)
                                } else {
                                    val ex = res.exceptionOrNull()
                                    val msg = ex?.message.orEmpty()
                                    if (msg.contains("reading_time_too_short")) {
                                        error = "Minimum 5 minutes of reading required. Please keep reading."
                                    } else {
                                        OfflineCompletionQueue(context).enqueue(
                                            uid,
                                            PendingCompletion(activeDay, sec, firstOpened, true),
                                        )
                                        error = "Completion saved offline. Will sync when reconnected."
                                    }
                                }
                                completeBusy = false
                            }
                        },
                        Modifier.fillMaxWidth(),
                        enabled = canComplete,
                        busy = completeBusy,
                    ) {
                        Text("Mark day $activeDay reading complete")
                    }
                }
            }
        }
        if (showTranslationPicker) {
            ModalBottomSheet(
                onDismissRequest = { showTranslationPicker = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Select Bible translation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    availableTranslations.forEach { tId ->
                        val normId = repo.normalizeTranslation(tId)
                        val isSelected = repo.normalizeTranslation(translation) == normId
                        val code = normId.removePrefix("versewell-").uppercase()
                        val label = if (normId == Repository.KJV_ID) "King James Version (KJV)" else code
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (repo.normalizeTranslation(translation) != normId) {
                                        translation = normId
                                        repo.session.translationId = normId
                                    }
                                    showTranslationPicker = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            showTranslationPicker = false
                            onManageTranslations()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Manage translations…")
                    }
                }
            }
        }
    }
}

/**
 * Strips cross-chapter running alphabetic/numeric prefixes like "a ", "b. ", "1) ", "d a " from raw footnote strings (Issue 3).
 */
 * Strips cross-chapter running alphabetic/numeric prefixes like "a ", "b. ", "1) ", "d a " from raw footnote strings (Issue 3).
 */
private fun cleanFootnoteText(raw: String): String {
    val stripped = raw.replace(Regex("^(?:[a-zA-Z]{1,2}[.)\\s]\\s*|\\d{1,2}[.)\\s]\\s*)+"), "")
    return stripped.trim().ifEmpty { raw.trim() }
}

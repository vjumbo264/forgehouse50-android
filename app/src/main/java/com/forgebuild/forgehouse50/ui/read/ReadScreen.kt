package com.forgebuild.forgehouse50.ui.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forgebuild.forgehouse50.audio.AudioChapterMeta
import com.forgebuild.forgehouse50.audio.AudioLogic
import com.forgebuild.forgehouse50.audio.AudioPlayerController
import com.forgebuild.forgehouse50.audio.AudioPlayerUiState
import com.forgebuild.forgehouse50.audio.ChapterAudioDownloadState
import com.forgebuild.forgehouse50.audio.DownloadedAudioChapter
import java.util.Locale
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
import com.forgebuild.forgehouse50.data.ReaderErrorSanitizer
import com.forgebuild.forgehouse50.data.ReadingTimer
import com.forgebuild.forgehouse50.data.runSuspendCatching
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.SavedReaderPosition
import com.forgebuild.forgehouse50.data.ScriptureReferenceUtils
import com.forgebuild.forgehouse50.data.Verse
import com.forgebuild.forgehouse50.ui.ExpressiveButton
import com.forgebuild.forgehouse50.ui.ExpressiveLinearProgress
import com.forgebuild.forgehouse50.ui.ExpressiveLoading
import kotlinx.coroutines.CancellationException
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

        error = null
        runSuspendCatching { repo.api.day(activeDay) }.onSuccess {
            dayData = it
            error = null
            if (it.progress.completed) completedDays = completedDays + activeDay
            AppCache.saveDay(context, activeDay, it)
        }.onFailure { t ->
            if (dayData == null) {
                error = ReaderErrorSanitizer.toUserMessage(
                    t,
                    fallback = "Could not load today's reading plan. Check your connection and try again."
                )
            }
        }
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
                    runSuspendCatching { repo.getPassage(ch.first, ch.second, ch.second, translation) }
                        .onSuccess {
                            chapterPassages[ch] = it
                            chapterErrors.remove(ch)
                            chapterLoading[ch] = false
                        }
                        .onFailure { t ->
                            chapterErrors[ch] = ReaderErrorSanitizer.toUserMessage(
                                t,
                                fallback = "Could not load ${ch.first} ${ch.second}. Check your connection and tap retry."
                            ) ?: "Could not load ${ch.first} ${ch.second}."
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

    // Measured heights for header and bottom dock to guarantee zero overlap and full scroll clearance
    var headerHeightDp by remember { mutableStateOf(116.dp) }
    var bottomDockHeightDp by remember { mutableStateOf(140.dp) }
    val density = LocalDensity.current
    var showFontSizeDialog by remember { mutableStateOf(false) }

    // Calm reader counters (2c): verses remaining in current chapter and chapters remaining in day
    val counterState by remember(currentChapterInfo, chapterPassages.toMap(), listState) {
        derivedStateOf {
            val bounds = currentChapterInfo ?: return@derivedStateOf (0 to 0)
            val p = chapterPassages[Pair(bounds.book, bounds.chapter)]
            val totalVerses = p?.verses?.size ?: 0
            val introCount = p?.intros?.size ?: 0
            val verseStartIndex = bounds.startIndex + 1 + introCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val vLeft = when {
                lastVisible < verseStartIndex -> totalVerses
                lastVisible >= verseStartIndex + totalVerses -> 0
                else -> (verseStartIndex + totalVerses - 1 - lastVisible).coerceIn(0, totalVerses)
            }
            val cLeft = (chapters.size - 1 - bounds.chapterIdx).coerceAtLeast(0)
            vLeft to cLeft
        }
    }
    val versesLeft = counterState.first
    val chaptersLeft = counterState.second

    var selectedVerses by remember(currentChapterIdx, activeIdx) { mutableStateOf<Set<Int>>(emptySet()) }
    var openFootnoteVerse by remember(activeDay) { mutableStateOf<Int?>(null) }

    // Item i10: Offline chapter audio controller & manifest state (zero coupling to ReadingTimer)
    val playerController = remember { AudioPlayerController(context.applicationContext) }
    val playerState by playerController.uiState.collectAsState()
    val audioManifests by repo.audioManager.manifests.collectAsState()
    val chapterAudioStates by repo.audioManager.chapterStates.collectAsState()
    val localAudioByVersion by repo.audioManager.localChaptersByVersion.collectAsState()
    val activeAudioCode = remember(translation) { AudioLogic.normalizeVersionCode(translation) }
    val activeAudioManifest = audioManifests[activeAudioCode]
    val activeLocalAudioRows = localAudioByVersion[activeAudioCode].orEmpty()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, playerController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    playerController.onAppBackgroundedOrLocked()
                }
                Lifecycle.Event.ON_RESUME -> {
                    scope.launch { repo.audioManager.refreshLocalIndex() }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerController.release()
        }
    }

    LaunchedEffect(activeDay, translation) {
        playerController.stopAndDismiss()
        repo.audioManager.refreshLocalIndex()
        repo.audioManager.loadManifestIfSupported(translation)
    }

    // Sync ONLY genuine active reading time accrued by ReadingTimer every 30 seconds
    // (passive audio playback on an untouched screen accrues 0s and syncs 0s).
    LaunchedEffect(activeDay, timer) {
        var lastSyncedSeconds = timer.getReadingSeconds()
        while (isActive) {
            delay(30_000)
            val currentSeconds = timer.getReadingSeconds()
            val delta = AudioLogic.computeSyncedReadingDelta(currentSeconds, lastSyncedSeconds)
            if (delta > 0) {
                try {
                    repo.api.addReadingTime(activeDay, delta)
                    lastSyncedSeconds = currentSeconds
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    // Keep lastSyncedSeconds unchanged so delta retries on next tick
                }
            }
        }
    }

    val isCurrentDone = completedDays.contains(activeDay)
    val hasMinTime = timerTick >= 300
    val canComplete = hasMinTime && finalChapterReached

    // Root layout: pointerInput lives strictly on LazyColumn (Scripture surface), so bottom-dock
    // audio playback controls never count as reading interaction.
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Continuous scrollable content extending to physical bottom edge
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(timer) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            timer.onInteraction()
                            isIdlePaused = false
                        }
                    }
                },
            contentPadding = PaddingValues(top = headerHeightDp + 8.dp, bottom = bottomDockHeightDp + 16.dp, start = 20.dp, end = 20.dp),
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
                        val chAudioKey = AudioLogic.chapterKey(activeAudioCode, ch.first, ch.second)
                        val localAudioRow = activeLocalAudioRows.firstOrNull {
                            it.book.equals(ch.first, ignoreCase = true) && it.chapter == ch.second
                        }
                        val manifestAudioMeta = AudioLogic.findChapterInManifest(activeAudioManifest, ch.first, ch.second)
                        val chDownloadState = chapterAudioStates[chAudioKey] ?: ChapterAudioDownloadState.Idle

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
                            val isAudioSupported = AudioLogic.isTranslationAudioAvailable(
                                translationIdOrCode = activeAudioCode,
                                versionsCatalog = repo.audioManager.versionsCatalog.value,
                                cachedManifest = activeAudioManifest,
                                localVerifiedChaptersCount = activeLocalAudioRows.size,
                            )
                            if (isAudioSupported) {
                                Spacer(Modifier.height(8.dp))
                                ChapterHeaderAudioRow(
                                    book = ch.first,
                                    chapter = ch.second,
                                    localRow = localAudioRow,
                                    manifestMeta = manifestAudioMeta,
                                    downloadState = chDownloadState,
                                    isActiveChapterInPlayer = playerState.activeChapterKey == chAudioKey,
                                    isPlaying = playerState.activeChapterKey == chAudioKey && playerState.isPlaying,
                                    onDownloadClick = {
                                        repo.audioManager.startChapterDownload(activeAudioCode, ch.first, ch.second)
                                    },
                                    onCancelDownload = {
                                        repo.audioManager.cancelChapterDownload(activeAudioCode, ch.first, ch.second)
                                    },
                                    onPlayOrPauseClick = {
                                        scope.launch {
                                            val file = repo.audioManager.getVerifiedLocalChapterFile(
                                                translationIdOrCode = activeAudioCode,
                                                book = ch.first,
                                                chapter = ch.second,
                                            )
                                            if (file != null) {
                                                val dur = localAudioRow?.durationMs?.takeIf { it > 0L }
                                                    ?: manifestAudioMeta?.duration_ms
                                                    ?: 0L
                                                playerController.playOrToggleLocalChapter(
                                                    localFile = file,
                                                    versionCodeOrId = activeAudioCode,
                                                    book = ch.first,
                                                    chapter = ch.second,
                                                    fallbackDurationMs = dur,
                                                )
                                            }
                                        }
                                    },
                                    onDeleteClick = {
                                        scope.launch {
                                            if (playerState.activeChapterKey == chAudioKey) {
                                                playerController.stopAndDismiss()
                                            }
                                            repo.audioManager.deleteChapterAudio(activeAudioCode, ch.first, ch.second)
                                        }
                                    },
                                )
                            }
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

                // Redesigned compact header (2a) & counters (2c)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .onGloballyPositioned { coordinates ->
                    headerHeightDp = with(density) { coordinates.size.height.toDp() }
                },
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                // Row 1: Back arrow, Title (never truncates mid-word), translation chip, Aa text size, Add note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }

                    // Title with Book & Chapter - never truncates mid-word
                    Text(
                        text = "${currentChapter?.first.orEmpty()} ${currentChapter?.second ?: 1}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )

                    // Compact translation chip (tap opens bottom sheet)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable { showTranslationPicker = true },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.Filled.LibraryBooks,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                translation.removePrefix("versewell-").uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Text size button (compact 48dp action target)
                    IconButton(
                        onClick = { showFontSizeDialog = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        ) {
                            Text(
                                "Aa",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // Note shortcut (compact 48dp action target)
                    IconButton(
                        onClick = {
                            val cur = currentChapter
                            onAddNote(activeDay, cur?.first.orEmpty(), cur?.second ?: 1, "1", "observation")
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        ) {
                            Box(modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "Add note",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                // Row 2 (slim, optional): Day tabs only when catch-up or read-ahead exist
                if (days.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
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

                // Row 3: Chapter indicator + chapter nav shortcuts + calm verses/chapters remaining counter chip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Chapter number with real total (never truncated)
                    Text(
                        text = "Chapter ${currentChapterIdx + 1} of ${chapters.size}${if (days.size > 1) " · Day $activeDay" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    // Small chapter navigation shortcuts
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
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous chapter", Modifier.size(18.dp))
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
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next chapter", Modifier.size(18.dp))
                    }

                    Spacer(Modifier.weight(1f))

                    // Calm Counters Chip (2c)
                    if (isCurrentDone) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        ) {
                            Text(
                                "Completed",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    } else if (versesLeft > 0 || chaptersLeft > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        ) {
                            val counterText = if (chaptersLeft > 0) {
                                "$versesLeft verses · $chaptersLeft ch left"
                            } else {
                                "$versesLeft verses left"
                            }
                            Text(
                                counterText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                // Thin progress bar for current chapter (height 3dp, resets per chapter)
                ExpressiveLinearProgress(
                    progress = { currentChapterProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // ONE bottom dock composable (2b): owns all bottom UI and reserves real height in contentPadding
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.98f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { coordinates ->
                    bottomDockHeightDp = with(density) { coordinates.size.height.toDp() }
                }
                .animateContentSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (playerState.activeChapterKey != null) {
                    ReaderAudioPlayerBar(
                        state = playerState,
                        onTogglePlayPause = { playerController.togglePlayPause() },
                        onSeekFraction = { playerController.seekToFraction(it) },
                        onCycleSpeed = { playerController.cycleSpeed() },
                        onClose = { playerController.stopAndDismiss() },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }

                if (selectedVerses.isNotEmpty()) {
                    // Verse Selected State (never overlaps timer: replaces timer inside dock, with timer chip visible)
                    val rangeText = ScriptureReferenceUtils.formatVerseRange(selectedVerses).orEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "v. $rangeText",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }

                        // Running timer chip remains visible in selection state
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            val m = timerTick / 60
                            val s = timerTick % 60
                            Text(
                                text = String.format("%d:%02d", m, s),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }

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
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add note", style = MaterialTheme.typography.labelMedium)
                        }

                        IconButton(
                            onClick = { selectedVerses = emptySet() },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Filled.Close, "Clear selection", Modifier.size(18.dp))
                        }
                    }
                } else {
                    // Reading State
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
                        // Timer & encouragement row
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
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
                                        ReaderErrorSanitizer.pauseBannerText(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            } else {
                                val encouragement = remember(timerTick) {
                                    ReaderEncouragement.getMessage(timerTick)
                                }
                                if (!encouragement.isNullOrBlank()) {
                                    Text(
                                        encouragement,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }

                            val m = timerTick / 60
                            val s = timerTick % 60
                            Text(
                                String.format("%d:%02d / 5:00", m, s),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (hasMinTime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Helper text when day completion is disabled
                        if (!canComplete) {
                            val helper = when {
                                !hasMinTime -> {
                                    val rem = (300 - timerTick).coerceAtLeast(0)
                                    "Read for ${rem / 60}:${String.format("%02d", rem % 60)} more to complete"
                                }
                                !finalChapterReached -> "Reach the final chapter (${chapters.lastOrNull()?.let { "${it.first} ${it.second}" } ?: "end"}) to complete"
                                else -> ""
                            }
                            if (helper.isNotBlank()) {
                                Text(
                                    helper,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                        }

                        if (error != null) {
                            Text(
                                error.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 6.dp),
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
                                    val res = runSuspendCatching {
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
                                        val msg = ex?.message.orEmpty().lowercase()
                                        when {
                                            msg.contains("reading_time_too_short") -> {
                                                error = "Minimum 5 minutes of reading required. Please keep reading."
                                            }
                                            msg.contains("catch-up closes at 7pm") || msg.contains("rest_day_catchup_closed") -> {
                                                error = "Catch-up closes at 7pm today for Bible Study, continue tomorrow."
                                            }
                                            msg.contains("normal_reading_locked_on_rest_day") -> {
                                                error = "Today is a rest day. Scheduled reading resumes tomorrow."
                                            }
                                            else -> {
                                                OfflineCompletionQueue(context).enqueue(
                                                    uid,
                                                    PendingCompletion(activeDay, sec, firstOpened, true),
                                                )
                                                error = "Completion saved offline. Will sync when reconnected."
                                            }
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
        }

        // Compact font size dialog (Aa)
        if (showFontSizeDialog) {
            AlertDialog(
                onDismissRequest = { showFontSizeDialog = false },
                title = {
                    Text(
                        "Text size",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                onClick = {
                                    fontScale = (fontScale - 0.1f).coerceAtLeast(0.85f)
                                    repo.session.fontScale = fontScale
                                },
                            ) {
                                Text("A-", fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = fontScale,
                                onValueChange = {
                                    fontScale = it
                                    repo.session.fontScale = it
                                },
                                valueRange = 0.85f..1.6f,
                                steps = 6,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    fontScale = (fontScale + 0.1f).coerceAtMost(1.6f)
                                    repo.session.fontScale = fontScale
                                },
                            ) {
                                Text("A+", fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) {
                            Text(
                                "The word of God is living and active.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFontSizeDialog = false }) {
                        Text("Done")
                    }
                },
            )
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
                    for (tId in availableTranslations) {
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
private fun cleanFootnoteText(raw: String): String {
    val stripped = raw.replace(Regex("^(?:[a-zA-Z]{1,2}[.)\\s]\\s*|\\d{1,2}[.)\\s]\\s*)+"), "")
    return stripped.trim().ifEmpty { raw.trim() }
}


@Composable
private fun ChapterHeaderAudioRow(
    book: String,
    chapter: Int,
    localRow: DownloadedAudioChapter?,
    manifestMeta: AudioChapterMeta?,
    downloadState: ChapterAudioDownloadState,
    isActiveChapterInPlayer: Boolean,
    isPlaying: Boolean,
    onDownloadClick: () -> Unit,
    onCancelDownload: () -> Unit,
    onPlayOrPauseClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (localRow != null) {
                val durMs = localRow.durationMs.takeIf { it > 0L } ?: (manifestMeta?.duration_ms ?: 0L)
                val infoParts = buildList {
                    if (durMs > 0L) add(AudioLogic.formatDurationMs(durMs))
                    add(AudioLogic.formatAudioBytes(localRow.sizeBytes))
                    add("Offline")
                }.joinToString(" · ")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = onPlayOrPauseClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                isPlaying -> "Pause audio"
                                isActiveChapterInPlayer -> "Resume audio"
                                else -> "Play audio"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Text(
                        text = infoParts,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete $book $chapter audio",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            } else {
                when (downloadState) {
                    is ChapterAudioDownloadState.Connecting -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Preparing audio…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onCancelDownload, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is ChapterAudioDownloadState.Downloading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Downloading audio… ${(downloadState.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (downloadState.totalBytes > 0L) {
                                Text(
                                    "${AudioLogic.formatAudioBytes(downloadState.bytesRead)} / ${AudioLogic.formatAudioBytes(downloadState.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            IconButton(onClick = onCancelDownload, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        ExpressiveLinearProgress(
                            progress = { downloadState.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = if ((manifestMeta?.bytes ?: 0L) > 0L) {
                                    "Audio · ${AudioLogic.formatAudioBytes(manifestMeta?.bytes ?: 0L)}"
                                } else {
                                    "Chapter audio available"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                onClick = onDownloadClick,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Download audio", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (downloadState is ChapterAudioDownloadState.Failed) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                downloadState.message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderAudioPlayerBar(
    state: AudioPlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${state.book} ${state.chapter} · ${state.versionCode}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = "${AudioLogic.formatDurationMs(state.positionMs)} / ${AudioLogic.formatDurationMs(state.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                onClick = onCycleSpeed,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            ) {
                val speedText = if (state.speed % 1f == 0f) {
                    String.format(Locale.US, "%.1fx", state.speed)
                } else {
                    "${state.speed}x"
                }
                Text(
                    text = speedText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close audio player",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Slider(
            value = state.progressFraction,
            onValueChange = onSeekFraction,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth().height(28.dp),
        )
        if (!state.error.isNullOrBlank()) {
            Text(
                text = state.error.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

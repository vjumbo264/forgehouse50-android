package com.forgebuild.forgehouse50.ui.notes

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgebuild.forgehouse50.data.AppCache
import com.forgebuild.forgehouse50.data.Note
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.ScriptureReferenceUtils
import com.forgebuild.forgehouse50.ui.ExpressiveButton
import com.forgebuild.forgehouse50.ui.ExpressiveLoading
import com.forgebuild.forgehouse50.ui.NOTE_TYPES
import com.forgebuild.forgehouse50.ui.noteTypeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun noteTypeIcon(type: String) = when (type.lowercase()) {
    "observation" -> Icons.Filled.Visibility
    "question" -> Icons.Filled.Help
    "insight", "scripture_connection" -> Icons.Filled.Lightbulb
    "prayer" -> Icons.Filled.Favorite
    "application" -> Icons.Filled.CheckCircle
    else -> Icons.Filled.Bookmark
}

/**
 * Main Notes screen: search, filter chips, list of notes with Sent to leaders & Answered badges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(repo: Repository, onBack: () -> Unit, onEdit: (String?, Int?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showNoticeDialog by remember {
        mutableStateOf(repo.session.userId != null && !repo.session.hasNotesPrivacyNoticeBeenAcknowledged())
    }

    if (showNoticeDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    "Notes & Community Update",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Observations and Questions are always sent to your leaders. They may be answered and shared with the group, using your name only if you allow it. Insights, Prayers and Applications stay private. If you delete a note, your leaders keep the copy you sent.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        repo.session.acknowledgeNotesPrivacyNotice()
                        showNoticeDialog = false
                    }
                ) {
                    Text("I Understand", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    var notes by remember { mutableStateOf(AppCache.getNotes(context)?.notes.orEmpty()) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(notes.isEmpty()) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    suspend fun refresh() {
        runCatching { repo.api.notes(q = query, type = typeFilter) }.onSuccess {
            notes = it.notes
            if (query.isBlank() && typeFilter.isBlank()) {
                AppCache.saveNotes(context, it)
            }
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    LaunchedEffect(query, typeFilter) {
        delay(300)
        refresh()
    }

    if (noteToDelete != null) {
        val n = noteToDelete!!
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete note?") },
            text = {
                Text(
                    if (n.note_type == "observation" || n.note_type == "question")
                        "This removes this note from your personal notes. Your leaders will retain the submitted copy."
                    else
                        "This will permanently delete this private note."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching { repo.api.deleteNote(n.id) }
                            notes = notes.filterNot { it.id == n.id }
                            noteToDelete = null
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Notes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEdit(null, null) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search notes or passage") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState(), enabled = false)
            ) {
                FilterChip(
                    selected = typeFilter.isBlank(),
                    onClick = { typeFilter = "" },
                    label = { Text("All") }
                )
                NOTE_TYPES.forEach { t ->
                    FilterChip(
                        selected = typeFilter == t,
                        onClick = { typeFilter = if (typeFilter == t) "" else t },
                        leadingIcon = {
                            Icon(
                                noteTypeIcon(t),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(noteTypeLabel(t)) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (loading && notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExpressiveLoading()
                }
            } else if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No notes found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap + to write an observation, question, or private note.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize().padding(bottom = 80.dp)
                ) {
                    items(notes, key = { it.id }) { n ->
                        val isLeaderType = n.note_type == "observation" || n.note_type == "question"
                        Card(
                            onClick = { onEdit(n.id, n.day_number) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        noteTypeIcon(n.note_type),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = noteTypeLabel(n.note_type),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (n.book.isNotBlank()) {
                                        Text(
                                            text = "  ·  ${n.book} ${n.chapter}${n.verse?.let { ":$it" } ?: ""}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(
                                        onClick = { noteToDelete = n },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(4.dp))

                                // Status Badges
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isLeaderType) {
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                "Sent to leaders",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                "Private",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    if (n.reply != null || n.inbox_status == "answered" || n.inbox_status == "published") {
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                "Answered",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = n.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // If leader replied, show reply quote
                                if (n.reply != null && n.reply.text.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(10.dp)) {
                                            Text(
                                                "Leader Reply:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                n.reply.text,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
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

/**
 * Premium Note Editor Screen with Tappable Pickers:
 * - BookPicker dialog (searchable list of 27 NT books)
 * - ChapterPicker dialog (grid 1..maxChapters)
 * - VersePicker dialog (grid 1..maxVerses with range support)
 * - Type chips with icons
 * - Autosave draft
 * - Policy text and community show_name toggle
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditScreen(
    repo: Repository,
    noteId: String?,
    dayNumber: Int?,
    initialBook: String? = null,
    initialChapter: Int? = null,
    initialVerse: String? = null,
    initialType: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("fh50_note_drafts", Context.MODE_PRIVATE) }
    val draftKey = remember(noteId) { "draft_${noteId ?: "new"}" }

    val fallbackAssign = remember {
        if (noteId == null && initialBook.isNullOrBlank() && (initialChapter == null || initialChapter <= 0)) {
            val today = AppCache.getToday(context)
            (today?.today?.assignment ?: today?.next_reading_day?.assignment)?.assignments?.firstOrNull()
        } else null
    }

    var type by remember { mutableStateOf(initialType?.takeIf { it in NOTE_TYPES } ?: "observation") }
    var book by remember { mutableStateOf(initialBook?.takeIf { it.isNotBlank() } ?: fallbackAssign?.book.orEmpty().ifBlank { "Matthew" }) }
    var chapter by remember {
        mutableStateOf(
            if (initialChapter != null && initialChapter > 0) "$initialChapter"
            else fallbackAssign?.let { "${it.chapter_start}" }.orEmpty().ifBlank { "1" }
        )
    }
    var verse by remember { mutableStateOf(initialVerse.orEmpty()) }
    var body by remember { mutableStateOf(if (noteId == null) prefs.getString(draftKey, "").orEmpty() else "") }
    var showName by remember { mutableStateOf(false) }

    var showBookPicker by remember { mutableStateOf(false) }
    var showChapterPicker by remember { mutableStateOf(false) }
    var showVersePicker by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(noteId == null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Draft autosave effect
    LaunchedEffect(body) {
        if (noteId == null && body.isNotBlank()) {
            prefs.edit().putString(draftKey, body).apply()
        }
    }

    LaunchedEffect(noteId) {
        if (noteId != null) {
            runCatching { repo.api.notes() }.onSuccess { res ->
                res.notes.firstOrNull { it.id == noteId }?.let { n ->
                    type = n.note_type
                    body = n.body
                    book = n.book
                    chapter = if (n.chapter > 0) "${n.chapter}" else "1"
                    verse = n.verse?.toString() ?: ""
                    showName = n.show_name
                }
            }
            loaded = true
        }
    }

    // Book Picker Dialog
    if (showBookPicker) {
        var bookSearch by remember { mutableStateOf("") }
        val filteredBooks = remember(bookSearch) {
            if (bookSearch.isBlank()) ScriptureReferenceUtils.NT_BOOKS
            else ScriptureReferenceUtils.NT_BOOKS.filter { it.first.contains(bookSearch.trim(), ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showBookPicker = false },
            title = { Text("Select Book") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    OutlinedTextField(
                        value = bookSearch,
                        onValueChange = { bookSearch = it },
                        placeholder = { Text("Search 27 NT books...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(filteredBooks, key = { it.first }) { (bName, chCount) ->
                            val isSelected = bName.equals(book, ignoreCase = true)
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        book = bName
                                        val maxCh = chCount
                                        val curCh = chapter.toIntOrNull() ?: 1
                                        if (curCh > maxCh) chapter = "1"
                                        showBookPicker = false
                                        error = null
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        bName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "$chCount ch",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isSelected) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookPicker = false }) { Text("Close") }
            }
        )
    }

    // Chapter Picker Dialog
    if (showChapterPicker) {
        val maxCh = ScriptureReferenceUtils.maxChaptersFor(book) ?: 28
        val chaptersList = remember(book) { (1..maxCh).toList() }
        AlertDialog(
            onDismissRequest = { showChapterPicker = false },
            title = { Text("$book — Select Chapter") },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 52.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(chaptersList) { c ->
                            val isSelected = "$c" == chapter
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable {
                                        chapter = "$c"
                                        showChapterPicker = false
                                        error = null
                                    }
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "$c",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChapterPicker = false }) { Text("Close") }
            }
        )
    }

    // Verse Picker Dialog
    if (showVersePicker) {
        val versesList = remember { (1..80).toList() }
        var rangeStart by remember { mutableStateOf<Int?>(verse.split("-").firstOrNull()?.toIntOrNull()) }
        var rangeEnd by remember { mutableStateOf<Int?>(verse.split("-").getOrNull(1)?.toIntOrNull()) }

        AlertDialog(
            onDismissRequest = { showVersePicker = false },
            title = { Text("$book $chapter — Select Verse") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    Text(
                        "Tap a verse or two verses for a range (e.g. 3 to 5):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 46.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(versesList) { vNum ->
                            val isStart = rangeStart == vNum
                            val isEnd = rangeEnd == vNum
                            val inRange = rangeStart != null && rangeEnd != null && vNum in rangeStart!!..rangeEnd!!
                            val isSelected = isStart || isEnd || inRange

                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = when {
                                    isStart || isEnd -> MaterialTheme.colorScheme.primary
                                    inRange -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clickable {
                                        if (rangeStart == null) {
                                            rangeStart = vNum
                                        } else if (rangeEnd == null) {
                                            if (vNum > rangeStart!!) {
                                                rangeEnd = vNum
                                            } else if (vNum < rangeStart!!) {
                                                rangeEnd = rangeStart
                                                rangeStart = vNum
                                            } else {
                                                // same tapped twice
                                                rangeEnd = null
                                            }
                                        } else {
                                            rangeStart = vNum
                                            rangeEnd = null
                                        }
                                    }
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "$vNum",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isStart || isEnd -> MaterialTheme.colorScheme.onPrimary
                                            inRange -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                rangeStart = null
                                rangeEnd = null
                                verse = ""
                                showVersePicker = false
                            }
                        ) {
                            Text("Entire Chapter (No Verse)")
                        }
                        ExpressiveButton(
                            onClick = {
                                verse = when {
                                    rangeStart != null && rangeEnd != null -> "$rangeStart-$rangeEnd"
                                    rangeStart != null -> "$rangeStart"
                                    else -> ""
                                }
                                showVersePicker = false
                            }
                        ) {
                            Text("Apply")
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId == null) "New Note" else "Edit Note") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExpressiveLoading()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Note Type Selector as Segmented Chips with Icons
            Text("Type", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                NOTE_TYPES.forEach { t ->
                    val isSelected = type == t
                    FilterChip(
                        selected = isSelected,
                        onClick = { type = t },
                        leadingIcon = {
                            Icon(
                                noteTypeIcon(t),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(noteTypeLabel(t)) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Reference Card with 3 Tappable Pickers: Book, Chapter, Verse
            Text("Scripture Reference", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Book Picker Button
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1.4f).clickable { showBookPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Book", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(book, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Pick book", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Chapter Picker Button
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f).clickable { showChapterPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Chapter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Ch. $chapter", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Pick chapter", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Verse Picker Button
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f).clickable { showVersePicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Verse", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(verse.ifBlank { "All" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Pick verse", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Policy Notice Banner & Community Toggle
            val isLeaderType = type == "observation" || type == "question"
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isLeaderType) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isLeaderType) Icons.Filled.Visibility else Icons.Filled.Lock,
                            contentDescription = null,
                            tint = if (isLeaderType) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isLeaderType)
                                "This goes to your leaders. They can read it and may reply."
                            else
                                "Private to you. Never shared with leaders or the group.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isLeaderType) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isLeaderType) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    "Show my name if shared with the group",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "If turned off, shown to community as 'Anonymous'. Leaders always see real author.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showName,
                                onCheckedChange = { showName = it }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Spacious Text Area with Character Counter
            OutlinedTextField(
                value = body,
                onValueChange = { if (it.length <= 5000) body = it },
                label = { Text("Write your ${noteTypeLabel(type).lowercase()}...") },
                minLines = 6,
                maxLines = 14,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "${body.length} / 5000",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(18.dp))

            // Primary Save Button
            ExpressiveButton(
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        val canonBook = ScriptureReferenceUtils.canonicalBook(book) ?: book.trim()
                        val ch = chapter.trim().toIntOrNull() ?: 1
                        val v = ScriptureReferenceUtils.parseStartingVerse(verse)

                        val r = if (noteId == null) {
                            runCatching {
                                repo.api.createNote(
                                    noteType = type,
                                    body = body,
                                    book = canonBook,
                                    chapter = ch,
                                    verse = v,
                                    dayNumber = dayNumber,
                                    showName = showName
                                )
                            }
                        } else {
                            runCatching {
                                repo.api.updateNote(
                                    id = noteId,
                                    noteType = type,
                                    body = body,
                                    showName = showName
                                )
                            }
                        }

                        r.onSuccess {
                            if (it.ok) {
                                prefs.edit().remove(draftKey).apply()
                                onBack()
                            } else {
                                error = it.error ?: "Save failed"
                            }
                        }.onFailure {
                            error = it.message ?: "Failed to save note"
                        }
                        busy = false
                    }
                },
                enabled = !busy && body.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (noteId == null) "Save Note" else "Update Note", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

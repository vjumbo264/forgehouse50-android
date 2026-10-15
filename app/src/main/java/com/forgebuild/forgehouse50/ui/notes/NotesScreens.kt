package com.forgebuild.forgehouse50.ui.notes
import com.forgebuild.forgehouse50.data.AppCache

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Surface
import com.forgebuild.forgehouse50.data.ScriptureReferenceUtils
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.Note
import com.forgebuild.forgehouse50.data.NotesResponse
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.AppJson
import com.forgebuild.forgehouse50.ui.NOTE_TYPES
import com.forgebuild.forgehouse50.ui.noteTypeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.forgebuild.forgehouse50.ui.ExpressiveLoading
import com.forgebuild.forgehouse50.ui.ExpressiveButton

/** Notes: create/edit/delete/search/filter, five types, private by default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(repo: Repository, onBack: () -> Unit, onEdit: (String?, Int?) -> Unit) {
    val context = LocalContext.current
    var showNoticeDialog by remember {
        mutableStateOf(repo.session.userId != null && !repo.session.hasNotesPrivacyNoticeBeenAcknowledged())
    }
    if (showNoticeDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    "Notes Sharing Update",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Observations and Questions you write can now be read by your leaders so they can help you. Insights, Prayers and Applications stay private.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        repo.session.acknowledgeNotesPrivacyNotice()
                        showNoticeDialog = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf(com.forgebuild.forgehouse50.data.AppCache.getNotes(context)?.notes.orEmpty()) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(notes.isEmpty()) }

    suspend fun refresh() {
        runCatching { repo.api.notes(q = query, type = typeFilter) }.onSuccess {
            notes = it.notes
            if (query.isBlank() && typeFilter.isBlank()) {
                com.forgebuild.forgehouse50.data.AppCache.saveNotes(context, it)
            }
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        refresh()
    }
    LaunchedEffect(query, typeFilter) {
        delay(300)   // debounce
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Notes") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(null, null) }) { Icon(Icons.Filled.Add, "New note") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search notes") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = typeFilter.isBlank(), onClick = { typeFilter = "" }, label = { Text("All") })
                NOTE_TYPES.take(3).forEach { t ->
                    FilterChip(selected = typeFilter == t, onClick = { typeFilter = if (typeFilter == t) "" else t },
                        label = { Text(noteTypeLabel(t)) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NOTE_TYPES.drop(3).forEach { t ->
                    FilterChip(selected = typeFilter == t, onClick = { typeFilter = if (typeFilter == t) "" else t },
                        label = { Text(noteTypeLabel(t)) })
                }
            }
            Spacer(Modifier.height(8.dp))
            if (loading && notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }
            } else if (notes.isEmpty()) {
                Spacer(Modifier.height(40.dp))
                Text("No notes yet — your notes are private to you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notes, key = { it.id }) { n ->
                        Card(
                            onClick = { onEdit(n.id, n.day_number) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(noteTypeLabel(n.note_type),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary)
                                        if (n.book.isNotBlank()) {
                                            Text("  ·  ${n.book} ${n.chapter}${n.verse?.let { ":$it" } ?: ""}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (n.note_type == "observation" || n.note_type == "question") {
                                        Spacer(Modifier.height(2.dp))
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {
                                            Text(
                                                "Visible to leaders",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(n.body, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        runCatching { repo.api.deleteNote(n.id) }
                                        notes = notes.filterNot { it.id == n.id }
                                    }
                                }) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    val fallbackAssign = remember {
        if (noteId == null && initialBook.isNullOrBlank() && (initialChapter == null || initialChapter <= 0)) {
            val today = AppCache.getToday(context)
            (today?.today?.assignment ?: today?.next_reading_day?.assignment)?.assignments?.firstOrNull()
        } else null
    }

    var type by remember { mutableStateOf(initialType?.takeIf { it in NOTE_TYPES } ?: "observation") }
    var body by remember { mutableStateOf("") }
    var book by remember { mutableStateOf(initialBook?.takeIf { it.isNotBlank() } ?: fallbackAssign?.book.orEmpty()) }
    var chapter by remember {
        mutableStateOf(
            if (initialChapter != null && initialChapter > 0) "$initialChapter"
            else fallbackAssign?.let { "${it.chapter_start}" }.orEmpty()
        )
    }
    var verse by remember { mutableStateOf(initialVerse.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(noteId == null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(noteId) {
        if (noteId != null) {
            runCatching { repo.api.notes() }.onSuccess { res ->
                res.notes.firstOrNull { it.id == noteId }?.let { n ->
                    type = n.note_type; body = n.body; book = n.book
                    chapter = if (n.chapter > 0) "${n.chapter}" else ""
                    verse = n.verse?.toString() ?: ""
                }
            }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (noteId == null) "New note" else "Edit note") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            })
        },
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(padding).padding(horizontal = 20.dp),
        ) {
            // Pre-filled reference banner
            if (book.isNotBlank() || chapter.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(18.dp).width(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Reference: ${book.ifBlank { "—" }} ${chapter.ifBlank { "" }}${if (verse.isNotBlank()) ":$verse" else ""}".trim(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text("Type", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NOTE_TYPES.take(3).forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(noteTypeLabel(t)) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NOTE_TYPES.drop(3).forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(noteTypeLabel(t)) })
                }
            }
            Spacer(Modifier.height(14.dp))
            val isLeaderVisible = type == "observation" || type == "question"
            val noteLabel = if (isLeaderVisible) "Note (your leaders can read this)" else "Note (private to you)"
            OutlinedTextField(body, { body = it }, label = { Text(noteLabel) },
                modifier = Modifier.fillMaxWidth().height(160.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = book,
                    onValueChange = { book = it; error = null },
                    label = { Text("Book") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f)
                )
                OutlinedTextField(
                    value = chapter,
                    onValueChange = { if (it.all(Char::isDigit)) { chapter = it; error = null } },
                    label = { Text("Chapter") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = verse,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '-' }) { verse = it; error = null } },
                    label = { Text("Verse") },
                    placeholder = { Text("e.g. 5 or 3-5") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            ExpressiveButton(
                onClick = {
                    scope.launch {
                        busy = true; error = null
                        val validationErr = ScriptureReferenceUtils.validate(book, chapter, verse)
                        if (validationErr != null) {
                            error = validationErr
                            busy = false
                            return@launch
                        }
                        val canonBook = ScriptureReferenceUtils.canonicalBook(book) ?: book.trim()
                        val ch = chapter.trim().toIntOrNull() ?: 1
                        val v = ScriptureReferenceUtils.parseStartingVerse(verse)
                        val r = if (noteId == null)
                            runCatching { repo.api.createNote(type, body, canonBook, ch, v, dayNumber) }
                        else
                            runCatching { repo.api.updateNote(noteId, type, body) }
                        r.onSuccess { if (it.ok) onBack() else error = it.error ?: "Save failed" }
                            .onFailure { error = it.message }
                        busy = false
                    }
                },
                enabled = !busy && body.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save note") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

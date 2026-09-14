package com.forgebuild.forgehouse50.ui.notes

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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

/** Notes: create/edit/delete/search/filter, five types, private by default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(repo: Repository, onBack: () -> Unit, onEdit: (String?, Int?) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fh50_cache", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    suspend fun refresh() {
        runCatching { repo.api.notes(q = query, type = typeFilter) }.onSuccess {
            notes = it.notes
            if (query.isBlank() && typeFilter.isBlank()) {
                prefs.edit().putString("notes", AppJson.encodeToString(NotesResponse.serializer(), it)).apply()
            }
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        prefs.getString("notes", null)?.let { c ->
            runCatching { AppJson.decodeFromString<NotesResponse>(c) }.getOrNull()
        }?.let { notes = it.notes; loading = false }
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
fun NoteEditScreen(repo: Repository, noteId: String?, dayNumber: Int?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("observation") }
    var body by remember { mutableStateOf("") }
    var book by remember { mutableStateOf("") }
    var chapter by remember { mutableStateOf("") }
    var verse by remember { mutableStateOf("") }
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(padding).padding(horizontal = 20.dp),
        ) {
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
            OutlinedTextField(body, { body = it }, label = { Text("Note (private to you)") },
                modifier = Modifier.fillMaxWidth().height(160.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(book, { book = it }, label = { Text("Book") },
                    singleLine = true, modifier = Modifier.weight(1.4f))
                OutlinedTextField(chapter, { if (it.all(Char::isDigit)) chapter = it }, label = { Text("Chapter") },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(verse, { if (it.all(Char::isDigit)) verse = it }, label = { Text("Verse") },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    scope.launch {
                        busy = true; error = null
                        val ch = chapter.toIntOrNull() ?: 0
                        val v = verse.toIntOrNull()
                        val r = if (noteId == null)
                            runCatching { repo.api.createNote(type, body, book, ch, v, dayNumber) }
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

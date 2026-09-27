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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.PassageResponse
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.ExpressiveLoading

/**
 * One chapter's scripture pane, shared by the classic single-day ReadScreen
 * and the catch-up ReadSessionScreen. Offline-first via [Repository] (bundled
 * KJV / downloaded translations); one network fetch only when the translation
 * is not on-device.
 */
@Composable
fun ChapterPane(
    repo: Repository,
    chapter: Pair<String, Int>?,
    translation: String,
    emptyMessage: String,
) {
    var passage by remember { mutableStateOf<PassageResponse?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }
    var openFootnoteVerse by remember { mutableStateOf<Int?>(null) }
    val fontScale = repo.session.fontScale
    val scroll = rememberScrollState()

    val key = chapter?.let { "${it.first}:${it.second}" } ?: ""
    LaunchedEffect(key, translation, reloadToken) {
        val cc = chapter ?: return@LaunchedEffect
        if (translation.isBlank()) return@LaunchedEffect
        loading = true; error = null; passage = null
        runCatching { repo.getPassage(cc.first, cc.second, cc.second, translation) }
            .onSuccess { passage = it }
            .onFailure { error = it.message ?: "Could not load this chapter." }
        loading = false
        openFootnoteVerse = null
        scroll.scrollTo(0)
    }

    Box(
        Modifier.fillMaxWidth().clipToBounds().padding(horizontal = 24.dp),
    ) {
        when {
            chapter == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            loading && passage == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExpressiveLoading()
            }
            passage == null -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error ?: "Could not load this passage.", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { reloadToken++ }) { Text("Retry") }
            }
            else -> {
                val p = passage!!
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    Text(p.reference, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    p.verses.forEach { v ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("${v.verse}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(28.dp).padding(top = 3.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.text, style = MaterialTheme.typography.bodyLarge,
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontScale)
                                if (v.footnotes.isNotEmpty()) {
                                    Text(
                                        "† footnote" + if (v.footnotes.size > 1) "s (${v.footnotes.size})" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clickable {
                                                openFootnoteVerse =
                                                    if (openFootnoteVerse == v.verse) null else v.verse
                                            }
                                            .padding(vertical = 2.dp),
                                    )
                                    if (openFootnoteVerse == v.verse) {
                                        Spacer(Modifier.height(4.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = MaterialTheme.shapes.medium,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Column(Modifier.padding(10.dp)) {
                                                v.footnotes.forEach { note ->
                                                    Text(note, style = MaterialTheme.typography.bodySmall,
                                                        fontSize = MaterialTheme.typography.bodySmall.fontSize * fontScale,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Spacer(Modifier.height(4.dp))
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

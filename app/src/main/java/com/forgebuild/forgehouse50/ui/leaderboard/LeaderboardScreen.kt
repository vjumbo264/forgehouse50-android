package com.forgebuild.forgehouse50.ui.leaderboard

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.FinalResultsResponse
import com.forgebuild.forgehouse50.data.LeaderboardEntry
import com.forgebuild.forgehouse50.data.LeaderboardResponse
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.AppJson
import com.forgebuild.forgehouse50.ui.ForgeHouseTheme
import com.forgebuild.forgehouse50.ui.PodiumColors
import com.forgebuild.forgehouse50.ui.formatDurationShort
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private val CATEGORIES = listOf(
    "overall" to "Overall", "consistency" to "Consistency", "chapters" to "Chapters",
    "reading_time" to "Reading time", "observations" to "Observations", "questions" to "Questions",
)

/**
 * Leaderboard: in-progress confidence-weighted ranking + one-time final
 * results. Top 3 get progressively more elaborate treatment (1st richest);
 * 4th+ stay simple list rows. Same rule on both screens.
 */
@Composable
fun LeaderboardScreen(repo: Repository) {
    var finals by remember { mutableStateOf<FinalResultsResponse?>(null) }
    var showFinals by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { repo.api.finalResults() }.onSuccess { finals = it } }

    if (showFinals && finals?.concluded == true) {
        FinalResultsView(repo, finals!!, onBack = { showFinals = false })
    } else {
        Column(Modifier.fillMaxSize()) {
            if (finals?.concluded == true) {
                TextButton(onClick = { showFinals = true }, modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("Programme concluded — view final results")
                }
            }
            InProgressBoard(repo)
        }
    }
}

@Composable
private fun InProgressBoard(repo: Repository) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fh50_cache", Context.MODE_PRIVATE) }
    var category by remember { mutableStateOf("overall") }
    var data by remember { mutableStateOf<LeaderboardResponse?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(category) {
        prefs.getString("leaderboard_$category", null)?.let { c ->
            runCatching { AppJson.decodeFromString<LeaderboardResponse>(c) }.getOrNull()
        }?.let { data = it; loading = false }
        runCatching { repo.api.leaderboard(category) }.onSuccess {
            data = it
            prefs.edit().putString("leaderboard_$category", AppJson.encodeToString(LeaderboardResponse.serializer(), it)).apply()
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Text("Leaderboard", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        ScrollableTabRow(selectedTabIndex = CATEGORIES.indexOfFirst { it.first == category }.coerceAtLeast(0),
            edgePadding = 16.dp) {
            CATEGORIES.forEach { (key, label) ->
                Tab(selected = category == key, onClick = { category = key; loading = true },
                    text = { Text(label) })
            }
        }
        if (loading && data == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val entries = data?.entries ?: emptyList()
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Rankings open from day 3 of your programme.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    itemsIndexed(entries) { _, e -> PodiumRow(e.rank, e.display_name, e.avatar_id, valueLabel(category, e), e.value) }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

private fun valueLabel(category: String, e: LeaderboardEntry): String = when (category) {
    "reading_time" -> formatDurationShort(e.value.toLong())
    "overall" -> "%.1f pts/day".format(e.value)
    else -> "%.0f".format(e.value)
}

/** Shared podium row: rank 1 richest (glow + crown), 2/3 distinguished, 4+ plain. */
@Composable
private fun PodiumRow(rank: Int, name: String, avatarId: String?, valueText: String, raw: Double) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val (container, accent) = when (rank) {
        1 -> (if (dark) PodiumColors.goldContainerDark else PodiumColors.goldContainerLight) to PodiumColors.gold
        2 -> (if (dark) PodiumColors.silverContainerDark else PodiumColors.silverContainerLight) to PodiumColors.silver
        3 -> (if (dark) PodiumColors.bronzeContainerDark else PodiumColors.bronzeContainerLight) to PodiumColors.bronze
        else -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        Modifier.fillMaxWidth().then(
            if (rank == 1) Modifier.drawBehind {
                // subtle warm glow behind first place
                drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent)))
            } else Modifier
        ),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = if (rank == 1) 4.dp else 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (rank <= 3) 16.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (rank == 1) {
                Icon(Icons.Filled.WorkspacePremium, "1st place", tint = accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text("$rank", style = if (rank <= 3) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Normal,
                color = accent, modifier = Modifier.width(if (rank == 1) 28.dp else 36.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = if (rank <= 3) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (rank <= 3) FontWeight.SemiBold else FontWeight.Normal)
                if (rank <= 3) {
                    Text(when (rank) { 1 -> "Leading the programme"; 2 -> "Second place"; else -> "Third place" },
                        style = MaterialTheme.typography.labelSmall, color = accent)
                }
            }
            Text(valueText, style = if (rank <= 3) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (rank <= 3) FontWeight.SemiBold else FontWeight.Normal,
                color = if (rank <= 3) accent else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FinalResultsView(repo: Repository, finals: FinalResultsResponse, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val me = finals.me
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
        }
        Text("Final results", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Programme concluded — these rankings are final.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (me != null && me.celebration_pending) {
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(14.dp)) {
                    Text("You finished #${me.rank} with ${me.total_points} points.",
                        style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { scope.launch { runCatching { repo.api.ackFinalResults() } } }) {
                        Text("Acknowledge")
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(finals.rankings) { _, r -> PodiumRow(r.rank, r.display_name, r.avatar_id, "${r.total_points} pts", r.total_points.toDouble()) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

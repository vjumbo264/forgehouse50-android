package com.forgebuild.forgehouse50.ui.leaderboard

import com.forgebuild.forgehouse50.data.AppCache
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.FinalResultsResponse
import com.forgebuild.forgehouse50.data.LeaderboardEntry
import com.forgebuild.forgehouse50.data.LeaderboardResponse
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.ui.AvatarAssets
import com.forgebuild.forgehouse50.ui.ExpressiveLoading
import com.forgebuild.forgehouse50.ui.PodiumColors
import com.forgebuild.forgehouse50.ui.formatDurationShort
import kotlinx.coroutines.launch

private val CATEGORIES = listOf(
    "overall" to "Overall",
    "consistency" to "Consistency",
    "chapters" to "Chapters",
    "reading_time" to "Reading time",
    "observations" to "Observations",
    "questions" to "Questions",
)

/**
 * Leaderboard: in-progress confidence-weighted ranking + one-time final
 * results.
 * Redesigned with premium Material 3 Expressive styling:
 * - Segmented pill category tabs
 * - Card-ized rows with soft elevation and 20dp corner radii
 * - Prominent top-3 metallic podium treatment
 * - Instant "You" row highlighting and summary banner
 * - Large bold score typography
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
    var category by remember { mutableStateOf("overall") }
    var data by remember(category) { mutableStateOf(AppCache.getLeaderboard(context, category)) }
    var loading by remember(category) { mutableStateOf(data == null) }
    var myUserId by remember { mutableStateOf(AppCache.getMe(context)?.id ?: repo.session.userId) }

    LaunchedEffect(Unit) {
        if (myUserId == null) {
            runCatching { repo.api.me() }.onSuccess {
                myUserId = it.id
                AppCache.saveMe(context, it)
                repo.session.userId = it.id
            }
        }
    }

    LaunchedEffect(category) {
        val cached = AppCache.getLeaderboard(context, category)
        if (cached != null) {
            data = cached
            loading = false
        }
        runCatching { repo.api.leaderboard(category) }.onSuccess {
            data = it
            AppCache.saveLeaderboard(context, category, it)
            loading = false
        }.onFailure {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Category pill tabs (horizontal scrolling)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CATEGORIES.forEach { (key, label) ->
                val isSelected = category == key
                Surface(
                    onClick = { category = key; loading = true },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = if (isSelected) 3.dp else 0.dp,
                    modifier = Modifier.height(38.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (loading && data == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoading() }
        } else {
            val entries = data?.entries ?: emptyList()
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Rankings open from day 3 of your programme.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val userEntry = entries.firstOrNull { it.user_id == myUserId }
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Pinned "Your Standing" summary banner if present in list
                    if (userEntry != null) {
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "#${userEntry.rank}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Your standing",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            userEntry.display_name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Text(
                                        valueLabel(category, userEntry),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(entries) { _, e ->
                        PodiumRow(
                            rank = e.rank,
                            name = e.display_name,
                            avatarId = e.avatar_id,
                            valueText = valueLabel(category, e),
                            raw = e.value,
                            isYou = e.user_id == myUserId
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

private fun valueLabel(category: String, e: LeaderboardEntry): String = when (category) {
    "reading_time" -> formatDurationShort(e.value.toLong())
    "overall" -> "%.0f pts".format(e.total_points?.toDouble() ?: e.value)
    else -> "%.0f".format(e.value)
}

/**
 * Shared podium row:
 * - Rank 1: Gold container with warm radial glow, crown icon, metallic border, bold numerals
 * - Rank 2: Silver container with metallic border and prominent badge
 * - Rank 3: Bronze container with metallic border and prominent badge
 * - Rank 4+: Elevated surfaceContainerLow card with 20dp corners and clean typography
 * - "You" highlight: 2dp primary accent border, tinted container, prominent "YOU" badge
 */
@Composable
private fun PodiumRow(
    rank: Int,
    name: String,
    avatarId: String?,
    valueText: String,
    raw: Double,
    isYou: Boolean = false
) {
    val dark = isSystemInDarkTheme()
    val (baseContainer, accentColor, borderStroke) = when (rank) {
        1 -> Triple(
            if (dark) PodiumColors.goldContainerDark else PodiumColors.goldContainerLight,
            PodiumColors.gold,
            if (dark) Color(0xFF6B5318) else Color(0xFFDFBA54)
        )
        2 -> Triple(
            if (dark) PodiumColors.silverContainerDark else PodiumColors.silverContainerLight,
            PodiumColors.silver,
            if (dark) Color(0xFF4A545C) else Color(0xFFCAD0D5)
        )
        3 -> Triple(
            if (dark) PodiumColors.bronzeContainerDark else PodiumColors.bronzeContainerLight,
            PodiumColors.bronze,
            if (dark) Color(0xFF5A3E26) else Color(0xFFD6B296)
        )
        else -> Triple(
            if (dark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Color.Transparent
        )
    }

    val finalContainer = if (isYou) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f).compositeOver(baseContainer)
    } else baseContainer

    val cardShape = RoundedCornerShape(20.dp)
    val elevation = when {
        isYou -> 3.dp
        rank == 1 -> 4.dp
        rank in 2..3 -> 2.dp
        else -> 1.dp
    }

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = finalContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isYou) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
                else if (rank <= 3) Modifier.border(1.dp, borderStroke, cardShape)
                else Modifier
            )
            .then(
                if (rank == 1) Modifier.drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(accentColor.copy(alpha = 0.14f), Color.Transparent),
                            radius = size.maxDimension
                        )
                    )
                } else Modifier
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (rank <= 3) 14.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank numeral / badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(if (rank == 1) 36.dp else 32.dp)
            ) {
                if (rank == 1) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.18f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.WorkspacePremium,
                                contentDescription = "1st Place",
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else if (rank in 2..3) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.16f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "$rank",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor
                            )
                        }
                    }
                } else {
                    Text(
                        "$rank",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Avatar with ring
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(AvatarAssets.resFor(avatarId)),
                    contentDescription = null,
                    modifier = Modifier
                        .size(if (rank == 1) 48.dp else if (rank <= 3) 44.dp else 38.dp)
                        .clip(CircleShape)
                        .then(
                            if (rank <= 3) Modifier.border(2.dp, accentColor, CircleShape)
                            else if (isYou) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier
                        )
                )
            }

            Spacer(Modifier.width(12.dp))

            // Name and subtitle
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = if (rank <= 3) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (rank <= 3 || isYou) FontWeight.Bold else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isYou) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "YOU",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (rank <= 3) {
                    Text(
                        when (rank) {
                            1 -> "Leading the programme"
                            2 -> "Second place"
                            else -> "Third place"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = accentColor
                    )
                }
            }

            // Score / value
            Text(
                valueText,
                style = if (rank <= 3) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (rank <= 3) accentColor else if (isYou) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
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
        Text(
            "Programme concluded — these rankings are final.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (me != null && me.celebration_pending) {
            Spacer(Modifier.height(10.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "You finished #${me.rank} with ${me.total_points} points.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { scope.launch { runCatching { repo.api.ackFinalResults() } } }) {
                        Text("Acknowledge")
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(finals.rankings) { _, r ->
                PodiumRow(
                    rank = r.rank,
                    name = r.display_name,
                    avatarId = r.avatar_id,
                    valueText = "${r.total_points} pts",
                    raw = r.total_points.toDouble(),
                    isYou = me != null && r.rank == me.rank
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

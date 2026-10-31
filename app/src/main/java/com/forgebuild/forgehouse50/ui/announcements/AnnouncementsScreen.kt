package com.forgebuild.forgehouse50.ui.announcements

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgebuild.forgehouse50.data.Announcement
import com.forgebuild.forgehouse50.data.AppCache
import com.forgebuild.forgehouse50.data.Repository
import com.forgebuild.forgehouse50.data.WatTimeFormatter
import com.forgebuild.forgehouse50.ui.ExpressiveLoading
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

fun formatAnnouncementDate(isoStr: String): String {
    val wat = WatTimeFormatter.formatWatTime(isoStr, includeYear = true)
    if (wat.isNotBlank()) return wat
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val datePart = isoStr.take(10)
        val date = parser.parse(datePart)
        val formatter = SimpleDateFormat("d MMM yyyy", Locale.US)
        formatter.format(date ?: return isoStr.take(10))
    }.getOrDefault(isoStr.take(10))
}

/**
 * Only collapse extraordinarily long announcements (> 1200 chars or > 18 line breaks).
 * Standard pastoral announcements (1-4 paragraphs) are ALWAYS shown in full on Today.
 */
fun shouldOfferAnnouncementCollapse(body: String): Boolean {
    return body.length > 1200 || body.count { it == '\n' } > 18
}

/**
 * Large, prominent, persistent Announcement card shown near the top of the Today screen
 * for all users (members and admins) and reused verbatim as the Admin Member Preview.
 */
@Composable
fun TodayAnnouncementCard(
    ann: Announcement,
    isUnread: Boolean,
    totalCount: Int = 1,
    showArchiveAction: Boolean = true,
    onMarkSeen: () -> Unit = {},
    onOpenArchive: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val collapsible = remember(ann.body) { shouldOfferAnnouncementCollapse(ann.body) }
    var expanded by remember(ann.id) { mutableStateOf(!collapsible) }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = if (isUnread) 1.5.dp else 1.dp,
            color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Announcement",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                if (isUnread) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "New",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatAnnouncementDate(ann.created_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = ann.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = ann.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 16,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
            )

            if (collapsible) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        expanded = !expanded
                        onMarkSeen()
                    },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (expanded) "Show less" else "Read full announcement",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (showArchiveAction) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isUnread) {
                        TextButton(
                            onClick = onMarkSeen,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Mark as read", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    TextButton(
                        onClick = {
                            onMarkSeen()
                            onOpenArchive()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (totalCount > 1) "Past announcements ($totalCount)" else "All announcements",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PastAnnouncementsLinkCard(
    totalCount: Int,
    onOpenArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpenArchive,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Icon(
                Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Past announcements ($totalCount)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsScreen(
    repo: Repository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(AppCache.getAnnouncements(context)) }
    var loading by remember { mutableStateOf(items.isEmpty()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = items.isEmpty()
        runCatching { repo.api.announcements(status = "all") }.onSuccess { res ->
            items = res.announcements
            nextCursor = res.next_cursor
            hasMore = res.has_more
            AppCache.saveAnnouncements(context, res.announcements)
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Announcements") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (loading && items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                ExpressiveLoading()
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        Icons.Filled.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No announcements yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Programme updates and notices from leadership will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(items, key = { it.id }) { ann ->
                    AnnouncementCard(ann)
                }
                if (hasMore && !nextCursor.isNullOrBlank()) {
                    item(key = "load_more") {
                        OutlinedButton(
                            onClick = {
                                if (!loadingMore) {
                                    loadingMore = true
                                    scope.launch {
                                        runCatching { repo.api.announcements(cursor = nextCursor, status = "all") }
                                            .onSuccess { res ->
                                                val existingIds = items.map { it.id }.toSet()
                                                items = items + res.announcements.filterNot { it.id in existingIds }
                                                nextCursor = res.next_cursor
                                                hasMore = res.has_more
                                            }
                                        loadingMore = false
                                    }
                                }
                            },
                            enabled = !loadingMore,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (loadingMore) "Loading..." else "Load older announcements")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnnouncementCard(
    ann: Announcement,
    modifier: Modifier = Modifier
) {
    val isActive = ann.status == "active"
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = if (isActive) "Active on Today" else "Past announcement",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatAnnouncementDate(ann.created_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = ann.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = ann.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

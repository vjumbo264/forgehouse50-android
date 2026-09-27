package com.forgebuild.forgehouse50.ui

import kotlinx.serialization.json.Json

val AppJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

fun formatClock(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatDurationShort(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${seconds}s"
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

val NOTE_TYPES = listOf("observation", "question", "insight", "prayer", "application")

fun noteTypeLabel(t: String) = t.replaceFirstChar { it.uppercase() }

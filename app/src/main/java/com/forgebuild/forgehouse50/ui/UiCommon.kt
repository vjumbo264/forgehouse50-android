package com.forgebuild.forgehouse50.ui

import kotlinx.serialization.json.Json

/** App-wide lenient JSON (cache snapshots must tolerate schema drift). */
val AppJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

/** Compact duration, e.g. 1h 12m / 4m / 38s. */
fun formatDurationShort(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${s}s"
    }
}

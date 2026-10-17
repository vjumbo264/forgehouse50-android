package com.forgebuild.forgehouse50.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Shared helper for formatting timestamps in West Africa Time (WAT, Africa/Lagos, UTC+1).
 * Used consistently across participant notes, admin inbox, and community cards.
 */
object WatTimeFormatter {
    private val WAT_ZONE = TimeZone.getTimeZone("Africa/Lagos")

    /**
     * Parses UTC ISO 8601 string and formats it in WAT: "17 Oct, 1:15 pm" (or with year: "17 Oct 2026, 1:15 pm")
     */
    fun formatWatTime(isoString: String?, includeYear: Boolean = false): String {
        if (isoString.isNullOrBlank()) return ""
        val clean = isoString.trim()
        val parsedDate = try {
            val utcParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            utcParser.parse(clean)
        } catch (e: Exception) {
            try {
                val utcParser2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                utcParser2.parse(clean)
            } catch (e2: Exception) {
                null
            }
        } ?: return ""

        val pattern = if (includeYear) "d MMM yyyy, h:mm a" else "d MMM, h:mm a"
        val watFormatter = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = WAT_ZONE
        }
        return watFormatter.format(parsedDate).replace("AM", "am").replace("PM", "pm")
    }

    fun formatSentTime(firstSentAt: String?, sentAt: String?): String {
        val base = formatWatTime(firstSentAt ?: sentAt)
        if (base.isBlank()) return ""
        val isEdited = !firstSentAt.isNullOrBlank() && !sentAt.isNullOrBlank() && firstSentAt != sentAt
        return if (isEdited) "Sent $base (Edited)" else "Sent $base"
    }

    fun formatRepliedTime(repliedAt: String?): String {
        val base = formatWatTime(repliedAt)
        return if (base.isNotBlank()) "Replied $base" else ""
    }
}

package com.forgebuild.forgehouse50.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WhatsAppFormatter {

    fun escapeWhatsAppText(raw: String): String {
        return raw.trim()
            .replace("\r\n", "\n")
            .replace("\r", "\n")
    }

    fun formatItem(item: LeaderInboxItem): String {
        val typeTitle = if (item.type.equals("question", ignoreCase = true)) "*Question*" else "*Observation*"
        val ref = "_${item.reference.trim()}_"
        val author = if (item.show_name && item.author_full_name.isNotBlank()) {
            item.author_full_name.trim()
        } else {
            "*Anonymous*"
        }

        val textLines = escapeWhatsAppText(item.text).lines()
        val quoteText = textLines.joinToString("\n") { "> $it" }

        val reply = item.reply?.text?.let { escapeWhatsAppText(it) } ?: ""
        val replyBlock = if (reply.isNotBlank()) {
            "*Reply:*\n$reply"
        } else {
            "*Reply:* (No reply)"
        }

        return buildString {
            appendLine(typeTitle)
            appendLine(ref)
            appendLine(quoteText)
            appendLine(replyBlock)
            appendLine("*Author:* $author")
        }.trimEnd()
    }

    fun formatBatch(items: List<LeaderInboxItem>, date: Date = Date()): String {
        if (items.isEmpty()) return ""
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.US)
        val dateStr = dateFormat.format(date)

        val header = "*ForgeHouse 50 — Community Highlights*\n$dateStr\n"
        val separator = "\n───────────────────────────────\n"

        val body = items.joinToString(separator) { formatItem(it) }
        return "$header\n$body"
    }
}

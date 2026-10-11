package com.forgebuild.forgehouse50.data

/**
 * Pure Kotlin utility for canonical New Testament books, chapter ranges,
 * verse range formatting, and user input validation.
 */
object ScriptureReferenceUtils {
    /** 27 canonical NT books with exact chapter counts (sum = 260 chapters). */
    val NT_BOOKS: List<Pair<String, Int>> = listOf(
        "Matthew" to 28, "Mark" to 16, "Luke" to 24, "John" to 21,
        "Acts" to 28, "Romans" to 16, "1 Corinthians" to 16,
        "2 Corinthians" to 13, "Galatians" to 6, "Ephesians" to 6,
        "Philippians" to 4, "Colossians" to 4, "1 Thessalonians" to 5,
        "2 Thessalonians" to 3, "1 Timothy" to 6, "2 Timothy" to 4,
        "Titus" to 3, "Philemon" to 1, "Hebrews" to 13, "James" to 5,
        "1 Peter" to 5, "2 Peter" to 3, "1 John" to 5, "2 John" to 1,
        "3 John" to 1, "Jude" to 1, "Revelation" to 22,
    )

    fun canonicalBook(name: String?): String? {
        val trimmed = name?.trim() ?: return null
        if (trimmed.isBlank()) return null
        return NT_BOOKS.firstOrNull { it.first.equals(trimmed, ignoreCase = true) }?.first
    }

    fun maxChaptersFor(book: String?): Int? {
        val canon = canonicalBook(book) ?: return null
        return NT_BOOKS.firstOrNull { it.first == canon }?.second
    }

    /**
     * Formats a set of verse numbers into a human string:
     * - empty -> null
     * - [3] -> "3"
     * - [3, 4, 5] -> "3-5"
     */
    fun formatVerseRange(verses: Set<Int>): String? {
        if (verses.isEmpty()) return null
        val sorted = verses.sorted()
        return if (sorted.size == 1) {
            "${sorted.first()}"
        } else {
            "${sorted.first()}-${sorted.last()}"
        }
    }

    /**
     * Parses the starting verse integer from a string representation (e.g. "5" -> 5, "3-5" -> 3).
     */
    fun parseStartingVerse(verseStr: String?): Int? {
        if (verseStr.isNullOrBlank()) return null
        val firstPart = verseStr.split("-").firstOrNull()?.trim() ?: return null
        return firstPart.toIntOrNull()
    }

    /**
     * Validates book, chapter, and optional verse / verse range.
     * Returns null if valid, or a friendly error message if invalid.
     */
    fun validate(book: String?, chapter: String?, verse: String?): String? {
        if (book.isNullOrBlank()) return "Please enter a book name."
        val canon = canonicalBook(book)
            ?: return "'$book' is not recognized as a New Testament book (e.g. Matthew, Romans)."
        val ch = chapter?.trim()?.toIntOrNull()
            ?: return "Please enter a valid chapter number."
        val maxCh = maxChaptersFor(canon) ?: 28
        if (ch < 1 || ch > maxCh) {
            return "$canon has $maxCh chapter${if (maxCh > 1) "s" else ""}. Chapter $ch is not valid."
        }
        if (!verse.isNullOrBlank()) {
            val trimmedV = verse.trim()
            if (trimmedV.contains("-")) {
                val parts = trimmedV.split("-").map { it.trim() }
                if (parts.size != 2) return "Verse range must look like 3-5."
                val v1 = parts[0].toIntOrNull()
                val v2 = parts[1].toIntOrNull()
                if (v1 == null || v2 == null || v1 < 1 || v2 < v1 || v2 > 176) {
                    return "Invalid verse range '$trimmedV'. Start verse must be less than or equal to end verse."
                }
            } else {
                val v = trimmedV.toIntOrNull()
                if (v == null || v < 1 || v > 176) {
                    return "Verse number must be between 1 and 176."
                }
            }
        }
        return null
    }
}

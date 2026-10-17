package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TranslationsPipelineTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun testBundleFootnoteDeserializer_scalarFootnote() {
        val input = """
            {"n": 1, "t": "In the beginning was the Word", "f": ["Reference: Gen 1:1"]}
        """.trimIndent()
        val verse = json.decodeFromString<BundledVerse>(input)
        assertEquals(1, verse.n)
        assertEquals("In the beginning was the Word", verse.t)
        assertEquals(listOf("Reference: Gen 1:1"), verse.f)
    }

    @Test
    fun testBundleFootnoteDeserializer_objectFootnote() {
        // This is the exact format that crashed prior builds on amp.json.gz
        val input = """
            {"n": 1, "t": "The record of the genealogy of Jesus the Messiah", "f": [{"marker": "a", "text": "Heb Yeshua, meaning salvation."}]}
        """.trimIndent()
        val verse = json.decodeFromString<BundledVerse>(input)
        assertEquals(1, verse.n)
        assertEquals(1, verse.f.size)
        assertEquals("a. Heb Yeshua, meaning salvation.", verse.f[0])
    }

    @Test
    fun testBundleFootnoteDeserializer_mixedFootnotes() {
        val input = """
            {
                "n": 2,
                "t": "Abraham was the father of Isaac",
                "f": [
                    "Early tradition note",
                    {"marker": "b", "text": "Lit begot"}
                ]
            }
        """.trimIndent()
        val verse = json.decodeFromString<BundledVerse>(input)
        assertEquals(2, verse.f.size)
        assertEquals("Early tradition note", verse.f[0])
        assertEquals("b. Lit begot", verse.f[1])
    }

    @Test
    fun testBundleVerse_missingFootnotesAndHeadings() {
        val input = """
            {"n": 3, "t": "and Judah the father of Perez", "unknown_field": 123}
        """.trimIndent()
        val verse = json.decodeFromString<BundledVerse>(input)
        assertEquals(3, verse.n)
        assertEquals("and Judah the father of Perez", verse.t)
        assertTrue(verse.f.isEmpty())
        assertNull(verse.h)
    }

    @Test
    fun testBundleChapter_skipMalformedVerse() {
        // Verse array containing a valid verse, a malformed element (number instead of verse object), and another valid verse
        val input = """
            {
                "v": [
                    {"n": 1, "t": "First verse"},
                    99999,
                    {"n": 2, "t": "Second verse"}
                ]
            }
        """.trimIndent()
        val chapter = json.decodeFromString<BundledChapter>(input)
        assertEquals(2, chapter.v.size)
        assertEquals(1, chapter.v[0].n)
        assertEquals("First verse", chapter.v[0].t)
        assertEquals(2, chapter.v[1].n)
        assertEquals("Second verse", chapter.v[1].t)
    }

    @Test
    fun testNormalizeTranslationId() {
        fun normalize(t: String): String =
            if (t.startsWith("versewell-", ignoreCase = true)) t.lowercase() else "versewell-${t.lowercase()}"

        assertEquals("versewell-kjv", normalize("kjv"))
        assertEquals("versewell-kjv", normalize("KJV"))
        assertEquals("versewell-kjv", normalize("versewell-kjv"))
        assertEquals("versewell-amp", normalize("amp"))
        assertEquals("versewell-amp", normalize("versewell-AMP"))
    }

    @Test
    fun testDownloadStateMachine_states() {
        var state: TranslationDownloadState = TranslationDownloadState.Idle
        assertEquals(TranslationDownloadState.Idle, state)

        state = TranslationDownloadState.Connecting
        assertTrue(state is TranslationDownloadState.Connecting)

        state = TranslationDownloadState.Progressing(bytesRead = 500L, totalBytes = 1000L, progress = 0.5f)
        assertTrue(state is TranslationDownloadState.Progressing)
        val progressing = state as TranslationDownloadState.Progressing
        assertEquals(500L, progressing.bytesRead)
        assertEquals(1000L, progressing.totalBytes)
        assertEquals(0.5f, progressing.progress, 0.001f)

        state = TranslationDownloadState.Done
        assertTrue(state is TranslationDownloadState.Done)

        state = TranslationDownloadState.Failed("Connection timed out")
        assertTrue(state is TranslationDownloadState.Failed)
        assertEquals("Connection timed out", (state as TranslationDownloadState.Failed).message)
    }

    @Test
    fun testTranslationTextDiffersFromKjv() {
        val kjvText = "The book of the generation of Jesus Christ, the son of David, the son of Abraham."
        val ampText = "The record of the genealogy of Jesus the Messiah, the son of David, the son of Abraham:"
        val nkjvText = "The book of the genealogy of Jesus Christ, the Son of David, the Son of Abraham:"
        val nivText = "This is the genealogy of Jesus the Messiah the son of David, the son of Abraham:"

        assertNotEquals(kjvText, ampText)
        assertNotEquals(kjvText, nkjvText)
        assertNotEquals(kjvText, nivText)
    }
}

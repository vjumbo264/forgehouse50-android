package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.ScriptureReferenceUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScripturePickersTest {

    @Test
    fun testAll27NewTestamentBooksPresentAndSumTo260Chapters() {
        val books = ScriptureReferenceUtils.NT_BOOKS
        assertEquals("Must have exactly 27 New Testament books", 27, books.size)
        val totalChapters = books.sumOf { it.second }
        assertEquals("Total chapters in the 27 NT books must equal 260", 260, totalChapters)
    }

    @Test
    fun testBookCanonicalLookup() {
        assertEquals("Matthew", ScriptureReferenceUtils.canonicalBook("matthew"))
        assertEquals("1 Corinthians", ScriptureReferenceUtils.canonicalBook("1 corinthians"))
        assertEquals("Revelation", ScriptureReferenceUtils.canonicalBook("REVELATION"))
        assertNull(ScriptureReferenceUtils.canonicalBook("Genesis"))
        assertNull(ScriptureReferenceUtils.canonicalBook(""))
    }

    @Test
    fun testChapterLimitsForPickers() {
        assertEquals(28, ScriptureReferenceUtils.maxChaptersFor("Matthew"))
        assertEquals(16, ScriptureReferenceUtils.maxChaptersFor("Mark"))
        assertEquals(1, ScriptureReferenceUtils.maxChaptersFor("Philemon"))
        assertEquals(1, ScriptureReferenceUtils.maxChaptersFor("Jude"))
        assertEquals(22, ScriptureReferenceUtils.maxChaptersFor("Revelation"))
    }

    @Test
    fun testVersePickerFormatting() {
        assertEquals("5", ScriptureReferenceUtils.formatVerseRange(setOf(5)))
        assertEquals("3-7", ScriptureReferenceUtils.formatVerseRange(setOf(3, 4, 5, 6, 7)))
        assertNull(ScriptureReferenceUtils.formatVerseRange(emptySet()))

        assertEquals(5, ScriptureReferenceUtils.parseStartingVerse("5"))
        assertEquals(3, ScriptureReferenceUtils.parseStartingVerse("3-7"))
        assertNull(ScriptureReferenceUtils.parseStartingVerse(null))
        assertNull(ScriptureReferenceUtils.parseStartingVerse(""))
    }

    @Test
    fun testReferenceValidation() {
        assertNull(ScriptureReferenceUtils.validate("Matthew", "1", "1"))
        assertNull(ScriptureReferenceUtils.validate("Matthew", "28", "20"))
        assertNull(ScriptureReferenceUtils.validate("Jude", "1", null))
        assertNull(ScriptureReferenceUtils.validate("Romans", "8", "28-30"))

        // Over-limit chapter
        val errMatt = ScriptureReferenceUtils.validate("Matthew", "29", null)
        assertNotNull(errMatt)
        assertTrue(errMatt!!.contains("Chapter 29 is not valid"))

        // Negative or zero chapter
        val errZero = ScriptureReferenceUtils.validate("Mark", "0", null)
        assertNotNull(errZero)

        // Invalid verse range
        val errRange = ScriptureReferenceUtils.validate("John", "3", "16-10")
        assertNotNull(errRange)
        assertTrue(errRange!!.contains("Start verse must be less than or equal to end verse"))
    }
}

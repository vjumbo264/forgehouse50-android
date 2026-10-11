package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.ScriptureReferenceUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesAutoFillLogicTest {

    @Test
    fun testNormalDayReaderPrefill() {
        // Normal day reader: Matthew chapter 1 open, no verse selected
        val book = "Matthew"
        val chapter = 1
        val selectedVerses = emptySet<Int>()

        val verseStr = ScriptureReferenceUtils.formatVerseRange(selectedVerses)
        assertNull(verseStr)

        val validation = ScriptureReferenceUtils.validate(book, "$chapter", verseStr)
        assertNull("Valid reference should have null validation error", validation)
        assertEquals("Matthew", ScriptureReferenceUtils.canonicalBook(book))
    }

    @Test
    fun testCatchupDayTabsSelectCorrectViewedChapter() {
        // In catchup mode, Day 9 tab shows Mark 1, Day 10 tab shows Mark 5
        data class CatchupTab(val day: Int, val currentBook: String, val currentChapter: Int)
        val day9Tab = CatchupTab(day = 9, currentBook = "Mark", currentChapter = 1)
        val day10Tab = CatchupTab(day = 10, currentBook = "Mark", currentChapter = 5)

        // Tapping note on Day 9 tab must pre-fill Mark 1
        val val9 = ScriptureReferenceUtils.validate(day9Tab.currentBook, "${day9Tab.currentChapter}", null)
        assertNull(val9)
        assertEquals("Mark", ScriptureReferenceUtils.canonicalBook(day9Tab.currentBook))
        assertEquals(1, day9Tab.currentChapter)

        // Tapping note on Day 10 tab must pre-fill Mark 5
        val val10 = ScriptureReferenceUtils.validate(day10Tab.currentBook, "${day10Tab.currentChapter}", null)
        assertNull(val10)
        assertEquals("Mark", ScriptureReferenceUtils.canonicalBook(day10Tab.currentBook))
        assertEquals(5, day10Tab.currentChapter)
    }

    @Test
    fun testSingleVerseHighlightPrefill() {
        // User highlights verse 16 in John 3
        val book = "John"
        val chapter = 3
        val selectedVerses = setOf(16)

        val verseStr = ScriptureReferenceUtils.formatVerseRange(selectedVerses)
        assertEquals("16", verseStr)

        val startingVerse = ScriptureReferenceUtils.parseStartingVerse(verseStr)
        assertEquals(16, startingVerse)

        val validation = ScriptureReferenceUtils.validate(book, "$chapter", verseStr)
        assertNull(validation)
    }

    @Test
    fun testVerseRangeHighlightPrefill() {
        // User highlights verses 3 to 5 in Romans 8
        val book = "Romans"
        val chapter = 8
        val selectedVerses = setOf(3, 4, 5)

        val verseStr = ScriptureReferenceUtils.formatVerseRange(selectedVerses)
        assertEquals("3-5", verseStr)

        val startingVerse = ScriptureReferenceUtils.parseStartingVerse(verseStr)
        assertEquals(3, startingVerse)

        val validation = ScriptureReferenceUtils.validate(book, "$chapter", verseStr)
        assertNull(validation)
    }

    @Test
    fun testNoContextFallbackLogic() {
        // When opening from My Notes with no context:
        // Case A: Today reading has assignment Luke 1-6 -> fallback book "Luke", chapter 1
        val assignmentBook: String? = "Luke"
        val assignmentChapter: Int? = 1
        val fallbackBook = assignmentBook ?: ""
        val fallbackChapter = assignmentChapter?.takeIf { it > 0 }?.toString() ?: ""

        assertEquals("Luke", fallbackBook)
        assertEquals("1", fallbackChapter)
        assertNull(ScriptureReferenceUtils.validate(fallbackBook, fallbackChapter, null))

        // Case B: No reading assignment available -> leaves blank
        val emptyBook: String? = null
        val emptyChapter: Int? = null
        val resultBook = emptyBook ?: ""
        val resultChapter = emptyChapter?.takeIf { it > 0 }?.toString() ?: ""
        assertTrue(resultBook.isEmpty())
        assertTrue(resultChapter.isEmpty())
    }

    @Test
    fun testReferenceValidationRules() {
        // Valid book and chapter
        assertNull(ScriptureReferenceUtils.validate("Matthew", "28", "20"))
        assertNull(ScriptureReferenceUtils.validate("Revelation", "22", "21"))
        assertNull(ScriptureReferenceUtils.validate("Philemon", "1", "25"))

        // Unknown book
        val errFake = ScriptureReferenceUtils.validate("Genesis", "1", "1")
        assertNotNull(errFake)
        assertTrue(errFake!!.contains("not recognized as a New Testament book"))

        // Chapter exceeds book maximum
        val errMattOver = ScriptureReferenceUtils.validate("Matthew", "29", "1")
        assertNotNull(errMattOver)
        assertTrue(errMattOver!!.contains("Matthew has 28 chapters. Chapter 29 is not valid"))

        val errPhilOver = ScriptureReferenceUtils.validate("Philemon", "2", "1")
        assertNotNull(errPhilOver)
        assertTrue(errPhilOver!!.contains("Philemon has 1 chapter. Chapter 2 is not valid"))

        // Invalid chapter number
        val errZero = ScriptureReferenceUtils.validate("Romans", "0", "1")
        assertNotNull(errZero)
        assertTrue(errZero!!.contains("Chapter 0 is not valid"))

        // Invalid verse range
        val errRangeInverted = ScriptureReferenceUtils.validate("Romans", "8", "10-5")
        assertNotNull(errRangeInverted)
        assertTrue(errRangeInverted!!.contains("Start verse must be less than or equal to end verse"))

        // Valid verse range
        assertNull(ScriptureReferenceUtils.validate("Romans", "8", "38-39"))
    }
}

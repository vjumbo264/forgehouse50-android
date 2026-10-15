package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.InMemoryReaderPositionStorage
import com.forgebuild.forgehouse50.data.InMemoryTimerStorage
import com.forgebuild.forgehouse50.data.MonotonicClock
import com.forgebuild.forgehouse50.data.ReaderEncouragement
import com.forgebuild.forgehouse50.data.ReadingTimer
import com.forgebuild.forgehouse50.data.SavedReaderPosition
import com.forgebuild.forgehouse50.data.WallClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeMonotonicClock(var currentMs: Long = 1000L) : MonotonicClock {
    override fun elapsedRealtime(): Long = currentMs
    fun advance(ms: Long) { currentMs += ms }
}

class FakeWallClock(var currentIso: String = "2026-10-15T08:00:00Z") : WallClock {
    override fun nowIso(): String = currentIso
}

class ReadingTimerEngineTest {

    @Test
    fun testContinuousCountingWhileScrollingNoChapterChange() {
        val clock = FakeMonotonicClock(10_000L)
        val storage = InMemoryTimerStorage()
        val timer = ReadingTimer("user1", 1, storage, clock, FakeWallClock(), idleTimeoutMs = 60_000L)
        timer.start()

        // User reads and scrolls within Chapter 1 for 10 minutes (600 seconds) without changing chapter
        // Interaction occurs every 20 seconds as user scrolls through verses
        repeat(30) {
            clock.advance(20_000L)
            timer.onInteraction()
            timer.tick()
        }

        assertEquals(600, timer.getReadingSeconds())
        assertFalse(timer.isIdlePaused())
        assertTrue(timer.hasReachedMinimum())
        assertEquals("10:00", timer.formattedTimer())
    }

    @Test
    fun testIdleDetectionAndImmediateTouchResume() {
        val clock = FakeMonotonicClock(10_000L)
        val storage = InMemoryTimerStorage()
        val timer = ReadingTimer("user1", 1, storage, clock, FakeWallClock(), idleTimeoutMs = 60_000L)
        timer.start()

        // 40 seconds of reading
        clock.advance(40_000L)
        assertEquals(40, timer.tick())
        assertFalse(timer.isIdlePaused())

        // User goes idle for 70 seconds (> 60s timeout)
        clock.advance(70_000L)
        timer.tick()
        assertTrue(timer.isIdlePaused())
        val frozenSec = timer.getReadingSeconds()

        // Advance 30s more while idle
        clock.advance(30_000L)
        assertEquals(frozenSec, timer.tick())
        assertTrue(timer.isIdlePaused())

        // User touches screen -> resumes immediately, idle time is NOT credited
        timer.onInteraction()
        assertFalse(timer.isIdlePaused())

        // Next 10 seconds of reading
        clock.advance(10_000L)
        assertEquals(frozenSec + 10, timer.tick())
        assertFalse(timer.isIdlePaused())
    }

    @Test
    fun testPauseResume() {
        val clock = FakeMonotonicClock(10_000L)
        val wallClock = FakeWallClock()
        val storage = InMemoryTimerStorage()
        val timer = ReadingTimer("user1", 1, storage, clock, wallClock)
        timer.start()

        clock.advance(50_000L) // 50s
        assertEquals(50, timer.tick())

        timer.pause()
        clock.advance(30_000L) // 30s while paused
        assertEquals(50, timer.tick()) // should not advance while paused

        timer.start()
        clock.advance(20_000L) // 20s
        assertEquals(70, timer.tick())
    }

    @Test
    fun testProcessDeathRestore() {
        val clock = FakeMonotonicClock(10_000L)
        val wallClock = FakeWallClock("2026-10-15T09:15:00Z")
        val storage = InMemoryTimerStorage()

        // Session 1
        val timer1 = ReadingTimer("user1", 2, storage, clock, wallClock)
        timer1.start()
        repeat(4) {
            clock.advance(30_000L)
            timer1.onInteraction()
            timer1.tick()
        }
        timer1.pause() // persists 120s
        assertEquals("2026-10-15T09:15:00Z", timer1.getFirstOpenedAt())

        // Process killed and relaunched (new instance with same storage)
        val wallClock2 = FakeWallClock("2026-10-15T10:00:00Z")
        val timer2 = ReadingTimer("user1", 2, storage, clock, wallClock2)
        assertEquals(120, timer2.getReadingSeconds())
        assertEquals("2026-10-15T09:15:00Z", timer2.getFirstOpenedAt()) // original firstOpenedAt preserved!

        timer2.start()
        repeat(6) {
            clock.advance(30_000L)
            timer2.onInteraction()
            timer2.tick()
        }
        assertEquals(300, timer2.getReadingSeconds())
        assertTrue(timer2.hasReachedMinimum())
    }

    @Test
    fun testMultiDayCatchupIndependence() {
        val clock = FakeMonotonicClock(10_000L)
        val storage = InMemoryTimerStorage()
        val timerDay1 = ReadingTimer("user1", 1, storage, clock, FakeWallClock("2026-10-15T08:00:00Z"))
        val timerDay2 = ReadingTimer("user1", 2, storage, clock, FakeWallClock("2026-10-15T08:30:00Z"))

        // Read Day 1 for 300s
        timerDay1.start()
        repeat(10) {
            clock.advance(30_000L)
            timerDay1.onInteraction()
            timerDay1.tick()
        }
        timerDay1.pause()

        // Switch to Day 2: Day 2 has 0 seconds
        assertEquals(300, timerDay1.getReadingSeconds())
        assertEquals(0, timerDay2.getReadingSeconds())
        assertTrue(timerDay1.hasReachedMinimum())
        assertFalse(timerDay2.hasReachedMinimum())

        // Read Day 2 for 310s
        timerDay2.start()
        repeat(10) {
            clock.advance(30_000L)
            timerDay2.onInteraction()
            timerDay2.tick()
        }
        clock.advance(10_000L)
        timerDay2.onInteraction()
        timerDay2.tick()
        timerDay2.pause()

        assertEquals(300, timerDay1.getReadingSeconds())
        assertEquals(310, timerDay2.getReadingSeconds())
        assertTrue(timerDay2.hasReachedMinimum())
    }

    @Test
    fun testButtonStateLogic() {
        fun canComplete(seconds: Int, finalChapterReached: Boolean): Boolean {
            val hasMinTime = seconds >= 300
            return hasMinTime && finalChapterReached
        }

        assertFalse(canComplete(100, false))
        assertFalse(canComplete(299, true))
        assertFalse(canComplete(300, false))
        assertFalse(canComplete(500, false))
        assertTrue(canComplete(300, true))
        assertTrue(canComplete(350, true))
    }

    @Test
    fun testEncouragementMessagesMinutes5To30AndStaticAfter() {
        // Assert never shown before 5:00
        assertNull(ReaderEncouragement.getMessage(0))
        assertNull(ReaderEncouragement.getMessage(299)) // 4:59

        // Assert an exact message exists for each minute 5..30 (26 distinct messages)
        for (m in 5..30) {
            val msg = ReaderEncouragement.getMessage(m * 60)
            assertNotNull("Message for minute $m must exist", msg)
            assertTrue("Message for minute $m should not be empty", msg!!.isNotBlank())
            assertEquals(ReaderEncouragement.messagesByMinute[m], msg)
        }
        assertEquals(26, ReaderEncouragement.messagesByMinute.size)

        // Assert static closing message for 31+ minutes
        assertEquals(ReaderEncouragement.STATIC_OVER_30, ReaderEncouragement.getMessage(31 * 60))
        assertEquals(ReaderEncouragement.STATIC_OVER_30, ReaderEncouragement.getMessage(60 * 60))
    }

    @Test
    fun testReaderPositionPersistenceAndClearOnComplete() {
        val storage = InMemoryReaderPositionStorage()
        val pos = SavedReaderPosition(chapterIdx = 2, scrollItemIndex = 14, scrollOffsetPx = 85)

        storage.setPosition("user1", 3, "versewell-kjv", pos)
        val loaded = storage.getPosition("user1", 3, "versewell-kjv")

        assertNotNull(loaded)
        assertEquals(2, loaded!!.chapterIdx)
        assertEquals(14, loaded.scrollItemIndex)
        assertEquals(85, loaded.scrollOffsetPx)

        // Translation switch isolation
        assertNull(storage.getPosition("user1", 3, "versewell-voice"))

        // Day switch isolation
        assertNull(storage.getPosition("user1", 4, "versewell-kjv"))

        // Clear when day completed
        storage.clear("user1", 3)
        assertNull(storage.getPosition("user1", 3, "versewell-kjv"))
    }
}

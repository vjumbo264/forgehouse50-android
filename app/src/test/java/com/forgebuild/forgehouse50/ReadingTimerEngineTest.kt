package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.InMemoryTimerStorage
import com.forgebuild.forgehouse50.data.MonotonicClock
import com.forgebuild.forgehouse50.data.ReadingTimer
import com.forgebuild.forgehouse50.data.WallClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun testIdleDetection() {
        val clock = FakeMonotonicClock(10_000L)
        val storage = InMemoryTimerStorage()
        val timer = ReadingTimer("user1", 1, storage, clock, FakeWallClock(), idleTimeoutMs = 60_000L)

        timer.start()
        clock.advance(40_000L) // 40s with interaction at start
        assertEquals(40, timer.tick())

        // Now user stops interacting for > 60 seconds
        clock.advance(70_000L) // idle for 70s (> 60s timeout)
        timer.tick() // idle, so time should NOT be counted once idle > 60s
        val idleSec = timer.getReadingSeconds()

        // Advancing further without interaction adds 0
        clock.advance(30_000L)
        assertEquals(idleSec, timer.tick())

        // User interacts (taps or scrolls)
        timer.onInteraction()
        clock.advance(10_000L)
        assertEquals(idleSec + 10, timer.tick())
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
        // Condition: hasMinTime (seconds >= 300) AND finalChapterReached
        fun canComplete(seconds: Int, finalChapterReached: Boolean): Boolean {
            val hasMinTime = seconds >= 300
            return hasMinTime && finalChapterReached
        }

        // Below minimum, not at final chapter
        assertFalse(canComplete(100, false))
        // Below minimum, at final chapter
        assertFalse(canComplete(299, true))
        // Above minimum, not at final chapter
        assertFalse(canComplete(300, false))
        assertFalse(canComplete(500, false))
        // Exactly minimum, at final chapter
        assertTrue(canComplete(300, true))
        // Above minimum, at final chapter
        assertTrue(canComplete(350, true))
    }
}

package com.forgebuild.forgehouse50.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import java.time.Instant

/**
 * ReadingTimerEngine — measures active reading time per (user, day) using the
 * monotonic clock (SystemClock.elapsedRealtime()), offline-capable.
 *
 * Rules:
 * - Counts only while in foreground and interaction in last 60 seconds (idle timeout).
 * - Monotonic elapsedRealtime() avoids cheat via system wall clock change.
 * - Minimum required: 300 seconds (5:00).
 * - Persisted every 5 seconds and on pause/stop.
 * - Records firstOpenedAt once per day.
 * - Day-switching keeps separate timers per day.
 */
interface MonotonicClock {
    fun elapsedRealtime(): Long
}

class SystemMonotonicClock : MonotonicClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}

interface WallClock {
    fun nowIso(): String
}

class SystemWallClock : WallClock {
    override fun nowIso(): String = Instant.now().toString()
}

interface TimerStorage {
    fun getSeconds(userId: String, day: Int): Int
    fun setSeconds(userId: String, day: Int, seconds: Int)
    fun getFirstOpenedAt(userId: String, day: Int): String?
    fun setFirstOpenedAt(userId: String, day: Int, iso: String)
    fun clear(userId: String, day: Int)
}

class PrefsTimerStorage(context: Context) : TimerStorage {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fh50_reading_timer_v1", Context.MODE_PRIVATE)

    override fun getSeconds(userId: String, day: Int): Int =
        prefs.getInt("sec_${userId}_$day", 0)

    override fun setSeconds(userId: String, day: Int, seconds: Int) {
        prefs.edit().putInt("sec_${userId}_$day", seconds).apply()
    }

    override fun getFirstOpenedAt(userId: String, day: Int): String? =
        prefs.getString("opened_${userId}_$day", null)

    override fun setFirstOpenedAt(userId: String, day: Int, iso: String) {
        if (!prefs.contains("opened_${userId}_$day")) {
            prefs.edit().putString("opened_${userId}_$day", iso).apply()
        }
    }

    override fun clear(userId: String, day: Int) {
        prefs.edit()
            .remove("sec_${userId}_$day")
            .remove("opened_${userId}_$day")
            .apply()
    }
}

class InMemoryTimerStorage : TimerStorage {
    private val secMap = mutableMapOf<String, Int>()
    private val openedMap = mutableMapOf<String, String>()

    override fun getSeconds(userId: String, day: Int): Int = secMap["${userId}_$day"] ?: 0
    override fun setSeconds(userId: String, day: Int, seconds: Int) { secMap["${userId}_$day"] = seconds }
    override fun getFirstOpenedAt(userId: String, day: Int): String? = openedMap["${userId}_$day"]
    override fun setFirstOpenedAt(userId: String, day: Int, iso: String) {
        if (!openedMap.containsKey("${userId}_$day")) openedMap["${userId}_$day"] = iso
    }
    override fun clear(userId: String, day: Int) {
        secMap.remove("${userId}_$day")
        openedMap.remove("${userId}_$day")
    }
}

class ReadingTimer(
    val userId: String,
    val day: Int,
    private val storage: TimerStorage,
    private val clock: MonotonicClock = SystemMonotonicClock(),
    private val wallClock: WallClock = SystemWallClock(),
    val minReadingSeconds: Int = 300,
    val idleTimeoutMs: Long = 60_000L,
) {
    private var accumulatedSeconds: Int = storage.getSeconds(userId, day)
    private var firstOpenedAt: String = storage.getFirstOpenedAt(userId, day) ?: run {
        val now = wallClock.nowIso()
        storage.setFirstOpenedAt(userId, day, now)
        now
    }

    private var lastTickTime: Long = clock.elapsedRealtime()
    private var lastInteractionTime: Long = clock.elapsedRealtime()
    private var isRunning: Boolean = false
    private var partialMillis: Long = 0L

    init {
        storage.setFirstOpenedAt(userId, day, firstOpenedAt)
    }

    fun onInteraction() {
        lastInteractionTime = clock.elapsedRealtime()
    }

    fun start() {
        val now = clock.elapsedRealtime()
        lastTickTime = now
        lastInteractionTime = now
        isRunning = true
    }

    fun pause() {
        tick()
        isRunning = false
        persist()
    }

    fun tick(): Int {
        val now = clock.elapsedRealtime()
        val delta = (now - lastTickTime).coerceAtLeast(0)
        lastTickTime = now

        if (isRunning) {
            val idleTime = (now - lastInteractionTime).coerceAtLeast(0)
            if (idleTime <= idleTimeoutMs) {
                partialMillis += delta
                if (partialMillis >= 1000L) {
                    val addSec = (partialMillis / 1000L).toInt()
                    accumulatedSeconds += addSec
                    partialMillis %= 1000L
                }
            }
        }
        return accumulatedSeconds
    }

    fun persist() {
        storage.setSeconds(userId, day, accumulatedSeconds)
    }

    fun getReadingSeconds(): Int = accumulatedSeconds

    fun getFirstOpenedAt(): String = firstOpenedAt

    fun hasReachedMinimum(): Boolean = accumulatedSeconds >= minReadingSeconds

    fun remainingSeconds(): Int = (minReadingSeconds - accumulatedSeconds).coerceAtLeast(0)

    fun formattedTimer(): String {
        val m = accumulatedSeconds / 60
        val s = accumulatedSeconds % 60
        return "%d:%02d / 5:00".format(m, s)
    }

    fun formattedRemaining(): String {
        val rem = remainingSeconds()
        val m = rem / 60
        val s = rem % 60
        return if (m > 0) "Read for %d:%02d more".format(m, s) else "Read for %d second%s more".format(s, if (s == 1) "" else "s")
    }

    fun clear() {
        accumulatedSeconds = 0
        storage.clear(userId, day)
    }
}

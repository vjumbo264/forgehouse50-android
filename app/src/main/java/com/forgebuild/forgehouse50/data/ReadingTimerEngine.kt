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
 * - When idle > 60s, marks isIdlePaused=true ("Paused. Touch the screen to continue").
 * - Any touch/interaction immediately resumes counting without credit for idle time.
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
        val now = clock.elapsedRealtime()
        if (isRunning && (now - lastInteractionTime) > idleTimeoutMs) {
            // Resuming from idle: reset lastTickTime to avoid crediting the idle pause duration
            lastTickTime = now
        }
        lastInteractionTime = now
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

    fun isRunning(): Boolean = isRunning

    fun isIdlePaused(): Boolean {
        if (!isRunning) return false
        val idle = (clock.elapsedRealtime() - lastInteractionTime).coerceAtLeast(0)
        return idle > idleTimeoutMs
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
        return if (accumulatedSeconds < minReadingSeconds) {
            "%d:%02d / 5:00".format(m, s)
        } else {
            "%d:%02d".format(m, s)
        }
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

/**
 * 26 scripture-aware, sincere encouragement messages for minutes 5 to 30,
 * plus a static closing message for 31+ minutes.
 * No emojis, no cheesiness. Shown only after 5:00.
 */
object ReaderEncouragement {
    val messagesByMinute: Map<Int, String> = mapOf(
        5 to "Five minutes in. Steady and faithful.",
        6 to "The word is a lamp unto your feet.",
        7 to "Steadfast and focused in the scriptures.",
        8 to "Deepening your walk with every verse.",
        9 to "Quiet devotion in the living word.",
        10 to "Ten minutes rooted in truth and grace.",
        11 to "Drinking deeply from the fountain of life.",
        12 to "Attentive hearing, patient understanding.",
        13 to "Letting the word dwell in you richly.",
        14 to "A heart inclined to wisdom and peace.",
        15 to "Fifteen minutes honoring the Lord's call.",
        16 to "Reflecting on the promises of God.",
        17 to "Renewing your mind chapter by chapter.",
        18 to "Grounded in hope, nourished in spirit.",
        19 to "Delighting in the statutes of the Lord.",
        20 to "Twenty minutes of devotion and diligence.",
        21 to "Hidden in the heart, guarded in love.",
        22 to "Light shining in every line and passage.",
        23 to "Treasuring truth above all treasures.",
        24 to "Still waters and restored soul.",
        25 to "Twenty-five minutes of faithful persistence.",
        26 to "Firm foundation built upon the rock.",
        27 to "Hearing the voice of the Shepherd.",
        28 to "Abiding in the vine, bearing good fruit.",
        29 to "Perseverance that produces character.",
        30 to "Thirty minutes in the word. Well done.",
    )
    const val STATIC_OVER_30 = "Abiding in the word. Keep drawing near."

    fun getMessage(seconds: Int): String? {
        val minutes = seconds / 60
        if (minutes < 5) return null
        return messagesByMinute[minutes] ?: STATIC_OVER_30
    }
}

/**
 * Reading position persistence: restores exact day, chapter, and scroll offset
 * when returning from menus or app restarts, cleared on day completion.
 */
data class SavedReaderPosition(
    val chapterIdx: Int,
    val scrollItemIndex: Int,
    val scrollOffsetPx: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

interface ReaderPositionStorage {
    fun getPosition(userId: String, day: Int, translation: String): SavedReaderPosition?
    fun setPosition(userId: String, day: Int, translation: String, position: SavedReaderPosition)
    fun clear(userId: String, day: Int)
}

class PrefsReaderPositionStorage(context: Context) : ReaderPositionStorage {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fh50_reader_position_v1", Context.MODE_PRIVATE)

    override fun getPosition(userId: String, day: Int, translation: String): SavedReaderPosition? {
        val raw = prefs.getString("pos_${userId}_${day}_$translation", null) ?: return null
        val parts = raw.split(":")
        if (parts.size < 3) return null
        val cIdx = parts[0].toIntOrNull() ?: 0
        val itemIdx = parts[1].toIntOrNull() ?: 0
        val offset = parts[2].toIntOrNull() ?: 0
        val ts = parts.getOrNull(3)?.toLongOrNull() ?: 0L
        return SavedReaderPosition(cIdx, itemIdx, offset, ts)
    }

    override fun setPosition(userId: String, day: Int, translation: String, position: SavedReaderPosition) {
        val raw = "${position.chapterIdx}:${position.scrollItemIndex}:${position.scrollOffsetPx}:${position.timestamp}"
        prefs.edit().putString("pos_${userId}_${day}_$translation", raw).apply()
    }

    override fun clear(userId: String, day: Int) {
        val editor = prefs.edit()
        val prefix = "pos_${userId}_${day}_"
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }
}

class InMemoryReaderPositionStorage : ReaderPositionStorage {
    private val map = mutableMapOf<String, SavedReaderPosition>()

    override fun getPosition(userId: String, day: Int, translation: String): SavedReaderPosition? =
        map["${userId}_${day}_$translation"]

    override fun setPosition(userId: String, day: Int, translation: String, position: SavedReaderPosition) {
        map["${userId}_${day}_$translation"] = position
    }

    override fun clear(userId: String, day: Int) {
        val prefix = "${userId}_${day}_"
        map.keys.filter { it.startsWith(prefix) }.toList().forEach { map.remove(it) }
    }
}
